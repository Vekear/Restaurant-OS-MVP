#Requires -Version 5.1
<#
.SYNOPSIS
    导出 SpringDoc OpenAPI 文档到 docs/openapi.json（可复现、无 BOM、格式化）。

.DESCRIPTION
    前提：后端已在 BaseUrl 启动，且 SpringDoc 暴露 /v3/api-docs
    （Swagger UI 默认 /swagger-ui/index.html）。

    流程：GET /v3/api-docs → 校验 openapi + paths → ConvertTo-Json 格式化 →
         以 UTF-8（无 BOM）写入 docs/openapi.json → 回读二次校验 JSON。
    幂等：重复执行会覆盖同一文件；失败时不会留下半截文件。

    本机无 Docker（brief §6）：Docker Compose 环境下的导出：未验证。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/export-openapi.ps1
    使用默认 http://127.0.0.1:8080，输出 docs/openapi.json

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/export-openapi.ps1 -OutFile docs/openapi.local.json
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$ApiDocsPath = '/v3/api-docs',
    [string]$OutFile = 'docs/openapi.json',
    [int]$TimeoutSec = 30
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BaseUrl = $BaseUrl.TrimEnd('/')

function Write-Step([string]$Message) { Write-Host ("[openapi] " + $Message) -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host ("  [OK]   " + $Message) -ForegroundColor Green }
function Write-FailMsg([string]$Message) { Write-Host ("  [FAIL] " + $Message) -ForegroundColor Red }

try {
    if (-not ($ApiDocsPath.StartsWith('/'))) { $ApiDocsPath = '/' + $ApiDocsPath }
    $url = $BaseUrl + $ApiDocsPath

    if (-not [System.IO.Path]::IsPathRooted($OutFile)) { $OutFile = Join-Path $RepoRoot $OutFile }
    $outDir = Split-Path -Parent $OutFile
    if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host ' restaurant-mvp · 导出 OpenAPI 文档' -ForegroundColor Cyan
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host (' 源地址 : ' + $url)
    Write-Host (' 输出   : ' + $OutFile)

    Write-Step '请求 SpringDoc /v3/api-docs'
    $raw = $null
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $TimeoutSec -ErrorAction Stop
        if ([int]$resp.StatusCode -ne 200) {
            Write-FailMsg ('HTTP ' + [int]$resp.StatusCode + '：' + $url)
            exit 1
        }
        $raw = [string]$resp.Content
    } catch {
        Write-FailMsg ('无法访问 ' + $url + '：' + $_.Exception.Message)
        Write-Host '  请先启动后端：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1' -ForegroundColor Yellow
        Write-Host '  或指定 -BaseUrl 指向已启动的实例。' -ForegroundColor Yellow
        exit 1
    }

    Write-Step '解析并校验 JSON'
    $doc = $null
    try { $doc = $raw | ConvertFrom-Json } catch { Write-FailMsg ('响应不是合法 JSON：' + $_.Exception.Message); exit 1 }
    if (-not ($doc.PSObject.Properties.Name -contains 'openapi')) {
        Write-FailMsg '响应缺少 openapi 字段，可能不是 SpringDoc /v3/api-docs 输出'
        exit 1
    }
    if (-not ($doc.PSObject.Properties.Name -contains 'paths')) {
        Write-FailMsg '响应缺少 paths 字段'
        exit 1
    }
    $pathCount = ($doc.paths.PSObject.Properties | Measure-Object).Count
    if ($pathCount -le 0) { Write-FailMsg 'paths 为空；检查后端 Controller 是否被 SpringDoc 扫描到'; exit 1 }
    Write-Ok ('openapi=' + [string]$doc.openapi + '，paths=' + $pathCount)

    Write-Step '格式化并写入（UTF-8 无 BOM）'
    $json = $doc | ConvertTo-Json -Depth 100
    if (-not $json.EndsWith("`n")) { $json = $json + "`n" }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($OutFile, $json, $utf8NoBom)

    Write-Step '回读校验'
    $written = [System.IO.File]::ReadAllText($OutFile)
    $check = $written | ConvertFrom-Json
    $checkPaths = ($check.paths.PSObject.Properties | Measure-Object).Count
    if ($checkPaths -ne $pathCount) {
        Write-FailMsg ('回读 paths 数量不一致：写入 ' + $pathCount + '，回读 ' + $checkPaths)
        exit 1
    }
    $bytes = (Get-Item -LiteralPath $OutFile).Length
    Write-Ok ('写入成功：' + $bytes + ' 字节，paths=' + $checkPaths)
    Write-Host ''
    Write-Host '完成。' -ForegroundColor Green
    Write-Host '  校验命令：Get-Content docs/openapi.json -Raw | ConvertFrom-Json | Out-Null'
    Write-Host '  文档页面：' + $BaseUrl + '/swagger-ui/index.html'
    exit 0
} catch {
    Write-FailMsg $_.Exception.Message
    exit 1
}
