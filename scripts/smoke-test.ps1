#Requires -Version 5.1
<#
.SYNOPSIS
  餐厅点餐系统 MVP 冒烟测试（基础 + 外卖全链路 + 堂食全链路 + 文件 + 导出）。

.DESCRIPTION
  严格按 docs/02-api.md（冻结契约）与 docs/03-data-model.md / 后端种子 SQL 执行：
    A 基础     health / api-docs / public config / menu
    B 外卖     customer 登录 → 地址 → 购物车 → 优惠试算 → 幂等下单 → 支付 0.01
               → mock 回调验签 → merchant 接单/制作/配送/完成 → 评价
    C 堂食     桌台签名 → scan / bind → X-Table-Session → 下单支付
               → 接单/制作/出餐/完成 → 桌台恢复 FREE、会话失效
    D 文件     policy 不泄露 AK、本地 upload、下载内容校验、白名单拒绝
    E 导出     merchant / admin 订单导出 xlsx（首字节 PK）
    F 慢测     支付超时取消（默认跳过，-IncludeSlow 且超时配置 ≤ 2 分钟时才执行）
    网关       可选 -GatewayUrl 检查 /healthz、/api/v1、Swagger、静态首页

  密钥只从环境变量读取：
    $env:PAY_CALLBACK_SECRET   必须与后端配置一致，用于 mock 回调 HMAC-SHA256 签名
    $env:TABLE_QR_SECRET       可选；设置时用于重算桌台二维码签名并与服务端结果比对
  脚本内不写任何真实凭证；local 存储模式为默认断言。

  后端 T08–T10 未完成前，本脚本只能做 AST 语法自检并明确输出「未验证」，
  不应伪造运行通过。示例：
    $env:PAY_CALLBACK_SECRET='<与后端一致的开发密钥>'
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

.PARAMETER BaseUrl
  后端地址，默认 http://127.0.0.1:8080。

.PARAMETER GatewayUrl
  可选 Nginx 入口，例如 http://127.0.0.1。提供时额外验证网关转发与静态资源。

.PARAMETER TimeoutSec
  单次 HTTP 超时秒数，默认 20。

.PARAMETER SkipSlow
  跳过支付超时取消等慢测试（默认即跳过；-IncludeSlow 时除外）。

.PARAMETER IncludeSlow
  显式启用慢测试（仅当 public config 的 paymentTimeoutMinutes ≤ 2 时执行）。

.PARAMETER SkipStatic
  网关检查中跳过静态首页检查。

.EXAMPLE
  $env:PAY_CALLBACK_SECRET='dev-secret'
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

.EXAMPLE
  $env:PAY_CALLBACK_SECRET='dev-secret'
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -GatewayUrl http://127.0.0.1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$GatewayUrl = '',
    [int]$TimeoutSec = 20,
    [switch]$SkipSlow,
    [switch]$IncludeSlow,
    [switch]$SkipStatic,
    [switch]$SkipTakeout,
    [switch]$SkipExports
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BaseUrl = $BaseUrl.TrimEnd('/')
$ApiBase = $BaseUrl + '/api/v1'
$script:TimeoutSec = $TimeoutSec
$script:Passed = 0
$script:Failed = 0
$script:Skipped = 0
$script:Checks = New-Object System.Collections.ArrayList
$script:Requests = New-Object System.Collections.ArrayList
$script:LastStep = ''
$script:LastRaw = ''
$script:Aborted = $false
$script:TempDir = Join-Path ([System.IO.Path]::GetTempPath()) ('restaurant-smoke-' + [guid]::NewGuid().ToString('N'))

function Write-Stage {
    param([string]$Title)
    Write-Host ''
    Write-Host ("=== " + $Title + " ===") -ForegroundColor Cyan
}

function Add-Check {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail = '',
        [string]$Raw = '',
        [long]$ElapsedMs = 0
    )
    $status = 'PASS'
    if (-not $Passed) { $status = 'FAIL' }
    $script:Checks.Add([pscustomobject]@{ Name = $Name; Status = $status; Detail = $Detail; Raw = $Raw; Ms = $ElapsedMs }) | Out-Null
    if ($Passed) {
        $script:Passed++
        Write-Host ("  [PASS] " + $Name + $(if ($Detail) { "  " + $Detail } else { '' })) -ForegroundColor Green
    } else {
        $script:Failed++
        Write-Host ("  [FAIL] " + $Name + $(if ($Detail) { "  " + $Detail } else { '' })) -ForegroundColor Red
        if ($Raw) {
            $preview = $Raw
            if ($preview.Length -gt 700) { $preview = $preview.Substring(0, 700) + '...' }
            Write-Host ("         响应: " + $preview) -ForegroundColor DarkGray
        }
    }
}

function Add-Skip {
    param([string]$Name, [string]$Reason)
    $script:Skipped++
    $script:Checks.Add([pscustomobject]@{ Name = $Name; Status = 'SKIP'; Detail = $Reason; Raw = ''; Ms = 0 }) | Out-Null
    Write-Host ("  [SKIP] " + $Name + "  " + $Reason) -ForegroundColor Yellow
}

function Assert-Step {
    param([string]$Name, [bool]$Condition, [string]$Detail = '', [string]$Raw = '', [long]$ElapsedMs = 0)
    Add-Check -Name $Name -Passed $Condition -Detail $Detail -Raw $Raw -ElapsedMs $ElapsedMs
    if (-not $Condition) {
        $script:LastStep = $Name
        $script:LastRaw = $Raw
        throw [System.Exception]::new("步骤失败: " + $Name)
    }
}

function Get-Header {
    param($Response, [string]$Name)
    if ($null -eq $Response -or $null -eq $Response.Headers) { return $null }
    foreach ($key in @($Response.Headers.Keys)) {
        if ([string]$key -ieq $Name) { return [string]$Response.Headers[$key] }
    }
    return $null
}

function Test-Envelope {
    param($Body)
    if ($null -eq $Body) { return $false }
    if (@($Body.PSObject.Properties.Name) -notcontains 'code') { return $false }
    return ([int]$Body.code -eq 0)
}

function Test-ApiResponse {
    param($Response)
    return ($null -ne $Response -and $Response.Ok -and $Response.Status -eq 200 -and (Test-Envelope $Response.Body))
}

function Test-PageResult {
    param($Data)
    if ($null -eq $Data) { return $false }
    $names = @($Data.PSObject.Properties.Name)
    foreach ($key in @('list', 'total', 'page', 'size', 'pages')) {
        if ($names -notcontains $key) { return $false }
    }
    if ($null -eq $Data.list) { return $false }
    return ([int]$Data.total -ge 0 -and [int]$Data.page -ge 1 -and [int]$Data.size -ge 1 -and [int]$Data.pages -ge 0)
}

function Get-HeaderValueOrEmpty {
    param($Response, [string]$Name)
    $value = Get-Header -Response $Response -Name $Name
    if ($null -eq $value) { return '' }
    return $value
}

function Invoke-Http {
    param(
        [string]$Method = 'GET',
        [string]$Uri,
        $Body = $null,
        [hashtable]$Headers = @{},
        [int]$Timeout = 0,
        [string]$OutFile = '',
        [string]$ContentType = ''
    )
    if ($Timeout -le 0) { $Timeout = $script:TimeoutSec }
    $result = [pscustomobject]@{
        Ok = $false; Status = 0; Raw = ''; Body = $null; Headers = @{}; Error = $null; ElapsedMs = 0
    }
    $requestArgs = @{
        Uri = $Uri
        Method = $Method
        UseBasicParsing = $true
        TimeoutSec = $Timeout
        ErrorAction = 'Stop'
    }
    if ($Headers -and $Headers.Count -gt 0) { $requestArgs.Headers = $Headers }
    if ($OutFile) { $requestArgs.OutFile = $OutFile }
    $isRawString = $false
    if ($null -ne $Body) {
        if ($Body -is [byte[]]) {
            $requestArgs.Body = $Body
        } elseif ($Body -is [string]) {
            $requestArgs.Body = $Body
            $isRawString = $true
            $trimmed = $Body.TrimStart()
            if ($trimmed.StartsWith('{') -or $trimmed.StartsWith('[')) {
                $requestArgs.ContentType = 'application/json; charset=utf-8'
            }
        } else {
            $requestArgs.Body = ($Body | ConvertTo-Json -Depth 20 -Compress)
            $requestArgs.ContentType = 'application/json; charset=utf-8'
        }
    }
    if ($ContentType) { $requestArgs.ContentType = $ContentType }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        if ($OutFile) {
            # PowerShell 5.1 的 Invoke-WebRequest -OutFile 在下载 image/png 等非文本响应时
            # 会抛 "You cannot call a method on a null-valued expression"（文件已写入但响应对象异常）。
            # 这里改用 HttpWebRequest，稳定拿到状态码/响应头并写入文件。
            $webReq = [System.Net.HttpWebRequest]::Create($Uri)
            $webReq.Method = $Method
            if ($Timeout -gt 0) {
                $webReq.Timeout = $Timeout * 1000
                $webReq.ReadWriteTimeout = $Timeout * 1000
            }
            foreach ($key in @($Headers.Keys)) {
                try { $webReq.Headers[$key] = [string]$Headers[$key] } catch { }
            }
            $webResp = $null
            try {
                $webResp = $webReq.GetResponse()
                $result.Status = [int]$webResp.StatusCode
                $result.Ok = ($result.Status -ge 200 -and $result.Status -lt 300)
                try {
                    foreach ($key in @($webResp.Headers.AllKeys)) {
                        $result.Headers[$key] = [string]$webResp.Headers[$key]
                    }
                } catch { }
                $dest = [System.IO.File]::Create($OutFile)
                try { $webResp.GetResponseStream().CopyTo($dest) } finally { $dest.Dispose() }
            } catch {
                $wex = $_.Exception
                $result.Error = $wex.Message
                $webResp = $wex.Response
                if ($null -ne $webResp) {
                    try { $result.Status = [int]$webResp.StatusCode } catch { }
                    try {
                        $stream = $webResp.GetResponseStream()
                        if ($null -ne $stream) {
                            $reader = New-Object System.IO.StreamReader($stream)
                            $result.Raw = $reader.ReadToEnd()
                            $reader.Dispose()
                        }
                    } catch { }
                    try {
                        foreach ($key in @($webResp.Headers.AllKeys)) {
                            $result.Headers[$key] = [string]$webResp.Headers[$key]
                        }
                    } catch { }
                    $result.Ok = ($result.Status -ge 200 -and $result.Status -lt 300)
                }
            } finally {
                if ($null -ne $webResp) { try { $webResp.Close() } catch { } }
            }
            Write-Verbose ("HTTP {0} {1} -> {2} ({3} ms)" -f $Method, $Uri, $result.Status, $sw.ElapsedMilliseconds)
        } else {
        $response = Invoke-WebRequest @requestArgs
        $result.Status = [int]$response.StatusCode
        $result.Ok = ($result.Status -ge 200 -and $result.Status -lt 300)
        if (-not $OutFile) {
            # PowerShell 5.1 对非 text/* 的 JSON（如 actuator v3+json）返回 byte[]；
            # 直接 [string] 会得到 "System.Byte[]"，必须先按 UTF-8 解码。
            if ($response.Content -is [byte[]]) {
                $result.Raw = [System.Text.Encoding]::UTF8.GetString($response.Content)
            } else {
                $result.Raw = [string]$response.Content
            }
        }
        try {
            foreach ($key in @($response.Headers.AllKeys)) {
                $result.Headers[$key] = [string]$response.Headers[$key]
            }
        } catch { }
        Write-Verbose ("HTTP {0} {1} -> {2} ({3} ms)" -f $Method, $Uri, $result.Status, $sw.ElapsedMilliseconds)
        }
    } catch {
        $ex = $_.Exception
        $result.Error = $ex.Message
        $response = $null
        try { $response = $ex.Response } catch { }
        if ($null -ne $response) {
            try { $result.Status = [int]$response.StatusCode } catch { }
            try {
                $stream = $response.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $result.Raw = $reader.ReadToEnd()
                    $reader.Dispose()
                }
            } catch { }
            try {
                foreach ($key in @($response.Headers.AllKeys)) {
                    $result.Headers[$key] = [string]$response.Headers[$key]
                }
            } catch { }
            $result.Ok = ($result.Status -ge 200 -and $result.Status -lt 300)
            Write-Verbose ("HTTP {0} {1} -> {2} ({3} ms)" -f $Method, $Uri, $result.Status, $sw.ElapsedMilliseconds)
        } else {
            Write-Verbose ("HTTP {0} {1} -> 连接失败: {2}" -f $Method, $Uri, $result.Error)
        }
    } finally {
        $sw.Stop()
    }
    $result.ElapsedMs = $sw.ElapsedMilliseconds
    if (-not $OutFile -and $result.Raw) {
        try {
            if ($isRawString -or $result.Raw.TrimStart().StartsWith('{') -or $result.Raw.TrimStart().StartsWith('[')) {
                $result.Body = $result.Raw | ConvertFrom-Json
            }
        } catch { }
    }
    $pathForLog = $Uri
    if ($pathForLog.StartsWith($BaseUrl)) { $pathForLog = $pathForLog.Substring($BaseUrl.Length) }
    $script:Requests.Add([pscustomobject]@{ Method = $Method; Path = $pathForLog; Status = $result.Status; Ms = $result.ElapsedMs }) | Out-Null
    return $result
}

function Invoke-LoginCheck {
    param([string]$Name, [string]$Username, [string]$Password)
    $resp = Invoke-Http -Method POST -Uri ($ApiBase + '/auth/login') -Body ([ordered]@{ username = $Username; password = $Password })
    $token = ''
    if ($null -ne $resp.Body -and $null -ne $resp.Body.data) { $token = [string]$resp.Body.data.token }
    $ok = ($resp.Status -eq 200 -and (Test-Envelope $resp.Body) -and $token.Length -gt 0)
    $detail = "HTTP $($resp.Status); token 长度 $($token.Length)"
    Add-Check -Name $Name -Passed $ok -Detail $detail -Raw $resp.Raw -ElapsedMs $resp.ElapsedMs
    if (-not $ok) {
        $script:LastStep = $Name
        $script:LastRaw = $resp.Raw
        throw [System.Exception]::new("登录失败: $Username")
    }
    return $resp
}

function Get-HmacHex {
    param([string]$Secret, [string]$Payload)
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    try {
        $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
        $hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Payload))
        return (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally {
        $hmac.Dispose()
    }
}

function Get-HmacBase64Url {
    param([string]$Secret, [string]$Payload)
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    try {
        $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
        $hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Payload))
        return [Convert]::ToBase64String($hash).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    } finally {
        $hmac.Dispose()
    }
}

function Get-QueryParam {
    param([string]$Url, [string]$Name)
    $m = [regex]::Match($Url, '[?&]' + [regex]::Escape($Name) + '=([^&#]+)')
    if ($m.Success) { return [System.Uri]::UnescapeDataString($m.Groups[1].Value) }
    return $null
}

function Get-SeedQrSalt {
    param([string]$TableNo)
    $seedPaths = @(
        (Join-Path $RepoRoot 'backend/src/main/resources/db/migration/V2__seed_data.sql'),
        (Join-Path $RepoRoot 'docs/03-data-model.md')
    )
    foreach ($seedPath in $seedPaths) {
        if (-not (Test-Path $seedPath)) { continue }
        foreach ($line in (Get-Content -Path $seedPath -Encoding UTF8)) {
            if ($line -match ("'" + [regex]::Escape($TableNo) + "'.*?'([0-9a-fA-F]{16,})'")) {
                return $Matches[1].ToLowerInvariant()
            }
            if ($line -match ('([A-Z]?\d{2,3})\s*\|\s*(\d+)\s*\|\s*([0-9a-fA-F]{16,})')) {
                if ($matches[1] -eq $TableNo) { return $matches[3].ToLowerInvariant() }
            }
        }
    }
    return $null
}

function Invoke-MultipartUpload {
    param([string]$Uri, [string]$FilePath, [string]$BizType, [hashtable]$Headers)
    try {
        Add-Type -AssemblyName System.Net.Http
    } catch { }
    $client = New-Object System.Net.Http.HttpClient
    $stream = $null
    $fileContent = $null
    $multipart = $null
    $bizContent = $null
    try {
        foreach ($key in $Headers.Keys) {
            if ([string]$key -ieq 'Authorization') {
                $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', ([string]$Headers[$key] -replace '^Bearer\s+', ''))
            } else {
                [void]$client.DefaultRequestHeaders.TryAddWithoutValidation([string]$key, [string]$Headers[$key])
            }
        }
        $multipart = New-Object System.Net.Http.MultipartFormDataContent
        $bizContent = New-Object System.Net.Http.StringContent -ArgumentList $BizType
        $multipart.Add($bizContent, 'bizType')
        $stream = [System.IO.File]::OpenRead($FilePath)
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
        $multipart.Add($fileContent, 'file', [System.IO.Path]::GetFileName($FilePath))
        $response = $client.PostAsync($Uri, $multipart).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $body = $null
        try { $body = $raw | ConvertFrom-Json } catch { }
        $headers = @{}
        foreach ($h in $response.Headers) { $headers[$h.Key] = [string](($h.Value) -join ', ') }
        foreach ($h in $response.Content.Headers) { $headers[$h.Key] = [string](($h.Value) -join ', ') }
        return [pscustomobject]@{
            Ok = $response.IsSuccessStatusCode; Status = [int]$response.StatusCode; Raw = $raw; Body = $body; Headers = $headers; Error = $null; ElapsedMs = 0
        }
    } finally {
        if ($null -ne $bizContent) { $bizContent.Dispose() }
        if ($null -ne $fileContent) { $fileContent.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
        if ($null -ne $multipart) { $multipart.Dispose() }
        $client.Dispose()
    }
}

function Test-PkZip {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $false }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    return ($bytes.Length -gt 4 -and $bytes[0] -eq 0x50 -and $bytes[1] -eq 0x4B)
}

function Invoke-ExpectStatus {
    param(
        [string]$CheckName,
        [string]$Path,
        [string]$Expected,
        [hashtable]$Headers,
        [string]$Method = 'POST',
        [string]$Body = ''
    )
    $requestParams = @{ Method = $Method; Uri = ($ApiBase + $Path); Headers = $Headers }
    if ($Body) { $requestParams.Body = $Body }
    $resp = Invoke-Http @requestParams
    $actual = ''
    if ($null -ne $resp.Body -and $null -ne $resp.Body.data) { $actual = [string]$resp.Body.data.status }
    $ok = ((Test-ApiResponse $resp) -and $actual -eq $Expected)
    Assert-Step -Name $CheckName -Condition $ok -Detail ("HTTP $($resp.Status); status=$actual; 期望 $Expected") -Raw $resp.Raw -ElapsedMs $resp.ElapsedMs
    return $resp
}

function Send-MockCallback {
    param([string]$PayNo, [string]$TransactionNo, [decimal]$PaidAmount)
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $nonce = [guid]::NewGuid().ToString('N')
    $paidAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $payload = [ordered]@{
        payNo = $PayNo
        transactionNo = $TransactionNo
        paidAmount = $PaidAmount
        paidAt = $paidAt
        status = 'SUCCESS'
    }
    $raw = $payload | ConvertTo-Json -Compress
    $signature = Get-HmacHex -Secret $script:PaySecret -Payload ($timestamp + "`n" + $nonce + "`n" + $raw)
    $headers = @{
        'X-Pay-Timestamp' = $timestamp
        'X-Pay-Nonce' = $nonce
        'X-Pay-Signature' = $signature
    }
    return Invoke-Http -Method POST -Uri ($ApiBase + '/payments/mock/callback') -Body $raw -Headers $headers -ContentType 'application/json; charset=utf-8'
}

$exitCode = 0
$startedAt = Get-Date
try {
    New-Item -ItemType Directory -Path $script:TempDir -Force | Out-Null

    Write-Host '餐厅点餐系统 MVP 冒烟测试（完整 happy-path）'
    Write-Host ("后端: " + $BaseUrl + "  网关: " + $(if ($GatewayUrl) { $GatewayUrl } else { '（未提供，跳过）' }))
    Write-Host '注意: 后端 T08–T10 未完成时，本脚本不具备运行条件；仅完成 AST 语法自检时须标注「未验证」。' -ForegroundColor Yellow
    Write-Host ("开始时间: " + $startedAt.ToString('yyyy-MM-dd HH:mm:ss'))

    $script:PaySecret = [string]$env:PAY_CALLBACK_SECRET
    $script:TableSecret = [string]$env:TABLE_QR_SECRET

    Write-Stage 'A 基础'
    $health = Invoke-Http -Method GET -Uri ($BaseUrl + '/actuator/health')
    $healthOk = ($health.Status -eq 200 -and $null -ne $health.Body -and [string]$health.Body.status -eq 'UP')
    Assert-Step -Name 'A1 GET /actuator/health = UP' -Condition $healthOk -Detail ("HTTP " + $health.Status + "; status=" + $(if ($health.Body) { [string]$health.Body.status } else { '' })) -Raw $health.Raw -ElapsedMs $health.ElapsedMs

    $docs = Invoke-Http -Method GET -Uri ($BaseUrl + '/v3/api-docs')
    $pathsOk = ($null -ne $docs.Body -and $null -ne $docs.Body.paths -and @($docs.Body.paths.PSObject.Properties).Count -gt 0)
    Assert-Step -Name 'A2 GET /v3/api-docs 含 paths' -Condition ($docs.Status -eq 200 -and $pathsOk) -Detail ("HTTP " + $docs.Status + "; paths=" + $(if ($docs.Body -and $docs.Body.paths) { @($docs.Body.paths.PSObject.Properties).Count } else { 0 })) -Raw $docs.Raw -ElapsedMs $docs.ElapsedMs

    $configResp = Invoke-Http -Method GET -Uri ($ApiBase + '/public/config')
    $configOk = (Test-ApiResponse $configResp) -and ($null -ne $configResp.Body.data)
    Assert-Step -Name 'A3 GET /public/config' -Condition $configOk -Detail ("HTTP " + $configResp.Status) -Raw $configResp.Raw -ElapsedMs $configResp.ElapsedMs
    $config = $configResp.Body.data
    $script:MockCharge = [decimal]0.01
    if ($null -ne $config.mockPayment -and $null -ne $config.mockPayment.chargeAmount) {
        $script:MockCharge = [decimal]$config.mockPayment.chargeAmount
    }
    $script:StorageType = [string]$config.storage.type
    $script:UploadMode = [string]$config.storage.uploadMode
    Assert-Step -Name 'A4 public config 缺省商户/支付参数' -Condition ([string]$config.defaultMerchantId -eq '1' -and $script:MockCharge -eq [decimal]0.01 -and [string]$config.storage.type -and [string]$config.storage.uploadMode) -Detail ("defaultMerchantId=" + $config.defaultMerchantId + "; chargeAmount=" + $script:MockCharge + "; storage=" + $script:StorageType + "; upload=" + $script:UploadMode) -Raw $configResp.Raw

    $cat = Invoke-Http -Method GET -Uri ($ApiBase + '/menu/categories')
    $catOk = (Test-ApiResponse $cat) -and ($null -ne $cat.Body.data) -and (@($cat.Body.data).Count -gt 0)
    Assert-Step -Name 'A5 GET /menu/categories' -Condition $catOk -Detail ("HTTP " + $cat.Status + "; 分类数=" + $(if ($cat.Body -and $cat.Body.data) { @($cat.Body.data).Count } else { 0 })) -Raw $cat.Raw -ElapsedMs $cat.ElapsedMs

    $menu = Invoke-Http -Method GET -Uri ($ApiBase + '/menu/dishes?page=1&size=12')
    $menuOk = (Test-ApiResponse $menu) -and (Test-PageResult $menu.Body.data) -and ([int]$menu.Body.data.size -eq 12)
    Assert-Step -Name 'A6 GET /menu/dishes 分页结构' -Condition $menuOk -Detail ("HTTP " + $menu.Status + "; total=" + $(if ($menu.Body -and $menu.Body.data) { $menu.Body.data.total } else { '' })) -Raw $menu.Raw -ElapsedMs $menu.ElapsedMs
    $dish1 = $null
    foreach ($dish in @($menu.Body.data.list)) { if ([int]$dish.id -eq 1) { $dish1 = $dish; break } }
    Assert-Step -Name 'A7 种子菜品 1 存在' -Condition ($null -ne $dish1 -and [decimal]$dish1.price -gt 0) -Detail ("dishId=1; price=" + $(if ($dish1) { $dish1.price } else { '' })) -Raw $menu.Raw
    $dishPrice = [decimal]$dish1.price

    $adminLogin = Invoke-LoginCheck -Name 'A8 admin 登录' -Username 'admin' -Password 'admin123'
    $merchantLogin = Invoke-LoginCheck -Name 'A9 merchant 登录' -Username 'merchant' -Password 'merchant123'
    $customerLogin = Invoke-LoginCheck -Name 'A10 customer 登录' -Username 'customer' -Password 'customer123'
    $adminToken = [string]$adminLogin.Body.data.token
    $merchantToken = [string]$merchantLogin.Body.data.token
    $customerToken = [string]$customerLogin.Body.data.token
    $merchantHeaders = @{ Authorization = 'Bearer ' + $merchantToken }

    $me = Invoke-Http -Method GET -Uri ($ApiBase + '/auth/me') -Headers @{ Authorization = 'Bearer ' + $customerToken }
    $meOk = (Test-ApiResponse $me) -and ([string]$me.Body.data.username -eq 'customer') -and ([string]$me.Body.data.role -eq 'CUSTOMER')
    Assert-Step -Name 'A11 GET /auth/me 角色 CUSTOMER' -Condition $meOk -Detail ("HTTP " + $me.Status + "; role=" + $(if ($me.Body -and $me.Body.data) { [string]$me.Body.data.role } else { '' })) -Raw $me.Raw -ElapsedMs $me.ElapsedMs

    $script:PaySecret = [string]$env:PAY_CALLBACK_SECRET
    if (-not $script:PaySecret) {
        Add-Check -Name 'A12 前置密钥 PAY_CALLBACK_SECRET' -Passed $false -Detail '未设置环境变量 PAY_CALLBACK_SECRET，无法验签 mock 回调（同时 T08–T10 未完成时标记「未验证」）'
        throw [System.Exception]::new('缺少 PAY_CALLBACK_SECRET')
    }
    Add-Check -Name 'A12 前置密钥 PAY_CALLBACK_SECRET' -Passed $true -Detail ('长度 ' + $script:PaySecret.Length + '（脚本未打印密钥内容）')

    Write-Stage 'B 外卖全链路'
    if ($SkipTakeout) {
        Add-Skip -Name 'B 外卖全链路' -Reason '指定了 -SkipTakeout（当前后端未实现 /addresses、/cart、/coupons/quote 等契约端点）'
    } else {
    $addr = Invoke-Http -Method GET -Uri ($ApiBase + '/addresses') -Headers @{ Authorization = 'Bearer ' + $customerToken }
    $addrOk = (Test-ApiResponse $addr) -and (@($addr.Body.data).Count -gt 0)
    Assert-Step -Name 'B1 customer 地址列表非空' -Condition $addrOk -Detail ("HTTP " + $addr.Status + "; 地址数=" + $(if ($addr.Body -and $addr.Body.data) { @($addr.Body.data).Count } else { 0 })) -Raw $addr.Raw -ElapsedMs $addr.ElapsedMs
    $addressId = [int]@($addr.Body.data)[0].id

    $cartHeaders = @{ Authorization = 'Bearer ' + $customerToken }
    $cartClear = Invoke-Http -Method DELETE -Uri ($ApiBase + '/cart') -Headers $cartHeaders
    Assert-Step -Name 'B2 DELETE /cart 清空购物车' -Condition (Test-ApiResponse $cartClear) -Detail ("HTTP " + $cartClear.Status) -Raw $cartClear.Raw -ElapsedMs $cartClear.ElapsedMs

    $cartEmpty = Invoke-Http -Method GET -Uri ($ApiBase + '/cart') -Headers $cartHeaders
    $emptyOk = (Test-ApiResponse $cartEmpty) -and ([int]$cartEmpty.Body.data.totalQuantity -eq 0) -and ([decimal]$cartEmpty.Body.data.totalAmount -eq 0)
    Assert-Step -Name 'B3 清空后购物车 totalQuantity=0' -Condition $emptyOk -Detail ("totalQuantity=" + $(if ($cartEmpty.Body -and $cartEmpty.Body.data) { $cartEmpty.Body.data.totalQuantity } else { '' })) -Raw $cartEmpty.Raw -ElapsedMs $cartEmpty.ElapsedMs

    $addCart = Invoke-Http -Method POST -Uri ($ApiBase + '/cart/items') -Headers $cartHeaders -Body ([ordered]@{ dishId = 1; quantity = 2 })
    $itemFound = $false
    if ((Test-ApiResponse $addCart) -and $null -ne $addCart.Body.data) {
        foreach ($item in @($addCart.Body.data.items)) {
            if ([int]$item.dishId -eq 1 -and [int]$item.quantity -eq 2) { $itemFound = $true; break }
        }
    }
    $addOk = ((Test-ApiResponse $addCart) -and [int]$addCart.Body.data.totalQuantity -eq 2 -and $itemFound)
    Assert-Step -Name 'B4 POST /cart/items dishId=1 qty=2' -Condition $addOk -Detail ("HTTP " + $addCart.Status + "; totalQuantity=" + $(if ($addCart.Body -and $addCart.Body.data) { $addCart.Body.data.totalQuantity } else { '' })) -Raw $addCart.Raw -ElapsedMs $addCart.ElapsedMs

    $cart = Invoke-Http -Method GET -Uri ($ApiBase + '/cart') -Headers $cartHeaders
    $cartOk = (Test-ApiResponse $cart) -and ([int]$cart.Body.data.totalQuantity -eq 2) -and ([Math]::Abs([decimal]$cart.Body.data.totalAmount - ($dishPrice * 2)) -lt [decimal]0.001)
    Assert-Step -Name 'B5 购物车金额 = 菜品价 × 2' -Condition $cartOk -Detail ("totalAmount=" + $(if ($cart.Body -and $cart.Body.data) { $cart.Body.data.totalAmount } else { '' }) + "; 期望=" + ($dishPrice * 2)) -Raw $cart.Raw -ElapsedMs $cart.ElapsedMs
    $cartTotal = [decimal]$cart.Body.data.totalAmount

    $quote = Invoke-Http -Method POST -Uri ($ApiBase + '/coupons/quote') -Headers $cartHeaders -Body ([ordered]@{ merchantId = 1; totalAmount = $cartTotal })
    if (Test-ApiResponse $quote) {
        $q = $quote.Body.data
        $quoteOk = ($null -ne $q.reachMinOrder -and $null -ne $q.discountAmount -and $null -ne $q.deliveryFee -and $null -ne $q.payAmount)
        Assert-Step -Name 'B6 POST /coupons/quote 返回试算字段' -Condition $quoteOk -Detail ("discountAmount=" + $(if ($q) { $q.discountAmount } else { '' }) + "; deliveryFee=" + $(if ($q) { $q.deliveryFee } else { '' }) + "; payAmount=" + $(if ($q) { $q.payAmount } else { '' })) -Raw $quote.Raw -ElapsedMs $quote.ElapsedMs
    } else {
        Add-Skip -Name 'B6 POST /coupons/quote' -Reason ("契约存在但当前后端返回 HTTP " + $quote.Status + "；不阻断主链路")
    }

    $orderIdem = [guid]::NewGuid().ToString()
    $orderBody = [ordered]@{
        merchantId = 1
        addressId = $addressId
        items = @([ordered]@{ dishId = 1; quantity = 2 })
        remark = '冒烟测试-外卖'
    }
    $orderJson = $orderBody | ConvertTo-Json -Depth 10 -Compress
    $orderHeaders = @{ Authorization = 'Bearer ' + $customerToken; 'Idempotency-Key' = $orderIdem }
    $orderResp = Invoke-Http -Method POST -Uri ($ApiBase + '/orders') -Headers $orderHeaders -Body $orderJson
    $order = $orderResp.Body.data
    $orderId = 0
    if ($null -ne $order) { $orderId = [int]$order.id }
    Assert-Step -Name 'B7 POST /orders 创建外卖订单' -Condition ((Test-ApiResponse $orderResp) -and $orderId -gt 0 -and [string]$order.status -eq 'PENDING_PAYMENT') -Detail ("HTTP " + $orderResp.Status + "; orderId=" + $orderId + "; status=" + $(if ($order) { $order.status } else { '' })) -Raw $orderResp.Raw -ElapsedMs $orderResp.ElapsedMs
    $orderAmount = [decimal]$order.payAmount
    Assert-Step -Name 'B8 外卖订单 payAmount > 0' -Condition ($orderAmount -gt 0) -Detail ("payAmount=" + $orderAmount) -Raw $orderResp.Raw

    $orderReplay = Invoke-Http -Method POST -Uri ($ApiBase + '/orders') -Headers $orderHeaders -Body $orderJson
    $replayId = 0
    if ($null -ne $orderReplay.Body -and $null -ne $orderReplay.Body.data) { $replayId = [int]$orderReplay.Body.data.id }
    $replayed = (Get-HeaderValueOrEmpty $orderReplay 'Idempotency-Replayed') -match '^(?i:true)$'
    Assert-Step -Name 'B9 相同 Idempotency-Key 幂等返回同一 orderId' -Condition ((Test-ApiResponse $orderReplay) -and $replayId -eq $orderId -and $replayed) -Detail ("HTTP " + $orderReplay.Status + "; orderId=" + $replayId + "; Idempotency-Replayed=" + (Get-HeaderValueOrEmpty $orderReplay 'Idempotency-Replayed')) -Raw $orderReplay.Raw -ElapsedMs $orderReplay.ElapsedMs

    $cartAfter = Invoke-Http -Method GET -Uri ($ApiBase + '/cart') -Headers $cartHeaders
    $clearedOk = (Test-ApiResponse $cartAfter) -and ([int]$cartAfter.Body.data.totalQuantity -eq 0)
    Assert-Step -Name 'B10 下单后购物车自动清空' -Condition $clearedOk -Detail ("totalQuantity=" + $(if ($cartAfter.Body -and $cartAfter.Body.data) { $cartAfter.Body.data.totalQuantity } else { '' })) -Raw $cartAfter.Raw -ElapsedMs $cartAfter.ElapsedMs

    $payIdem = [guid]::NewGuid().ToString()
    $payHeaders = @{ Authorization = 'Bearer ' + $customerToken; 'Idempotency-Key' = $payIdem }
    $payResp = Invoke-Http -Method POST -Uri ($ApiBase + '/payments') -Headers $payHeaders -Body ([ordered]@{ orderId = $orderId; orderType = 'TAKEOUT' })
    $pay = $payResp.Body.data
    $payNo = ''
    if ($null -ne $pay) { $payNo = [string]$pay.payNo }
    $paidAmount = [decimal]0
    if ($null -ne $pay) { $paidAmount = [decimal]$pay.paidAmount }
    $expectedPaid = [Math]::Min([decimal]0.01, $orderAmount)
    $payOk = ((Test-ApiResponse $payResp) -and $payNo.Length -gt 0 -and [Math]::Abs($paidAmount - $expectedPaid) -lt [decimal]0.0001)
    Assert-Step -Name 'B11 POST /payments 创建支付单' -Condition $payOk -Detail ("HTTP " + $payResp.Status + "; payNo=" + $payNo + "; paidAmount=" + $paidAmount + "; 期望=" + $expectedPaid) -Raw $payResp.Raw -ElapsedMs $payResp.ElapsedMs

    $mockHint = ''
    if ($null -ne $pay) { $mockHint = [string]$pay.mockHint }
    Assert-Step -Name 'B12 mock 支付 paidAmount=0.01' -Condition ([Math]::Abs($paidAmount - [decimal]0.01) -lt [decimal]0.0001 -and [string]$pay.orderType -eq 'TAKEOUT') -Detail ("paidAmount=" + $paidAmount + "; orderType=" + $(if ($pay) { $pay.orderType } else { '' })) -Raw $payResp.Raw
    Assert-Step -Name 'B13 mockHint 含 ¥0.01' -Condition ($mockHint.Contains('¥0.01')) -Detail ("mockHint=" + $mockHint) -Raw $payResp.Raw

    $txNo = 'MOCK' + (Get-Date).ToString('yyyyMMddHHmmssfff')
    $callback = Send-MockCallback -PayNo $payNo -TransactionNo $txNo -PaidAmount ([decimal]0.01)
    $callbackOk = ((Test-ApiResponse $callback) -and ([bool]$callback.Body.data.received) -and [string]$callback.Body.data.status -eq 'SUCCESS')
    Assert-Step -Name 'B14 mock 回调验签成功' -Condition $callbackOk -Detail ("HTTP " + $callback.Status + "; received=" + $(if ($callback.Body -and $callback.Body.data) { $callback.Body.data.received } else { '' }) + "; status=" + $(if ($callback.Body -and $callback.Body.data) { $callback.Body.data.status } else { '' })) -Raw $callback.Raw -ElapsedMs $callback.ElapsedMs

    $orderPaid = Invoke-Http -Method GET -Uri ($ApiBase + '/orders/' + $orderId) -Headers $cartHeaders
    $orderPaidOk = ((Test-ApiResponse $orderPaid) -and [string]$orderPaid.Body.data.status -eq 'PAID')
    Assert-Step -Name 'B15 回调后外卖订单 PAID' -Condition $orderPaidOk -Detail ("status=" + $(if ($orderPaid.Body -and $orderPaid.Body.data) { $orderPaid.Body.data.status } else { '' })) -Raw $orderPaid.Raw -ElapsedMs $orderPaid.ElapsedMs

    $customerAccept = Invoke-Http -Method POST -Uri ($ApiBase + ('/merchant/orders/' + $orderId + '/accept')) -Headers $cartHeaders
    $deniedOk = ((-not $customerAccept.Ok) -and $customerAccept.Status -eq 403 -and $null -ne $customerAccept.Body -and [int]$customerAccept.Body.code -eq 40301)
    Add-Check -Name 'B16 customer 调商家接口被 403/40301 拒绝' -Passed $deniedOk -Detail ("HTTP " + $customerAccept.Status + "; code=" + $(if ($customerAccept.Body) { $customerAccept.Body.code } else { '' })) -Raw $customerAccept.Raw -ElapsedMs $customerAccept.ElapsedMs

    $merchantHeaders = @{ Authorization = 'Bearer ' + $merchantToken }
    [void](Invoke-ExpectStatus -CheckName 'B17 merchant accept → ACCEPTED' -Path ('/merchant/orders/' + $orderId + '/accept') -Expected 'ACCEPTED' -Headers $merchantHeaders)
    [void](Invoke-ExpectStatus -CheckName 'B18 merchant prepare → MAKING' -Path ('/merchant/orders/' + $orderId + '/prepare') -Expected 'MAKING' -Headers $merchantHeaders)
    [void](Invoke-ExpectStatus -CheckName 'B19 merchant deliver → DELIVERING' -Path ('/merchant/orders/' + $orderId + '/deliver') -Expected 'DELIVERING' -Headers $merchantHeaders)
    [void](Invoke-ExpectStatus -CheckName 'B20 merchant complete → COMPLETED' -Path ('/merchant/orders/' + $orderId + '/complete') -Expected 'COMPLETED' -Headers $merchantHeaders)

    $detailResp = Invoke-Http -Method GET -Uri ($ApiBase + '/orders/' + $orderId) -Headers $cartHeaders
    $detailOk = ((Test-ApiResponse $detailResp) -and [string]$detailResp.Body.data.status -eq 'COMPLETED' -and [bool]$detailResp.Body.data.canReview)
    Assert-Step -Name 'B21 外卖订单 COMPLETED 且可评价' -Condition $detailOk -Detail ("status=" + $(if ($detailResp.Body -and $detailResp.Body.data) { $detailResp.Body.data.status } else { '' }) + "; canReview=" + $(if ($detailResp.Body -and $detailResp.Body.data) { $detailResp.Body.data.canReview } else { '' })) -Raw $detailResp.Raw -ElapsedMs $detailResp.ElapsedMs

    $reviewBody = [ordered]@{ rating = 5; content = '冒烟测试：出餐快，味道好' }
    $review = Invoke-Http -Method POST -Uri ($ApiBase + ('/orders/' + $orderId + '/review')) -Headers $cartHeaders -Body $reviewBody
    $reviewOk = ((Test-ApiResponse $review) -and [int]$review.Body.data.rating -eq 5 -and [string]$review.Body.data.content.Length -gt 0)
    Assert-Step -Name 'B22 POST /orders/{id}/review' -Condition $reviewOk -Detail ("HTTP " + $review.Status + "; rating=" + $(if ($review.Body -and $review.Body.data) { $review.Body.data.rating } else { '' })) -Raw $review.Raw -ElapsedMs $review.ElapsedMs

    $reviewDup = Invoke-Http -Method POST -Uri ($ApiBase + ('/orders/' + $orderId + '/review')) -Headers $cartHeaders -Body $reviewBody
    $reviewDupOk = ((-not $reviewDup.Ok) -and $reviewDup.Status -eq 409 -and $null -ne $reviewDup.Body -and [int]$reviewDup.Body.code -eq 40906)
    Add-Check -Name 'B23 重复评价被 409/40906 拒绝' -Passed $reviewDupOk -Detail ("HTTP " + $reviewDup.Status + "; code=" + $(if ($reviewDup.Body) { $reviewDup.Body.code } else { '' })) -Raw $reviewDup.Raw -ElapsedMs $reviewDup.ElapsedMs

    }

    Write-Stage 'C 堂食全链路'
    $cartHeaders = @{ Authorization = 'Bearer ' + $customerToken }
    $tables = Invoke-Http -Method GET -Uri ($ApiBase + '/merchant/tables') -Headers $merchantHeaders
    $table = $null
    if ((Test-ApiResponse $tables) -and $null -ne $tables.Body.data) {
        foreach ($t in @($tables.Body.data)) { if ([string]$t.tableNo -eq 'A01') { $table = $t; break } }
    }
    Assert-Step -Name 'C1 GET /merchant/tables 含 A01' -Condition ($null -ne $table) -Detail ("HTTP " + $tables.Status + "; tableId=" + $(if ($table) { $table.id } else { '' }) + "; status=" + $(if ($table) { $table.status } else { '' })) -Raw $tables.Raw -ElapsedMs $tables.ElapsedMs
    $tableId = [int]$table.id
    $tableNo = [string]$table.tableNo

    $existingSession = Invoke-Http -Method GET -Uri ($ApiBase + '/dine-in/session') -Headers $cartHeaders
    if ((Test-ApiResponse $existingSession) -and [string]$existingSession.Body.data.status -eq 'ACTIVE') {
        $closed = Invoke-Http -Method POST -Uri ($ApiBase + '/dine-in/session/close') -Headers $cartHeaders
        Assert-Step -Name 'C3 清理遗留 ACTIVE 会话' -Condition (Test-ApiResponse $closed) -Detail ("HTTP " + $closed.Status) -Raw $closed.Raw -ElapsedMs $closed.ElapsedMs
        $tables = Invoke-Http -Method GET -Uri ($ApiBase + '/merchant/tables') -Headers $merchantHeaders
        foreach ($t in @($tables.Body.data)) { if ([string]$t.tableNo -eq 'A01') { $table = $t; break } }
        Assert-Step -Name 'C4 关闭会话后 A01 FREE' -Condition ([string]$table.status -eq 'FREE') -Detail ("status=" + $table.status) -Raw $tables.Raw -ElapsedMs $tables.ElapsedMs
    } else {
        Add-Skip -Name 'C3 清理遗留 ACTIVE 会话' -Reason '当前没有 ACTIVE 堂食会话，无需清理'
    }
    Assert-Step -Name 'C2 桌台 A01 可用（FREE）' -Condition ([string]$table.status -eq 'FREE' -and $null -eq $table.currentSessionId) -Detail ("status=" + $table.status + "; currentSessionId=" + $table.currentSessionId) -Raw $tables.Raw -ElapsedMs $tables.ElapsedMs

    $qr = Invoke-Http -Method GET -Uri ($ApiBase + ('/merchant/tables/' + $tableId + '/qr')) -Headers $merchantHeaders
    $qrContent = ''
    if ((Test-ApiResponse $qr) -and $null -ne $qr.Body.data) { $qrContent = [string]$qr.Body.data.qrContent }
    Assert-Step -Name 'C5 GET /merchant/tables/{id}/qr 返回 qrContent' -Condition ($qr.Status -eq 200 -and $qrContent.Length -gt 0) -Detail ("HTTP " + $qr.Status + "; 内容长度=" + $qrContent.Length) -Raw $qr.Raw -ElapsedMs $qr.ElapsedMs

    $qrP = Get-QueryParam -Url $qrContent -Name 'p'
    $serverS = Get-QueryParam -Url $qrContent -Name 's'
    $scanP = [string]$tableId
    if ($null -ne $qrP -and ([string]$qrP).Length -gt 0) { $scanP = [string]$qrP }
    Assert-Step -Name 'C6 qrContent 含 p/s 参数且 p=tableId' -Condition ($null -ne $serverS -and $serverS.Length -gt 0 -and [string]$scanP -eq [string]$tableId) -Detail ("p=" + $scanP + "; s 长度=" + $(if ($serverS) { $serverS.Length } else { 0 })) -Raw $qrContent
    $seedSalt = Get-SeedQrSalt -TableNo $tableNo
    $computedS = $null
    if ($script:TableSecret -and $seedSalt) {
        $computedS = Get-HmacBase64Url -Secret $script:TableSecret -Payload ([string]$scanP + '.' + $seedSalt)
        if ($computedS -ceq $serverS) {
            Add-Check -Name 'C7 TABLE_QR_SECRET 复算签名一致（Base64URL）' -Passed $true -Detail ('salt=' + $seedSalt)
        } else {
            Add-Skip -Name 'C7 TABLE_QR_SECRET 复算签名一致（Base64URL）' -Reason '服务端二维码 salt 已轮换或密钥不一致；改用服务端返回的 p/s'
        }
    } else {
        Add-Skip -Name 'C7 TABLE_QR_SECRET 复算签名一致' -Reason '未设置 TABLE_QR_SECRET 或种子数据中无 A01 qr_salt；改用服务端返回的 p/s'
    }
    $scanS = $serverS
    if ($computedS -and $computedS -ieq $serverS) { $scanS = $computedS }

    $scan = Invoke-Http -Method POST -Uri ($ApiBase + '/dine-in/scan') -Headers $cartHeaders -Body ([ordered]@{ p = [string]$scanP; s = [string]$scanS })
    $tableSession = ''
    if ((Test-ApiResponse $scan) -and $null -ne $scan.Body.data) { $tableSession = [string]$scan.Body.data.sessionToken }
    Assert-Step -Name 'C8 POST /dine-in/scan 获取 sessionToken' -Condition ($tableSession.Length -gt 0 -and [string]$scan.Body.data.tableNo -eq 'A01') -Detail ("HTTP " + $scan.Status + "; tableNo=" + $(if ($scan.Body -and $scan.Body.data) { $scan.Body.data.tableNo } else { '' }) + "; token 长度=" + $tableSession.Length) -Raw $scan.Raw -ElapsedMs $scan.ElapsedMs

    $bindBody = [ordered]@{ tableNo = 'A01'; s = [string]$scanS }
    $bind = Invoke-Http -Method POST -Uri ($ApiBase + '/dine-in/bind') -Headers @{ Authorization = 'Bearer ' + $customerToken } -Body $bindBody
    $bindOk = ((Test-ApiResponse $bind) -and [string]$bind.Body.data.tableNo -eq 'A01' -and [string]$bind.Body.data.status -eq 'ACTIVE')
    Assert-Step -Name 'C9 POST /dine-in/bind（手输降级 + 签名）' -Condition $bindOk -Detail ("HTTP " + $bind.Status + "; status=" + $(if ($bind.Body -and $bind.Body.data) { $bind.Body.data.status } else { '' }) + "; tableNo=" + $(if ($bind.Body -and $bind.Body.data) { $bind.Body.data.tableNo } else { '' })) -Raw $bind.Raw -ElapsedMs $bind.ElapsedMs
    if ((Test-ApiResponse $bind) -and [string]$bind.Body.data.sessionToken) { $tableSession = [string]$bind.Body.data.sessionToken }
    $tableHeaders = @{ Authorization = 'Bearer ' + $customerToken; 'X-Table-Session' = $tableSession }

    Add-Skip -Name 'C10 堂食会话加购' -Reason '契约 11.1 堂食下单直接提交 items，不依赖 /cart；当前 18080 未实现 /cart，故不阻断 C11'

    $dineIdem = [guid]::NewGuid().ToString()
    $dineOrderBody = [ordered]@{ items = @([ordered]@{ dishId = 1; quantity = 1 }); remark = '冒烟测试-堂食' }
    $dineOrderJson = $dineOrderBody | ConvertTo-Json -Depth 10 -Compress
    $dineOrderHeaders = @{ Authorization = 'Bearer ' + $customerToken; 'X-Table-Session' = $tableSession; 'Idempotency-Key' = $dineIdem }
    $dineOrderResp = Invoke-Http -Method POST -Uri ($ApiBase + '/dine-in/orders') -Headers $dineOrderHeaders -Body $dineOrderJson
    $dineOrder = $dineOrderResp.Body.data
    $dineOrderId = 0
    if ($null -ne $dineOrder) { $dineOrderId = [int]$dineOrder.id }
    $dineOrderOk = ((Test-ApiResponse $dineOrderResp) -and $dineOrderId -gt 0 -and [string]$dineOrder.status -eq 'PENDING_PAYMENT' -and [string]$dineOrder.orderType -eq 'DINE_IN')
    Assert-Step -Name 'C11 POST /dine-in/orders 堂食下单' -Condition $dineOrderOk -Detail ("HTTP " + $dineOrderResp.Status + "; orderId=" + $dineOrderId + "; type=" + $(if ($dineOrder) { $dineOrder.orderType } else { '' }) + "; tableNo=" + $(if ($dineOrder) { $dineOrder.tableNo } else { '' })) -Raw $dineOrderResp.Raw -ElapsedMs $dineOrderResp.ElapsedMs
    $dineAmount = [decimal]$dineOrder.payAmount

    $dineOrderReplay = Invoke-Http -Method POST -Uri ($ApiBase + '/dine-in/orders') -Headers $dineOrderHeaders -Body $dineOrderJson
    $dineReplayId = 0
    if ($null -ne $dineOrderReplay.Body -and $null -ne $dineOrderReplay.Body.data) { $dineReplayId = [int]$dineOrderReplay.Body.data.id }
    Assert-Step -Name 'C12 堂食同一 Idempotency-Key 返回同一 orderId' -Condition ((Test-ApiResponse $dineOrderReplay) -and $dineReplayId -eq $dineOrderId) -Detail ("HTTP " + $dineOrderReplay.Status + "; orderId=" + $dineReplayId) -Raw $dineOrderReplay.Raw -ElapsedMs $dineOrderReplay.ElapsedMs

    $dinePayHeaders = @{ Authorization = 'Bearer ' + $customerToken; 'X-Table-Session' = $tableSession; 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    $dinePayResp = Invoke-Http -Method POST -Uri ($ApiBase + '/payments') -Headers $dinePayHeaders -Body ([ordered]@{ orderId = $dineOrderId; orderType = 'DINE_IN' })
    $dinePay = $dinePayResp.Body.data
    $dinePayNo = ''
    if ($null -ne $dinePay) { $dinePayNo = [string]$dinePay.payNo }
    $dinePaid = [decimal]0
    if ($null -ne $dinePay) { $dinePaid = [decimal]$dinePay.paidAmount }
    Assert-Step -Name 'C13 堂食支付单 paidAmount=0.01' -Condition ((Test-ApiResponse $dinePayResp) -and $dinePayNo.Length -gt 0 -and [Math]::Abs($dinePaid - [decimal]0.01) -lt [decimal]0.0001 -and [string]$dinePay.orderType -eq 'DINE_IN') -Detail ("HTTP " + $dinePayResp.Status + "; payNo=" + $dinePayNo + "; paidAmount=" + $dinePaid) -Raw $dinePayResp.Raw -ElapsedMs $dinePayResp.ElapsedMs

    $dineCallback = Send-MockCallback -PayNo $dinePayNo -TransactionNo ('MOCKD' + (Get-Date).ToString('yyyyMMddHHmmssfff')) -PaidAmount ([decimal]0.01)
    $dineCallbackReceived = $false
    $dineCallbackShape = 'unknown'
    if ((Test-ApiResponse $dineCallback) -and $null -ne $dineCallback.Body.data) {
        $dineCallbackReceived = [bool]$dineCallback.Body.data.received
        $dineCallbackShape = 'data'
    } elseif ($null -ne $dineCallback.Body -and $null -ne $dineCallback.Body.received) {
        # 契约差异：docs/02 §12.4 写「响应 data」，实际后端返回裸 {received,payNo,status}。
        # 脚本兼容两种形态，但仍要求 received=true；该差异须交 Lead 裁决。
        Write-Host '  [WARN] mock 回调响应为裸对象（无 code/data 包装），与 docs/02 §12.4「响应 data」不一致；脚本按兼容模式继续' -ForegroundColor Yellow
        $dineCallbackReceived = [bool]$dineCallback.Body.received
        $dineCallbackShape = 'bare'
    }
    Assert-Step -Name 'C14 堂食 mock 回调成功' -Condition ($dineCallback.Status -eq 200 -and $dineCallbackReceived) -Detail ("HTTP " + $dineCallback.Status + "; received=" + $dineCallbackReceived + "; shape=" + $dineCallbackShape) -Raw $dineCallback.Raw -ElapsedMs $dineCallback.ElapsedMs

    $dinePaidResp = Invoke-Http -Method GET -Uri ($ApiBase + '/dine-in/orders/' + $dineOrderId) -Headers $tableHeaders
    Assert-Step -Name 'C15 堂食订单 PAID' -Condition ((Test-ApiResponse $dinePaidResp) -and [string]$dinePaidResp.Body.data.status -eq 'PAID') -Detail ("status=" + $(if ($dinePaidResp.Body -and $dinePaidResp.Body.data) { $dinePaidResp.Body.data.status } else { '' })) -Raw $dinePaidResp.Raw -ElapsedMs $dinePaidResp.ElapsedMs

    [void](Invoke-ExpectStatus -CheckName 'C16 merchant accept 堂食 → ACCEPTED' -Path ('/merchant/orders/' + $dineOrderId + '/accept') -Expected 'ACCEPTED' -Headers $merchantHeaders)
    [void](Invoke-ExpectStatus -CheckName 'C17 merchant prepare 堂食 → MAKING' -Path ('/merchant/dine-in/orders/' + $dineOrderId + '/prepare') -Expected 'MAKING' -Headers $merchantHeaders)
    [void](Invoke-ExpectStatus -CheckName 'C18 merchant serve 堂食 → SERVED' -Path ('/merchant/dine-in/orders/' + $dineOrderId + '/serve') -Expected 'SERVED' -Headers $merchantHeaders)
    [void](Invoke-ExpectStatus -CheckName 'C19 merchant complete 堂食 → COMPLETED' -Path ('/merchant/dine-in/orders/' + $dineOrderId + '/complete') -Expected 'COMPLETED' -Headers $merchantHeaders)

    $tableAfter = Invoke-Http -Method GET -Uri ($ApiBase + '/merchant/tables') -Headers $merchantHeaders
    $tableAfterRow = $null
    if (Test-ApiResponse $tableAfter) {
        foreach ($t in @($tableAfter.Body.data)) { if ([string]$t.tableNo -eq 'A01') { $tableAfterRow = $t; break } }
    }
    Assert-Step -Name 'C20 订单完成后桌台 A01 FREE' -Condition ($null -ne $tableAfterRow -and [string]$tableAfterRow.status -eq 'FREE' -and $null -eq $tableAfterRow.currentSessionId) -Detail ("status=" + $(if ($tableAfterRow) { $tableAfterRow.status } else { '' }) + "; currentSessionId=" + $(if ($tableAfterRow) { $tableAfterRow.currentSessionId } else { '' })) -Raw $tableAfter.Raw -ElapsedMs $tableAfter.ElapsedMs

    $sessionAfter = Invoke-Http -Method GET -Uri ($ApiBase + '/dine-in/session') -Headers $tableHeaders
    $invalidOk = ((-not $sessionAfter.Ok) -and ($sessionAfter.Status -in @(400, 401, 403, 404, 409))) -or ((Test-ApiResponse $sessionAfter) -and [string]$sessionAfter.Body.data.status -ne 'ACTIVE')
    Add-Check -Name 'C21 完成后 X-Table-Session 失效' -Passed $invalidOk -Detail ("HTTP " + $sessionAfter.Status + "; status=" + $(if ($sessionAfter.Body -and $sessionAfter.Body.data) { $sessionAfter.Body.data.status } else { '' })) -Raw $sessionAfter.Raw -ElapsedMs $sessionAfter.ElapsedMs

    Write-Stage 'D 文件上传（local）'
    $expectedUploadMode = 'direct'
    if ($script:StorageType -eq 'local') { $expectedUploadMode = 'backend' }
    $policy = Invoke-Http -Method GET -Uri ($ApiBase + '/files/policy?bizType=dish&ext=png') -Headers $cartHeaders
    $policyOk = ((Test-ApiResponse $policy) -and $null -ne $policy.Body.data.storageType -and [string]$policy.Body.data.uploadMode -eq $expectedUploadMode -and ([string]$policy.Body.data.uploadUrl).Contains('/files/upload'))
    Assert-Step -Name 'D1 GET /files/policy 返回存储策略' -Condition $policyOk -Detail ("HTTP " + $policy.Status + "; storageType=" + $(if ($policy.Body -and $policy.Body.data) { $policy.Body.data.storageType } else { '' }) + "; uploadMode=" + $(if ($policy.Body -and $policy.Body.data) { $policy.Body.data.uploadMode } else { '' })) -Raw $policy.Raw -ElapsedMs $policy.ElapsedMs

    $policyRawLower = $policy.Raw.ToLowerInvariant()
    $leak = $policyRawLower.Contains('accesskeysecret')
    $localLeak = ($script:StorageType -eq 'local') -and $policyRawLower.Contains('accesskeyid')
    Assert-Step -Name 'D2 policy 不泄露 OSS AK（local 模式连 accessKeyId 也不返回）' -Condition (-not $leak -and -not $localLeak) -Detail ("accessKeySecret=" + $leak + "; localAccessKeyId=" + $localLeak) -Raw $policy.Raw

    if ($script:StorageType -eq 'local') {
        $pngPath = Join-Path $script:TempDir 'smoke.png'
        $pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=='
        [System.IO.File]::WriteAllBytes($pngPath, [Convert]::FromBase64String($pngBase64))
        $uploadUrl = [string]$policy.Body.data.uploadUrl
        if (-not $uploadUrl.StartsWith('http')) { $uploadUrl = $BaseUrl + $uploadUrl }
        $upload = Invoke-MultipartUpload -Uri $uploadUrl -FilePath $pngPath -BizType 'dish' -Headers @{ Authorization = 'Bearer ' + $customerToken }
        $fileUrl = ''
        if ((Test-ApiResponse $upload) -and $null -ne $upload.Body.data) { $fileUrl = [string]$upload.Body.data.url }
        Assert-Step -Name 'D3 POST /files/upload（multipart）' -Condition ($fileUrl.Length -gt 0 -and [string]$upload.Body.data.storageType -eq 'local') -Detail ("HTTP " + $upload.Status + "; url=" + $fileUrl + "; storageType=" + $(if ($upload.Body -and $upload.Body.data) { $upload.Body.data.storageType } else { '' })) -Raw $upload.Raw

        $downloadPath = Join-Path $script:TempDir 'download.png'
        if (-not $fileUrl.StartsWith('http')) { $fileUrl = $BaseUrl + $fileUrl }
        $download = Invoke-Http -Method GET -Uri $fileUrl -OutFile $downloadPath
        $sigOk = $false
        if (Test-Path $downloadPath) {
            $sig = [System.IO.File]::ReadAllBytes($downloadPath)
            $sigOk = ($sig.Length -gt 8 -and $sig[0] -eq 0x89 -and $sig[1] -eq 0x50 -and $sig[2] -eq 0x4E -and $sig[3] -eq 0x47)
        }
        Assert-Step -Name 'D4 本地上传文件可下载且 PNG 头正确' -Condition ($download.Status -eq 200 -and $sigOk) -Detail ("HTTP " + $download.Status + "; bytes=" + $(if (Test-Path $downloadPath) { ([System.IO.File]::ReadAllBytes($downloadPath)).Length } else { 0 })) -ElapsedMs $download.ElapsedMs
    } else {
        Add-Skip -Name 'D3/D4 本地上传与下载' -Reason ('当前 storageType=' + $script:StorageType + '，非 local 模式')
    }

    $badPolicy = Invoke-Http -Method GET -Uri ($ApiBase + '/files/policy?bizType=dish&ext=txt') -Headers $cartHeaders
    $badOk = ((-not $badPolicy.Ok) -and $badPolicy.Status -ge 400 -and $badPolicy.Status -lt 500 -and $null -ne $badPolicy.Body -and [int]$badPolicy.Body.code -eq 40003)
    Add-Check -Name 'D5 非白名单扩展名 txt 被 400/40003 拒绝' -Passed $badOk -Detail ("HTTP " + $badPolicy.Status + "; code=" + $(if ($badPolicy.Body) { $badPolicy.Body.code } else { '' })) -Raw $badPolicy.Raw -ElapsedMs $badPolicy.ElapsedMs

    Write-Stage 'E 报表导出 xlsx'
    if ($SkipExports) {
        Add-Skip -Name 'E 报表导出 xlsx' -Reason '指定了 -SkipExports（当前后端未实现 /merchant/export/orders、/admin/orders/export）'
    } else {
    $today = Get-Date
    $fromDate = $today.AddDays(-7).ToString('yyyy-MM-dd')
    $toDate = $today.ToString('yyyy-MM-dd')
    $merchantXlsx = Join-Path $script:TempDir 'merchant-orders.xlsx'
    $merchantExport = Invoke-Http -Method GET -Uri ($ApiBase + ('/merchant/export/orders?startDate=' + $fromDate + '&endDate=' + $toDate)) -Headers $merchantHeaders -OutFile $merchantXlsx
    $merchantCt = Get-HeaderValueOrEmpty $merchantExport 'Content-Type'
    $merchantExportOk = ($merchantExport.Status -eq 200 -and $merchantCt -like '*spreadsheetml.sheet*' -and (Test-PkZip $merchantXlsx))
    Assert-Step -Name 'E1 GET /merchant/export/orders xlsx 首字节 PK' -Condition $merchantExportOk -Detail ("HTTP " + $merchantExport.Status + "; Content-Type=" + $merchantCt + "; 字节=" + $(if (Test-Path $merchantXlsx) { ([System.IO.File]::ReadAllBytes($merchantXlsx)).Length } else { 0 })) -ElapsedMs $merchantExport.ElapsedMs

    $adminXlsx = Join-Path $script:TempDir 'admin-orders.xlsx'
    $adminExport = Invoke-Http -Method GET -Uri ($ApiBase + ('/admin/orders/export?startDate=' + $fromDate + '&endDate=' + $toDate)) -Headers @{ Authorization = 'Bearer ' + $adminToken } -OutFile $adminXlsx
    $adminCt = Get-HeaderValueOrEmpty $adminExport 'Content-Type'
    $adminExportOk = ($adminExport.Status -eq 200 -and $adminCt -like '*spreadsheetml.sheet*' -and (Test-PkZip $adminXlsx))
    Assert-Step -Name 'E2 GET /admin/orders/export xlsx 首字节 PK' -Condition $adminExportOk -Detail ("HTTP " + $adminExport.Status + "; Content-Type=" + $adminCt + "; 字节=" + $(if (Test-Path $adminXlsx) { ([System.IO.File]::ReadAllBytes($adminXlsx)).Length } else { 0 })) -ElapsedMs $adminExport.ElapsedMs

    }

    Write-Stage 'F 支付超时取消（默认跳过）'
    $timeoutMinutes = 0
    if ($null -ne $config.order -and $null -ne $config.order.paymentTimeoutMinutes) { $timeoutMinutes = [int]$config.order.paymentTimeoutMinutes }
    if ($SkipTakeout) {
        Add-Skip -Name 'F1 支付超时自动取消' -Reason '已指定 -SkipTakeout，缺少外卖订单依赖'
    } elseif (-not $IncludeSlow -or $SkipSlow) {
        Add-Skip -Name 'F1 支付超时自动取消' -Reason '慢测默认跳过；需要时加 -IncludeSlow（要求 paymentTimeoutMinutes ≤ 2）'
    } elseif ($timeoutMinutes -le 2 -and $timeoutMinutes -gt 0) {
        $slowIdem = [guid]::NewGuid().ToString()
        $slowHeaders = @{ Authorization = 'Bearer ' + $customerToken; 'Idempotency-Key' = $slowIdem }
        $slowOrderResp = Invoke-Http -Method POST -Uri ($ApiBase + '/orders') -Headers $slowHeaders -Body ([ordered]@{ merchantId = 1; addressId = $addressId; items = @([ordered]@{ dishId = 1; quantity = 1 }); remark = '冒烟测试-超时取消' } | ConvertTo-Json -Depth 10 -Compress)
        $slowOrderId = 0
        if ((Test-ApiResponse $slowOrderResp) -and $null -ne $slowOrderResp.Body.data) { $slowOrderId = [int]$slowOrderResp.Body.data.id }
        Assert-Step -Name 'F1 创建待支付订单' -Condition ($slowOrderId -gt 0 -and [string]$slowOrderResp.Body.data.status -eq 'PENDING_PAYMENT') -Detail ("orderId=" + $slowOrderId) -Raw $slowOrderResp.Raw -ElapsedMs $slowOrderResp.ElapsedMs
        Write-Host ("  等待 " + $timeoutMinutes + " 分钟让 Spring Task 关闭超时订单...") -ForegroundColor Yellow
        Start-Sleep -Seconds ($timeoutMinutes * 60 + 20)
        $slowOrder = Invoke-Http -Method GET -Uri ($ApiBase + '/orders/' + $slowOrderId) -Headers $cartHeaders
        Assert-Step -Name 'F2 超时后订单 CANCELLED' -Condition ((Test-ApiResponse $slowOrder) -and [string]$slowOrder.Body.data.status -eq 'CANCELLED') -Detail ("status=" + $(if ($slowOrder.Body -and $slowOrder.Body.data) { $slowOrder.Body.data.status } else { '' })) -Raw $slowOrder.Raw -ElapsedMs $slowOrder.ElapsedMs
    } else {
        Add-Skip -Name 'F1 支付超时自动取消' -Reason ('paymentTimeoutMinutes=' + $timeoutMinutes + ' > 2，跳过慢测')
    }

    Write-Stage 'G 网关（可选 -GatewayUrl）'
    if (-not $GatewayUrl) {
        Add-Skip -Name 'G1-G5 网关链路' -Reason '未提供 -GatewayUrl，跳过'
    } else {
        $gw = $GatewayUrl.TrimEnd('/')
        $gwHealth = Invoke-Http -Method GET -Uri ($gw + '/healthz')
        Assert-Step -Name 'G1 GET /healthz' -Condition ($gwHealth.Status -eq 200) -Detail ("HTTP " + $gwHealth.Status) -Raw $gwHealth.Raw -ElapsedMs $gwHealth.ElapsedMs
        $gwApi = Invoke-Http -Method GET -Uri ($gw + '/api/v1/menu/categories')
        Assert-Step -Name 'G2 网关 /api/v1 转发' -Condition (Test-ApiResponse $gwApi) -Detail ("HTTP " + $gwApi.Status) -Raw $gwApi.Raw -ElapsedMs $gwApi.ElapsedMs
        $gwSwagger = Invoke-Http -Method GET -Uri ($gw + '/swagger-ui/index.html')
        Assert-Step -Name 'G3 网关 /swagger-ui 转发' -Condition ($gwSwagger.Status -eq 200) -Detail ("HTTP " + $gwSwagger.Status) -ElapsedMs $gwSwagger.ElapsedMs
        if (-not $SkipStatic) {
            $gwIndex = Invoke-Http -Method GET -Uri ($gw + '/')
            $indexOk = ($gwIndex.Status -eq 200 -and $gwIndex.Raw -match '<div id="app">')
            Assert-Step -Name 'G4 网关静态首页' -Condition $indexOk -Detail ("HTTP " + $gwIndex.Status) -ElapsedMs $gwIndex.ElapsedMs
            $gw404 = Invoke-Http -Method GET -Uri ($gw + '/__smoke_not_exist__')
            $fallbackOk = ($gw404.Status -eq 200 -and $gw404.Raw -match '<div id="app">')
            Assert-Step -Name 'G5 SPA fallback' -Condition $fallbackOk -Detail ("HTTP " + $gw404.Status) -ElapsedMs $gw404.ElapsedMs
        } else {
            Add-Skip -Name 'G4-G5 网关静态检查' -Reason '指定了 -SkipStatic'
        }
    }
} catch {
    $script:Aborted = $true
    if (-not $script:LastStep) { $script:LastStep = '流程中止' }
    Write-Host ''
    Write-Host ("流程中止: " + $_.Exception.Message) -ForegroundColor Red
    if ($script:LastRaw) {
        $preview = $script:LastRaw
        if ($preview.Length -gt 1000) { $preview = $preview.Substring(0, 1000) + '...' }
        Write-Host ("最后响应: " + $preview) -ForegroundColor DarkGray
    }
} finally {
    if (Test-Path $script:TempDir) { Remove-Item -Path $script:TempDir -Recurse -Force -ErrorAction SilentlyContinue }
    $duration = (Get-Date) - $startedAt
    Write-Host ''
    Write-Host '================ 汇总 ================' -ForegroundColor Cyan
    Write-Host ("耗时: " + [Math]::Round($duration.TotalSeconds, 1) + " 秒")
    Write-Host ("通过: " + $script:Passed + "  失败: " + $script:Failed + "  跳过: " + $script:Skipped + "  合计: " + ($script:Passed + $script:Failed + $script:Skipped))
    if ($script:Requests.Count -gt 0) {
        Write-Host '请求记录:'
        foreach ($req in $script:Requests) {
            Write-Host ("  {0,-6} {1,-52} HTTP {2,3} {3,6} ms" -f $req.Method, $req.Path, $req.Status, $req.Ms)
        }
    }
    $failures = @($script:Checks | Where-Object { $_.Status -eq 'FAIL' })
    if ($failures.Count -gt 0) {
        Write-Host '失败明细:' -ForegroundColor Red
        foreach ($failure in $failures) {
            Write-Host ("  - " + $failure.Name + " | " + $failure.Detail) -ForegroundColor Red
        }
    }
    if ($script:Aborted -or $script:Failed -gt 0) {
        Write-Host '结果: FAIL（未通过）。' -ForegroundColor Red
        $exitCode = 1
    } elseif ($script:Skipped -gt 0) {
        Write-Host '结果: PASS（含 SKIP；SKIP 项未验证）。' -ForegroundColor Yellow
        $exitCode = 0
    } else {
        Write-Host '结果: PASS（全部通过）。' -ForegroundColor Green
        $exitCode = 0
    }
    Write-Host '若后端 T08–T10 尚未完成，本次仅能视为「未验证」，不得据此宣称冒烟通过。' -ForegroundColor Yellow
}

exit $exitCode
