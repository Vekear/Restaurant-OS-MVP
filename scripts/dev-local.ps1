#Requires -Version 5.1
<#
.SYNOPSIS
    餐厅点餐 MVP —— 本机开发一键编排（无 Docker 环境）。

.DESCRIPTION
    本机无 Docker（brief §6），脚本用本机 MySQL / Redis / Nginx 跑通等价链路：
      1. 读取仓库根 .env（缺省回退 .env.example）并注入后端进程环境；
      2. 启动 Spring Boot 后端（backend/ 下 mvn spring-boot:run，端口 8080）；
      3. 启动 Web Vite dev server（web/ 下 pnpm run dev，端口 5173）；
      4. 可选 -WithNginx 启动本机 Nginx（deploy/nginx/nginx.local.conf，端口 80）。

    已在运行的同类服务会被复用（幂等），不会重复拉起；-Stop 依据
    .runtime/logs/dev-local.pids.json 停止本次记录的进程。

    环境变量覆盖（本机开发专用）：
      STORAGE_LOCAL_DIR = <repo>/.runtime/storage/files（绝对路径，gitignore）
      DB_HOST / REDIS_HOST 保持 .env 中的 127.0.0.1，供后端直连本机服务。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1
    启动后端 + Web，输出日志到 .runtime/logs/

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1 -WithNginx
    额外启动本机 Nginx；需先执行 cd web && pnpm build 产出 web/dist

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1 -Stop
    停止本脚本记录的进程（含子进程树）并优雅停止 Nginx
#>
[CmdletBinding()]
param(
    [switch]$Stop,
    [switch]$WithNginx,
    [switch]$SkipBackend,
    [switch]$SkipWeb,
    [switch]$SkipChecks,
    [int]$BackendPort = 8080,
    [int]$WebPort = 5173,
    [string]$NginxExe = 'D:\Code_Environment\Nginx\nginx-1.30.6\nginx.exe'
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$RuntimeDir = Join-Path $RepoRoot '.runtime'
$LogDir = Join-Path $RuntimeDir 'logs'
$NginxRuntimeDir = Join-Path $RuntimeDir 'nginx'
$NginxConf = Join-Path $RepoRoot 'deploy\nginx\nginx.local.conf'
$PidFile = Join-Path $LogDir 'dev-local.pids.json'

function Write-Step([string]$Message) { Write-Host ("[dev-local] " + $Message) -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host ("  [OK]   " + $Message) -ForegroundColor Green }
function Write-WarnMsg([string]$Message) { Write-Host ("  [WARN] " + $Message) -ForegroundColor Yellow }
function Write-FailMsg([string]$Message) { Write-Host ("  [FAIL] " + $Message) -ForegroundColor Red }

function Get-ToolPath([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd -and $cmd.Source) { return $cmd.Source }
    return $null
}

function Test-PortOpen([string]$HostName, [int]$Port, [int]$TimeoutMs = 800) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Test-HttpOk([string]$Url, [int]$TimeoutSec = 4) {
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec -ErrorAction Stop
        $code = [int]$resp.StatusCode
        return ($code -ge 200 -and $code -lt 500)
    } catch {
        return $false
    }
}

function Wait-Http([string]$Url, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $lastError = ''
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ([int]$resp.StatusCode -eq 200) { return $true }
            $lastError = "HTTP " + [int]$resp.StatusCode
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 1500
    }
    Write-WarnMsg ("等待 " + $Url + " 超时（" + $TimeoutSec + "s）：" + $lastError)
    return $false
}

function Show-LogTail([string]$Path, [int]$Lines = 30) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    Write-Host ("  --- " + $Path + "（末 " + $Lines + " 行）---") -ForegroundColor DarkYellow
    Get-Content -LiteralPath $Path -Tail $Lines -ErrorAction SilentlyContinue | ForEach-Object { Write-Host ("  " + $_) }
}

function Import-DotEnv([string]$Path) {
    $count = 0
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        $trimmed = $line.Trim()
        if (-not $trimmed) { continue }
        if ($trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim()
        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        Set-Item -Path ("Env:" + $key) -Value $value
        $count++
    }
    return $count
}

function Stop-ProcessTree([int]$ProcessId, [string]$Name) {
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $proc) {
        Write-WarnMsg ($Name + " PID " + $ProcessId + " 已不存在")
        return
    }
    Write-Host ("  停止 " + $Name + "（PID " + $ProcessId + "，" + $proc.ProcessName + "）")
    & taskkill.exe /PID $ProcessId /T /F 2>&1 | Out-Null
}

function Stop-LocalStack {
    Write-Step '停止本机开发进程'
    $handled = $false
    if (Test-Path -LiteralPath $PidFile) {
        try {
            $saved = Get-Content -LiteralPath $PidFile -Raw | ConvertFrom-Json
            if ($saved.backendPid) { Stop-ProcessTree -ProcessId ([int]$saved.backendPid) -Name '后端'; $handled = $true }
            if ($saved.webPid) { Stop-ProcessTree -ProcessId ([int]$saved.webPid) -Name 'Web'; $handled = $true }
            if ($saved.nginxPid) {
                if (Test-Path -LiteralPath $NginxExe) {
                    $prefix = ($NginxRuntimeDir -replace '\\', '/') + '/'
                    $conf = $NginxConf -replace '\\', '/'
                    & $NginxExe -s stop -p $prefix -c $conf 2>&1 | Out-Null
                }
                Stop-ProcessTree -ProcessId ([int]$saved.nginxPid) -Name 'Nginx'
                $handled = $true
            }
        } catch {
            Write-WarnMsg ("读取 " + $PidFile + " 失败：" + $_.Exception.Message)
        }
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    }

    $nginxPidFile = Join-Path $NginxRuntimeDir 'nginx.pid'
    if (Test-Path -LiteralPath $nginxPidFile) {
        $rawPid = Get-Content -LiteralPath $nginxPidFile -Raw -ErrorAction SilentlyContinue
        if ($rawPid -and $rawPid.Trim() -match '^\d+$') {
            Stop-ProcessTree -ProcessId ([int]$rawPid.Trim()) -Name 'Nginx'
            $handled = $true
        } else {
            Write-WarnMsg ('忽略空/无效 nginx.pid：' + $nginxPidFile)
        }
        Remove-Item -LiteralPath $nginxPidFile -Force -ErrorAction SilentlyContinue
    }

    if (-not $handled) { Write-WarnMsg '没有发现本脚本记录的运行进程（可用 Get-NetTCPConnection 检查 8080/5173/80）' }
    else { Write-Ok '已停止' }
    Write-Host '提示：MySQL / Redis 由 Lead 统一维护，本脚本不启停。' -ForegroundColor DarkGray
}

function Initialize-Directories {
    foreach ($dir in @($RuntimeDir, $LogDir, $NginxRuntimeDir)) {
        if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    }
    foreach ($dir in @('logs', 'temp\client_body_temp', 'temp\proxy_temp', 'temp\fastcgi_temp', 'temp\uwsgi_temp', 'temp\scgi_temp')) {
        $full = Join-Path $NginxRuntimeDir $dir
        if (-not (Test-Path -LiteralPath $full)) { New-Item -ItemType Directory -Path $full -Force | Out-Null }
    }
    $storageDir = Join-Path $RuntimeDir 'storage\files'
    if (-not (Test-Path -LiteralPath $storageDir)) { New-Item -ItemType Directory -Path $storageDir -Force | Out-Null }
    return $storageDir
}

function Start-Backend([int]$Port) {
    if (Test-PortOpen '127.0.0.1' $Port) {
        if (Test-HttpOk ("http://127.0.0.1:" + $Port + "/actuator/health")) {
            Write-WarnMsg ("端口 " + $Port + " 上已有健康后端，复用现有进程（不记录 PID）")
            return [pscustomobject]@{ Process = $null; Reused = $true }
        }
        throw ("端口 " + $Port + " 已被占用且不是健康后端；请先释放端口，参见 docs/06-deployment.md 故障排查")
    }

    $mvn = Get-ToolPath 'mvn.cmd'
    if (-not $mvn) { $mvn = Get-ToolPath 'mvn' }
    if (-not $mvn) { throw '未找到 Maven（mvn），请安装 Maven 3.9+ 并加入 PATH' }

    $outLog = Join-Path $LogDir 'backend.log'
    $errLog = Join-Path $LogDir 'backend.err.log'
    Write-Step ("启动后端：backend/ 下 mvn -DskipTests spring-boot:run（端口 " + $Port + "）")
    $proc = Start-Process -FilePath $mvn -ArgumentList @('-q', '-DskipTests', 'spring-boot:run', ('-Dspring-boot.run.arguments=--server.port=' + $Port)) `
        -WorkingDirectory (Join-Path $RepoRoot 'backend') `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
        -PassThru -WindowStyle Hidden
    Write-Host ("  PID " + $proc.Id + "，日志 " + $outLog)

    if (-not (Wait-Http ("http://127.0.0.1:" + $Port + "/actuator/health") 180)) {
        Show-LogTail $outLog 30
        Show-LogTail $errLog 30
        throw '后端未在 180s 内通过 /actuator/health；请查看上方日志'
    }
    Write-Ok ("后端健康：http://127.0.0.1:" + $Port + "/actuator/health")
    return [pscustomobject]@{ Process = $proc; Reused = $false }
}

function Start-Web([int]$Port) {
    if (Test-PortOpen '127.0.0.1' $Port) {
        if (Test-HttpOk ("http://127.0.0.1:" + $Port + "/")) {
            Write-WarnMsg ("端口 " + $Port + " 上已有 Web dev server，复用现有进程（不记录 PID）")
            return [pscustomobject]@{ Process = $null; Reused = $true }
        }
        throw ("端口 " + $Port + " 已被占用；请先释放端口或使用 -WebPort 指定其它端口")
    }

    $pnpm = Get-ToolPath 'pnpm.cmd'
    if (-not $pnpm) { $pnpm = Get-ToolPath 'pnpm' }
    if (-not $pnpm) { throw '未找到 pnpm，请安装 pnpm（Node 24 自带 corepack enable 后可启用）' }
    if (-not (Get-ToolPath 'node.exe')) { throw '未找到 node，请安装 Node.js 20.19+ 并加入 PATH' }

    $outLog = Join-Path $LogDir 'web.log'
    $errLog = Join-Path $LogDir 'web.err.log'
    Write-Step ("启动 Web：web/ 下 pnpm run dev（端口 " + $Port + "）")
    $proc = Start-Process -FilePath $pnpm -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', [string]$Port) `
        -WorkingDirectory (Join-Path $RepoRoot 'web') `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
        -PassThru -WindowStyle Hidden
    Write-Host ("  PID " + $proc.Id + "，日志 " + $outLog)

    if (-not (Wait-Http ("http://127.0.0.1:" + $Port + "/") 120)) {
        Show-LogTail $outLog 30
        Show-LogTail $errLog 30
        throw 'Web dev server 未在 120s 内就绪；请查看上方日志'
    }
    Write-Ok ("Web dev：http://127.0.0.1:" + $Port + "/")
    return [pscustomobject]@{ Process = $proc; Reused = $false }
}

function Start-Nginx {
    if (Test-HttpOk 'http://127.0.0.1/healthz' 3) {
        Write-WarnMsg '端口 80 上已有 Nginx（/healthz 可访问），复用现有进程'
        $existingPid = $null
        $pidPath = Join-Path $NginxRuntimeDir 'nginx.pid'
        if (Test-Path -LiteralPath $pidPath) {
            $raw = Get-Content -LiteralPath $pidPath -Raw -ErrorAction SilentlyContinue
            if ($raw -and $raw.Trim() -match '^\d+$') { $existingPid = [int]$raw.Trim() }
        }
        return [pscustomobject]@{ Process = $null; Reused = $true; NginxPid = $existingPid }
    }
    if (Test-PortOpen '127.0.0.1' 80) {
        throw '端口 80 已被其它进程占用；请停止该进程（见 docs/06-deployment.md 故障排查）'
    }
    if (-not (Test-Path -LiteralPath $NginxExe)) {
        throw ('未找到 nginx.exe：' + $NginxExe + '（可用 -NginxExe 指定本机 nginx 路径）')
    }
    if (-not (Test-Path -LiteralPath $NginxConf)) { throw ('缺少 Nginx 配置：' + $NginxConf) }
    $indexFile = Join-Path $RepoRoot 'web\dist\index.html'
    if (-not (Test-Path -LiteralPath $indexFile)) {
        throw '缺少 web/dist/index.html；请先执行 cd web; pnpm build，再使用 -WithNginx'
    }

    Write-Step ('启动本机 Nginx：' + $NginxExe + '（端口 80）')
    $prefix = ($NginxRuntimeDir -replace '\\', '/') + '/'
    $conf = $NginxConf -replace '\\', '/'
    Start-Process -FilePath $NginxExe -ArgumentList @('-p', $prefix, '-c', $conf) -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 1

    if (-not (Wait-Http 'http://127.0.0.1/healthz' 20)) {
        Show-LogTail (Join-Path $NginxRuntimeDir 'logs\error.log') 30
        throw 'Nginx 未能在 20s 内提供 /healthz；请运行 nginx -t 检查配置'
    }
    $nginxPid = $null
    $pidPath = Join-Path $NginxRuntimeDir 'nginx.pid'
    if (Test-Path -LiteralPath $pidPath) {
        $raw = Get-Content -LiteralPath $pidPath -Raw -ErrorAction SilentlyContinue
        if ($raw -and $raw.Trim() -match '^\d+$') { $nginxPid = [int]$raw.Trim() }
    }
    Write-Ok 'Nginx 网关：http://127.0.0.1/（静态 Web + /api 反代 + /ws + Swagger）'
    return [pscustomobject]@{ Process = $null; Reused = $false; NginxPid = $nginxPid }
}

# =============================================================================
# main
# =============================================================================
try {
    if ($Stop) {
        Stop-LocalStack
        exit 0
    }

    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host ' restaurant-mvp · 本机开发编排（无 Docker 等价链路）' -ForegroundColor Cyan
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host (' 项目根目录 : ' + $RepoRoot)

    $storageDir = Initialize-Directories

    Write-Step '加载环境变量'
    $envFile = Join-Path $RepoRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) {
        $envFile = Join-Path $RepoRoot '.env.example'
        Write-WarnMsg '未找到 .env，回退使用 .env.example（仅本地演示，生产请复制 .env 并替换密钥）'
    }
    $envCount = Import-DotEnv $envFile
    Write-Ok ('已加载 ' + $envCount + ' 个键：' + $envFile)
    $env:STORAGE_LOCAL_DIR = $storageDir
    if (-not $env:STORAGE_TYPE) { $env:STORAGE_TYPE = 'local' }
    Write-Ok ('STORAGE_LOCAL_DIR=' + $storageDir)

    if (-not $SkipChecks -and -not $SkipBackend) {
        Write-Step '检查本机依赖服务'
        if (Test-PortOpen '127.0.0.1' 3306) { Write-Ok 'MySQL 127.0.0.1:3306 可连接' }
        else { throw 'MySQL 127.0.0.1:3306 未监听；请先启动本机 MySQL（见 docs/06-deployment.md）' }
        if (Test-PortOpen '127.0.0.1' 6379) { Write-Ok 'Redis 127.0.0.1:6379 可连接' }
        else { Write-WarnMsg 'Redis 127.0.0.1:6379 未监听；后端缓存/限流可能启动失败' }
    }

    $backendInfo = [pscustomobject]@{ Process = $null; Reused = $true }
    if (-not $SkipBackend) { $backendInfo = Start-Backend -Port $BackendPort }
    else { Write-WarnMsg '已跳过后端启动（-SkipBackend）' }

    $webInfo = [pscustomobject]@{ Process = $null; Reused = $true }
    if (-not $SkipWeb) { $webInfo = Start-Web -Port $WebPort }
    else { Write-WarnMsg '已跳过 Web 启动（-SkipWeb）' }

    $nginxInfo = [pscustomobject]@{ Process = $null; Reused = $true; NginxPid = $null }
    if ($WithNginx) {
        if ($SkipBackend) { Write-WarnMsg '后端未启动，Nginx 的 /api 反代将返回 502' }
        $nginxInfo = Start-Nginx
    }

    $backendPid = $null
    if ($backendInfo -and $backendInfo.Process) { $backendPid = $backendInfo.Process.Id }
    $webPid = $null
    if ($webInfo -and $webInfo.Process) { $webPid = $webInfo.Process.Id }
    $nginxPid = $null
    if ($nginxInfo -and $nginxInfo.NginxPid) { $nginxPid = [int]$nginxInfo.NginxPid }

    $state = [ordered]@{
        startedAt = (Get-Date).ToString('o')
        repoRoot = $RepoRoot
        backendPort = $BackendPort
        webPort = $WebPort
        backendPid = $backendPid
        webPid = $webPid
        nginxPid = $nginxPid
    }
    $state | ConvertTo-Json | Set-Content -LiteralPath $PidFile -Encoding UTF8

    Write-Host ''
    Write-Host '------------------------------------------------------------' -ForegroundColor Green
    Write-Step '就绪'
    if (-not $SkipBackend) { Write-Host ('  后端 API   : http://127.0.0.1:' + $BackendPort + '/api/v1') }
    if (-not $SkipBackend) { Write-Host ('  健康检查   : http://127.0.0.1:' + $BackendPort + '/actuator/health') }
    if (-not $SkipBackend) { Write-Host ('  Swagger    : http://127.0.0.1:' + $BackendPort + '/swagger-ui/index.html') }
    if (-not $SkipWeb) { Write-Host ('  Web dev    : http://127.0.0.1:' + $WebPort + '/') }
    if ($WithNginx) { Write-Host '  Nginx 网关 : http://127.0.0.1/' -ForegroundColor Green }
    Write-Host ('  日志目录   : ' + $LogDir)
    Write-Host ('  PID 记录   : ' + $PidFile)
    Write-Host '  停止命令   : powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1 -Stop'
    Write-Host '------------------------------------------------------------' -ForegroundColor Green
    exit 0
} catch {
    Write-Host ''
    Write-FailMsg $_.Exception.Message
    Write-Host '  可运行 -Stop 清理已记录进程；环境检查可加 -SkipChecks。' -ForegroundColor DarkGray
    exit 1
}
