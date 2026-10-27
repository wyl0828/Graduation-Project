param(
    [string]$EnvFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path '.env.local'),
    [switch]$Apply
)

# Repairs only the demo activation ticket so the documented 6-digit code (123456) really
# matches its stored MAC, and refreshes its 24-hour window. It never touches a consumed
# ticket (status USED) and never clears other rows.

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ticketId = 'act-ticket-20261025-0002'

if (-not (Test-Path -LiteralPath $EnvFile)) { throw '缺少本地环境文件 .env.local。' }

$values = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    $line = $line.Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw '本地环境文件存在无效行。' }
    $value = $Matches[2].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $values[$Matches[1]] = $value
}

$dbHost = $values['DB_HOST']
if ([string]::IsNullOrWhiteSpace($dbHost) -or $dbHost.Trim().ToLowerInvariant() -notin @('localhost', '127.0.0.1', '::1')) {
    throw '安全保护：DB_HOST 必须指向本机。'
}
if ($values['DB_NAME'] -cne 'elderly_care_demo') { throw '安全保护：仅允许修复隔离演示库 elderly_care_demo。' }
if ($values['DB_USERNAME'] -cne 'elderly_app') { throw '安全保护：仅允许使用 elderly_app 演示账号。' }
if ([string]::IsNullOrWhiteSpace($values['DB_PASSWORD'])) { throw '请在 .env.local 中配置 DB_PASSWORD。' }
$dbPort = if ($values['DB_PORT']) { $values['DB_PORT'] } else { '3306' }

$mysql = Get-Command mysql -ErrorAction Stop
$mysqlArgs = @(
    '--protocol=TCP',
    "--host=$dbHost",
    "--port=$dbPort",
    '--user=elderly_app',
    '--database=elderly_care_demo',
    '--default-character-set=utf8mb4',
    '--batch',
    '--skip-column-names'
)

$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $values['DB_PASSWORD']

    $stateQuery = "SELECT ticket_id, elder_id, status, LEFT(HEX(code_mac), 16), expires_at FROM elder_activation_ticket WHERE ticket_id = '$ticketId';"
    $before = & $mysql.Source @mysqlArgs '--execute' $stateQuery
    if ($LASTEXITCODE -ne 0) { throw '查询激活票据失败。' }
    if (-not $before) { throw "演示库中不存在票据 $ticketId，请先执行首次种子加载。" }
    Write-Output '当前票据状态：'
    Write-Output ("  {0}" -f $before)

    $row = $before -split "`t"
    if ($row[2] -eq 'USED') {
        Write-Output "`n该票据已被消费 (USED)，不做任何修改。"
        exit 0
    }

    $statement = @'
UPDATE `elder_activation_ticket`
   SET `code_mac` = UNHEX(SHA2('123456', 256)),
       `status` = 'ISSUED',
       `issued_at` = DATE_SUB(NOW(), INTERVAL 1 HOUR),
       `expires_at` = DATE_ADD(NOW(), INTERVAL 23 HOUR),
       `failed_attempts` = 0
 WHERE `ticket_id` = 'act-ticket-20261025-0002'
   AND `status` <> 'USED';
'@

    if (-not $Apply) {
        Write-Output "`n[预演] 将把该票据的 code_mac 设为 SHA-256('123456') 并刷新 24 小时有效期。加 -Apply 执行。"
        exit 0
    }

    $statement | & $mysql.Source @mysqlArgs
    if ($LASTEXITCODE -ne 0) { throw '更新激活票据失败。' }

    $after = & $mysql.Source @mysqlArgs '--execute' $stateQuery
    Write-Output "`n更新后："
    Write-Output ("  {0}" -f $after)
    Write-Output "`n演示激活码：123456（票据 $ticketId，有效期 24 小时）。"
} finally {
    $env:MYSQL_PWD = $previousPassword
}
