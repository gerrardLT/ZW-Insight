<#
.SYNOPSIS
  开发闭环统筹脚本：修改 → 推送 → 部署 → 测试 → 反馈

.DESCRIPTION
  实现 "测试-反馈-修改-验证" 循环自动化。
  - push: 提交并推送代码到main，触发CI/CD
  - test: 直接SSH到服务器执行测试（不等CI）
  - wait: 等待GitHub Actions完成并拉取结果
  - full: push + wait + test 完整闭环

.EXAMPLE
  # 完整闭环：推送 → 等部署 → 测试 → 反馈
  ./keys/dev-loop.ps1 full

.EXAMPLE
  # 仅推送代码（触发CI/CD自动部署+测试）
  ./keys/dev-loop.ps1 push

.EXAMPLE
  # 手动触发测试（不推代码，直接测当前服务器版本）
  ./keys/dev-loop.ps1 test

.EXAMPLE
  # 等待最近一次CI完成并展示结果
  ./keys/dev-loop.ps1 wait
#>
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [ValidateSet('push', 'test', 'wait', 'full', 'status')]
  [string]$Command,

  [Parameter(Position = 1)]
  [string]$CommitMessage = "chore: dev-loop auto commit"
)

$ErrorActionPreference = 'Stop'

# ─── 配置 ────────────────────────────────────────────────────────────────────
$Pem = $env:ZWI_PEM; if (-not $Pem) { $Pem = 'C:\Users\gerrard\.ssh\zwinsight.pem' }
$RemoteHost = $env:ZWI_HOST; if (-not $RemoteHost) { $RemoteHost = 'root@129.204.3.200' }
$ServerIP = '129.204.3.200'
$RepoOwner = 'gerrardLT'  # GitHub org/user
$RepoName = 'ZW-Insight'             # GitHub repo name
$ResultDir = Join-Path $PSScriptRoot '..\test-results'

if (-not (Test-Path $ResultDir)) { New-Item -ItemType Directory -Path $ResultDir | Out-Null }

function Write-Phase($msg) {
  Write-Host ""
  Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
  Write-Host "  $msg" -ForegroundColor Cyan
  Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
}

function Write-Ok($msg) { Write-Host "  ✅ $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  ❌ $msg" -ForegroundColor Red }
function Write-Info($msg) { Write-Host "  ℹ️  $msg" -ForegroundColor Yellow }

# ─── 推送代码 ────────────────────────────────────────────────────────────────
function Do-Push {
  Write-Phase "推送代码到 main"

  # 检查是否有改动
  $status = git status --porcelain
  if (-not $status) {
    Write-Info "工作区无改动，跳过提交"
  } else {
    Write-Info "检测到 $(($status -split "`n").Count) 个文件改动"
    git add -A
    git commit -m $CommitMessage
    Write-Ok "本地提交完成"
  }

  git push origin main
  Write-Ok "已推送到 main，CI/CD 已触发"
  Write-Info "GitHub Actions 将自动：构建 → 部署 → 集成测试"
}

# ─── 直接SSH运行测试 ─────────────────────────────────────────────────────────
function Do-Test {
  Write-Phase "远程执行集成测试"

  $LocalScript = Join-Path $PSScriptRoot 'lifecycle-sim.sh'
  if (-not (Test-Path $LocalScript)) {
    Write-Fail "找不到 lifecycle-sim.sh: $LocalScript"
    Write-Info "请先创建测试脚本 keys/lifecycle-sim.sh"
    return
  }

  # 上传最新版本的测试脚本
  Write-Info "上传测试脚本..."
  & scp -i $Pem -o StrictHostKeyChecking=no $LocalScript "${RemoteHost}:/root/zwi-deploy/lifecycle-sim.sh"
  if ($LASTEXITCODE -ne 0) { Write-Fail "上传失败"; return }

  # 执行测试
  Write-Info "执行测试中（预计2-5分钟）..."
  $output = & ssh -i $Pem -o StrictHostKeyChecking=no $RemoteHost "sed -i 's/\r$//' /root/zwi-deploy/lifecycle-sim.sh; chmod +x /root/zwi-deploy/lifecycle-sim.sh; bash /root/zwi-deploy/lifecycle-sim.sh 2>&1"
  $exitCode = $LASTEXITCODE

  # 保存结果
  $resultFile = Join-Path $ResultDir "test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
  $output | Out-File -FilePath $resultFile -Encoding utf8
  Write-Info "测试结果已保存: $resultFile"

  # 分析结果
  Write-Phase "测试结果分析"
  $successCount = ($output | Select-String '✅').Count
  $failCount = ($output | Select-String '❌').Count
  $warnCount = ($output | Select-String '⚠️').Count

  Write-Host ""
  Write-Host "  成功: $successCount" -ForegroundColor Green
  Write-Host "  失败: $failCount" -ForegroundColor Red
  Write-Host "  警告: $warnCount" -ForegroundColor Yellow
  Write-Host ""

  if ($failCount -gt 0) {
    Write-Fail "存在 $failCount 项失败："
    Write-Host ""
    $output | Select-String '❌' | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    Write-Host ""
    Write-Info "请修改代码后重新运行: ./keys/dev-loop.ps1 full"
  } else {
    Write-Ok "所有测试通过！"
  }

  # 下载详细日志
  & scp -i $Pem -o StrictHostKeyChecking=no "${RemoteHost}:/root/zwi-deploy/lifecycle-sim.log" (Join-Path $ResultDir "lifecycle-detail.log") 2>$null
}

# ─── 等待CI完成 ──────────────────────────────────────────────────────────────
function Do-Wait {
  Write-Phase "等待 GitHub Actions 完成"

  # 检查是否安装了 gh CLI
  $ghAvailable = Get-Command gh -ErrorAction SilentlyContinue
  if (-not $ghAvailable) {
    Write-Info "未安装 GitHub CLI (gh)，改用轮询服务器方式等待"
    Write-Info "等待部署生效（预计5-8分钟）..."

    # 轮询健康检查
    for ($i = 1; $i -le 20; $i++) {
      Start-Sleep -Seconds 30
      try {
        $resp = Invoke-WebRequest -Uri "http://${ServerIP}:18080/api/v1/system/menu/user" -TimeoutSec 10 -ErrorAction SilentlyContinue
        if ($resp.StatusCode -eq 200 -or $resp.StatusCode -eq 401 -or $resp.StatusCode -eq 403) {
          Write-Ok "服务已就绪 (HTTP $($resp.StatusCode))"
          return
        }
      } catch {
        Write-Info "等待中... ($i/20)"
      }
    }
    Write-Fail "等待超时，服务可能未就绪"
    return
  }

  # 使用 gh CLI 等待
  Write-Info "使用 gh CLI 监控 workflow 运行..."
  & gh run watch --repo "${RepoOwner}/${RepoName}" --exit-status
  if ($LASTEXITCODE -eq 0) {
    Write-Ok "CI/CD + 集成测试 全部通过"
  } else {
    Write-Fail "CI/CD 或集成测试失败，请检查 GitHub Actions 日志"
    & gh run view --repo "${RepoOwner}/${RepoName}" --log-failed
  }
}

# ─── 查看状态 ────────────────────────────────────────────────────────────────
function Do-Status {
  Write-Phase "当前状态"

  # Git 状态
  Write-Info "Git 工作区:"
  $status = git status --short
  if ($status) { $status | ForEach-Object { Write-Host "    $_" } }
  else { Write-Host "    (无改动)" -ForegroundColor Gray }

  # 服务器状态
  Write-Info "服务器后端状态:"
  try {
    $resp = Invoke-WebRequest -Uri "http://${ServerIP}:18080/api/v1/system/menu/user" -TimeoutSec 5 -ErrorAction Stop
    Write-Ok "后端运行中 (HTTP $($resp.StatusCode))"
  } catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401 -or $code -eq 403) {
      Write-Ok "后端运行中 (HTTP $code - 需要认证)"
    } else {
      Write-Fail "后端不可达: $_"
    }
  }

  # 最近测试结果
  $latestLog = Get-ChildItem $ResultDir -Filter "test-*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($latestLog) {
    Write-Info "最近测试: $($latestLog.Name) ($($latestLog.LastWriteTime))"
  }
}

# ─── 完整闭环 ────────────────────────────────────────────────────────────────
function Do-Full {
  Write-Phase "完整开发闭环：推送 → 等待部署 → 测试 → 反馈"
  Write-Host ""

  Do-Push
  Write-Host ""
  Write-Info "等待部署完成..."
  Do-Wait
  Write-Host ""
  Do-Test

  Write-Host ""
  Write-Phase "闭环完成"
  Write-Info "如有失败项：修改代码 → 再次运行 ./keys/dev-loop.ps1 full"
}

# ─── 主入口 ──────────────────────────────────────────────────────────────────
switch ($Command) {
  'push'   { Do-Push }
  'test'   { Do-Test }
  'wait'   { Do-Wait }
  'full'   { Do-Full }
  'status' { Do-Status }
}
