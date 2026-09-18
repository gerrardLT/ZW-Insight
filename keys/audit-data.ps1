<#
.SYNOPSIS
  ZW-Insight 线上数据库第 7 轮数据审计的本地入口。
  把 keys/audit-data-round7.sh 上传到服务器并经 SSH 调用，
  执行完毕后把生成的 Markdown 报告下载到本地 audit-reports/。

.DESCRIPTION
  - 全程只读审计，禁止任何写操作（脚本内置关键字拦截）。
  - 敏感值（私钥/DB口令/token）在服务器侧脚本内脱敏后才回显，本地不落明文。
  - SSH 私钥默认 C:\Users\gerrard\.ssh\zwinsight.pem，目标 root@129.204.3.200。

.EXAMPLE
  # 完整审计（Section 0-7）
  ./keys/audit-data.ps1

.EXAMPLE
  # 只跑回归检查（~30s）
  ./keys/audit-data.ps1 -Regression

.EXAMPLE
  # 只跑第 3 节（金额勾稽）
  ./keys/audit-data.ps1 -Section 3

.EXAMPLE
  # 只跑新增表覆盖
  ./keys/audit-data.ps1 -NewTables
#>
param(
  [Parameter(Position = 0)]
  [ValidateSet('all', 'regression', 'new-tables', 'section')]
  [string]$Command = 'all',

  [Parameter(Position = 1)]
  [ValidateRange(0, 7)]
  [int]$Section = -1,

  [switch]$Regression,
  [switch]$NewTables,

  [string]$Pem = $env:ZWI_PEM,
  [string]$RemoteHost = $env:ZWI_HOST
)

$ErrorActionPreference = 'Stop'

if (-not $Pem) { $Pem = 'C:\Users\gerrard\.ssh\zwinsight.pem' }
if (-not $RemoteHost) { $RemoteHost = 'root@129.204.3.200' }
$RemoteDir = '/root/zwi-deploy'
$RemoteScript = "$RemoteDir/audit-data-round7.sh"
$LocalScript = Join-Path $PSScriptRoot 'audit-data-round7.sh'
$LocalReportDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'audit-reports'

if (-not (Test-Path $LocalScript)) { throw "找不到 audit-data-round7.sh: $LocalScript" }
if (-not (Test-Path $Pem)) { throw "找不到 SSH 私钥: $Pem" }
if (-not (Test-Path $LocalReportDir)) { New-Item -ItemType Directory -Path $LocalReportDir | Out-Null }

# 参数归一化：开关优先于位置参数
if ($Regression) { $Command = 'regression' }
elseif ($NewTables) { $Command = 'new-tables' }
elseif ($Section -ge 0) { $Command = 'section' }

# 时间戳（与服务器侧脚本独立生成，用于本地报告命名）
$RunTs = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH-mm-ssZ')
$RemoteReport = "$RemoteDir/audit-round7-$RunTs.md"
$LocalReport = Join-Path $LocalReportDir "data-audit-round7-$RunTs.md"

# 构造显示信息
$sectionInfo = ''
if ($Section -ge 0) { $sectionInfo = "Section=$Section" }

Write-Host '================ ZW-Insight 数据审计 Round 7 ================' -ForegroundColor Cyan
Write-Host "命令: $Command $sectionInfo"
Write-Host "远程: $RemoteHost"
Write-Host "报告: $LocalReport"
Write-Host ''

# 1) 上传审计脚本（每次同步最新版本）
Write-Host '[1/4] 上传 audit-data-round7.sh ...' -ForegroundColor Yellow
& scp -i $Pem -o StrictHostKeyChecking=no $LocalScript "${RemoteHost}:$RemoteScript" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'scp 上传 audit-data-round7.sh 失败' }

# 2) 构造远程命令：规范换行 + 赋可执行权限 + 执行 + 指定报告路径
$argLine = ''
if ($Command -eq 'section' -and $Section -ge 0) {
  $argLine = "section $Section"
} elseif ($Command -ne 'all') {
  $argLine = $Command
}
# 注意：PowerShell 中反引号 ` 是转义符，`$ 表示字面量 $
$remoteCmd = "sed -i 's/\r`$//' $RemoteScript; chmod +x $RemoteScript; ZWI_REPORT_FILE='$RemoteReport' bash $RemoteScript $argLine"

Write-Host '[2/4] 远程执行审计（可能需要 1-3 分钟）...' -ForegroundColor Yellow
& ssh -i $Pem -o StrictHostKeyChecking=no $RemoteHost $remoteCmd
$remoteExit = $LASTEXITCODE

# 3) 下载报告
Write-Host ''
Write-Host '[3/4] 下载审计报告 ...' -ForegroundColor Yellow
& scp -i $Pem -o StrictHostKeyChecking=no "${RemoteHost}:$RemoteReport" $LocalReport 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Warning "报告下载失败（远程路径: $RemoteReport），请检查远程执行是否成功"
} else {
  Write-Host "      已保存到: $LocalReport" -ForegroundColor Green
}

# 4) 汇总
Write-Host ''
Write-Host '[4/4] 审计完成' -ForegroundColor Yellow
Write-Host '================ 结果汇总 ================' -ForegroundColor Cyan

$exitMsg = ''
$exitColor = 'White'
if ($remoteExit -eq 0) {
  $exitMsg = '退出码: 0  [PASS] 全部通过（数据健康）'
  $exitColor = 'Green'
} elseif ($remoteExit -eq 1) {
  $exitMsg = '退出码: 1  [FAIL] 存在 FAIL 级缺陷（需立即处置）'
  $exitColor = 'Red'
} elseif ($remoteExit -eq 2) {
  $exitMsg = '退出码: 2  [WARN] 存在 WARN 级观察项（无 FAIL）'
  $exitColor = 'Yellow'
} elseif ($remoteExit -eq 3) {
  $exitMsg = '退出码: 3  [BLOCK] 脚本拒绝执行非只读 SQL（安全阀触发）'
  $exitColor = 'Magenta'
} else {
  $exitMsg = "退出码: $remoteExit  [ERROR] 非预期退出码（可能 SSH/MySQL 连接失败）"
  $exitColor = 'Red'
}
# 确保 $exitColor 有有效值
if (-not $exitColor) { $exitColor = 'White' }
Write-Host $exitMsg -ForegroundColor $exitColor

if (Test-Path $LocalReport) {
  Write-Host ''
  Write-Host "报告路径: $LocalReport" -ForegroundColor Cyan
  Write-Host "查看命令: Get-Content '$LocalReport'" -ForegroundColor DarkGray
}

exit $remoteExit
