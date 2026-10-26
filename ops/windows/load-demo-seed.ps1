param(
    [string]$EnvFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path '.env.local')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$seed1 = Join-Path $repoRoot 'database\seed\demo_synthetic_seed_m01_m02.sql'
$seed2 = Join-Path $repoRoot 'database\seed\demo_synthetic_seed_m03_m12.sql'

if (-not (Test-Path -LiteralPath $EnvFile)) { throw '缺少本地环境文件。请先配置 .env.local。' }
if (-not (Test-Path -LiteralPath $seed1)) { throw "缺少种子数据文件: $seed1" }
if (-not (Test-Path -LiteralPath $seed2)) { throw "缺少种子数据文件: $seed2" }

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
if ($values['DB_NAME'] -ne 'elderly_care_demo') { throw '安全保护：仅允许向隔离演示库 elderly_care_demo 加载。' }
if ($values['DB_USERNAME'] -ne 'elderly_app') { throw '安全保护：仅允许使用 elderly_app 演示账号。' }
if ([string]::IsNullOrWhiteSpace($values['DB_PASSWORD'])) { throw '请配置 DB_PASSWORD。' }

$dbPort = if ($values['DB_PORT']) { $values['DB_PORT'] } else { '3306' }
if ($dbPort -notmatch '^\d{1,5}$' -or [int]$dbPort -lt 1 -or [int]$dbPort -gt 65535) { throw 'DB_PORT 必须为有效 TCP 端口。' }
$mysql = Get-Command mysql -ErrorAction Stop
$mysqlArgs = @(
    '--protocol=TCP',
    "--host=$dbHost",
    "--port=$dbPort",
    '--user=elderly_app',
    '--database=elderly_care_demo',
    '--default-character-set=utf8mb4'
)
$previousPassword = $env:MYSQL_PWD

# All tables touched by the seed SQL must be empty. This keeps this command
# limited to a first load; it never doubles as a reset command.
$seedTables = @(
    'elder_correction_item', 'elder_correction_request', 'elder_family_link_event', 'elder_family_link',
    'family_link_application', 'elder_archive_event', 'elder_identity_link', 'elder_activation_ticket',
    'elder_profile', 'auth_session', 'sys_user_role', 'phone_change_request', 'phone_verification_challenge',
    'sys_user', 'sys_community', 'business_file_link', 'file_asset_version', 'file_asset',
    'notification_delivery', 'notification_event', 'service_stop_request', 'reservation_cancel_request',
    'reservation_execution_link', 'service_reservation', 'service_catalog', 'work_order_handoff',
    'work_order_action_history', 'work_order_assignment_history', 'work_order', 'medication_checkin',
    'medication_task', 'medication_plan_reminder_time', 'medication_plan_version', 'medication_plan',
    'simulation_control', 'sos_event', 'alert_trigger_evidence', 'health_alert', 'assessment_task',
    'alert_rule_version', 'vital_record', 'audit_log', 'idempotency_record'
)
$emptyTableCounts = ($seedTables | ForEach-Object { "SELECT COUNT(*) AS row_count FROM ``$_``" }) -join "`nUNION ALL`n"
$seedTableNames = ($seedTables | ForEach-Object { "'$_'" }) -join ', '
$statsTables = @('sys_user', 'elder_profile', 'elder_family_link', 'elder_correction_request', 'service_catalog', 'alert_rule_version', 'vital_record', 'health_alert', 'sos_event', 'medication_plan', 'medication_task', 'medication_checkin', 'service_reservation', 'work_order', 'notification_delivery', 'audit_log')
$expectedCounts = @(6, 3, 3, 1, 4, 3, 5, 1, 1, 2, 2, 1, 1, 2, 2, 1)
$statsQuery = 'SELECT ' + (($statsTables | ForEach-Object { "(SELECT COUNT(*) FROM ``$_``)" }) -join ",`n    ") + ';'
$expectedSeedCounts = @{}
foreach ($table in $seedTables) { $expectedSeedCounts[$table] = 0 }
for ($index = 0; $index -lt $statsTables.Count; $index++) {
    $expectedSeedCounts[$statsTables[$index]] = $expectedCounts[$index]
}
$expectedSeedCounts['elder_correction_item'] = 2
$expectedSeedCounts['elder_family_link_event'] = 3
$expectedSeedCounts['family_link_application'] = 3
$expectedSeedCounts['elder_archive_event'] = 3
$expectedSeedCounts['elder_identity_link'] = 1
$expectedSeedCounts['elder_activation_ticket'] = 2
$expectedSeedCounts['sys_user_role'] = 6
$expectedSeedCounts['notification_event'] = 2
$expectedSeedCounts['reservation_execution_link'] = 1
$expectedSeedCounts['work_order_action_history'] = 3
$expectedSeedCounts['work_order_assignment_history'] = 1
$expectedSeedCounts['medication_plan_reminder_time'] = 2
$expectedSeedCounts['medication_plan_version'] = 2
$expectedSeedCounts['simulation_control'] = 1
$expectedSeedCounts['alert_trigger_evidence'] = 1
$expectedSeedCounts['assessment_task'] = 1
$expectedSeedCounts['sys_community'] = 2
$statsGateConditions = @(
    foreach ($table in $seedTables) {
        "(SELECT COUNT(*) FROM ``$table``) = $($expectedSeedCounts[$table])"
    }
)
$statsGateConditionSql = $statsGateConditions -join "`n    AND "

try {
    $env:MYSQL_PWD = $values['DB_PASSWORD']

    $preflightSql = @'
CREATE TEMPORARY TABLE `_demo_seed_preflight` (`gate_id` TINYINT NOT NULL PRIMARY KEY);
INSERT INTO `_demo_seed_preflight` (`gate_id`) VALUES (0);
INSERT INTO `_demo_seed_preflight` (`gate_id`)
SELECT IF(
    GET_LOCK('elderly_care_demo_first_load', 0) = 1
    AND (SELECT SUM(row_count) FROM (__EMPTY_TABLE_COUNTS__) AS seed_rows) = 0
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (__SEED_TABLE_NAMES__) AND engine = 'InnoDB') = __SEED_TABLE_COUNT__,
    1,
    0
);
SET @demo_seed_authorized = 'first-load-synthetic-demo-v1';
START TRANSACTION;
'@ -replace '__EMPTY_TABLE_COUNTS__', $emptyTableCounts -replace '__SEED_TABLE_NAMES__', $seedTableNames -replace '__SEED_TABLE_COUNT__', [string]$seedTables.Count
    $statsGateSql = @'
CREATE TEMPORARY TABLE `_demo_seed_stats_gate` (`gate_id` TINYINT NOT NULL PRIMARY KEY);
INSERT INTO `_demo_seed_stats_gate` (`gate_id`) VALUES (0);
INSERT INTO `_demo_seed_stats_gate` (`gate_id`)
SELECT IF(__STATS_GATE_CONDITION__, 1, 0);
DROP TEMPORARY TABLE `_demo_seed_stats_gate`;
'@ -replace '__STATS_GATE_CONDITION__', $statsGateConditionSql
    $seedInput = $preflightSql + "`r`n" + (Get-Content -LiteralPath $seed1 -Raw -Encoding UTF8) + "`r`n" + (Get-Content -LiteralPath $seed2 -Raw -Encoding UTF8) + "`r`n" + $statsGateSql + "`r`nCOMMIT;`r`n"

    Write-Output '正在隔离演示库执行首次合成数据加载（要求所有种子业务表为空）...'
    $seedInput | & $mysql.Source @mysqlArgs
    $importExitCode = $LASTEXITCODE
    if ($importExitCode -ne 0) { throw "合成数据加载失败，MySQL 退出码: $importExitCode。客户端未确认导入完成，提交状态待核对；请先核对数据库状态，再决定是否重试。" }

    $stats = @(& $mysql.Source @mysqlArgs '--batch' '--skip-column-names' '--execute' $statsQuery)
    $statsExitCode = $LASTEXITCODE
    if ($statsExitCode -ne 0) { throw "事务已提交；提交后统计核验失败（只读查询退出码: $statsExitCode），请核验数据状态，勿直接重试。" }
    if ($stats.Count -ne 1) { throw '事务已提交；提交后统计核验失败（返回格式不正确），请核验数据状态，勿直接重试。' }
    $fields = $stats[0] -split "`t"
    if ($fields.Count -ne 16 -or @($fields | Where-Object { $_ -notmatch '^\d+$' }).Count -ne 0) {
        throw '事务已提交；提交后统计核验失败（字段缺失或包含无效值），请核验数据状态，勿直接重试。'
    }
    for ($index = 0; $index -lt $expectedCounts.Count; $index++) {
        if ([int]$fields[$index] -ne $expectedCounts[$index]) {
            throw "事务已提交；提交后统计核验失败（第 $($index + 1) 项数量不匹配），请核验数据状态，勿直接重试。"
        }
    }

    Write-Output "M01/M02 accounts=$($fields[0]), elders=$($fields[1]), family_links=$($fields[2]), corrections=$($fields[3])"
    Write-Output "M03/M05 catalog=$($fields[4]), rules=$($fields[5]), vitals=$($fields[6]), alerts=$($fields[7])"
    Write-Output "M04/M06/M07 plans=$($fields[9]), tasks=$($fields[10]), checkins=$($fields[11]), reservations=$($fields[12]), work_orders=$($fields[13])"
    Write-Output "M11/M12 notifications=$($fields[14]), audit_logs=$($fields[15])"
    Write-Output '状态：首次合成数据加载完成，MySQL 查询和统计读取均成功。'
}
finally {
    $env:MYSQL_PWD = $previousPassword
}
