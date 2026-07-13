<#
.SYNOPSIS
  开发闭环统筹脚本：修改 → 推送 → 部署 → 测试 → 反馈

.DESCRIPTION
  实现 "测试-反馈-修改-验证" 循环自动化。
  - push:  提交并推送代码到main，触发CI/CD
  - quick: 本地快速测试（仅 L1 单元测试）
  - test:  完整远程测试（L1+L3+L4）通过 run-all-tests.sh 编排
  - wait:  等待GitHub Actions完成并拉取结果
  - full:  push + wait + test 完整闭环
  - status: 查看当前状态

.EXAMPLE
  # 完整闭环：推送 → 等部署 → 测试 → 反馈
  ./keys/dev-loop.ps1 full

.EXAMPLE
  # 仅推送代码（触发CI/CD自动部署+测试）
  ./keys/dev-loop.ps1 push

.EXAMPLE
  # 本地快速测试：仅执行 L1 单元测试
  ./keys/dev-loop.ps1 quick

.EXAMPLE
  # 完整远程测试（L1+L3+L4）
  ./keys/dev-loop.ps1 test

.EXAMPLE
  # 自定义层级组合
  ./keys/dev-loop.ps1 test -Layers "L1,L3"

.EXAMPLE
  # 等待最近一次CI完成并展示结果
  ./keys/dev-loop.ps1 wait
#>
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [ValidateSet('push', 'test', 'quick', 'wait', 'full', 'status')]
  [string]$Command,

  [Parameter(Position = 1)]
  [string]$CommitMessage = "chore: dev-loop auto commit",

  [Parameter()]
  [string]$Layers = ""
)

$ErrorActionPreference = 'Stop'

# ─── 配置 ────────────────────────────────────────────────────────────────────
$Pem = $env:ZWI_PEM; if (-not $Pem) { $Pem = 'C:\Users\gerrard\.ssh\zwinsight.pem' }
$RemoteHost = $env:ZWI_HOST; if (-not $RemoteHost) { $RemoteHost = 'root@129.204.3.200' }
$ServerIP = '129.204.3.200'
$RepoOwner = 'gerrardLT'
$RepoName = 'ZW-Insight'
$ResultDir = Join-Path $PSScriptRoot '..\test-results'

if (-not (Test-Path $ResultDir)) { New-Item -ItemType Directory -Path $ResultDir | Out-Null }

function Write-Phase($msg) {
  Write-Host ""
  Write-Host "=======================================================" -ForegroundColor Cyan
  Write-Host "  $msg" -ForegroundColor Cyan
  Write-Host "=======================================================" -ForegroundColor Cyan
}

function Write-Ok($msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Write-Info($msg) { Write-Host "  [INFO] $msg" -ForegroundColor Yellow }

# ─── 测试结果汇总输出 ─────────────────────────────────────────────────────────
function Show-TestSummary {
  param(
    [string[]]$TestOutput,
    [int]$TestExitCode,
    [TimeSpan]$TestDuration,
    [string]$LayerName,
    [string]$TestLogFile
  )

  Write-Phase "test results: $LayerName"

  $mins = [math]::Floor($TestDuration.TotalMinutes)
  $secs = $TestDuration.Seconds
  Write-Info "duration: ${mins}m ${secs}s"

  # Parse Maven Surefire style output
  $testsRun = 0; $failures = 0; $errors = 0; $skipped = 0
  foreach ($line in $TestOutput) {
    if ($line -match 'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)') {
      $testsRun += [int]$Matches[1]
      $failures += [int]$Matches[2]
      $errors += [int]$Matches[3]
      $skipped += [int]$Matches[4]
    }
  }

  # Parse pass/fail markers from run-all-tests.sh
  $successCount = 0; $failCount = 0
  foreach ($line in $TestOutput) {
    if ($line -match 'PASS|INFO.*pass') { $successCount++ }
    if ($line -match 'FAIL') { $failCount++ }
  }

  Write-Host ""
  if ($testsRun -gt 0) {
    $passed = $testsRun - $failures - $errors - $skipped
    Write-Host "  +-------------------------------------+" -ForegroundColor Gray
    Write-Host "  | Tests run: $testsRun" -ForegroundColor Gray
    if ($passed -gt 0) { Write-Host "  | Passed:    $passed" -ForegroundColor Green }
    if ($failures -gt 0) { Write-Host "  | Failures:  $failures" -ForegroundColor Red } else { Write-Host "  | Failures:  0" -ForegroundColor Green }
    if ($errors -gt 0) { Write-Host "  | Errors:    $errors" -ForegroundColor Red } else { Write-Host "  | Errors:    0" -ForegroundColor Green }
    if ($skipped -gt 0) { Write-Host "  | Skipped:   $skipped" -ForegroundColor Yellow } else { Write-Host "  | Skipped:   0" -ForegroundColor Green }
    Write-Host "  +-------------------------------------+" -ForegroundColor Gray
  } else {
    Write-Host "  +-------------------------------------+" -ForegroundColor Gray
    Write-Host "  | Success items: $successCount" -ForegroundColor Green
    if ($failCount -gt 0) { Write-Host "  | Failed items:  $failCount" -ForegroundColor Red } else { Write-Host "  | Failed items:  0" -ForegroundColor Green }
    Write-Host "  +-------------------------------------+" -ForegroundColor Gray
  }
  Write-Host ""

  if ($TestExitCode -ne 0) {
    Write-Fail "Tests did not all pass (exit code: $TestExitCode)"
    Write-Host ""

    # Output failure summary
    Write-Host "  --- Failure Summary ---" -ForegroundColor Red
    $failureLines = @()
    for ($i = 0; $i -lt $TestOutput.Count; $i++) {
      $l = $TestOutput[$i]
      if ($l -match 'FAIL|FAILURE|AssertionError|Failures:\s*[1-9]') {
        $failureLines += $l.Trim()
        if ($failureLines.Count -ge 15) { break }
      }
    }

    if ($failureLines.Count -gt 0) {
      foreach ($fl in $failureLines) {
        Write-Host "    $fl" -ForegroundColor Red
      }
    } else {
      # Try to find BUILD FAILURE context
      $foundBuildFailure = $false
      for ($i = 0; $i -lt $TestOutput.Count; $i++) {
        if ($TestOutput[$i] -match 'BUILD FAILURE') {
          $foundBuildFailure = $true
          Write-Host "    $($TestOutput[$i].Trim())" -ForegroundColor Red
          $endIdx = [math]::Min($i + 5, $TestOutput.Count - 1)
          for ($j = $i + 1; $j -le $endIdx; $j++) {
            if ($TestOutput[$j].Trim()) {
              Write-Host "    $($TestOutput[$j].Trim())" -ForegroundColor Red
            }
          }
          break
        }
      }
      if (-not $foundBuildFailure) {
        Write-Host "    See log for details: $TestLogFile" -ForegroundColor Red
      }
    }
    Write-Host ""
    Write-Info "Fix issues then rerun: ./keys/dev-loop.ps1 $Command"
  } else {
    Write-Ok "All tests passed!"
  }
}

# ─── 推送代码 ────────────────────────────────────────────────────────────────
function Do-Push {
  Write-Phase "Push code to main"

  $status = git status --porcelain
  if (-not $status) {
    Write-Info "Working tree clean, skip commit"
  } else {
    $changeCount = ($status -split "`n").Count
    Write-Info "Detected $changeCount file changes"
    git add -A
    git commit -m $CommitMessage
    Write-Ok "Local commit done"
  }

  git push origin main
  Write-Ok "Pushed to main, CI/CD triggered"
  Write-Info "GitHub Actions will: build -> deploy -> integration test"
}

# ─── 本地快速测试（仅 L1 单元测试）─────────────────────────────────────────
function Do-QuickTest {
  Write-Phase "Quick local test (L1 unit tests only)"

  $ServerDir = Join-Path $PSScriptRoot '..\zw-insight-server'
  $pomFile = Join-Path $ServerDir 'pom.xml'
  if (-not (Test-Path $pomFile)) {
    Write-Fail "Cannot find zw-insight-server/pom.xml"
    Write-Info "Please verify project structure is intact"
    return
  }

  $mvn = Get-Command mvn -ErrorAction SilentlyContinue
  if (-not $mvn) {
    Write-Fail "mvn command unavailable, please install Maven"
    return
  }

  Write-Info "Running: mvn test -B (unit tests)"
  $startTime = Get-Date

  $output = & mvn test -B -f $pomFile 2>&1
  $exitCode = $LASTEXITCODE
  $elapsed = (Get-Date) - $startTime

  # Save test output
  $logFile = Join-Path $ResultDir ("quick-test-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".log")
  $output | Out-File -FilePath $logFile -Encoding utf8

  Show-TestSummary -TestOutput $output -TestExitCode $exitCode -TestDuration $elapsed -LayerName "L1 Unit Tests" -TestLogFile $logFile
}

# ─── 完整测试（L1+L3+L4 通过 run-all-tests.sh 编排）──────────────────────────
function Do-Test {
  Write-Phase "Full test (via run-all-tests.sh)"

  $RunAllScript = Join-Path $PSScriptRoot '..\tests\run-all-tests.sh'
  if (-not (Test-Path $RunAllScript)) {
    Write-Fail "Cannot find tests/run-all-tests.sh"
    Write-Info "Please create the unified test orchestration script first"
    return
  }

  # Determine layers to execute
  $layerArg = "L1,L3,L4"
  if ($Layers) { $layerArg = $Layers }
  Write-Info "Test layers: $layerArg"

  # Determine execution method: local bash or remote SSH
  $bashAvailable = Get-Command bash -ErrorAction SilentlyContinue

  if ($bashAvailable) {
    Write-Info "Using local bash to execute run-all-tests.sh..."
    $startTime = Get-Date

    $output = & bash $RunAllScript "--layers=$layerArg" --fail-fast 2>&1
    $exitCode = $LASTEXITCODE
    $elapsed = (Get-Date) - $startTime

  } else {
    Write-Info "No local bash, executing via SSH..."
    Write-Info "Uploading run-all-tests.sh to server..."

    & scp -i $Pem -o StrictHostKeyChecking=no $RunAllScript "${RemoteHost}:/root/zwi-deploy/run-all-tests.sh"
    if ($LASTEXITCODE -ne 0) { Write-Fail "Upload failed"; return }

    # Sync API test scripts
    $keysDir = Join-Path $PSScriptRoot '.'
    $apiScripts = Get-ChildItem $keysDir -Filter "test-api-*.sh"
    foreach ($script in $apiScripts) {
      & scp -i $Pem -o StrictHostKeyChecking=no $script.FullName "${RemoteHost}:/root/zwi-deploy/keys/" 2>$null
    }

    # Remote execution
    $startTime = Get-Date
    $sedCmd = "sed -i 's/\r" + '$' + "//' /root/zwi-deploy/run-all-tests.sh"
    $bashCmd = "bash /root/zwi-deploy/run-all-tests.sh --layers=$layerArg --fail-fast"
    $fullCmd = "$sedCmd && chmod +x /root/zwi-deploy/run-all-tests.sh && $bashCmd"
    $output = & ssh -i $Pem -o StrictHostKeyChecking=no $RemoteHost $fullCmd 2>&1
    $exitCode = $LASTEXITCODE
    $elapsed = (Get-Date) - $startTime
  }

  # Save results
  $logFile = Join-Path $ResultDir ("test-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".log")
  $output | Out-File -FilePath $logFile -Encoding utf8
  Write-Info "Test log saved: $logFile"

  # Show summary
  Show-TestSummary -TestOutput $output -TestExitCode $exitCode -TestDuration $elapsed -LayerName "Full Test ($layerArg)" -TestLogFile $logFile

  # Download JSON report (remote mode only)
  if (-not $bashAvailable) {
    $remoteReport = "${RemoteHost}:/root/zwi-deploy/tests/reports/all-tests-report.json"
    $localReport = Join-Path $ResultDir "all-tests-report.json"
    & scp -i $Pem -o StrictHostKeyChecking=no $remoteReport $localReport 2>$null
    if (Test-Path $localReport) {
      Write-Info "JSON report downloaded: $localReport"
    }
  }
}

# ─── 等待CI完成 ──────────────────────────────────────────────────────────────
function Do-Wait {
  Write-Phase "Wait for GitHub Actions"

  $ghAvailable = Get-Command gh -ErrorAction SilentlyContinue
  if (-not $ghAvailable) {
    Write-Info "GitHub CLI (gh) not installed, polling server instead"
    Write-Info "Waiting for deploy (est. 5-8 min)..."

    for ($i = 1; $i -le 20; $i++) {
      Start-Sleep -Seconds 30
      try {
        $healthUrl = "http://${ServerIP}:18080/api/v1/system/menu/user"
        $resp = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 10 -ErrorAction SilentlyContinue
        if ($resp.StatusCode -eq 200 -or $resp.StatusCode -eq 401 -or $resp.StatusCode -eq 403) {
          Write-Ok "Service ready (HTTP $($resp.StatusCode))"
          return
        }
      } catch {
        Write-Info "Waiting... ($i/20)"
      }
    }
    Write-Fail "Timeout, service may not be ready"
    return
  }

  Write-Info "Monitoring workflow via gh CLI..."
  $repoSlug = "$RepoOwner/$RepoName"
  & gh run watch --repo $repoSlug --exit-status
  if ($LASTEXITCODE -eq 0) {
    Write-Ok "CI/CD + integration tests all passed"
  } else {
    Write-Fail "CI/CD or integration tests failed, check GitHub Actions logs"
    & gh run view --repo $repoSlug --log-failed
  }
}

# ─── 查看状态 ────────────────────────────────────────────────────────────────
function Do-Status {
  Write-Phase "Current Status"

  # Git status
  Write-Info "Git working tree:"
  $status = git status --short
  if ($status) { $status | ForEach-Object { Write-Host "    $_" } }
  else { Write-Host "    (no changes)" -ForegroundColor Gray }

  # Server status
  Write-Info "Server backend status:"
  try {
    $healthUrl = "http://${ServerIP}:18080/api/v1/system/menu/user"
    $resp = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 5 -ErrorAction Stop
    Write-Ok "Backend running (HTTP $($resp.StatusCode))"
  } catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401 -or $code -eq 403) {
      Write-Ok "Backend running (HTTP $code - auth required)"
    } else {
      Write-Fail "Backend unreachable: $_"
    }
  }

  # Latest test result
  $latestLog = Get-ChildItem $ResultDir -Filter "test-*.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($latestLog) {
    Write-Info "Latest test: $($latestLog.Name) ($($latestLog.LastWriteTime))"
  }
}

# ─── 完整闭环 ────────────────────────────────────────────────────────────────
function Do-Full {
  Write-Phase "Full dev loop: push -> wait deploy -> test -> feedback"
  Write-Host ""

  Do-Push
  Write-Host ""
  Write-Info "Waiting for deployment..."
  Do-Wait
  Write-Host ""
  Do-Test

  Write-Host ""
  Write-Phase "Loop complete"
  Write-Info "If failures exist: fix code -> rerun ./keys/dev-loop.ps1 full"
  Write-Info "Quick local verify (unit tests only): ./keys/dev-loop.ps1 quick"
}

# ─── 主入口 ──────────────────────────────────────────────────────────────────
switch ($Command) {
  'push'   { Do-Push }
  'quick'  { Do-QuickTest }
  'test'   { Do-Test }
  'wait'   { Do-Wait }
  'full'   { Do-Full }
  'status' { Do-Status }
}
