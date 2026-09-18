# ============================================================
# pack.ps1 — 在 Windows 本地构建后端 jar 与前端 dist，并归集到 deploy 部署包
# 用法（在项目根目录执行）：
#   .\deploy\pack.ps1                 # 全量构建（后端 + 前端）并打包
#   .\deploy\pack.ps1 -SkipBackend    # 只用现有 target 里的 jar
#   .\deploy\pack.ps1 -SkipFrontend   # 只用现有 dist
#   .\deploy\pack.ps1 -SkipBuild      # 不重新编译，仅拷贝现有产物
# ============================================================
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root  = Split-Path -Parent $PSScriptRoot          # 项目根目录
$deploy = $PSScriptRoot                             # deploy 目录

Write-Host '==================== 打制品包 ====================' -ForegroundColor Cyan

# ---------- 后端 ----------
if (-not $SkipBackend) {
    if (-not $SkipBuild) {
        Write-Host ">>> 构建后端（mvn package -DskipTests）..." -ForegroundColor Yellow
        Push-Location $root
        try {
            mvn clean package -DskipTests -pl cangjie-start -am -B
            if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败（退出码 $LASTEXITCODE）" }
        } finally { Pop-Location }
    }
    $jar = Join-Path $root 'cangjie-start\target\cangjie-start.jar'
    if (-not (Test-Path $jar)) { throw "未找到后端产物：$jar" }
    $destDir = Join-Path $deploy 'app'
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    Copy-Item $jar (Join-Path $destDir 'cangjie-start.jar') -Force
    $mb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Write-Host ">>> 后端制品已就位：app\cangjie-start.jar ($mb MB)" -ForegroundColor Green
}

# ---------- 前端 ----------
if (-not $SkipFrontend) {
    $ui = Join-Path $root 'cangjie-agent-ui'
    if (-not $SkipBuild) {
        Write-Host ">>> 构建前端（npm run build）..." -ForegroundColor Yellow
        Push-Location $ui
        try {
            if (-not (Test-Path 'node_modules')) { npm install }
            npm run build
            if ($LASTEXITCODE -ne 0) { throw "前端构建失败（退出码 $LASTEXITCODE）" }
        } finally { Pop-Location }
    }
    $dist = Join-Path $ui 'dist'
    if (-not (Test-Path (Join-Path $dist 'admin')) -or -not (Test-Path (Join-Path $dist 'chat'))) {
        throw "未找到前端产物：$dist\admin 或 $dist\chat（请先执行 npm run build）"
    }
    $destDir = Join-Path $deploy 'frontend\dist'
    if (Test-Path $destDir) { Remove-Item $destDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    Copy-Item (Join-Path $dist '*') $destDir -Recurse -Force
    Write-Host ">>> 前端制品已就位：frontend\dist（admin + chat）" -ForegroundColor Green
}

Write-Host ''
Write-Host '制品打包完成。接下来的操作：' -ForegroundColor Cyan
Write-Host '  1) 将整个 deploy 文件夹上传到服务器（无需上传源码）'
Write-Host '  2) 服务器上执行：chmod +x *.sh && sudo ./deploy-all.sh'
