param(
    [string]$EnvFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path '.env.local'),
    [switch]$Apply
)

# Repairs only the demo accounts' password_hash column so that a database seeded with
# the legacy placeholder hashes (PBKDF2v1$...) can be used with BCrypt-only verification.
# It never touches any other table or column, and it is idempotent: rows that already
# hold a BCrypt hash are skipped by the WHERE clause.

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$seedFile = Join-Path $repoRoot 'database\seed\demo_synthetic_seed_m01_m02.sql'

if (-not (Test-Path -LiteralPath $EnvFile)) { throw '缺少本地环境文件 .env.local。' }
if (-not (Test-Path -LiteralPath $seedFile)) { throw "缺少种子文件: $seedFile" }

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

# Source of truth: the BCrypt hashes already committed in the synthetic seed.
$accounts = @()
foreach ($line in Get-Content -LiteralPath $seedFile) {
    if ($line -match "\(\s*(\d+),\s*'(?:STAFF|FAMILY)',\s*'(\d{11})',\s*'(\`$2[aby]\`$\d{2}\`$[./A-Za-z0-9]{53})'") {
        $accounts += [pscustomobject]@{
            UserId = [int]$Matches[1]
            Phone  = $Matches[2]
            Hash   = $Matches[3]
        }
    }
}
if ($accounts.Count -eq 0) { throw '未能从种子文件中解析出 BCrypt 演示账号哈希。' }

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

    $stateQuery = "SELECT user_id, phone_normalized, LEFT(password_hash, 12) FROM sys_user ORDER BY user_id;"
    $before = & $mysql.Source @mysqlArgs '--execute' $stateQuery
    if ($LASTEXITCODE -ne 0) { throw '查询 sys_user 失败。' }
    if (-not $before) { throw '演示库中没有 sys_user 记录，请先执行首次种子加载。' }

    Write-Output '当前 sys_user 密码哈希前缀：'
    $current = @{}
    foreach ($row in $before) {
        $parts = $row -split "`t"
        if ($parts.Count -lt 3) { continue }
        $current[[int]$parts[0]] = $parts[2]
        Write-Output ("  user_id={0}  {1}  {2}" -f $parts[0], $parts[1], $parts[2])
    }

    $pending = @($accounts | Where-Object { -not ($current[$_.UserId] -like '$2*') })
    if ($pending.Count -eq 0) {
        Write-Output "`n全部演示账号已是 BCrypt 哈希，无需修复。"
        exit 0
    }

    Write-Output ("`n需要更新密码哈希的账号（{0} 个）：" -f $pending.Count)
    foreach ($row in $pending) {
        Write-Output ("  user_id={0}  {1}  ->  {2}…" -f $row.UserId, $row.Phone, $row.Hash.Substring(0, 12))
    }

    if (-not $Apply) {
        Write-Output "`n[预演] 未做任何修改。确认无误后加 -Apply 执行。"
        exit 0
    }

    $template = @'
UPDATE `sys_user`
   SET `password_hash` = '__HASH__'
 WHERE `user_id` = __ID__
   AND `phone_normalized` = '__PHONE__'
   AND `password_hash` NOT LIKE '$2%';
'@

    $script = New-Object System.Collections.Generic.List[string]
    $script.Add('START TRANSACTION;')
    foreach ($row in $pending) {
        $statement = $template.Replace('__HASH__', $row.Hash).
            Replace('__ID__', [string]$row.UserId).
            Replace('__PHONE__', $row.Phone)
        $script.Add($statement)
    }
    $script.Add('COMMIT;')

    ($script -join "`n") | & $mysql.Source @mysqlArgs
    if ($LASTEXITCODE -ne 0) { throw '更新密码哈希失败，事务已回滚。' }

    $after = & $mysql.Source @mysqlArgs '--execute' "SELECT COUNT(*) FROM sys_user WHERE password_hash NOT LIKE '`$2%';"
    if ($LASTEXITCODE -ne 0) { throw '校验密码哈希失败。' }
    if ([int]$after -ne 0) { throw "仍有 $after 个账号不是 BCrypt 哈希，请检查。" }

    Write-Output "`n已更新 $($pending.Count) 个账号，全部演示账号现为 BCrypt 哈希。"
    Write-Output '现在可以用种子注释中的口令登录（如 13800000001 / Admin123456）。'
} finally {
    $env:MYSQL_PWD = $previousPassword
}
