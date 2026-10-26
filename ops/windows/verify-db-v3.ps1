$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envLocal = Join-Path $repoRoot '.env.local'

if (-not (Test-Path -LiteralPath $envLocal)) { throw '缺少 .env.local' }
$values = @{}
foreach ($line in Get-Content -LiteralPath $envLocal) {
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
if ($values['DB_NAME'] -ne 'elderly_care_demo') { throw '安全保护：仅允许检查隔离演示库 elderly_care_demo。' }
if ($values['DB_USERNAME'] -ne 'elderly_app') { throw '安全保护：仅允许使用 elderly_app 演示账号。' }
if ([string]::IsNullOrWhiteSpace($values['DB_PASSWORD'])) { throw '请在 .env.local 中配置 DB_PASSWORD。' }

$dbPort = if ($values['DB_PORT']) { $values['DB_PORT'] } else { '3306' }
if ($dbPort -notmatch '^\d{1,5}$' -or [int]$dbPort -lt 1 -or [int]$dbPort -gt 65535) { throw 'DB_PORT 必须为有效 TCP 端口。' }
$mysql = Get-Command mysql -ErrorAction Stop
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $values['DB_PASSWORD']
    $mysqlArgs = @(
        '--protocol=TCP', "--host=$dbHost", "--port=$dbPort", '--user=elderly_app',
        '--database=elderly_care_demo', '--default-character-set=utf8mb4'
    )

    $requiredTables = @(
        'idempotency_record', 'audit_log', 'vital_record', 'alert_rule_version', 'assessment_task',
        'health_alert', 'alert_trigger_evidence', 'sos_event', 'simulation_control', 'medication_plan',
        'medication_plan_version', 'medication_plan_reminder_time', 'medication_task', 'medication_checkin',
        'work_order', 'work_order_assignment_history', 'work_order_action_history', 'work_order_handoff',
        'service_catalog', 'service_reservation', 'reservation_execution_link', 'reservation_cancel_request',
        'service_stop_request', 'notification_event', 'notification_delivery', 'file_asset',
        'file_asset_version', 'business_file_link'
    )
    $requiredTableSql = ($requiredTables | ForEach-Object { "'$_'" }) -join ', '
    $verificationSql = @"
SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = 1;
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name IN ($requiredTableSql);
SELECT
    (SELECT COUNT(*) FROM sys_user),
    (SELECT COUNT(*) FROM elder_profile),
    (SELECT COUNT(*) FROM elder_family_link),
    (SELECT COUNT(*) FROM elder_correction_request),
    (SELECT COUNT(*) FROM service_catalog),
    (SELECT COUNT(*) FROM alert_rule_version),
    (SELECT COUNT(*) FROM vital_record),
    (SELECT COUNT(*) FROM health_alert),
    (SELECT COUNT(*) FROM sos_event),
    (SELECT COUNT(*) FROM medication_plan),
    (SELECT COUNT(*) FROM medication_task),
    (SELECT COUNT(*) FROM medication_checkin),
    (SELECT COUNT(*) FROM service_reservation),
    (SELECT COUNT(*) FROM work_order),
    (SELECT COUNT(*) FROM notification_delivery),
    (SELECT COUNT(*) FROM audit_log);
"@
    $result = @(& $mysql.Source @mysqlArgs '--batch' '--skip-column-names' '--execute' $verificationSql)
    $mysqlExitCode = $LASTEXITCODE
    if ($mysqlExitCode -ne 0) { throw "数据库核验查询失败，MySQL 退出码: $mysqlExitCode。" }
    if ($result.Count -ne 3) { throw '数据库核验结果结构不完整。' }
    if ($result[0] -ne '1') { throw '未找到成功应用的 Flyway V3 迁移记录。' }
    if ([int]$result[1] -ne $requiredTables.Count) { throw 'V3 所需数据表集合不完整。' }

    $counts = $result[2] -split "`t"
    $expectedCounts = @(6, 3, 3, 1, 4, 3, 5, 1, 1, 2, 2, 1, 1, 2, 2, 1)
    if ($counts.Count -ne $expectedCounts.Count -or @($counts | Where-Object { $_ -notmatch '^\d+$' }).Count -ne 0) {
        throw '数据库种子统计字段缺失或包含无效值。'
    }
    for ($index = 0; $index -lt $expectedCounts.Count; $index++) {
        if ([int]$counts[$index] -ne $expectedCounts[$index]) {
            throw "数据库种子统计不符合预期：第 $($index + 1) 项数量不匹配。"
        }
    }

    Write-Output 'Flyway V3：SUCCESS'
    Write-Output "V3 必需表：$($requiredTables.Count)/$($requiredTables.Count)"
    Write-Output '合成种子统计：通过（用户、档案、关系、健康、告警、SOS、服药、预约、工单、通知与审计）'
}
finally {
    $env:MYSQL_PWD = $previousPassword
}
