param(
    [string]$EnvFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path '.env.local'),
    [switch]$RequireMigration
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $EnvFile)) { throw '缺少本地环境文件。请先配置 .env.local。' }

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

$dbHost = if ($values['DB_HOST']) { $values['DB_HOST'] } else { '127.0.0.1' }
$dbPort = if ($values['DB_PORT']) { $values['DB_PORT'] } else { '3306' }
if ($dbHost -notin @('localhost', '127.0.0.1', '::1')) { throw '仅允许本机 MySQL。' }
if ($dbPort -notmatch '^\d{1,5}$' -or [int]$dbPort -lt 1 -or [int]$dbPort -gt 65535) { throw 'DB_PORT 无效。' }
if ($values['DB_NAME'] -ne 'elderly_care_demo') { throw '仅允许隔离演示库 elderly_care_demo。' }
if ($values['DB_USERNAME'] -ne 'elderly_app' -or -not $values['DB_PASSWORD']) {
    throw '请使用已配置密码的演示库应用账号 elderly_app。'
}

$mysql = Get-Command mysql -ErrorAction Stop
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $values['DB_PASSWORD']
    $mysqlArgs = @('--protocol=TCP', "--host=$dbHost", "--port=$dbPort", '--user=elderly_app', '--database=elderly_care_demo', '--batch', '--skip-column-names')
    $result = & $mysql.Source @mysqlArgs '--execute' "SELECT DATABASE(), @@character_set_database, COUNT(*) FROM information_schema.tables WHERE table_schema='elderly_care_demo' AND table_name='flyway_schema_history';" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $result) { throw '演示库连接检查失败。' }
    $fields = ($result | Select-Object -Last 1) -split "`t"
    if ($fields.Count -ne 3 -or $fields[0] -ne 'elderly_care_demo' -or $fields[1] -ne 'utf8mb4') {
        throw '演示库名称或字符集不符合 D1 要求。'
    }
    $hasHistory = [int]$fields[2] -eq 1
    if ($RequireMigration -and -not $hasHistory) { throw 'Flyway 历史表不存在。' }
    if ($hasHistory) {
        $migration = & $mysql.Source @mysqlArgs '--execute' 'SELECT COUNT(*), MIN(success) FROM flyway_schema_history;' 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $migration) { throw 'Flyway 记录检查失败。' }
        $migrationFields = ($migration | Select-Object -Last 1) -split "`t"
        if ($migrationFields.Count -ne 2 -or [int]$migrationFields[0] -lt 1 -or $migrationFields[1] -ne '1') {
            throw 'Flyway 迁移记录不符合 D1 要求。'
        }
    }
    Write-Output "演示库检查通过：库=$($fields[0]) 字符集=$($fields[1]) Flyway历史表=$hasHistory。"
} finally {
    $env:MYSQL_PWD = $previousPassword
}
