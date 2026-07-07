<#
.SYNOPSIS
  前后端联调验证基座的本地入口（frontend-backend-integration spec, 任务 4.1）。
  把 keys/verify-base.sh 上传到服务器并经 SSH 调用，完成「真实登录→拿token→调接口→核对日志」闭环。

.DESCRIPTION
  - 真实接口、真实数据，禁止 mock / 假数据兜底。
  - 敏感值(私钥/token/验证码/DB口令)在服务器侧脚本内脱敏后才回显，本地不落明文。
  - SSH 私钥默认 C:\Users\gerrard\.ssh\zwinsight.pem，目标 root@129.204.3.200。

.EXAMPLE
  # 一次完整闭环：登录 → 调用动态菜单接口 → 核对日志
  ./keys/verify.ps1 loop GET /api/v1/system/menu/user

.EXAMPLE
  # 仅真实登录（带验证码重试，缓存 token）
  ./keys/verify.ps1 login

.EXAMPLE
  # 带 token 调用任意真实接口
  ./keys/verify.ps1 call GET /api/v1/system/user/page

.EXAMPLE
  # 核对最近 90 秒后端日志
  ./keys/verify.ps1 logs 90
#>
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [ValidateSet('login', 'call', 'logs', 'loop', 'clear-token')]
  [string]$Command,

  [Parameter(Position = 1, ValueFromRemainingArguments = $true)]
  [string[]]$Rest = @()
)

$ErrorActionPreference = 'Stop'

$Pem = $env:ZWI_PEM; if (-not $Pem) { $Pem = 'C:\Users\gerrard\.ssh\zwinsight.pem' }
$RemoteHost = $env:ZWI_HOST; if (-not $RemoteHost) { $RemoteHost = 'root@129.204.3.200' }
$RemoteDir = '/root/zwi-deploy'
$RemoteScript = "$RemoteDir/verify-base.sh"
$LocalScript = Join-Path $PSScriptRoot 'verify-base.sh'

if (-not (Test-Path $LocalScript)) { throw "找不到 verify-base.sh: $LocalScript" }

# 1) 上传基座脚本（每次同步最新版本，确保可复用且一致）
& scp -i $Pem -o StrictHostKeyChecking=no $LocalScript "${RemoteHost}:$RemoteScript" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "scp 上传 verify-base.sh 失败" }

# 2) 规范换行 + 赋可执行权限，再执行子命令
$argLine = ($Rest | ForEach-Object { "'" + ($_ -replace "'", "'\''") + "'" }) -join ' '
$remoteCmd = "sed -i 's/\r$//' $RemoteScript; chmod +x $RemoteScript; bash $RemoteScript $Command $argLine"

& ssh -i $Pem -o StrictHostKeyChecking=no $RemoteHost $remoteCmd
exit $LASTEXITCODE
