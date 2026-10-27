#Requires -Version 5.1
<#
.SYNOPSIS
    餐厅点餐 MVP —— REST 冒烟测试（本机后端 / Nginx 网关）。

.DESCRIPTION
    覆盖「健康检查 → OpenAPI → 演示账号登录 → 鉴权接口 → 菜单接口」最小可验证子集：
      1. GET  /actuator/health                 后端存活（status=UP）
      2. GET  /v3/api-docs                     SpringDoc 文档可导出（openapi + paths）
      3. POST /api/v1/auth/login               admin / merchant / customer 三个演示账号
      4. GET  /api/v1/auth/me                  管理员 Bearer Token 鉴权
      5. GET  /api/v1/menu/categories          菜单分类（种子数据非空）
      6. GET  /api/v1/menu/dishes?page=1&size=10 菜品分页
      7. 可选 -GatewayUrl：/healthz、/actuator/health、/api/v1/menu/categories、/swagger-ui/index.html、
         /v3/api-docs、/ 静态首页（Nginx 反代链路）

    说明：本脚本只做 REST 冒烟；WebSocket 推送、下单/支付/超时取消、POI 导出、
    小程序链路不在本脚本范围（见 docs/06-deployment.md 验收矩阵，T21/T22 扩展）。

    前置：后端已启动，MySQL / Redis 可用，Flyway 已执行 V1/V2 迁移与种子数据。
    本机无 Docker（brief §6）：Docker Compose 运行态冒烟：未验证。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
    默认直连 http://127.0.0.1:8080

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -GatewayUrl http://127.0.0.1
    额外验证本机 Nginx 网关（需已执行 pnpm build 并启动 nginx）
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$GatewayUrl = '',
    [int]$TimeoutSec = 15,
    [switch]$SkipDemoAccounts,
    [switch]$SkipStatic
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BaseUrl = $BaseUrl.TrimEnd('/')
$ApiBase = $BaseUrl + '/api/v1'

$script:Passed = 0
$script:Failed = 0
$script:Skipped = 0
$script:Results = New-Object System.Collections.ArrayList

function Write-Step([string]$Message) { Write-Host ("[smoke] " + $Message) -ForegroundColor Cyan }
function Get-Brief([string]$Text, [int]$Max = 220) {
    if (-not $Text) { return '' }
    $one = ($Text -replace '\s+', ' ').Trim()
    if ($one.Length -gt $Max) { return $one.Substring(0, $Max) + '...' }
    return $one
}

function Add-Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $null = $script:Results.Add([pscustomobject]@{ Name = $Name; Status = $(if ($Passed) { 'PASS' } else { 'FAIL' }); Detail = $Detail })
    if ($Passed) {
        $script:Passed++
        Write-Host ("[PASS] " + $Name) -ForegroundColor Green
    } else {
        $script:Failed++
        Write-Host ("[FAIL] " + $Name) -ForegroundColor Red
    }
    if ($Detail) { Write-Host ("       " + $Detail) -ForegroundColor DarkGray }
}

function Add-Skip([string]$Name, [string]$Reason) {
    $script:Skipped++
    $null = $script:Results.Add([pscustomobject]@{ Name = $Name; Status = 'SKIP'; Detail = $Reason })
    Write-Host ("[SKIP] " + $Name + " —— " + $Reason) -ForegroundColor Yellow
}

function Invoke-Http {
    param(
        [string]$Method = 'GET',
        [string]$Uri,
        $Body = $null,
        [hashtable]$Headers = @{},
        [int]$Timeout = 15
    )
    $result = [ordered]@{ Ok = $false; Status = 0; Body = $null; Raw = ''; Error = '' }
    try {
        $requestArgs = @{
            Uri = $Uri
            Method = $Method
            UseBasicParsing = $true
            TimeoutSec = $Timeout
            ErrorAction = 'Stop'
        }
        if ($Headers -and $Headers.Count -gt 0) { $requestArgs.Headers = $Headers }
        if ($null -ne $Body) {
            $json = $Body
            if ($Body -isnot [string]) { $json = $Body | ConvertTo-Json -Depth 10 -Compress }
            $requestArgs.Body = $json
            $requestArgs.ContentType = 'application/json; charset=utf-8'
        }
        $resp = Invoke-WebRequest @requestArgs
        $result.Status = [int]$resp.StatusCode
        $result.Raw = [string]$resp.Content
        $result.Ok = $true
        if ($result.Raw) {
            try { $result.Body = $result.Raw | ConvertFrom-Json } catch { $result.Body = $null }
        }
    } catch {
        $ex = $_.Exception
        $response = $ex.Response
        if ($response) {
            try { $result.Status = [int]$response.StatusCode } catch { $result.Status = 0 }
            try {
                $stream = $response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $result.Raw = $reader.ReadToEnd()
                    $reader.Close()
                }
            } catch { }
            if ($result.Raw) {
                try { $result.Body = $result.Raw | ConvertFrom-Json } catch { $result.Body = $null }
            }
        }
        $result.Error = $ex.Message
    }
    return [pscustomobject]$result
}

function Test-Envelope($Body) {
    if ($null -eq $Body) { return $false }
    $names = $Body.PSObject.Properties.Name
    if (-not ($names -contains 'code')) { return $false }
    return ($Body.code -eq 0 -or $Body.code -eq 200)
}

function Get-EnvelopeCount($Body) {
    if ($null -eq $Body) { return -1 }
    if (-not ($Body.PSObject.Properties.Name -contains 'data')) { return -1 }
    $data = $Body.data
    if ($null -eq $data) { return 0 }
    if ($data -is [System.Array]) { return $data.Count }
    $listKeys = @('list', 'records', 'items', 'content', 'rows')
    foreach ($key in $listKeys) {
        if ($data.PSObject.Properties.Name -contains $key) {
            $value = $data.$key
            if ($value -is [System.Array]) { return $value.Count }
        }
    }
    return 1
}

function Invoke-Login([string]$Username, [string]$Password) {
    $resp = Invoke-Http -Method 'POST' -Uri ($ApiBase + '/auth/login') -Body @{ username = $Username; password = $Password } -Timeout $TimeoutSec
    $token = $null
    if ($resp.Ok -and (Test-Envelope $resp.Body)) {
        $data = $resp.Body.data
        if ($data) {
            foreach ($key in @('token', 'accessToken', 'access_token')) {
                if ($data.PSObject.Properties.Name -contains $key -and $data.$key) { $token = [string]$data.$key; break }
            }
        }
    }
    return [pscustomobject]@{ Response = $resp; Token = $token }
}

function Invoke-LoginCheck([string]$Role, [string]$Username, [string]$Password) {
    $login = Invoke-Login -Username $Username -Password $Password
    $resp = $login.Response
    $ok = $resp.Ok -and ($resp.Status -eq 200) -and (Test-Envelope $resp.Body) -and [bool]$login.Token
    $detail = ''
    if (-not $resp.Ok) { $detail = '请求失败：' + $resp.Error }
    elseif ($resp.Status -ne 200) { $detail = 'HTTP ' + $resp.Status + '：' + (Get-Brief $resp.Raw) }
    elseif (-not (Test-Envelope $resp.Body)) { $detail = '统一响应 code 非 0/200：' + (Get-Brief $resp.Raw) }
    elseif (-not $login.Token) { $detail = '响应缺少 token/accessToken 字段：' + (Get-Brief $resp.Raw) }
    else { $detail = ('账号 ' + $Username + ' 登录成功（token 长度 ' + $login.Token.Length + '）') }
    Add-Check ('登录 · ' + $Role + '（' + $Username + '）') $ok $detail
    return $login
}

# =============================================================================
# main
# =============================================================================
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' restaurant-mvp · REST 冒烟测试' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host (' 后端 BaseUrl : ' + $BaseUrl)
Write-Host (' API 前缀     : ' + $ApiBase)
if ($GatewayUrl) { Write-Host (' 网关 Gateway : ' + $GatewayUrl) }
Write-Host ''

Write-Step '1/6 后端健康检查'
$health = Invoke-Http -Uri ($BaseUrl + '/actuator/health') -Timeout $TimeoutSec
$healthOk = $health.Ok -and $health.Status -eq 200 -and $health.Body -and ([string]$health.Body.status -eq 'UP')
$healthDetail = ''
if (-not $health.Ok) { $healthDetail = '后端不可达：' + $health.Error + '（先启动后端：scripts/dev-local.ps1）' }
elseif (-not $health.Body) { $healthDetail = 'HTTP ' + $health.Status + ' 响应非 JSON：' + (Get-Brief $health.Raw) }
else { $healthDetail = 'status=' + [string]$health.Body.status }
Add-Check 'GET /actuator/health = UP' $healthOk $healthDetail

if (-not $healthOk) {
    Write-Host ''
    Write-Host '后端不可达，后续接口检查跳过。启动后端后再运行本脚本。' -ForegroundColor Red
    Write-Host '  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1' -ForegroundColor Yellow
    exit 1
}

Write-Step '2/6 OpenAPI 文档'
$openapi = Invoke-Http -Uri ($BaseUrl + '/v3/api-docs') -Timeout $TimeoutSec
$pathCount = 0
if ($openapi.Body -and ($openapi.Body.PSObject.Properties.Name -contains 'paths')) {
    $pathCount = ($openapi.Body.paths.PSObject.Properties | Measure-Object).Count
}
$openapiOk = $openapi.Ok -and $openapi.Status -eq 200 -and $openapi.Body -and ($openapi.Body.PSObject.Properties.Name -contains 'openapi') -and $pathCount -gt 0
$openapiDetail = ''
if (-not $openapi.Ok) { $openapiDetail = '请求失败：' + $openapi.Error }
else { $openapiDetail = 'openapi=' + [string]$openapi.Body.openapi + '，paths=' + $pathCount }
Add-Check 'GET /v3/api-docs（SpringDoc）' $openapiOk $openapiDetail

Write-Step '3/6 演示账号登录'
$adminLogin = Invoke-LoginCheck -Role '管理员' -Username 'admin' -Password 'admin123'
if (-not $SkipDemoAccounts) {
    $null = Invoke-LoginCheck -Role '商家' -Username 'merchant' -Password 'merchant123'
    $null = Invoke-LoginCheck -Role '顾客' -Username 'customer' -Password 'customer123'
} else {
    Add-Skip '登录 · 商家/顾客' '已指定 -SkipDemoAccounts'
}

Write-Step '4/6 鉴权接口（Bearer）'
if ($adminLogin.Token) {
    $me = Invoke-Http -Uri ($ApiBase + '/auth/me') -Headers @{ Authorization = 'Bearer ' + $adminLogin.Token } -Timeout $TimeoutSec
    $meOk = $me.Ok -and $me.Status -eq 200 -and (Test-Envelope $me.Body) -and $null -ne $me.Body.data
    $meDetail = ''
    if (-not $me.Ok) { $meDetail = '请求失败：' + $me.Error }
    elseif (-not $meOk) { $meDetail = 'HTTP ' + $me.Status + '：' + (Get-Brief $me.Raw) }
    else { $meDetail = 'data 非空（' + (Get-Brief ($me.Body.data | ConvertTo-Json -Depth 5 -Compress) 120) + '）' }
    Add-Check 'GET /api/v1/auth/me（admin token）' $meOk $meDetail
} else {
    Add-Skip 'GET /api/v1/auth/me' '登录未取得 token'
}

Write-Step '5/6 菜单接口'
$categories = Invoke-Http -Uri ($ApiBase + '/menu/categories') -Timeout $TimeoutSec
$categoryCount = -1
if ($categories.Ok -and (Test-Envelope $categories.Body)) { $categoryCount = Get-EnvelopeCount $categories.Body }
$categoriesOk = $categories.Ok -and $categories.Status -eq 200 -and (Test-Envelope $categories.Body) -and $categoryCount -gt 0
$categoriesDetail = ''
if (-not $categories.Ok) { $categoriesDetail = '请求失败：' + $categories.Error }
elseif ($categoryCount -eq -1) { $categoriesDetail = 'HTTP ' + $categories.Status + '：' + (Get-Brief $categories.Raw) }
else { $categoriesDetail = '分类数=' + $categoryCount + '（期望种子数据 6 个分类，非空即可）' }
Add-Check 'GET /api/v1/menu/categories（非空）' $categoriesOk $categoriesDetail

$items = Invoke-Http -Uri ($ApiBase + '/menu/dishes?page=1&size=10') -Timeout $TimeoutSec
$itemCount = -1
if ($items.Ok -and (Test-Envelope $items.Body)) { $itemCount = Get-EnvelopeCount $items.Body }
$itemsOk = $items.Ok -and $items.Status -eq 200 -and (Test-Envelope $items.Body) -and $itemCount -gt 0
$itemsDetail = ''
if (-not $items.Ok) { $itemsDetail = '请求失败：' + $items.Error }
elseif ($itemCount -eq -1) { $itemsDetail = 'HTTP ' + $items.Status + '：' + (Get-Brief $items.Raw) }
else { $itemsDetail = '返回条目=' + $itemCount }
Add-Check 'GET /api/v1/menu/dishes（分页）' $itemsOk $itemsDetail

Write-Step '6/6 网关检查'
if (-not $GatewayUrl) {
    Add-Skip 'Nginx 网关链路' '未提供 -GatewayUrl（本机 Nginx 未启动或本阶段不需要）'
} else {
    $GatewayUrl = $GatewayUrl.TrimEnd('/')

    $healthz = Invoke-Http -Uri ($GatewayUrl + '/healthz') -Timeout $TimeoutSec
    $healthzOk = $healthz.Ok -and $healthz.Status -eq 200 -and ([string]$healthz.Raw).Trim() -eq 'ok'
    Add-Check 'GET /healthz（Nginx 存活）' $healthzOk ('HTTP ' + $healthz.Status + ' body=' + (Get-Brief $healthz.Raw 40))

    $gwActuator = Invoke-Http -Uri ($GatewayUrl + '/actuator/health') -Timeout $TimeoutSec
    $gwActuatorOk = $gwActuator.Ok -and $gwActuator.Status -eq 200 -and ([string]$gwActuator.Body.status) -eq 'UP'
    Add-Check 'GET /actuator/health（Nginx 反代 db/redis 组件）' $gwActuatorOk ('HTTP ' + $gwActuator.Status + '：' + (Get-Brief $gwActuator.Raw 120))

    $gwCategories = Invoke-Http -Uri ($GatewayUrl + '/api/v1/menu/categories') -Timeout $TimeoutSec
    $gwCategoriesOk = $gwCategories.Ok -and $gwCategories.Status -eq 200 -and (Test-Envelope $gwCategories.Body)
    Add-Check 'GET /api/v1/menu/categories（Nginx 反代）' $gwCategoriesOk ('HTTP ' + $gwCategories.Status + '：' + (Get-Brief $gwCategories.Raw 160))

    $gwDocs = Invoke-Http -Uri ($GatewayUrl + '/v3/api-docs') -Timeout $TimeoutSec
    $gwDocsOk = $gwDocs.Ok -and $gwDocs.Status -eq 200 -and $gwDocs.Body -and ($gwDocs.Body.PSObject.Properties.Name -contains 'openapi')
    Add-Check 'GET /v3/api-docs（Nginx 反代）' $gwDocsOk ('HTTP ' + $gwDocs.Status)

    $gwSwagger = Invoke-Http -Uri ($GatewayUrl + '/swagger-ui/index.html') -Timeout $TimeoutSec
    $gwSwaggerOk = $gwSwagger.Ok -and $gwSwagger.Status -eq 200
    Add-Check 'GET /swagger-ui/index.html（Nginx 反代）' $gwSwaggerOk ('HTTP ' + $gwSwagger.Status)

    if ($SkipStatic) {
        Add-Skip 'GET /（Web 静态首页）' '已指定 -SkipStatic'
    } else {
        $index = Invoke-Http -Uri ($GatewayUrl + '/') -Timeout $TimeoutSec
        $indexOk = $index.Ok -and $index.Status -eq 200 -and ([string]$index.Raw).IndexOf('<div id="app"') -ge 0
        Add-Check 'GET /（Web 静态首页）' $indexOk ('HTTP ' + $index.Status + '（期望含 <div id="app">）')
    }
}

Write-Host ''
Write-Host '------------------------------------------------------------' -ForegroundColor Cyan
Write-Host (' 结果：PASS ' + $script:Passed + ' / FAIL ' + $script:Failed + ' / SKIP ' + $script:Skipped) -ForegroundColor $(if ($script:Failed -gt 0) { 'Red' } else { 'Green' })
Write-Host ' 范围：健康 / OpenAPI / 演示账号登录 / 鉴权 / 菜单（REST 最小子集）'
Write-Host ' 未覆盖：WebSocket 推送、下单/支付/超时取消、POI 导出、小程序、Docker Compose 运行态（未验证）'
Write-Host '------------------------------------------------------------' -ForegroundColor Cyan

if ($script:Failed -gt 0) { exit 1 }
exit 0
