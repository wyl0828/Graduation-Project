[CmdletBinding()]
param(
    [ValidateRange(0, 300)]
    [int]$HoldSeconds = 0
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$tempRootLeaf = 'system-http-smoke-' + [Guid]::NewGuid().ToString('N')
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) $tempRootLeaf
$dataDirectory = Join-Path $tempRoot 'mysql-data'
$reportPath = Join-Path $repoRoot '.local-data\repair-system-http-smoke-report.md'
$script:mysqlExe = $null
$script:mysqlServerExe = $null
$script:baseDirectory = $null
$script:apiProcess = $null
$script:apiLauncherPath = $null
$script:apiLogPhaseName = $null
$script:mysqlProcess = $null
$script:dbPassword = $null
$script:dbPort = $null
$script:httpPort = $null
$script:apiBase = $null
$script:httpClient = $null
$script:cookieContainer = $null
$script:storageRoot = $null
$script:extraHttpClients = [Collections.Generic.List[System.Net.Http.HttpClient]]::new()
$script:syntheticWorkOrderIds = [Collections.Generic.List[long]]::new()
$script:syntheticStaffUserIds = [Collections.Generic.List[long]]::new()
$script:syntheticFamilyUserId = 0L
$script:syntheticFamilyElderId = 0L
$script:syntheticFamilyTicketId = $null
$script:syntheticFamilyPhone = $null
$script:syntheticFamilyArchiveNo = $null
$script:syntheticStaffPhone = $null
$script:syntheticCrossCommunityStaffPhone = $null
$script:syntheticCrossCommunityElderId = 0L
$script:syntheticFixturesStarted = $false
$script:fixtureCleanupMessage = '未执行'
$script:envKeys = @(
    'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD',
    'SERVER_ADDRESS', 'SERVER_PORT', 'SPRING_PROFILES_ACTIVE',
    'AUTH_RATE_LIMIT_HMAC_KEY', 'AUTH_COOKIE_SECURE', 'PHONE_VERIFICATION_ENABLED', 'APP_STORAGE_ROOT',
    'AUTH_TEST_DB_URL', 'AUTH_TEST_DB_USERNAME', 'AUTH_TEST_DB_PASSWORD', 'AUTH_TEST_HMAC_KEY',
    'AUTH_TEST_DB_PORT', 'AUTH_TEST_DB_MARKER', 'AUTH_TEST_SEED_DOC_PATH'
)
$script:previousValues = @{}
$script:checks = [ordered]@{
    '临时 MySQL 使用随机 loopback 端口且目标库名校验一致' = '未执行'
    'Spring Boot 首次启动由 Flyway 初始化隔离库并通过健康检查' = '未执行'
    'API 重启后同键预约重放原 HTTP 状态码与 JSON 正文' = '未执行'
    'Flyway 后使用随机 marker 校验目标库身份并删除 marker' = '未执行'
    '首轮 API 已退出，且确认其端口已释放后才加载 seed' = '未执行'
    '现有 first-load seed wrapper 在临时库返回退出码 0' = '未执行'
    'Seed 后 API 在随机 loopback HTTP 端口健康可用（Maven 前）' = '未执行'
    '匿名访问当前用户接口返回 HTTP 401' = '未执行'
    '有效子女通过 HTTP 读取已绑定老人档案' = '未执行'
    '待审核子女读取老人档案返回 HTTP 404' = '未执行'
    '待审核子女创建预约返回 HTTP 404 且业务事实未变化' = '未执行'
    '冻结亲情访问返回 HTTP 403 KINSHIP_NOT_ACTIVE' = '未执行'
    '有效子女读取同社区未绑定老人返回 HTTP 404' = '未执行'
    '过期绝对期限会话访问返回 HTTP 401' = '未执行'
    '空闲超过会话上限的旧 Bearer 返回 HTTP 401 且会话事实不变' = '未执行'
    '撤销当前角色分配后旧会话返回 HTTP 401' = '未执行'
    '重新授予角色后旧 Web Cookie 与 MINIAPP Bearer 仍返回 HTTP 401' = '未执行'
    'UNLINKED 亲情访问返回 HTTP 404 且业务/会话/通知/审计不变' = '未执行'
    '改密后旧 Web Cookie 与 Bearer 返回 HTTP 401 且撤销原因/时间可查询' = '未执行'
    'DISABLED 账号的旧 Web Cookie 与 Bearer 返回 HTTP 401 且无请求副作用' = '未执行'
    '社区管理员和网格员读取另一社区老人均返回 HTTP 404' = '未执行'
    '跨社区管理员写入返回 HTTP 404 且数据库业务事实未变化' = '未执行'
    '同社区管理员和网格员仍可读取本社区老人档案' = '未执行'
    '契约 §5.1 的六个合成账号全部登录成功且凭据未输出' = '未执行'
    'Web 登录设置 HttpOnly 会话 Cookie、提供 CSRF Token 且响应不返回 Bearer Token' = '未执行'
    'Web Cookie 写请求缺少 CSRF Token 时返回 HTTP 403' = '未执行'
    '老人本人资料接口读回 elderId=1' = '未执行'
    '老人今日药盒接口返回成功列表' = '未执行'
    '老人将今日一条未确认药盒任务打卡为 CONFIRMED' = '未执行'
    '有效子女账号读回 elderId=1 的绑定关系' = '未执行'
    '子女创建唯一未来预约并读回待确认状态' = '未执行'
    '管理员待确认列表包含本次创建的预约' = '未执行'
    '管理员通过 Web Cookie 与 CSRF 确认预约并生成工单' = '未执行'
    '读回预约为 CONFIRMED，关联工单为 PENDING_DISPATCH' = '未执行'
    '管理员派单后工单读回 DISPATCHED' = '未执行'
    '网格员接单后状态为 ACCEPTED' = '未执行'
    '网格员开始服务后状态为 PROCESSING' = '未执行'
    '网格员上传现场照片并得到临时文件 URL' = '未执行'
    '网格员提交服务总结与照片后进入 PENDING_RESULT_REVIEW' = '未执行'
    '管理员审核履约结果后工单关闭' = '未执行'
    '子女读回预约 COMPLETED 与关联工单 CLOSED' = '未执行'
    '老人触发 SOS 并原子生成求助工单' = '未执行'
    '管理员派发并处置 SOS 后求助与工单均 CLOSED' = '未执行'
    '老人注销成功并撤销服务端会话' = '未执行'
    '老人注销后的旧 Bearer Token 请求返回 HTTP 401' = '未执行'
    '子女注销成功并撤销服务端会话' = '未执行'
    '子女注销后的旧 Bearer Token 请求返回 HTTP 401' = '未执行'
    'HTTP 冒烟结束后 API 已停止且端口释放，才运行完整 Maven 测试' = '未执行'
    'Maven 全套结束后 API 在隔离端口重新健康可用' = '未执行'
    '临时数据库上的完整 Maven 测试套件通过' = '未执行'
    'Task 4 新增员工/家庭/工单夹具在清理前移除' = '未执行'
    '停止临时 API/MySQL，并核验 datadir 位于专属 tempRoot 后清理' = '未执行'
}
$script:failureMessage = $null
$script:cleanupMessage = '尚未执行'
$script:servicesStopped = $true
$script:httpSmokeResult = '未执行'
$script:junitResult = '尚未运行'
$script:junitExitCode = $null
$script:junitSummary = '未运行'
$script:junitFailureNames = @()
$script:junitFailureMessage = $null
$script:apiFailureDiagnostics = @()
$script:runStartedAt = Get-Date
$script:scriptExitCode = 1

function Confirm-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Confirm-Chain([string]$Name, [bool]$Condition, [string]$FailureMessage) {
    if ($Condition) {
        $script:checks[$Name] = '通过'
        Write-Output "通过：$Name"
        return
    }
    $script:checks[$Name] = '失败'
    throw $FailureMessage
}

function ConvertTo-CommandLineArgument([string]$Argument) {
    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.Append('"')
    $backslashCount = 0
    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq [char]'\') {
            $backslashCount++
            continue
        }
        if ($character -eq [char]'"') {
            if ($backslashCount -gt 0) { [void]$builder.Append(('\' * (2 * $backslashCount + 1))) }
            else { [void]$builder.Append('\') }
            [void]$builder.Append('"')
            $backslashCount = 0
            continue
        }
        if ($backslashCount -gt 0) {
            [void]$builder.Append(('\' * $backslashCount))
            $backslashCount = 0
        }
        [void]$builder.Append($character)
    }
    if ($backslashCount -gt 0) { [void]$builder.Append(('\' * (2 * $backslashCount))) }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function ConvertTo-PowerShellLiteral([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-RandomLoopbackPort([int[]]$Exclude = @()) {
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        try {
            $listener.Start()
            $port = [int]$listener.LocalEndpoint.Port
        } finally {
            $listener.Stop()
        }
        if ($port -notin $Exclude) { return $port }
    }
    throw 'Could not select a free random loopback port.'
}

function Test-TcpPortOpen([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync([Net.IPAddress]::Loopback, $Port)
        if (-not $task.Wait(500)) { return $false }
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Test-ApiBoundToLoopback([int]$Port) {
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
        return (@($listeners | Where-Object { $_.LocalAddress -eq '127.0.0.1' }).Count -gt 0)
    }
    $netstat = Get-Command netstat.exe -ErrorAction SilentlyContinue
    if (-not $netstat) { return $false }
    $rows = @(& $netstat.Source -ano -p tcp 2>$null | Where-Object { $_ -match ':\d+\s+.*LISTENING' })
    foreach ($row in $rows) {
        if ($row -match '127\.0\.0\.1:(\d+)\s+.*LISTENING' -and [int]$Matches[1] -eq $Port) { return $true }
    }
    return $false
}

function Get-SanitizedApiLogTail([string]$PhaseName) {
    $diagnostics = [Collections.Generic.List[string]]::new()
    foreach ($suffix in @('out', 'err')) {
        $logPath = Join-Path $tempRoot "api-$PhaseName.$suffix"
        if (-not (Test-Path -LiteralPath $logPath)) { continue }
        foreach ($line in (Get-Content -LiteralPath $logPath -Tail 100 -ErrorAction SilentlyContinue)) {
            $safeLine = [string]$line
            foreach ($secret in @($script:dbPassword, $script:hmacKey)) {
                if ($secret) { $safeLine = $safeLine.Replace([string]$secret, '[REDACTED]') }
            }
            $safeLine = [regex]::Replace($safeLine, '(?i)(password|secret|token|authorization|cookie)(\s*[=:]\s*)([^\s,;]+)', '$1$2[REDACTED]')
            $diagnostics.Add("[$suffix] $safeLine")
        }
    }
    return @($diagnostics.ToArray() | Select-Object -Last 60)
}

function Invoke-Mysql([string[]]$Arguments, [string]$InputText = '') {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:mysqlExe
    $quotedArguments = $Arguments | ForEach-Object { ConvertTo-CommandLineArgument ([string]$_) }
    $startInfo.Arguments = $quotedArguments -join ' '
    $startInfo.WorkingDirectory = $tempRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $InputText.Length -gt 0
    [void]$startInfo.EnvironmentVariables.Remove('MYSQL_PWD')
    if ($script:dbPassword -and $Arguments -contains '-uelderly_app') {
        $startInfo.EnvironmentVariables['MYSQL_PWD'] = $script:dbPassword
    }

    $process = [Diagnostics.Process]::Start($startInfo)
    if ($InputText.Length -gt 0) {
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [pscustomobject]@{ ExitCode = $process.ExitCode; StdOut = $stdout; StdErr = $stderr }
}

function Invoke-AppDatabaseQuery([string]$Sql) {
    return Invoke-Mysql @(
        '-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app',
        '--database=elderly_care_demo', '-N', '-B', '-e', $Sql
    )
}

function Get-IsolatedBusinessFacts {
    $sql = @'
SELECT CONCAT_WS('|',
    COALESCE((SELECT GROUP_CONCAT(CONCAT(elder_id, ':', status, ':', row_version) ORDER BY elder_id SEPARATOR ',') FROM elder_profile), ''),
    COALESCE((SELECT GROUP_CONCAT(CONCAT(link_id, ':', status, ':', row_version) ORDER BY link_id SEPARATOR ',') FROM elder_family_link), ''),
    (SELECT COUNT(*) FROM family_link_application),
    (SELECT COUNT(*) FROM service_reservation),
    COALESCE((SELECT GROUP_CONCAT(CONCAT(reservation_id, ':', status) ORDER BY reservation_id SEPARATOR ',') FROM service_reservation), ''),
    (SELECT COUNT(*) FROM work_order),
    COALESCE((SELECT GROUP_CONCAT(CONCAT(work_order_id, ':', status, ':', COALESCE(current_assignee_id, '')) ORDER BY work_order_id SEPARATOR ',') FROM work_order), ''),
    (SELECT COUNT(*) FROM notification_event),
    (SELECT COUNT(*) FROM notification_delivery)
);
'@
    $result = Invoke-AppDatabaseQuery $sql
    if ($result.ExitCode -ne 0) { throw 'Could not read the isolated business-fact snapshot.' }
    return [string]$result.StdOut.Trim()
}

function Get-StaffMutationSnapshot {
    $sql = @'
SELECT CONCAT_WS('|',
    (SELECT CONCAT(COUNT(*), ':', COALESCE(GROUP_CONCAT(CONCAT(user_id, ':', account_kind, ':', status) ORDER BY user_id SEPARATOR ','), '')) FROM sys_user),
    (SELECT CONCAT(COUNT(*), ':', COALESCE(GROUP_CONCAT(CONCAT(assignment_id, ':', user_id, ':', role_code, ':', COALESCE(community_id, 'NULL'), ':', status, ':', COALESCE(revoke_reason, '')) ORDER BY assignment_id SEPARATOR ','), '')) FROM sys_user_role),
    (SELECT CONCAT(COUNT(*), ':', COALESCE(GROUP_CONCAT(CONCAT(user_id, ':', client_type, ':', IF(revoked_at IS NULL, 'ACTIVE', CONCAT('REVOKED:', COALESCE(revoke_reason, '')))) ORDER BY user_id, created_at, client_type SEPARATOR ','), '')) FROM auth_session),
    (SELECT COUNT(*) FROM audit_log)
);
'@
    $result = Invoke-AppDatabaseQuery $sql
    if ($result.ExitCode -ne 0) { throw 'Could not read the isolated staff/session/audit snapshot.' }
    return [string]$result.StdOut.Trim()
}

function Get-IsolatedTestSideEffectSnapshot {
    return (Get-IsolatedBusinessFacts) + '||' + (Get-StaffMutationSnapshot)
}

function Get-SessionFactsForUser([long]$UserId) {
    $result = Invoke-AppDatabaseQuery @"
SELECT CONCAT_WS('|',
    COUNT(*),
    COALESCE(SUM(revoked_at IS NULL), 0),
    COALESCE(SUM(revoked_at IS NOT NULL AND revoke_reason = 'ROLE_CHANGED'), 0),
    COALESCE(GROUP_CONCAT(CONCAT(client_type, ':', IF(revoked_at IS NULL, 'ACTIVE', CONCAT('REVOKED:', COALESCE(revoke_reason, '')))) ORDER BY created_at, client_type SEPARATOR ','), '')
)
FROM auth_session WHERE user_id = $UserId;
"@
    if ($result.ExitCode -ne 0) { throw 'Could not read the isolated target-session snapshot.' }
    return [string]$result.StdOut.Trim()
}

function Get-SessionDetailsForUser([long]$UserId) {
    $result = Invoke-AppDatabaseQuery @"
SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('~', session_id, client_type,
    COALESCE(DATE_FORMAT(last_activity_at, '%Y-%m-%d %H:%i:%s.%f'), 'NULL'),
    COALESCE(DATE_FORMAT(revoked_at, '%Y-%m-%d %H:%i:%s.%f'), 'NULL'),
    COALESCE(revoke_reason, 'NULL')) ORDER BY session_id SEPARATOR ','), '')
FROM auth_session WHERE user_id = $UserId;
"@
    if ($result.ExitCode -ne 0) { throw 'Could not read detailed isolated session facts.' }
    return [string]$result.StdOut.Trim()
}

function Get-SessionStateForToken([long]$UserId, [string]$Token) {
    $escapedToken = $Token.Replace("'", "''")
    $result = Invoke-AppDatabaseQuery @"
SELECT CONCAT_WS('|', session_id, client_type,
    COALESCE(DATE_FORMAT(last_activity_at, '%Y-%m-%d %H:%i:%s.%f'), 'NULL'),
    COALESCE(DATE_FORMAT(revoked_at, '%Y-%m-%d %H:%i:%s.%f'), 'NULL'),
    COALESCE(revoke_reason, 'NULL'))
FROM auth_session
WHERE user_id = $UserId AND token_hash = UNHEX(SHA2('$escapedToken', 256))
LIMIT 1;
"@
    if ($result.ExitCode -ne 0 -or -not $result.StdOut.Trim()) { throw 'Could not read isolated session state for a token.' }
    return [string]$result.StdOut.Trim()
}

function Get-NotificationAndAuditFacts {
    $result = Invoke-AppDatabaseQuery "SELECT CONCAT_WS('|', (SELECT COUNT(*) FROM notification_event), (SELECT COUNT(*) FROM notification_delivery), (SELECT COUNT(*) FROM audit_log));"
    if ($result.ExitCode -ne 0) { throw 'Could not read isolated notification and audit facts.' }
    return [string]$result.StdOut.Trim()
}

function Get-AuthorizationSideEffectSnapshot([long]$UserId) {
    return (Get-IsolatedTestSideEffectSnapshot) + '||' +
        (Get-SessionDetailsForUser -UserId $UserId) + '||' +
        (Get-NotificationAndAuditFacts)
}

function Get-StaffAuditFacts([long]$UserId, [string]$Action) {
    $result = Invoke-AppDatabaseQuery @"
SELECT CONCAT_WS('|', actor_user_id, actor_role, COALESCE(community_id, 'NULL'), target_type, target_id, action, result,
    COALESCE(HEX(CAST(details_json AS CHAR CHARACTER SET utf8mb4)), ''))
FROM audit_log
WHERE target_type = 'STAFF' AND target_id = '$UserId' AND action = '$Action'
ORDER BY audit_id DESC LIMIT 1;
"@
    if ($result.ExitCode -ne 0) { throw 'Could not read the isolated staff audit fact.' }
    $parts = [string]$result.StdOut.Trim() -split '\|', 8
    if ($parts.Count -eq 8 -and $parts[7]) {
        try {
            $detailsBytes = [Convert]::FromHexString($parts[7])
            $parts[7] = [Text.Encoding]::UTF8.GetString($detailsBytes)
        } catch {
            throw 'Could not decode the isolated UTF-8 staff audit details.'
        }
    }
    return $parts -join '|'
}

function Get-ActiveStaffRoles([long]$UserId) {
    $result = Invoke-AppDatabaseQuery @"
SELECT COALESCE(GROUP_CONCAT(role_code ORDER BY role_code SEPARATOR ','), '')
FROM sys_user_role WHERE user_id = $UserId AND community_id = 1 AND status = 'ACTIVE';
"@
    if ($result.ExitCode -ne 0) { throw 'Could not read the isolated active staff roles.' }
    return [string]$result.StdOut.Trim()
}

function Get-FamilyIdentityFacts([long]$UserId, [long]$ElderId, [string]$TicketId) {
    $result = Invoke-AppDatabaseQuery @"
SELECT CONCAT_WS('|',
    (SELECT COALESCE(GROUP_CONCAT(role_code ORDER BY role_code SEPARATOR ','), '')
       FROM sys_user_role WHERE user_id = $UserId AND status = 'ACTIVE'),
    (SELECT COUNT(*) FROM elder_family_link WHERE child_user_id = $UserId AND elder_id = $ElderId AND status = 'ACTIVE'),
    (SELECT COUNT(*) FROM elder_identity_link WHERE user_id = $UserId AND elder_id = $ElderId
       AND linked_by_activation_ticket_id = '$TicketId'),
    (SELECT COUNT(*) FROM elder_activation_ticket WHERE ticket_id = '$TicketId' AND status = 'USED')
);
"@
    if ($result.ExitCode -ne 0) { throw 'Could not read the isolated FAMILY role/link/identity snapshot.' }
    return [string]$result.StdOut.Trim()
}

function New-SyntheticWorkOrder([string]$Status, [long]$AssigneeId, [long]$ElderId, [long]$CommunityId) {
    $orderNo = 'SYSCARE' + [Guid]::NewGuid().ToString('N').Substring(0, 20)
    $insert = Invoke-AppDatabaseQuery @"
INSERT INTO work_order (
    order_no, source_type, source_reference_id, elder_id, community_id, service_type,
    order_title, order_description, status, current_assignee_id, dispatch_time, result_photo_required
)
VALUES (
    '$orderNo', 'MANUAL', '$orderNo', $ElderId, $CommunityId, 'DAILY_CARE',
    '隔离护工工单', '仅用于隔离 HTTP 授权验收', '$Status', $AssigneeId, NOW(3), 1
);
"@
    if ($insert.ExitCode -ne 0) { throw 'Could not create an isolated care-worker work-order fixture.' }
    $query = Invoke-AppDatabaseQuery "SELECT work_order_id FROM work_order WHERE order_no = '$orderNo';"
    $workOrderId = 0L
    $parsed = [long]::TryParse($query.StdOut.Trim(), [ref]$workOrderId)
    if ($query.ExitCode -ne 0 -or -not $parsed -or $workOrderId -le 0) {
        throw 'Could not read back an isolated care-worker work-order fixture.'
    }
    $script:syntheticWorkOrderIds.Add($workOrderId)
    return $workOrderId
}

function Remove-SyntheticDatabaseFixtures {
    $hasFixtures = $script:syntheticFixturesStarted -or $script:syntheticWorkOrderIds.Count -gt 0 -or
        $script:syntheticStaffPhone -or $script:syntheticCrossCommunityStaffPhone -or
        $script:syntheticFamilyPhone -or $script:syntheticCrossCommunityElderId -gt 0
    if (-not $hasFixtures) { return $true }
    if (-not $script:dbPort -or -not $script:mysqlProcess) { return $false }
    try {
        $script:mysqlProcess.Refresh()
        if ($script:mysqlProcess.HasExited) { return $false }

        $orderIds = @($script:syntheticWorkOrderIds.ToArray() | Select-Object -Unique)
        $orderIdList = if ($orderIds.Count -gt 0) { $orderIds -join ',' } else { '0' }
        $staffPhone = if ($script:syntheticStaffPhone) { [string]$script:syntheticStaffPhone } else { '13900000000' }
        $crossStaffPhone = if ($script:syntheticCrossCommunityStaffPhone) { [string]$script:syntheticCrossCommunityStaffPhone } else { '13900000000' }
        $familyPhone = if ($script:syntheticFamilyPhone) { [string]$script:syntheticFamilyPhone } else { '13900000000' }
        $familyArchiveNo = if ($script:syntheticFamilyArchiveNo) { [string]$script:syntheticFamilyArchiveNo } else { 'SYSHTTP-FAM-NONE' }
        $ticketId = if ($script:syntheticFamilyTicketId) { [string]$script:syntheticFamilyTicketId } else { '00000000-0000-0000-0000-000000000000' }
        $communityElderId = if ($script:syntheticCrossCommunityElderId -gt 0) { [string]$script:syntheticCrossCommunityElderId } else { '0' }

        $sql = @"
SET @syshttp_staff_id = COALESCE((SELECT user_id FROM sys_user WHERE phone_normalized = '$staffPhone' AND account_kind = 'STAFF' LIMIT 1), 0);
SET @syshttp_cross_staff_id = COALESCE((SELECT user_id FROM sys_user WHERE phone_normalized = '$crossStaffPhone' AND account_kind = 'STAFF' LIMIT 1), 0);
SET @syshttp_family_user_id = COALESCE((SELECT user_id FROM sys_user WHERE phone_normalized = '$familyPhone' AND account_kind = 'FAMILY' LIMIT 1), 0);
SET @syshttp_family_elder_id = COALESCE((SELECT elder_id FROM elder_profile WHERE archive_no = '$familyArchiveNo' LIMIT 1), 0);
DELETE FROM work_order_action_history WHERE work_order_id IN ($orderIdList);
DELETE FROM work_order_assignment_history WHERE work_order_id IN ($orderIdList);
DELETE FROM work_order_handoff WHERE work_order_id IN ($orderIdList);
DELETE FROM audit_log WHERE target_type = 'WORK_ORDER'
  AND target_id IN (SELECT CAST(work_order_id AS CHAR) FROM work_order WHERE work_order_id IN ($orderIdList));
DELETE FROM work_order WHERE work_order_id IN ($orderIdList);
DELETE FROM elder_family_link WHERE child_user_id = @syshttp_family_user_id OR elder_id = @syshttp_family_elder_id;
DELETE FROM elder_identity_link WHERE user_id = @syshttp_family_user_id OR elder_id = @syshttp_family_elder_id;
DELETE FROM elder_activation_ticket WHERE ticket_id = '$ticketId' OR elder_id = @syshttp_family_elder_id;
DELETE FROM auth_session WHERE user_id IN (@syshttp_staff_id, @syshttp_cross_staff_id, @syshttp_family_user_id);
DELETE FROM sys_user_role WHERE user_id IN (@syshttp_staff_id, @syshttp_cross_staff_id, @syshttp_family_user_id);
DELETE FROM audit_log WHERE target_type = 'STAFF'
  AND target_id IN (CAST(@syshttp_staff_id AS CHAR), CAST(@syshttp_cross_staff_id AS CHAR));
DELETE FROM sys_user WHERE user_id IN (@syshttp_staff_id, @syshttp_cross_staff_id, @syshttp_family_user_id);
DELETE FROM elder_profile WHERE elder_id = @syshttp_family_elder_id;
DELETE FROM elder_profile WHERE elder_id = $communityElderId AND archive_no = 'SYSHTTP-COMM2-ELDER';
SELECT CONCAT_WS('|',
    (SELECT COUNT(*) FROM sys_user WHERE phone_normalized IN ('$staffPhone', '$crossStaffPhone', '$familyPhone')),
    (SELECT COUNT(*) FROM elder_profile WHERE archive_no = '$familyArchiveNo' OR archive_no = 'SYSHTTP-COMM2-ELDER'),
    (SELECT COUNT(*) FROM elder_activation_ticket WHERE ticket_id = '$ticketId'),
    (SELECT COUNT(*) FROM work_order WHERE work_order_id IN ($orderIdList))
);
"@
        $result = Invoke-AppDatabaseQuery $sql
        if ($result.ExitCode -ne 0) { return $false }
        return [string]$result.StdOut.Trim() -eq '0|0|0|0'
    } catch {
        return $false
    }
}

function Get-StorageFileCount {
    if (-not $script:storageRoot -or -not (Test-Path -LiteralPath $script:storageRoot)) { return 0 }
    return @(Get-ChildItem -LiteralPath $script:storageRoot -File -Recurse -Force -ErrorAction Stop).Count
}

function New-TestHttpClient {
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $cookies = [Net.CookieContainer]::new()
    $handler.CookieContainer = $cookies
    $handler.UseCookies = $true
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $script:extraHttpClients.Add($client)
    return [pscustomobject]@{ Client = $client; Cookies = $cookies }
}

function New-SyntheticPhone {
    return '139' + [System.Security.Cryptography.RandomNumberGenerator]::GetInt32(10000000, 99999999)
}

function Start-IsolatedMysql([string[]]$Arguments, [string]$StdoutPath, [string]$StderrPath) {
    $quotedArgs = $Arguments | ForEach-Object { ConvertTo-CommandLineArgument ([string]$_) }
    return Start-Process -FilePath $script:mysqlServerExe -ArgumentList ($quotedArgs -join ' ') `
        -PassThru -WindowStyle Hidden -RedirectStandardOutput $StdoutPath -RedirectStandardError $StderrPath
}

function Get-IsolatedMysqlProcesses {
    if (-not (Test-Path -LiteralPath $dataDirectory)) { return @() }
    $resolvedDataDirectory = [IO.Path]::GetFullPath($dataDirectory)
    return @(
        Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction Stop |
            Where-Object {
                $_.CommandLine -and
                $_.CommandLine.Contains($resolvedDataDirectory) -and
                $_.CommandLine.Contains('--no-defaults')
            }
    )
}

function Start-TestApi([string]$PhaseName) {
    $launcherPath = Join-Path $tempRoot ("start-api-$PhaseName.ps1")
    $wrapperPath = Join-Path $repoRoot 'mvnw.cmd'
    $pomPath = Join-Path $repoRoot 'services\api\pom.xml'
    $launcherLines = @(
        '$ErrorActionPreference = ''Continue'''
        ('Set-Location -LiteralPath ' + (ConvertTo-PowerShellLiteral $tempRoot))
        ('& ' + (ConvertTo-PowerShellLiteral $wrapperPath) + ' -f ' + (ConvertTo-PowerShellLiteral $pomPath) +
            ' ''-Dspring-boot.run.profiles=local'' ' + (ConvertTo-PowerShellLiteral ('-Dspring-boot.run.workingDirectory=' + $tempRoot)) + ' spring-boot:run')
        'exit $LASTEXITCODE'
    )
    Set-Content -LiteralPath $launcherPath -Value ($launcherLines -join [Environment]::NewLine) -Encoding UTF8
    $pwsh = Get-Command pwsh -ErrorAction Stop
    $arguments = @('-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $launcherPath)
    $quotedArguments = $arguments | ForEach-Object { ConvertTo-CommandLineArgument ([string]$_) }
    $script:apiLauncherPath = $launcherPath
    $script:apiLogPhaseName = $PhaseName
    return Start-Process -FilePath $pwsh.Source -ArgumentList ($quotedArguments -join ' ') `
        -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $tempRoot "api-$PhaseName.out") `
        -RedirectStandardError (Join-Path $tempRoot "api-$PhaseName.err")
}

function Wait-ApiReady([Diagnostics.Process]$Process, [string]$PhaseName, [int]$TimeoutSeconds = 240) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) {
            $details = @(Get-SanitizedApiLogTail $script:apiLogPhaseName)
            if ($details.Count -gt 0) { $script:apiFailureDiagnostics = $details }
            throw "Isolated API exited before readiness during $PhaseName (launcher exit code $($Process.ExitCode))."
        }
        if (Test-TcpPortOpen $script:httpPort) {
            try {
                $response = Invoke-Api -Method 'GET' -Path '/actuator/health'
                if ($response.StatusCode -eq 200) {
                    $health = ConvertFrom-Json -InputObject $response.Content
                    if ($health.status -eq 'UP') { return $health }
                }
            } catch { }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Isolated API did not become healthy during $PhaseName."
}

function Stop-TestApi {
    if (-not $script:apiProcess) { return }
    $script:apiProcess.Refresh()
    if ($script:apiProcess.HasExited) { return }

    $rootProcessId = [int]$script:apiProcess.Id
    $rootInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $rootProcessId" -ErrorAction SilentlyContinue
    if (-not $rootInfo -or -not $rootInfo.CommandLine -or
        -not $rootInfo.CommandLine.Contains($script:apiLauncherPath)) {
        throw 'Refusing to stop an API process whose launcher identity cannot be verified.'
    }

    $processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $processIds = [Collections.Generic.List[int]]::new()
    $processIds.Add($rootProcessId)
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($processInfo in $processes) {
            $childId = [int]$processInfo.ProcessId
            $parentId = [int]$processInfo.ParentProcessId
            if (($processIds -contains $parentId) -and -not ($processIds -contains $childId)) {
                $processIds.Add($childId)
                $changed = $true
            }
        }
    }
    for ($index = $processIds.Count - 1; $index -ge 0; $index--) {
        Stop-Process -Id $processIds[$index] -Force -ErrorAction SilentlyContinue
    }
    [void]$script:apiProcess.WaitForExit(15000)
    $script:apiProcess = $null
}

function Invoke-Api(
    [ValidateSet('GET', 'POST', 'PUT', 'PATCH', 'DELETE')][string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [string]$BearerToken = $null,
    [string]$CsrfToken = $null,
    [string]$IdempotencyKey = $null,
    [System.Net.Http.HttpClient]$Client = $null
) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), ($script:apiBase + $Path))
    $clientToUse = if ($Client) { $Client } else { $script:httpClient }
    if ($BearerToken) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $BearerToken)
    }
    if ($CsrfToken) { [void]$request.Headers.TryAddWithoutValidation('X-CSRF-Token', $CsrfToken) }
    if ($IdempotencyKey) { [void]$request.Headers.TryAddWithoutValidation('Idempotency-Key', $IdempotencyKey) }
    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
        $request.Content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $clientToUse.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = $content; Response = $response }
    } catch {
        throw "HTTP transport failed for $Method $Path."
    } finally {
        $request.Dispose()
    }
}

function Invoke-PhotoUpload(
    [string]$Path,
    [string]$BearerToken,
    [long]$WorkOrderId = 0,
    [System.Net.Http.HttpClient]$Client = $null
) {
    $uploadPath = '/api/v1/files/upload'
    if ($WorkOrderId -gt 0) { $uploadPath += '?workOrderId=' + [Uri]::EscapeDataString([string]$WorkOrderId) }
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, ($script:apiBase + $uploadPath))
    $clientToUse = if ($Client) { $Client } else { $script:httpClient }
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = $null
    try {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $BearerToken)
        $bytes = [IO.File]::ReadAllBytes($Path)
        $fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
        $multipart.Add($fileContent, 'file', [IO.Path]::GetFileName($Path))
        $request.Content = $multipart
        $response = $clientToUse.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = $content; Response = $response }
    } catch {
        throw 'HTTP transport failed while uploading isolated work-order evidence.'
    } finally {
        if ($fileContent) { $fileContent.Dispose() }
        $multipart.Dispose()
        $request.Dispose()
    }
}

function Assert-OkApiResponse([object]$Response, [string]$Description, [int]$ExpectedStatus = 200) {
    if ($Response.StatusCode -ne $ExpectedStatus) { throw "$Description returned an unexpected HTTP status." }
    try { $json = ConvertFrom-Json -InputObject $Response.Content }
    catch { throw "$Description returned invalid JSON." }
    if ($json.code -ne 'OK') { throw "$Description returned a non-OK application result." }
    return $json
}

function Add-RedactedJsonDiff([object]$First, [object]$Second, [string]$Path, [Collections.Generic.List[string]]$Diffs) {
    if ($First -is [Collections.IDictionary] -and $Second -is [Collections.IDictionary]) {
        $keys = @(@($First.Keys) + @($Second.Keys) | Sort-Object -Unique)
        foreach ($key in $keys) {
            if (-not $First.Contains($key)) { $Diffs.Add("$Path.$key:missing-first"); continue }
            if (-not $Second.Contains($key)) { $Diffs.Add("$Path.$key:missing-replay"); continue }
            Add-RedactedJsonDiff -First $First[$key] -Second $Second[$key] -Path "$Path.$key" -Diffs $Diffs
        }
        return
    }
    if (($First -is [Array] -or $First -is [Collections.IList]) -and ($Second -is [Array] -or $Second -is [Collections.IList])) {
        if ($First.Count -ne $Second.Count) { $Diffs.Add("$Path:array-count") }
        for ($index = 0; $index -lt [Math]::Min($First.Count, $Second.Count); $index++) {
            Add-RedactedJsonDiff -First $First[$index] -Second $Second[$index] -Path "$Path[$index]" -Diffs $Diffs
        }
        return
    }
    $firstType = if ($null -eq $First) { 'null' } elseif ($First -is [string]) { 'string' } elseif ($First -is [bool]) { 'boolean' } elseif ($First -is [ValueType]) { 'number' } else { 'object' }
    $secondType = if ($null -eq $Second) { 'null' } elseif ($Second -is [string]) { 'string' } elseif ($Second -is [bool]) { 'boolean' } elseif ($Second -is [ValueType]) { 'number' } else { 'object' }
    if ($firstType -ne $secondType -or $First -cne $Second) {
        $Diffs.Add("${Path}:$firstType/$secondType")
    }
}

function Get-RedactedJsonDiff([string]$FirstJson, [string]$SecondJson) {
    try {
        $firstObject = ConvertFrom-Json -InputObject $FirstJson -AsHashtable
        $secondObject = ConvertFrom-Json -InputObject $SecondJson -AsHashtable
        $diffs = [Collections.Generic.List[string]]::new()
        Add-RedactedJsonDiff -First $firstObject -Second $secondObject -Path '$' -Diffs $diffs
        if ($diffs.Count -eq 0) { return 'none' }
        return ($diffs -join ',')
    } catch {
        return 'invalid-json'
    }
}

function Get-MavenTestSummary([object[]]$OutputLines, [DateTime]$StartedAt) {
    $total = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    $matchesFound = 0
    foreach ($line in $OutputLines) {
        $text = [string]$line
        foreach ($match in [regex]::Matches($text, 'Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)')) {
            $total += [int]$match.Groups[1].Value
            $failures += [int]$match.Groups[2].Value
            $errors += [int]$match.Groups[3].Value
            $skipped += [int]$match.Groups[4].Value
            $matchesFound++
        }
    }

    $testReportDirectory = Join-Path $repoRoot 'services\api\target\surefire-reports'
    $currentReports = @()
    if (Test-Path -LiteralPath $testReportDirectory) {
        $currentReports = @(Get-ChildItem -LiteralPath $testReportDirectory -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTime -ge $StartedAt.AddSeconds(-2) })
    }
    if ($currentReports.Count -gt 0) {
        # Surefire also prints a final aggregate line; adding it to per-class console lines doubles counts.
        $total = 0; $failures = 0; $errors = 0; $skipped = 0; $matchesFound = 0
        foreach ($reportFile in $currentReports) {
            try {
                [xml]$xml = Get-Content -LiteralPath $reportFile.FullName -Raw -Encoding UTF8
                $suite = $xml.testsuite
                if ($suite) {
                    $total += [int]$suite.tests
                    $failures += [int]$suite.failures
                    $errors += [int]$suite.errors
                    $skipped += [int]$suite.skipped
                    $matchesFound++
                }
            } catch { }
        }
    }

    $failedTestNames = [Collections.Generic.List[string]]::new()
    if ($currentReports.Count -gt 0) {
        foreach ($reportFile in $currentReports) {
            try {
                [xml]$xml = Get-Content -LiteralPath $reportFile.FullName -Raw -Encoding UTF8
                foreach ($case in @($xml.testsuite.testcase)) {
                    if ($null -ne $case.SelectSingleNode('failure') -or $null -ne $case.SelectSingleNode('error')) {
                        $failedTestNames.Add("$($case.classname)#$($case.name)")
                    }
                }
            } catch { }
        }
    }
    return [pscustomobject]@{
        Total = $total
        Failures = $failures
        Errors = $errors
        Skipped = $skipped
        ReportCount = $matchesFound
        FailedTestNames = @($failedTestNames.ToArray() | Select-Object -Unique)
    }
}

function Get-ContractCredentials([string]$ContractPath) {
    $document = Get-Content -LiteralPath $ContractPath -Raw -Encoding UTF8
    $sectionMatch = [regex]::Match($document, '(?ms)^###\s*5\.1\s*测试账号凭据表\s*$\r?\n(?<section>.*?)(?=^###\s*5\.2|\z)')
    if (-not $sectionMatch.Success) { throw 'Could not locate interface contract section 5.1.' }
    $accounts = [Collections.Generic.List[object]]::new()
    foreach ($line in ($sectionMatch.Groups['section'].Value -split '\r?\n')) {
        if ($line -match '^\|\s*\*\*(?<label>[^*]+)\*\*\s*\|\s*`(?<phone>[^`]+)`\s*\|\s*`(?<password>[^`]+)`\s*\|') {
            $accounts.Add([pscustomobject]@{
                Label = $Matches['label'].Trim()
                Phone = $Matches['phone'].Trim()
                Password = $Matches['password']
            })
        }
    }
    if ($accounts.Count -ne 6) { throw 'Interface contract section 5.1 must contain exactly six synthetic accounts.' }
    return ,$accounts.ToArray()
}

function Write-RunReport([int]$ExitCode) {
    $relativeScript = 'ops/windows/tests/Test-SystemHttpTempDb.ps1'
    $command = "pwsh -NoProfile -ExecutionPolicy Bypass -File `"$relativeScript`""
    if ($HoldSeconds -gt 0) { $command += " -HoldSeconds $HoldSeconds" }
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('# 隔离临时数据库真实 HTTP 冒烟报告')
    $lines.Add('')
    $lines.Add('- 命令：`' + $command + '`')
    $lines.Add("- 退出码：$ExitCode")
    $lines.Add("- 开始时间：$($script:runStartedAt.ToString('yyyy-MM-dd HH:mm:ss zzz'))")
    $lines.Add("- 报告时间：$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz'))")
    $lines.Add("- HTTP 冒烟结果：$($script:httpSmokeResult)")
    $lines.Add("- 完整 Maven 测试结果：$($script:junitResult)")
    if ($null -ne $script:junitExitCode) { $lines.Add("- 完整 Maven 测试退出码：$($script:junitExitCode)") }
    $lines.Add("- 完整 Maven 测试汇总：$($script:junitSummary)")
    if ($script:junitFailureNames.Count -gt 0) { $lines.Add("- 失败测试：$($script:junitFailureNames -join ', ')") }
    $lines.Add('- 后端全套测试子命令：`mvnw.cmd -f pom.xml -Dbasedir=<tempRoot> test`（初始只读 HTTP 健康/匿名探针后停止 API；同一隔离临时库）')
    $lines.Add('- 完整业务 HTTP 链在 Maven 测试结束后重启 API 执行，避免预约、SOS、药盒等测试写入改变 JUnit 的种子基线。')
    if ($script:httpPort) { $lines.Add("- 临时 API 端口：127.0.0.1:$($script:httpPort)（不含任何登录凭据）") }
    if ($script:dbPort) { $lines.Add("- 临时 MySQL 端口：127.0.0.1:$($script:dbPort)，目标库名固定为 elderly_care_demo；连接 marker 的值不写入报告。") }
    $lines.Add('')
    $lines.Add('## 链路断言')
    $lines.Add('')
    foreach ($check in $script:checks.GetEnumerator()) { $lines.Add("- [$($check.Value)] $($check.Key)") }
    $lines.Add('')
    $lines.Add('## 失败与清理')
    $lines.Add('')
    $lines.Add("- Task 4 合成夹具清理：$($script:fixtureCleanupMessage)")
    if ($script:failureMessage) { $lines.Add("- HTTP/脚本失败原因：$($script:failureMessage)") }
    else { $lines.Add('- HTTP/脚本失败原因：无。') }
    if ($script:junitFailureMessage) { $lines.Add("- JUnit 失败原因：$($script:junitFailureMessage)") }
    else { $lines.Add('- JUnit 失败原因：无。') }
    if ($script:apiFailureDiagnostics.Count -gt 0) {
        $lines.Add('')
        $lines.Add('### API 启动诊断（已脱敏）')
        $lines.Add('')
        $lines.Add('```text')
        foreach ($diagnostic in $script:apiFailureDiagnostics) { $lines.Add($diagnostic) }
        $lines.Add('```')
    }
    $lines.Add("- 临时资源清理：$($script:cleanupMessage)")
    $lines.Add('')
    $lines.Add('## 局限与边界')
    $lines.Add('')
    $lines.Add('- 脚本只在系统临时目录下建立专属 mysqld datadir；seed wrapper 仅连接 loopback 上的随机 MySQL 端口、`elderly_care_demo` / `elderly_app`。清理会先校验 datadir 位于本次唯一 tempRoot 内，否则拒绝递归删除。')
    $lines.Add('- 六个账号从接口契约 §5.1 动态读取；脚本输出和报告不包含手机号、密码、Cookie、Bearer Token、CSRF Token 或随机 marker 值。')
    $lines.Add('- 覆盖员工 API 创建/角色替换、授权拒绝与无副作用快照、审计字段、API 角色变更后的 Web Cookie/MINIAPP 令牌撤销及重授后持续失效；覆盖预置 FAMILY CHILD+ELDER 切换后的独立关系/身份范围，以及 CARE_WORKER 当前承接工单执行和 workOrderId 绑定上传拒绝边界。')
    $lines.Add('- 保留原有六账号登录、Web Cookie/CSRF、老人资料/服药打卡、子女绑定老人/预约、管理员确认并派单、网格员履约照片提交、管理员审核至预约 COMPLETED，以及老人 SOS 触发/管理员处置控制链；完整 `mvn test` 与 HTTP 链分别报告。')
    $lines.Add('- 护工和家庭账号均为临时合成夹具；家庭 elder_identity_link 的激活票据仅为外键测试直接预置，未调用短信、注册、激活或设备 API。CARE_WORKER 上传前后统计 `APP_STORAGE_ROOT` 文件数，存储目录位于本次唯一 tempRoot 内。')
    $lines.Add('- Maven 测试前脚本会清空进程内所有 AUTH_TEST_* 变量，故 AuthDatabaseIntegrationTest 的 4 个认证门控用例按环境条件跳过；它们由 Test-AuthSessionTempDb.ps1 在独立临时库实际证明，跳过仍计入本次 JUnit 汇总。')
    $lines.Add('- 批次 1 认证矩阵额外核验 DISABLED、改密后旧 Cookie/Bearer、空闲超时、UNLINKED 和角色撤销后重新授予；断言拒绝状态及会话撤销、业务、通知、审计事实。')
    $lines.Add('- 图片链使用合成 1x1 PNG 验证上传接口、文件读取与履约 URL 引用，不验证真实相机图片、前端预览/裁剪或完整 H5/管理端 UI；本入口不覆盖真实短信/验证码、全部角色/接口或压力负载；同键并发处理中冲突由 IdempotencyIntegrationTest 覆盖。')
    $lines.Add('- `-HoldSeconds` 在完整业务 HTTP 链通过后、停止 API/MySQL 前保留隔离 API 与 MySQL，默认 0，最大 300 秒；JUnit 已在此前 API 停止窗口完成。')
    $lines.Add('')
    $reportDirectory = Split-Path -Parent $reportPath
    if (-not (Test-Path -LiteralPath $reportDirectory)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    Set-Content -LiteralPath $reportPath -Value $lines -Encoding UTF8
}

try {
    foreach ($key in $script:envKeys) { $script:previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

    $mysqlCommand = Get-Command mysql -ErrorAction Stop
    $script:mysqlExe = $mysqlCommand.Source
    $script:mysqlServerExe = Join-Path (Split-Path -Parent $script:mysqlExe) 'mysqld.exe'
    $script:baseDirectory = Split-Path -Parent (Split-Path -Parent $script:mysqlExe)
    Confirm-That (Test-Path -LiteralPath $script:mysqlServerExe) 'A local MySQL server binary is required for the isolated system HTTP smoke test.'
    $pwshCommand = Get-Command pwsh -ErrorAction Stop

    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    $script:dbPort = Get-RandomLoopbackPort
    $script:httpPort = Get-RandomLoopbackPort -Exclude @($script:dbPort)
    $script:dbPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $hmacKey = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $script:hmacKey = $hmacKey
    $marker = [Guid]::NewGuid().ToString('N')

    $init = Start-Process -FilePath $script:mysqlServerExe `
        -ArgumentList ((@('--no-defaults', '--initialize-insecure', "--basedir=$($script:baseDirectory)", "--datadir=$dataDirectory", '--console') | ForEach-Object { ConvertTo-CommandLineArgument ([string]$_) }) -join ' ') `
        -PassThru -WindowStyle Hidden -Wait `
        -RedirectStandardOutput (Join-Path $tempRoot 'mysql-init.out') `
        -RedirectStandardError (Join-Path $tempRoot 'mysql-init.err')
    Confirm-That ($init.ExitCode -eq 0) 'Isolated MySQL initialization failed.'

    $script:mysqlProcess = Start-IsolatedMysql @(
        '--no-defaults', "--basedir=$($script:baseDirectory)", "--datadir=$dataDirectory",
        "--port=$($script:dbPort)", '--bind-address=127.0.0.1',
        "--log-error=$(Join-Path $tempRoot 'mysql-error.log')", '--console'
    ) (Join-Path $tempRoot 'mysql-server.out') (Join-Path $tempRoot 'mysql-server.err')
    $mysqlReady = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        $script:mysqlProcess.Refresh()
        if ($script:mysqlProcess.HasExited) { throw 'Isolated MySQL exited before becoming ready.' }
        if (Test-TcpPortOpen $script:dbPort) {
            $probe = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uroot', '-N', '-B', '-e', 'SELECT @@port;')
            if ($probe.ExitCode -eq 0 -and $probe.StdOut.Trim() -eq [string]$script:dbPort) { $mysqlReady = $true; break }
        }
        Start-Sleep -Milliseconds 250
    }
    Confirm-That $mysqlReady 'Isolated MySQL did not become ready on its random loopback port.'

    $setupSql = @"
CREATE DATABASE ``elderly_care_demo`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'elderly_app'@'127.0.0.1' IDENTIFIED BY '$($script:dbPassword)';
GRANT ALL PRIVILEGES ON ``elderly_care_demo``.* TO 'elderly_app'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
    $setup = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uroot') $setupSql
    Confirm-That ($setup.ExitCode -eq 0) 'Could not prepare the isolated empty database and test account.'

    $identityProbe = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-N', '-B', '-e', 'SELECT @@port, DATABASE();')
    $expectedIdentity = "$($script:dbPort)`telderly_care_demo"
    Confirm-Chain '临时 MySQL 使用随机 loopback 端口且目标库名校验一致' `
        ($identityProbe.ExitCode -eq 0 -and $identityProbe.StdOut.Trim() -eq $expectedIdentity) `
        'Temporary MySQL target did not match the expected random port and database.'

    [Environment]::SetEnvironmentVariable('DB_HOST', '127.0.0.1', 'Process')
    [Environment]::SetEnvironmentVariable('DB_PORT', [string]$script:dbPort, 'Process')
    [Environment]::SetEnvironmentVariable('DB_NAME', 'elderly_care_demo', 'Process')
    [Environment]::SetEnvironmentVariable('DB_USERNAME', 'elderly_app', 'Process')
    [Environment]::SetEnvironmentVariable('DB_PASSWORD', $script:dbPassword, 'Process')
    [Environment]::SetEnvironmentVariable('SERVER_ADDRESS', '127.0.0.1', 'Process')
    [Environment]::SetEnvironmentVariable('SERVER_PORT', [string]$script:httpPort, 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', 'local', 'Process')
    [Environment]::SetEnvironmentVariable('AUTH_RATE_LIMIT_HMAC_KEY', $hmacKey, 'Process')
    [Environment]::SetEnvironmentVariable('AUTH_COOKIE_SECURE', 'false', 'Process')
    [Environment]::SetEnvironmentVariable('PHONE_VERIFICATION_ENABLED', 'false', 'Process')
    [Environment]::SetEnvironmentVariable('APP_STORAGE_ROOT', (Join-Path $tempRoot 'files'), 'Process')
    $script:storageRoot = Join-Path $tempRoot 'files'
    New-Item -ItemType Directory -Path $script:storageRoot -Force | Out-Null

    $script:apiBase = "http://127.0.0.1:$($script:httpPort)"
    Add-Type -AssemblyName System.Net.Http
    $mainHttpContext = New-TestHttpClient
    $script:cookieContainer = $mainHttpContext.Cookies
    $script:httpClient = $mainHttpContext.Client

    $script:apiProcess = Start-TestApi 'flyway'
    $null = Wait-ApiReady -Process $script:apiProcess -PhaseName 'Flyway initialization'
    $flywayCount = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-N', '-B', '-e', "SELECT COUNT(DISTINCT version) FROM flyway_schema_history WHERE success=1 AND version IN ('1','2','3','3.1','3.2','3.3','3.4');")
    $markerSetup = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo') @"
CREATE TABLE system_http_test_marker (marker_value CHAR(32) NOT NULL PRIMARY KEY);
INSERT INTO system_http_test_marker (marker_value) VALUES ('$marker');
"@
    $markerAfterFlyway = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-N', '-B', '-e', 'SELECT @@port, DATABASE(), (SELECT marker_value FROM system_http_test_marker);')
    $expectedMarkedIdentity = "$($script:dbPort)`telderly_care_demo`t$marker"
    $markerDrop = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-e', 'DROP TABLE system_http_test_marker;')
    Confirm-Chain 'Spring Boot 首次启动由 Flyway 初始化隔离库并通过健康检查' `
        ($flywayCount.ExitCode -eq 0 -and $flywayCount.StdOut.Trim() -eq '7' -and
            (Test-ApiBoundToLoopback $script:httpPort)) `
        'Flyway/API readiness check failed.'
    Confirm-Chain 'Flyway 后使用随机 marker 校验目标库身份并删除 marker' `
        ($markerSetup.ExitCode -eq 0 -and $markerAfterFlyway.ExitCode -eq 0 -and
            $markerAfterFlyway.StdOut.Trim() -eq $expectedMarkedIdentity -and $markerDrop.ExitCode -eq 0) `
        'Post-Flyway database marker validation or removal failed.'

    Stop-TestApi
    $portReleased = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (-not (Test-TcpPortOpen $script:httpPort)) { $portReleased = $true; break }
        Start-Sleep -Milliseconds 250
    }
    Confirm-Chain '首轮 API 已退出，且确认其端口已释放后才加载 seed' $portReleased `
        'The first API process still owns its random HTTP port; refusing to seed before shutdown.'

    $seedEnvFile = Join-Path $tempRoot 'seed.env'
    @(
        'DB_HOST=127.0.0.1'
        "DB_PORT=$($script:dbPort)"
        'DB_NAME=elderly_care_demo'
        'DB_USERNAME=elderly_app'
        "DB_PASSWORD=$($script:dbPassword)"
    ) | Set-Content -LiteralPath $seedEnvFile -Encoding ASCII
    $seedWrapper = Join-Path $repoRoot 'ops\windows\load-demo-seed.ps1'
    Confirm-That (Test-Path -LiteralPath $seedWrapper) 'The existing first-load seed wrapper is missing.'
    $seedOutput = & $pwshCommand.Source -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $seedWrapper -EnvFile $seedEnvFile 2>&1
    $seedExitCode = $LASTEXITCODE
    if ($null -eq $seedExitCode) { $seedExitCode = 1 }
    Confirm-Chain '现有 first-load seed wrapper 在临时库返回退出码 0' ($seedExitCode -eq 0) `
        'The isolated first-load seed wrapper did not return exit code 0.'

    $script:apiProcess = Start-TestApi 'seeded-pretest'
    $null = Wait-ApiReady -Process $script:apiProcess -PhaseName 'seeded API restart'
    Confirm-Chain 'Seed 后 API 在随机 loopback HTTP 端口健康可用（Maven 前）' `
        ((Test-ApiBoundToLoopback $script:httpPort) -and (Test-TcpPortOpen $script:httpPort)) `
        'The seeded API pre-test smoke was not listening on the expected loopback HTTP port.'

    $anonymousResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user'
    Confirm-Chain '匿名访问当前用户接口返回 HTTP 401' ($anonymousResponse.StatusCode -eq 401) `
        'Anonymous current-user request did not return HTTP 401.'

    Stop-TestApi
    $apiPortReleased = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (-not (Test-TcpPortOpen $script:httpPort)) { $apiPortReleased = $true; break }
        Start-Sleep -Milliseconds 250
    }
    Confirm-Chain 'HTTP 冒烟结束后 API 已停止且端口释放，才运行完整 Maven 测试' $apiPortReleased `
        'The seeded API still owns its random HTTP port; refusing to run the full suite concurrently.'

    foreach ($key in @(
            'AUTH_TEST_DB_URL', 'AUTH_TEST_DB_USERNAME', 'AUTH_TEST_DB_PASSWORD', 'AUTH_TEST_HMAC_KEY',
            'AUTH_TEST_DB_PORT', 'AUTH_TEST_DB_MARKER', 'AUTH_TEST_SEED_DOC_PATH')) {
        [Environment]::SetEnvironmentVariable($key, $null, 'Process')
    }
    $mavenTestStartedAt = Get-Date
    $mavenWrapperPath = Join-Path $repoRoot 'mvnw.cmd'
    $rootPomPath = Join-Path $repoRoot 'pom.xml'
    $mavenOutput = @()
    try {
        Push-Location $tempRoot
        try {
            $mavenOutput = & $mavenWrapperPath -f $rootPomPath "-Dbasedir=$tempRoot" test 2>&1
            $script:junitExitCode = $LASTEXITCODE
        } finally { Pop-Location }
        $testSummary = Get-MavenTestSummary -OutputLines @($mavenOutput) -StartedAt $mavenTestStartedAt
        $script:junitFailureNames = @($testSummary.FailedTestNames)
        if ($testSummary.ReportCount -gt 0) {
            $passedTests = $testSummary.Total - $testSummary.Failures - $testSummary.Errors - $testSummary.Skipped
            $failedTests = $testSummary.Failures + $testSummary.Errors
            $script:junitSummary = "通过: $passedTests, 失败: $failedTests, 跳过: $($testSummary.Skipped) (Tests run: $($testSummary.Total), Failures: $($testSummary.Failures), Errors: $($testSummary.Errors), Skipped: $($testSummary.Skipped))"
        } else {
            $script:junitSummary = 'No Surefire summary was emitted; inspect the Maven run result.'
        }
        $script:junitResult = if ($script:junitExitCode -eq 0 -and $testSummary.Failures -eq 0 -and
            $testSummary.Errors -eq 0 -and $testSummary.ReportCount -gt 0) { '通过' } else { '失败' }
        if ($script:junitResult -eq '通过') {
            $script:checks['临时数据库上的完整 Maven 测试套件通过'] = '通过'
            Write-Output "通过：临时数据库上的完整 Maven 测试套件通过；$($script:junitSummary)"
        } else {
            $script:checks['临时数据库上的完整 Maven 测试套件通过'] = '失败'
            $script:junitFailureMessage = "完整 Maven 测试未通过，退出码 $($script:junitExitCode)。"
            Write-Output "失败：临时数据库上的完整 Maven 测试套件；$($script:junitSummary)"
        }
    } catch {
        $script:junitResult = '失败'
        $script:junitExitCode = if ($null -ne $LASTEXITCODE) { $LASTEXITCODE } else { 1 }
        $script:junitFailureMessage = '运行完整 Maven 测试时发生错误；HTTP 链路仍会独立继续验收。'
        $script:checks['临时数据库上的完整 Maven 测试套件通过'] = '失败'
        Write-Output '失败：完整 Maven 测试命令未能正常完成；继续独立 HTTP 链路验收。'
    }

    $script:apiProcess = Start-TestApi 'workflow'
    $null = Wait-ApiReady -Process $script:apiProcess -PhaseName 'post-Maven workflow API restart'
    Confirm-Chain 'Maven 全套结束后 API 在隔离端口重新健康可用' `
        ((Test-ApiBoundToLoopback $script:httpPort) -and (Test-TcpPortOpen $script:httpPort)) `
        'The API did not restart on its isolated HTTP port after the Maven suite.'

    # Community 2 is already part of the synthetic seed. Add one isolated target only
    # after Maven has finished, so no JUnit test can observe this HTTP fixture.
    $script:syntheticFixturesStarted = $true
    $crossCommunityInsert = Invoke-AppDatabaseQuery @'
INSERT INTO `elder_profile` (
    `archive_no`, `community_id`, `name`, `sex`, `address`, `status`, `row_version`,
    `created_by`, `updated_by`
)
VALUES (
    'SYSHTTP-COMM2-ELDER', 2, '隔离跨社区测试老人', 'UNKNOWN', '隔离测试地址',
    'ACTIVE', 1, 1, 1
);
'@
    Confirm-That ($crossCommunityInsert.ExitCode -eq 0) 'Could not create the synthetic community-2 elder fixture in the isolated database.'
    $crossCommunityIdQuery = Invoke-AppDatabaseQuery "SELECT elder_id FROM elder_profile WHERE archive_no = 'SYSHTTP-COMM2-ELDER';"
    $crossCommunityElderId = 0L
    $idParsed = [long]::TryParse($crossCommunityIdQuery.StdOut.Trim(), [ref]$crossCommunityElderId)
    Confirm-Chain 'HTTP 权限矩阵仅在隔离数据库建立社区 2 目标档案' `
        ($crossCommunityIdQuery.ExitCode -eq 0 -and $idParsed -and $crossCommunityElderId -gt 0) `
        'Could not read back the synthetic community-2 elder fixture.'
    $script:syntheticCrossCommunityElderId = $crossCommunityElderId

    $crossCommunityStaffPhone = New-SyntheticPhone
    $script:syntheticCrossCommunityStaffPhone = $crossCommunityStaffPhone
    $crossCommunityStaffInsert = Invoke-AppDatabaseQuery @"
INSERT INTO sys_user (account_kind, phone_normalized, password_hash, status)
VALUES ('STAFF', '$crossCommunityStaffPhone', 'not-for-login-test-fixture', 'ACTIVE');
SET @syshttp_cross_staff_id = LAST_INSERT_ID();
INSERT INTO sys_user_role (user_id, role_code, community_id, status, granted_by, granted_at)
VALUES (@syshttp_cross_staff_id, 'COMMUNITY_ADMIN', 2, 'ACTIVE', 1, NOW(3));
"@
    Confirm-That ($crossCommunityStaffInsert.ExitCode -eq 0) 'Could not create the isolated community-2 staff target.'
    $crossCommunityStaffQuery = Invoke-AppDatabaseQuery "SELECT user_id FROM sys_user WHERE phone_normalized = '$crossCommunityStaffPhone' AND account_kind = 'STAFF';"
    $crossCommunityStaffId = 0L
    $crossStaffIdParsed = [long]::TryParse($crossCommunityStaffQuery.StdOut.Trim(), [ref]$crossCommunityStaffId)
    Confirm-That ($crossCommunityStaffQuery.ExitCode -eq 0 -and $crossStaffIdParsed -and $crossCommunityStaffId -gt 0) `
        'Could not read back the isolated community-2 staff target.'

    $credentialPath = Join-Path $repoRoot 'docs\current\接口契约_M01_M02.md'
    $accounts = Get-ContractCredentials -ContractPath $credentialPath
    $adminAccount = $null
    $workerAccount = $null
    $childAccount = $null
    $pendingChildAccount = $null
    $frozenChildAccount = $null
    $elderAccount = $null
    foreach ($account in $accounts) {
        $clientType = if ($account.Label -match '社区管理员') { 'WEB' } else { 'MINIAPP' }
        $loginSession = $null
        if ($clientType -eq 'WEB') { $loginSession = $true }
        $loginResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Body @{
            phone = $account.Phone
            password = $account.Password
            clientType = $clientType
        }
        $loginJson = $null
        if ($loginResponse.StatusCode -eq 200) {
            try { $loginJson = ConvertFrom-Json -InputObject $loginResponse.Content } catch { }
        }
        $loginSucceeded = ($loginResponse.StatusCode -eq 200 -and $null -ne $loginJson -and
            $loginJson.code -eq 'OK' -and $null -ne $loginJson.data -and $loginJson.data.userId)
        Confirm-Chain "账号 $($account.Label) 登录成功" $loginSucceeded 'A synthetic account login failed; credential values were intentionally omitted.'
        if ($account.Label -match '社区管理员') {
            $adminAccount = [pscustomobject]@{ Data = $loginJson.data; UserId = [long]$loginJson.data.userId; Csrf = [string]$loginJson.data.csrfToken }
        } elseif ($account.Label -match '网格员') {
            $workerAccount = [pscustomobject]@{ Data = $loginJson.data; UserId = [long]$loginJson.data.userId; Token = [string]$loginJson.data.token }
        } elseif ($account.Label -match '已激活老人') {
            $elderAccount = [pscustomobject]@{ Token = [string]$loginJson.data.token }
        } elseif ($account.Label -match '子女\s*1') {
            $childAccount = [pscustomobject]@{ Token = [string]$loginJson.data.token }
        } elseif ($account.Label -match '子女\s*2') {
            $pendingChildAccount = [pscustomobject]@{
                Token = [string]$loginJson.data.token
                Phone = [string]$account.Phone
                Password = [string]$account.Password
            }
        } elseif ($account.Label -match '子女\s*3') {
            $frozenChildAccount = [pscustomobject]@{ Token = [string]$loginJson.data.token }
        }
    }
    Confirm-Chain '契约 §5.1 的六个合成账号全部登录成功且凭据未输出' `
        (@($script:checks.GetEnumerator() | Where-Object { $_.Key -match '^账号 .+ 登录成功$' -and $_.Value -eq '通过' }).Count -eq 6) `
        'Not all six documented synthetic accounts completed login.'

    $sessionCookies = @($script:cookieContainer.GetCookies([Uri]($script:apiBase + '/api/v1/auth/login')) | Where-Object { $_.Name -eq 'SESSION_TOKEN' })
    $webCookieOk = ($adminAccount -and $adminAccount.Csrf -and $sessionCookies.Count -eq 1 -and
        $sessionCookies[0].HttpOnly -and $sessionCookies[0].Path -eq '/api/v1' -and
        -not $sessionCookies[0].Secure -and $null -eq $adminAccount.Data.token)
    Confirm-Chain 'Web 登录设置 HttpOnly 会话 Cookie、提供 CSRF Token 且响应不返回 Bearer Token' $webCookieOk `
        'Web login did not satisfy the expected Cookie/CSRF session contract.'

    $anonymousHttpContext = New-TestHttpClient
    $workerHttpContext = New-TestHttpClient
    $staffNegativeStart = Get-StaffMutationSnapshot
    $anonymousStaffList = Invoke-Api -Method 'GET' -Path '/api/v1/admin/staff' -Client $anonymousHttpContext.Client
    $anonymousStaffCreate = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -Client $anonymousHttpContext.Client `
        -IdempotencyKey ('SYSHTTP-ANON-' + [Guid]::NewGuid().ToString('N')) -Body @{
            phone = New-SyntheticPhone; initialPassword = [Guid]::NewGuid().ToString('N'); roles = @('GRID_WORKER')
        }
    $workerStaffList = Invoke-Api -Method 'GET' -Path '/api/v1/admin/staff' -BearerToken $workerAccount.Token -Client $workerHttpContext.Client
    $workerStaffCreate = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -BearerToken $workerAccount.Token `
        -Client $workerHttpContext.Client -IdempotencyKey ('SYSHTTP-WORKER-' + [Guid]::NewGuid().ToString('N')) -Body @{
            phone = New-SyntheticPhone; initialPassword = [Guid]::NewGuid().ToString('N'); roles = @('CARE_WORKER')
        }
    $workerStaffReplace = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$($adminAccount.UserId)/roles" `
        -BearerToken $workerAccount.Token -Client $workerHttpContext.Client `
        -IdempotencyKey ('SYSHTTP-WORKER-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('GRID_WORKER'); reason = '隔离 HTTP 工作人员越权拒绝' }
    $staffNegativeEnd = Get-StaffMutationSnapshot
    Confirm-Chain '匿名员工列表与创建均返回 HTTP 401 且无 DB/session/audit 副作用' `
        ($anonymousStaffList.StatusCode -eq 401 -and $anonymousStaffCreate.StatusCode -eq 401 -and
            $staffNegativeStart -eq $staffNegativeEnd) `
        'Anonymous staff-management requests were not rejected without changing isolated staff/session/audit facts.'
    Confirm-Chain '网格员员工列表/创建/角色替换均返回 HTTP 403 且无 DB/session/audit 副作用' `
        ($workerStaffList.StatusCode -eq 403 -and $workerStaffCreate.StatusCode -eq 403 -and
            $workerStaffReplace.StatusCode -eq 403 -and $staffNegativeStart -eq $staffNegativeEnd) `
        'A grid worker reached staff-management operations or changed isolated staff/session/audit facts.'

    $missingIdempotencyBefore = Get-StaffMutationSnapshot
    $missingIdempotencyResponse = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -CsrfToken $adminAccount.Csrf -Body @{
        phone = New-SyntheticPhone; initialPassword = [Guid]::NewGuid().ToString('N'); roles = @('GRID_WORKER')
    }
    $missingIdempotencyAfter = Get-StaffMutationSnapshot
    Confirm-Chain '员工创建缺少 Idempotency-Key 返回 HTTP 400 且无 DB/session/audit 副作用' `
        ($missingIdempotencyResponse.StatusCode -eq 400 -and $missingIdempotencyBefore -eq $missingIdempotencyAfter) `
        'A staff creation without Idempotency-Key was accepted or changed isolated staff/session/audit facts.'

    $staffPhone = New-SyntheticPhone
    $script:syntheticStaffPhone = $staffPhone
    $staffPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $shortPasswordBefore = Get-StaffMutationSnapshot
    $shortPasswordResponse = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -CsrfToken $adminAccount.Csrf `
        -IdempotencyKey ('SYSHTTP-SHORT-PASSWORD-' + [Guid]::NewGuid().ToString('N')) -Body @{
            phone = New-SyntheticPhone; initialPassword = 'short7'; roles = @('GRID_WORKER')
        }
    $shortPasswordAfter = Get-StaffMutationSnapshot
    $shortPasswordNoSideEffects = $shortPasswordBefore -eq $shortPasswordAfter
    Confirm-Chain '短初始密码返回 HTTP 422 且无 DB/session/audit 副作用' `
        ($shortPasswordResponse.StatusCode -eq 422 -and $shortPasswordNoSideEffects) `
        "A short initial password was accepted or changed isolated staff/session/audit facts (HTTP $($shortPasswordResponse.StatusCode), snapshotUnchanged=$shortPasswordNoSideEffects)."

    $invalidRoleBefore = Get-StaffMutationSnapshot
    $invalidRoleResponse = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -CsrfToken $adminAccount.Csrf `
        -IdempotencyKey ('SYSHTTP-INVALID-ROLE-' + [Guid]::NewGuid().ToString('N')) -Body @{
            phone = New-SyntheticPhone; initialPassword = $staffPassword; roles = @('MAINTENANCE')
        }
    $invalidRoleAfter = Get-StaffMutationSnapshot
    Confirm-Chain '未知或运维角色返回 HTTP 400 且无 DB/session/audit 副作用' `
        ($invalidRoleResponse.StatusCode -eq 400 -and $invalidRoleBefore -eq $invalidRoleAfter) `
        'An unknown or maintenance role was accepted or changed isolated staff/session/audit facts.'

    $familyRoleBefore = Get-StaffMutationSnapshot
    $familyRoleResponse = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -CsrfToken $adminAccount.Csrf `
        -IdempotencyKey ('SYSHTTP-FAMILY-ROLE-' + [Guid]::NewGuid().ToString('N')) -Body @{
            phone = New-SyntheticPhone; initialPassword = $staffPassword; roles = @('CHILD')
        }
    $familyRoleAfter = Get-StaffMutationSnapshot
    Confirm-Chain '家庭角色不能用于员工创建且无 DB/session/audit 副作用' `
        ($familyRoleResponse.StatusCode -eq 400 -and $familyRoleBefore -eq $familyRoleAfter) `
        'A family role was accepted for staff creation or changed isolated staff/session/audit facts.'

    $lastAdminBefore = Get-StaffMutationSnapshot
    $lastAdminResponse = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$($adminAccount.UserId)/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-LAST-ADMIN-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @(); reason = '隔离 HTTP 最后管理员保护验收' }
    $lastAdminAfter = Get-StaffMutationSnapshot
    Confirm-Chain '撤销最后一个有效管理员返回 HTTP 400 且角色/会话/审计事实不变' `
        ($lastAdminResponse.StatusCode -eq 400 -and $lastAdminBefore -eq $lastAdminAfter) `
        'The last active community administrator was removable or the rejected request changed isolated facts.'

    $crossStaffBefore = Get-StaffMutationSnapshot
    $crossStaffResponse = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$crossCommunityStaffId/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-CROSS-STAFF-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('CARE_WORKER'); reason = '隔离 HTTP 跨社区员工越权验收' }
    $crossStaffAfter = Get-StaffMutationSnapshot
    Confirm-Chain '跨社区角色替换返回 HTTP 404 且角色/会话/审计事实不变' `
        ($crossStaffResponse.StatusCode -eq 404 -and $crossStaffBefore -eq $crossStaffAfter) `
        'A community administrator modified another community staff target or changed isolated staff/session/audit facts.'

    $staffCreateBefore = Get-StaffMutationSnapshot
    $staffCreateResponse = Invoke-Api -Method 'POST' -Path '/api/v1/admin/staff' -CsrfToken $adminAccount.Csrf `
        -IdempotencyKey ('SYSHTTP-STAFF-CREATE-' + [Guid]::NewGuid().ToString('N')) -Body @{
        phone = $staffPhone
        initialPassword = $staffPassword
        roles = @('GRID_WORKER')
    }
    $staffCreateJson = Assert-OkApiResponse $staffCreateResponse 'Community administrator staff creation' 201
    $staffTargetUserId = [long]$staffCreateJson.data.userId
    $script:syntheticStaffUserIds.Add($staffTargetUserId)
    $staffCreateAfter = Get-StaffMutationSnapshot
    $expectedMaskedPhone = $staffPhone.Substring(0, 3) + '****' + $staffPhone.Substring($staffPhone.Length - 4)
    Confirm-Chain '员工 API 创建返回 HTTP 201 且仅返回脱敏字段' `
        ($staffTargetUserId -gt 0 -and [string]$staffCreateJson.data.maskedPhone -eq $expectedMaskedPhone -and
            [string]$staffCreateJson.data.accountStatus -eq 'ACTIVE' -and
            @($staffCreateJson.data.roleCodes | Where-Object { $_ -eq 'GRID_WORKER' }).Count -eq 1 -and
            -not $staffCreateResponse.Content.Contains($staffPhone) -and
            -not $staffCreateResponse.Content.Contains($staffPassword) -and $staffCreateBefore -ne $staffCreateAfter) `
        'POST /api/v1/admin/staff did not return a sanitized HTTP 201 response.'

    $staffCreateAuditLine = Get-StaffAuditFacts -UserId $staffTargetUserId -Action 'STAFF_CREATED'
    $staffCreateAuditParts = $staffCreateAuditLine -split '\|', 8
    $staffCreateAuditDetails = if ($staffCreateAuditParts.Count -eq 8) { ConvertFrom-Json -InputObject $staffCreateAuditParts[7] } else { $null }
    Confirm-Chain '员工创建审计包含当前管理员、社区、目标和初始角色且不含密码' `
        ($staffCreateAuditParts.Count -eq 8 -and $staffCreateAuditParts[0] -eq [string]$adminAccount.UserId -and
            $staffCreateAuditParts[1] -eq 'COMMUNITY_ADMIN' -and $staffCreateAuditParts[2] -eq '1' -and
            $staffCreateAuditParts[3] -eq 'STAFF' -and $staffCreateAuditParts[4] -eq [string]$staffTargetUserId -and
            $staffCreateAuditParts[5] -eq 'STAFF_CREATED' -and $staffCreateAuditParts[6] -eq 'SUCCESS' -and
            $staffCreateAuditDetails.afterRoles.Count -eq 1 -and $staffCreateAuditDetails.afterRoles[0] -eq 'GRID_WORKER' -and
            -not $staffCreateAuditLine.Contains($staffPassword)) `
        'Staff creation audit facts did not match the authorized actor/community/target/roles boundary.'

    $staffListResponse = Invoke-Api -Method 'GET' -Path '/api/v1/admin/staff'
    $staffListJson = Assert-OkApiResponse $staffListResponse 'Community staff list'
    $staffListTarget = @($staffListJson.data | Where-Object { [long]$_.userId -eq $staffTargetUserId })
    $staffListCrossTarget = @($staffListJson.data | Where-Object { [long]$_.userId -eq $crossCommunityStaffId })
    Confirm-Chain '员工列表只返回本社区员工并显示新账号角色' `
        ($staffListTarget.Count -eq 1 -and $staffListCrossTarget.Count -eq 0 -and
            [string]$staffListTarget[0].maskedPhone -eq $expectedMaskedPhone -and
            @($staffListTarget[0].roleCodes | Where-Object { $_ -eq 'GRID_WORKER' }).Count -eq 1 -and
            -not $staffListResponse.Content.Contains($staffPhone)) `
        'GET /api/v1/admin/staff exposed a cross-community account or failed to return the created staff summary.'

    $adminGridUpdate = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$staffTargetUserId/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-ADMIN-GRID-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('COMMUNITY_ADMIN', 'GRID_WORKER'); reason = '隔离 HTTP 管理员兼任网格员验收' }
    $adminGridJson = Assert-OkApiResponse $adminGridUpdate 'Administrator plus grid-worker role replacement'
    $adminGridRoles = @((Get-ActiveStaffRoles -UserId $staffTargetUserId) -split ',')
    Confirm-Chain '员工 API 允许 COMMUNITY_ADMIN + GRID_WORKER' `
        (@($adminGridJson.data.roleCodes | Where-Object { $_ -in @('COMMUNITY_ADMIN', 'GRID_WORKER') }).Count -eq 2 -and
            $adminGridRoles.Count -eq 2 -and $adminGridRoles -contains 'COMMUNITY_ADMIN' -and $adminGridRoles -contains 'GRID_WORKER') `
        'The staff API did not persist the allowed COMMUNITY_ADMIN + GRID_WORKER combination.'

    $adminCareUpdate = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$staffTargetUserId/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-ADMIN-CARE-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('COMMUNITY_ADMIN', 'CARE_WORKER'); reason = '隔离 HTTP 管理员兼任护工验收' }
    $adminCareJson = Assert-OkApiResponse $adminCareUpdate 'Administrator plus care-worker role replacement'
    $adminCareRoles = @((Get-ActiveStaffRoles -UserId $staffTargetUserId) -split ',')
    Confirm-Chain '员工 API 允许 COMMUNITY_ADMIN + CARE_WORKER' `
        (@($adminCareJson.data.roleCodes | Where-Object { $_ -in @('COMMUNITY_ADMIN', 'CARE_WORKER') }).Count -eq 2 -and
            $adminCareRoles.Count -eq 2 -and $adminCareRoles -contains 'COMMUNITY_ADMIN' -and $adminCareRoles -contains 'CARE_WORKER') `
        'The staff API did not persist the allowed COMMUNITY_ADMIN + CARE_WORKER combination.'

    $invalidCombinationBefore = Get-StaffMutationSnapshot
    $invalidCombination = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$staffTargetUserId/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-GRID-CARE-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('GRID_WORKER', 'CARE_WORKER'); reason = '隔离 HTTP 互斥角色验收' }
    $invalidCombinationAfter = Get-StaffMutationSnapshot
    Confirm-Chain '员工 API 拒绝 GRID_WORKER + CARE_WORKER 且无 DB/session/audit 副作用' `
        ($invalidCombination.StatusCode -eq 400 -and $invalidCombinationBefore -eq $invalidCombinationAfter) `
        'The mutually exclusive GRID_WORKER + CARE_WORKER combination was accepted or changed isolated facts.'

    $adminCareAuditLine = Get-StaffAuditFacts -UserId $staffTargetUserId -Action 'STAFF_ROLES_REPLACED'
    $adminCareAuditParts = $adminCareAuditLine -split '\|', 8
    $adminCareAuditDetails = if ($adminCareAuditParts.Count -eq 8) { ConvertFrom-Json -InputObject $adminCareAuditParts[7] } else { $null }
    $adminCareAuditIdentityOk = $adminCareAuditParts.Count -eq 8 -and $adminCareAuditParts[0] -eq [string]$adminAccount.UserId -and
        $adminCareAuditParts[1] -eq 'COMMUNITY_ADMIN' -and $adminCareAuditParts[2] -eq '1' -and
        $adminCareAuditParts[3] -eq 'STAFF' -and $adminCareAuditParts[4] -eq [string]$staffTargetUserId -and
        $adminCareAuditParts[5] -eq 'STAFF_ROLES_REPLACED' -and $adminCareAuditParts[6] -eq 'SUCCESS'
    $adminCareAuditBeforeRoleCount = @($adminCareAuditDetails.beforeRoles | Where-Object { $_ -in @('COMMUNITY_ADMIN', 'GRID_WORKER') }).Count
    $adminCareAuditAfterRoleCount = @($adminCareAuditDetails.afterRoles | Where-Object { $_ -in @('COMMUNITY_ADMIN', 'CARE_WORKER') }).Count
    $adminCareAuditReasonMatches = [string]$adminCareAuditDetails.reason -eq '隔离 HTTP 管理员兼任护工验收'
    $adminCareAuditOk = $adminCareAuditIdentityOk -and $adminCareAuditBeforeRoleCount -eq 2 -and
        $adminCareAuditAfterRoleCount -eq 2 -and $adminCareAuditReasonMatches
    Confirm-Chain '角色替换审计包含 actor/community/target、前后角色和原因' `
        $adminCareAuditOk `
        "Staff role replacement audit mismatch (parts=$($adminCareAuditParts.Count), identity=$adminCareAuditIdentityOk, beforeRoles=$adminCareAuditBeforeRoleCount, afterRoles=$adminCareAuditAfterRoleCount, reasonMatch=$adminCareAuditReasonMatches, reason=$([string]$adminCareAuditDetails.reason))."

    $staffWebContext = New-TestHttpClient
    $staffMiniContext = New-TestHttpClient
    $staffWebLoginResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $staffWebContext.Client -Body @{
        phone = $staffPhone; password = $staffPassword; clientType = 'WEB'
    }
    $staffWebLoginJson = Assert-OkApiResponse $staffWebLoginResponse 'Synthetic staff Web login'
    $staffMiniLoginResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $staffMiniContext.Client -Body @{
        phone = $staffPhone; password = $staffPassword; clientType = 'MINIAPP'
    }
    $staffMiniLoginJson = Assert-OkApiResponse $staffMiniLoginResponse 'Synthetic staff MINIAPP login'
    $staffMiniToken = [string]$staffMiniLoginJson.data.token
    $staffWebCookies = @($staffWebContext.Cookies.GetCookies([Uri]($script:apiBase + '/api/v1/auth/login')) |
        Where-Object { $_.Name -eq 'SESSION_TOKEN' })
    $staffWebCurrentBefore = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -Client $staffWebContext.Client
    $staffMiniCurrentBefore = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $staffMiniToken -Client $staffMiniContext.Client
    $targetSessionsBefore = (Get-SessionFactsForUser -UserId $staffTargetUserId) -split '\|', 4
    $targetSessionDetailsBefore = Get-SessionDetailsForUser -UserId $staffTargetUserId
    Confirm-Chain '合成员工 Web Cookie 与 MINIAPP Bearer 会话均可独立认证' `
        ($staffWebCookies.Count -eq 1 -and $null -eq $staffWebLoginJson.data.token -and $staffMiniToken.Length -gt 0 -and
            $staffWebCurrentBefore.StatusCode -eq 200 -and $staffMiniCurrentBefore.StatusCode -eq 200 -and
            $targetSessionsBefore.Count -eq 4 -and $targetSessionsBefore[0] -eq '2' -and $targetSessionsBefore[1] -eq '2') `
        'Synthetic staff Web and MINIAPP sessions were not independently established in the isolated API.'

    $revokeSessionsResponse = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$staffTargetUserId/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-REVOKE-SESSIONS-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('GRID_WORKER'); reason = '隔离旧 token 撤销验收' }
    $null = Assert-OkApiResponse $revokeSessionsResponse 'Synthetic staff role removal and session revocation'
    $targetSessionsRevoked = (Get-SessionFactsForUser -UserId $staffTargetUserId) -split '\|', 4
    $targetSessionDetailsRevoked = Get-SessionDetailsForUser -UserId $staffTargetUserId
    $roleRevokedSessionDetails = @($targetSessionDetailsRevoked -split ',')
    $allRoleRevocationsHaveReasonAndTime = $roleRevokedSessionDetails.Count -eq 2 -and
        @($roleRevokedSessionDetails | Where-Object {
            $parts = $_ -split '~', 5
            $parts.Count -ne 5 -or $parts[3] -eq 'NULL' -or $parts[4] -ne 'ROLE_CHANGED'
        }).Count -eq 0
    $roleRevokedFactsBeforeWebRequest = Get-AuthorizationSideEffectSnapshot -UserId $staffTargetUserId
    $staffWebOldTokenResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -Client $staffWebContext.Client
    $roleRevokedFactsAfterWebRequest = Get-AuthorizationSideEffectSnapshot -UserId $staffTargetUserId
    $staffMiniOldTokenResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $staffMiniToken -Client $staffMiniContext.Client
    $roleRevokedFactsAfterMiniRequest = Get-AuthorizationSideEffectSnapshot -UserId $staffTargetUserId
    Confirm-Chain 'API 角色变更撤销目标全部 Web/MINIAPP 会话并标记 ROLE_CHANGED' `
        ($targetSessionsRevoked.Count -eq 4 -and $targetSessionsRevoked[0] -eq '2' -and
            $targetSessionsRevoked[1] -eq '0' -and $targetSessionsRevoked[2] -eq '2' -and
            $allRoleRevocationsHaveReasonAndTime) `
        'The API-driven role change did not revoke every target session with ROLE_CHANGED.'
    Confirm-Chain 'API 角色变更后旧 Web Cookie 与 MINIAPP Bearer 均返回 HTTP 401' `
        ($staffWebOldTokenResponse.StatusCode -eq 401 -and $staffMiniOldTokenResponse.StatusCode -eq 401 -and
            $roleRevokedFactsBeforeWebRequest -eq $roleRevokedFactsAfterWebRequest -and
            $roleRevokedFactsAfterWebRequest -eq $roleRevokedFactsAfterMiniRequest) `
        'A staff Web Cookie or MINIAPP Bearer Token remained valid after role revocation, or a denied request changed business, session, notification, or audit facts.'

    $revokeAuditLine = Get-StaffAuditFacts -UserId $staffTargetUserId -Action 'STAFF_ROLES_REPLACED'
    $revokeAuditParts = $revokeAuditLine -split '\|', 8
    $revokeAuditDetails = if ($revokeAuditParts.Count -eq 8) { ConvertFrom-Json -InputObject $revokeAuditParts[7] } else { $null }
    Confirm-Chain '角色撤销审计记录社区、目标、前后角色和指定原因' `
        ($revokeAuditParts.Count -eq 8 -and $revokeAuditParts[0] -eq [string]$adminAccount.UserId -and
            $revokeAuditParts[1] -eq 'COMMUNITY_ADMIN' -and $revokeAuditParts[2] -eq '1' -and
            $revokeAuditParts[3] -eq 'STAFF' -and $revokeAuditParts[4] -eq [string]$staffTargetUserId -and
            @($revokeAuditDetails.beforeRoles | Where-Object { $_ -in @('COMMUNITY_ADMIN', 'CARE_WORKER') }).Count -eq 2 -and
            $revokeAuditDetails.afterRoles.Count -eq 1 -and $revokeAuditDetails.afterRoles[0] -eq 'GRID_WORKER' -and
            [string]$revokeAuditDetails.reason -eq '隔离旧 token 撤销验收') `
        'The role-change audit did not preserve the actor, community, target, before/after roles, and reason.'

    $staffRegrantResponse = Invoke-Api -Method 'PUT' -Path "/api/v1/admin/staff/$staffTargetUserId/roles" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-REGRANT-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ roles = @('COMMUNITY_ADMIN', 'CARE_WORKER'); reason = '隔离角色重新授予验收' }
    $null = Assert-OkApiResponse $staffRegrantResponse 'Synthetic staff role regrant'
    $targetSessionsAfterRegrant = (Get-SessionFactsForUser -UserId $staffTargetUserId) -split '\|', 4
    $targetSessionDetailsAfterRegrant = Get-SessionDetailsForUser -UserId $staffTargetUserId
    $regrantDeniedFactsBeforeWebRequest = Get-AuthorizationSideEffectSnapshot -UserId $staffTargetUserId
    $staffWebOldAfterRegrant = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -Client $staffWebContext.Client
    $regrantDeniedFactsAfterWebRequest = Get-AuthorizationSideEffectSnapshot -UserId $staffTargetUserId
    $staffMiniOldAfterRegrant = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $staffMiniToken -Client $staffMiniContext.Client
    $regrantDeniedFactsAfterMiniRequest = Get-AuthorizationSideEffectSnapshot -UserId $staffTargetUserId
    Confirm-Chain '重新授予角色后旧 Web Cookie 与 MINIAPP Bearer 仍返回 HTTP 401' `
        ($staffWebOldAfterRegrant.StatusCode -eq 401 -and $staffMiniOldAfterRegrant.StatusCode -eq 401 -and
            $targetSessionsAfterRegrant.Count -eq 4 -and $targetSessionsAfterRegrant[1] -eq '0' -and
            $targetSessionsAfterRegrant[2] -eq '2' -and
            $targetSessionDetailsAfterRegrant -eq $targetSessionDetailsRevoked -and
            $targetSessionDetailsAfterRegrant -ne $targetSessionDetailsBefore -and
            $regrantDeniedFactsBeforeWebRequest -eq $regrantDeniedFactsAfterWebRequest -and
            $regrantDeniedFactsAfterWebRequest -eq $regrantDeniedFactsAfterMiniRequest) `
        'Regranting roles revived an old target token, reactivated a revoked session, or a denied request changed business, session, notification, or audit facts.'

    $familyPhone = New-SyntheticPhone
    $script:syntheticFamilyPhone = $familyPhone
    $familyTicketId = [Guid]::NewGuid().ToString()
    $script:syntheticFamilyTicketId = $familyTicketId
    $familyArchiveNo = 'SYSHTTP-FAM-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $script:syntheticFamilyArchiveNo = $familyArchiveNo
    $familyFixtureInsert = Invoke-AppDatabaseQuery @"
INSERT INTO sys_user (account_kind, phone_normalized, password_hash, status)
SELECT 'FAMILY', '$familyPhone', password_hash, 'ACTIVE' FROM sys_user WHERE user_id = 4;
SET @syshttp_family_user_id = LAST_INSERT_ID();
INSERT INTO sys_user_role (user_id, role_code, community_id, status, granted_by, granted_at)
VALUES
    (@syshttp_family_user_id, 'CHILD', NULL, 'ACTIVE', 1, NOW(3)),
    (@syshttp_family_user_id, 'ELDER', NULL, 'ACTIVE', 1, NOW(3));
INSERT INTO elder_profile (archive_no, community_id, name, sex, address, status, row_version, created_by, updated_by)
VALUES ('$familyArchiveNo', 1, '隔离多角色老人', 'UNKNOWN', '隔离测试地址', 'ACTIVE', 1, 1, 1);
SET @syshttp_family_elder_id = LAST_INSERT_ID();
INSERT INTO elder_activation_ticket (
    ticket_id, elder_id, code_mac, status, issued_by, issued_at, expires_at, used_at, used_by_user_id
)
VALUES (
    '$familyTicketId', @syshttp_family_elder_id, UNHEX(SHA2('$familyTicketId', 256)),
    'USED', 1, NOW(3), DATE_ADD(NOW(3), INTERVAL 1 DAY), NOW(3), @syshttp_family_user_id
);
INSERT INTO elder_identity_link (elder_id, user_id, linked_by_activation_ticket_id)
VALUES (@syshttp_family_elder_id, @syshttp_family_user_id, '$familyTicketId');
INSERT INTO elder_family_link (elder_id, child_user_id, status, link_generation, row_version)
VALUES (@syshttp_family_elder_id, @syshttp_family_user_id, 'ACTIVE', 1, 1);
SELECT CONCAT(@syshttp_family_user_id, '|', @syshttp_family_elder_id);
"@
    Confirm-That ($familyFixtureInsert.ExitCode -eq 0) 'Could not provision the isolated FAMILY CHILD+ELDER fixture and activation-ticket FK.'
    $familyFixtureParts = $familyFixtureInsert.StdOut.Trim() -split '\|', 2
    $script:syntheticFamilyUserId = 0L
    $script:syntheticFamilyElderId = 0L
    $familyUserParsed = $familyFixtureParts.Count -eq 2 -and [long]::TryParse($familyFixtureParts[0], [ref]$script:syntheticFamilyUserId)
    $familyElderParsed = $familyFixtureParts.Count -eq 2 -and [long]::TryParse($familyFixtureParts[1], [ref]$script:syntheticFamilyElderId)
    Confirm-That ($familyUserParsed -and $familyElderParsed -and $script:syntheticFamilyUserId -gt 0 -and $script:syntheticFamilyElderId -gt 0) `
        'Could not read back the isolated FAMILY identity fixture IDs.'

    $childOneCredential = $accounts | Where-Object { $_.Label -match '子女\s*1' } | Select-Object -First 1
    $familyClientContext = New-TestHttpClient
    $familyLoginResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $familyClientContext.Client -Body @{
        phone = $familyPhone; password = [string]$childOneCredential.Password; clientType = 'MINIAPP'
    }
    $familyLoginJson = Assert-OkApiResponse $familyLoginResponse 'Synthetic multi-role FAMILY login'
    $familyToken = [string]$familyLoginJson.data.token
    $familyInitialRoles = @($familyLoginJson.data.roles)
    $familyChildScopeBefore = Get-IsolatedTestSideEffectSnapshot
    $familyIdentityFactsBefore = Get-FamilyIdentityFacts -UserId $script:syntheticFamilyUserId `
        -ElderId $script:syntheticFamilyElderId -TicketId $familyTicketId
    $familyChildListResponse = Invoke-Api -Method 'GET' -Path '/api/v1/family/links/my-elders' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    $familyChildListJson = Assert-OkApiResponse $familyChildListResponse 'Synthetic FAMILY child-link scope'
    $familyChildListTargets = @($familyChildListJson.data | Where-Object { [long]$_.elderId -eq $script:syntheticFamilyElderId })
    $familyProfileAsChild = Invoke-Api -Method 'GET' -Path '/api/v1/elder/profile/me' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    Confirm-Chain '预置 FAMILY CHILD+ELDER 登录先进入 CHILD 独立关系范围' `
        ($familyInitialRoles -contains 'CHILD' -and $familyInitialRoles -contains 'ELDER' -and
            [string]$familyLoginJson.data.activeRole -eq 'CHILD' -and $familyChildListTargets.Count -eq 1 -and
            $familyProfileAsChild.StatusCode -eq 403) `
        'The synthetic FAMILY account did not start with an independent CHILD scope.'

    $familySwitchToElder = Invoke-Api -Method 'POST' -Path '/api/v1/auth/switch-role' -BearerToken $familyToken `
        -Client $familyClientContext.Client -Body @{ targetRoleCode = 'ELDER' }
    $familySwitchToElderJson = Assert-OkApiResponse $familySwitchToElder 'Synthetic FAMILY switch to ELDER'
    $familyElderCurrentResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    $familyElderCurrentJson = Assert-OkApiResponse $familyElderCurrentResponse 'Synthetic FAMILY current ELDER role'
    $familyElderProfileResponse = Invoke-Api -Method 'GET' -Path '/api/v1/elder/profile/me' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    $familyElderProfileJson = Assert-OkApiResponse $familyElderProfileResponse 'Synthetic FAMILY elder-identity scope'
    $familyChildListAsElder = Invoke-Api -Method 'GET' -Path '/api/v1/family/links/my-elders' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    Confirm-Chain '切换 ELDER 后只读取 elder_identity_link 指向的本人档案' `
        ([string]$familySwitchToElderJson.data.activeRole -eq 'ELDER' -and
            [string]$familyElderCurrentJson.data.currentRole -eq 'ELDER' -and
            [long]$familyElderProfileJson.data.elderId -eq $script:syntheticFamilyElderId -and
            $familyChildListAsElder.StatusCode -eq 403) `
        'Switching to ELDER did not isolate the elder identity from the CHILD relationship endpoint.'

    $familySwitchToChild = Invoke-Api -Method 'POST' -Path '/api/v1/auth/switch-role' -BearerToken $familyToken `
        -Client $familyClientContext.Client -Body @{ targetRoleCode = 'CHILD' }
    $familySwitchToChildJson = Assert-OkApiResponse $familySwitchToChild 'Synthetic FAMILY switch back to CHILD'
    $familyChildListAfterSwitch = Invoke-Api -Method 'GET' -Path '/api/v1/family/links/my-elders' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    $familyChildListAfterSwitchJson = Assert-OkApiResponse $familyChildListAfterSwitch 'Synthetic FAMILY child scope after role switch'
    $familyProfileAfterSwitch = Invoke-Api -Method 'GET' -Path '/api/v1/elder/profile/me' `
        -BearerToken $familyToken -Client $familyClientContext.Client
    $familyChildListAfterTargets = @($familyChildListAfterSwitchJson.data | Where-Object { [long]$_.elderId -eq $script:syntheticFamilyElderId })
    $familyChildScopeAfter = Get-IsolatedTestSideEffectSnapshot
    $familyIdentityFactsAfter = Get-FamilyIdentityFacts -UserId $script:syntheticFamilyUserId `
        -ElderId $script:syntheticFamilyElderId -TicketId $familyTicketId
    Confirm-Chain '切回 CHILD 后亲情关系范围保留且不继承 ELDER 本人范围' `
        ([string]$familySwitchToChildJson.data.activeRole -eq 'CHILD' -and $familyChildListAfterTargets.Count -eq 1 -and
            $familyProfileAfterSwitch.StatusCode -eq 403 -and $familyChildScopeBefore -eq $familyChildScopeAfter -and
            $familyIdentityFactsBefore -eq $familyIdentityFactsAfter) `
        'Switching back to CHILD changed the independent family-link or elder-identity fixture facts.'

    $careHttpContext = New-TestHttpClient
    $careLoginResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $careHttpContext.Client -Body @{
        phone = $staffPhone; password = $staffPassword; clientType = 'MINIAPP'
    }
    $careLoginJson = Assert-OkApiResponse $careLoginResponse 'Synthetic care-worker login'
    $careToken = [string]$careLoginJson.data.token
    $careSwitchResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/switch-role' -BearerToken $careToken `
        -Client $careHttpContext.Client -Body @{ targetRoleCode = 'CARE_WORKER' }
    $careSwitchJson = Assert-OkApiResponse $careSwitchResponse 'Synthetic switch to CARE_WORKER'
    $careCurrentResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $careToken -Client $careHttpContext.Client
    $careCurrentJson = Assert-OkApiResponse $careCurrentResponse 'Synthetic CARE_WORKER current-user read'
    Confirm-Chain '合成员工可切换到社区内 CARE_WORKER 会话' `
        ([string]$careLoginJson.data.accountKind -eq 'STAFF' -and
            [string]$careSwitchJson.data.activeRole -eq 'CARE_WORKER' -and
            [string]$careCurrentJson.data.currentRole -eq 'CARE_WORKER' -and
            [string]$careCurrentJson.data.communityId -eq '1') `
        'The synthetic staff session did not enter the expected same-community CARE_WORKER scope.'

    $careWorkOrderId = New-SyntheticWorkOrder -Status 'DISPATCHED' -AssigneeId $staffTargetUserId -ElderId 1 -CommunityId 1
    $careNonAssigneeOrderId = New-SyntheticWorkOrder -Status 'DISPATCHED' -AssigneeId $workerAccount.UserId -ElderId 1 -CommunityId 1
    $careReassignedOrderId = New-SyntheticWorkOrder -Status 'DISPATCHED' -AssigneeId $staffTargetUserId -ElderId 1 -CommunityId 1
    $careCrossCommunityOrderId = New-SyntheticWorkOrder -Status 'DISPATCHED' -AssigneeId $staffTargetUserId `
        -ElderId $crossCommunityElderId -CommunityId 2
    $terminalCareOrderIds = [ordered]@{}
    foreach ($terminalStatus in @('CLOSED', 'VOIDED', 'TERMINATED_PRE_ACCEPTANCE', 'SERVICE_STOPPED')) {
        $terminalCareOrderIds[$terminalStatus] = New-SyntheticWorkOrder -Status $terminalStatus `
            -AssigneeId $staffTargetUserId -ElderId 1 -CommunityId 1
    }
    $careEvidencePath = Join-Path $tempRoot 'system-http-care-evidence.png'
    $careEvidenceBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l5sAAAAASUVORK5CYII=')
    [IO.File]::WriteAllBytes($careEvidencePath, $careEvidenceBytes)

    $missingOrderFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $missingOrderFileCountBefore = Get-StorageFileCount
    $missingOrderUpload = Invoke-PhotoUpload -Path $careEvidencePath -BearerToken $careToken -Client $careHttpContext.Client
    $missingOrderFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $missingOrderFileCountAfter = Get-StorageFileCount
    $missingOrderFactsUnchanged = $missingOrderFactsBefore -eq $missingOrderFactsAfter
    $missingOrderFilesUnchanged = $missingOrderFileCountBefore -eq $missingOrderFileCountAfter
    Confirm-Chain '护工缺少 workOrderId 上传返回 HTTP 422 且无 DB/session/audit/文件副作用' `
        ($missingOrderUpload.StatusCode -eq 422 -and $missingOrderFactsUnchanged -and
            $missingOrderFilesUnchanged) `
        "A care-worker upload without workOrderId was not rejected without writes (HTTP $($missingOrderUpload.StatusCode), dbSnapshotUnchanged=$missingOrderFactsUnchanged, filesUnchanged=$missingOrderFilesUnchanged)."

    foreach ($terminalStatus in $terminalCareOrderIds.Keys) {
        $terminalId = [long]$terminalCareOrderIds[$terminalStatus]
        $terminalListResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/my-tasks?status=$terminalStatus" `
            -BearerToken $careToken -Client $careHttpContext.Client
        $terminalListJson = Assert-OkApiResponse $terminalListResponse "CARE_WORKER terminal list $terminalStatus"
        $terminalListed = @($terminalListJson.data | Where-Object { [long]$_.workOrderId -eq $terminalId }).Count -gt 0
        $terminalDetailResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$terminalId" `
            -BearerToken $careToken -Client $careHttpContext.Client
        $terminalFactsBefore = Get-IsolatedTestSideEffectSnapshot
        $terminalFileCountBefore = Get-StorageFileCount
        $terminalUpload = Invoke-PhotoUpload -Path $careEvidencePath -BearerToken $careToken `
            -WorkOrderId $terminalId -Client $careHttpContext.Client
        $terminalFactsAfter = Get-IsolatedTestSideEffectSnapshot
        $terminalFileCountAfter = Get-StorageFileCount
        Confirm-Chain "护工终态 $terminalStatus 即使保留承接人也不可列出/读取/上传" `
            (-not $terminalListed -and $terminalDetailResponse.StatusCode -eq 404 -and $terminalUpload.StatusCode -eq 404 -and
                $terminalFactsBefore -eq $terminalFactsAfter -and $terminalFileCountBefore -eq $terminalFileCountAfter) `
            "A terminal $terminalStatus care order remained visible or its denied upload wrote isolated state/file data."
    }

    $nonAssigneeDetail = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careNonAssigneeOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $nonAssigneeFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $nonAssigneeFileCountBefore = Get-StorageFileCount
    $nonAssigneeUpload = Invoke-PhotoUpload -Path $careEvidencePath -BearerToken $careToken `
        -WorkOrderId $careNonAssigneeOrderId -Client $careHttpContext.Client
    $nonAssigneeFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $nonAssigneeFileCountAfter = Get-StorageFileCount
    Confirm-Chain '护工不可读取或上传非本人承接工单且无副作用/文件写入' `
        ($nonAssigneeDetail.StatusCode -eq 404 -and $nonAssigneeUpload.StatusCode -eq 404 -and
            $nonAssigneeFactsBefore -eq $nonAssigneeFactsAfter -and $nonAssigneeFileCountBefore -eq $nonAssigneeFileCountAfter) `
        'A care worker accessed another worker order or its denied upload wrote isolated state/file data.'

    $reassignResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$careReassignedOrderId/dispatch" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-CARE-REASSIGN-' + [Guid]::NewGuid().ToString('N')) `
        -Body @{ assigneeUserId = $workerAccount.UserId; reason = '隔离 HTTP 护工工单改派验收' }
    $null = Assert-OkApiResponse $reassignResponse 'Administrator reassignment from care worker to grid worker'
    $reassignedAssignee = Invoke-AppDatabaseQuery "SELECT current_assignee_id FROM work_order WHERE work_order_id = $careReassignedOrderId;"
    $reassignedCareDetail = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careReassignedOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $reassignedFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $reassignedFileCountBefore = Get-StorageFileCount
    $reassignedUpload = Invoke-PhotoUpload -Path $careEvidencePath -BearerToken $careToken `
        -WorkOrderId $careReassignedOrderId -Client $careHttpContext.Client
    $reassignedFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $reassignedFileCountAfter = Get-StorageFileCount
    Confirm-Chain '工单经管理员改派后原护工不可读取/上传且不写文件' `
        ($reassignedAssignee.ExitCode -eq 0 -and $reassignedAssignee.StdOut.Trim() -eq [string]$workerAccount.UserId -and
            $reassignedCareDetail.StatusCode -eq 404 -and $reassignedUpload.StatusCode -eq 404 -and
            $reassignedFactsBefore -eq $reassignedFactsAfter -and $reassignedFileCountBefore -eq $reassignedFileCountAfter) `
        'A reassigned care worker retained access or the denied upload wrote isolated state/file data.'

    $crossCommunityCareDetail = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careCrossCommunityOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $crossCareFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $crossCareFileCountBefore = Get-StorageFileCount
    $crossCommunityCareUpload = Invoke-PhotoUpload -Path $careEvidencePath -BearerToken $careToken `
        -WorkOrderId $careCrossCommunityOrderId -Client $careHttpContext.Client
    $crossCareFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $crossCareFileCountAfter = Get-StorageFileCount
    Confirm-Chain '护工跨社区工单读取/上传均拒绝且无 DB/session/audit/文件副作用' `
        ($crossCommunityCareDetail.StatusCode -eq 404 -and $crossCommunityCareUpload.StatusCode -eq 404 -and
            $crossCareFactsBefore -eq $crossCareFactsAfter -and $crossCareFileCountBefore -eq $crossCareFileCountAfter) `
        'A care worker accessed a different-community order or its denied upload wrote isolated state/file data.'

    $careArchiveFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $careArchiveFileCountBefore = Get-StorageFileCount
    $careArchiveList = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives' -BearerToken $careToken -Client $careHttpContext.Client
    $careArchiveDetail = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/1' -BearerToken $careToken -Client $careHttpContext.Client
    $careDashboard = Invoke-Api -Method 'GET' -Path '/api/v1/dashboard/community/summary' -BearerToken $careToken -Client $careHttpContext.Client
    $careArchiveFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $careArchiveFileCountAfter = Get-StorageFileCount
    Confirm-Chain '护工档案列表/档案详情/社区看板均返回 HTTP 403 且无状态或文件写入' `
        ($careArchiveList.StatusCode -eq 403 -and $careArchiveDetail.StatusCode -eq 403 -and $careDashboard.StatusCode -eq 403 -and
            $careArchiveFactsBefore -eq $careArchiveFactsAfter -and $careArchiveFileCountBefore -eq $careArchiveFileCountAfter) `
        'A care worker reached community archive/dashboard data or changed isolated state/file data.'

    $careMyTasksResponse = Invoke-Api -Method 'GET' -Path '/api/v1/work-orders/my-tasks' -BearerToken $careToken -Client $careHttpContext.Client
    $careMyTasksJson = Assert-OkApiResponse $careMyTasksResponse 'CARE_WORKER assigned work-order list'
    $careListedActiveOrder = @($careMyTasksJson.data | Where-Object { [long]$_.workOrderId -eq $careWorkOrderId })
    $careOrderDetailResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careWorkOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $careOrderDetailJson = Assert-OkApiResponse $careOrderDetailResponse 'CARE_WORKER assigned work-order detail'
    Confirm-Chain '护工仅在本人待办列表读取当前分配工单的执行字段' `
        ($careListedActiveOrder.Count -eq 1 -and [long]$careOrderDetailJson.data.workOrderId -eq $careWorkOrderId -and
            [string]$careOrderDetailJson.data.status -eq 'DISPATCHED' -and
            $null -eq $careOrderDetailJson.data.elderId -and $null -eq $careOrderDetailJson.data.contactPhone) `
        'The current care worker did not read the assigned order or its response contained unrelated elder fields.'

    $careAcceptResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$careWorkOrderId/accept" `
        -BearerToken $careToken -IdempotencyKey ('SYSHTTP-CARE-ACCEPT-' + [Guid]::NewGuid().ToString('N')) `
        -Client $careHttpContext.Client
    $null = Assert-OkApiResponse $careAcceptResponse 'CARE_WORKER accept assigned work order'
    $careAcceptedDetail = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careWorkOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $careAcceptedJson = Assert-OkApiResponse $careAcceptedDetail 'CARE_WORKER accepted work-order readback'
    Confirm-Chain '护工接单后工单状态为 ACCEPTED' ([string]$careAcceptedJson.data.status -eq 'ACCEPTED') `
        'The assigned care order did not transition to ACCEPTED.'

    $careStartResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$careWorkOrderId/start" `
        -BearerToken $careToken -IdempotencyKey ('SYSHTTP-CARE-START-' + [Guid]::NewGuid().ToString('N')) `
        -Client $careHttpContext.Client
    $null = Assert-OkApiResponse $careStartResponse 'CARE_WORKER start assigned work order'
    $careProcessingDetail = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careWorkOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $careProcessingJson = Assert-OkApiResponse $careProcessingDetail 'CARE_WORKER processing work-order readback'
    Confirm-Chain '护工开始服务后工单状态为 PROCESSING' ([string]$careProcessingJson.data.status -eq 'PROCESSING') `
        'The assigned care order did not transition to PROCESSING.'

    $careFileCountBefore = Get-StorageFileCount
    $careUploadResponse = Invoke-PhotoUpload -Path $careEvidencePath -BearerToken $careToken `
        -WorkOrderId $careWorkOrderId -Client $careHttpContext.Client
    $careUploadJson = Assert-OkApiResponse $careUploadResponse 'CARE_WORKER order-bound evidence upload'
    $carePhotoUrl = [string]$careUploadJson.data.url
    $careFileCountAfter = Get-StorageFileCount
    $carePhotoView = if ($carePhotoUrl) { Invoke-Api -Method 'GET' -Path $carePhotoUrl -Client $careHttpContext.Client } else { $null }
    Confirm-Chain '护工使用 workOrderId 成功上传凭据且文件写入隔离 APP_STORAGE_ROOT' `
        ($carePhotoUrl -match '^/api/v1/files/view/[^/]+\.png$' -and
            [string]$careUploadJson.data.filename -match '^[A-Fa-f0-9]+\.png$' -and
            $careFileCountAfter -eq ($careFileCountBefore + 1) -and $null -ne $carePhotoView -and $carePhotoView.StatusCode -eq 200) `
        'A valid order-bound care-worker upload did not create exactly one readable file under isolated storage.'

    $careExecutionResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$careWorkOrderId/execution" `
        -BearerToken $careToken -IdempotencyKey ('SYSHTTP-CARE-EXECUTION-' + [Guid]::NewGuid().ToString('N')) `
        -Client $careHttpContext.Client -Body @{
            resultSummary = '隔离护工 HTTP 验收已完成。'; photoUrls = @($carePhotoUrl); isExemptPhoto = $false
        }
    $null = Assert-OkApiResponse $careExecutionResponse 'CARE_WORKER execution submission'
    $careExecutionDetail = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$careWorkOrderId" `
        -BearerToken $careToken -Client $careHttpContext.Client
    $careExecutionJson = Assert-OkApiResponse $careExecutionDetail 'CARE_WORKER execution readback'
    Confirm-Chain '护工提交执行摘要与上传照片后进入 PENDING_RESULT_REVIEW' `
        ([string]$careExecutionJson.data.status -eq 'PENDING_RESULT_REVIEW' -and
            @($careExecutionJson.data.photoUrls | Where-Object { $_ -eq $carePhotoUrl }).Count -eq 1) `
        'The care worker execution result did not persist the uploaded evidence and pending-review status.'

    $webUnsafePath = '/api/v1/admin/reservations/0/confirm'
    $missingCsrfResponse = Invoke-Api -Method 'POST' -Path $webUnsafePath
    Confirm-Chain 'Web Cookie 写请求缺少 CSRF Token 时返回 HTTP 403' ($missingCsrfResponse.StatusCode -eq 403) `
        'A cookie-authenticated unsafe request without CSRF did not return HTTP 403.'

    $activeChildElderRead = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/1' -BearerToken $childAccount.Token
    $activeChildElderJson = Assert-OkApiResponse $activeChildElderRead 'Active child bound elder read'
    Confirm-Chain '有效子女通过 HTTP 读取已绑定老人档案' `
        ([string]$activeChildElderJson.data.elderId -eq '1') `
        'The active child could not read the elder attached by an active family link.'

    $pendingReadFacts = Get-IsolatedBusinessFacts
    $pendingReadResponse = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/1' -BearerToken $pendingChildAccount.Token
    $pendingReadAfterFacts = Get-IsolatedBusinessFacts
    Confirm-Chain '待审核子女读取老人档案返回 HTTP 404' ($pendingReadResponse.StatusCode -eq 404) `
        'A pending family relationship did not receive the documented inaccessible-resource HTTP 404.'
    Confirm-Chain '待审核子女档案读取没有业务数据副作用' ($pendingReadFacts -eq $pendingReadAfterFacts) `
        'A denied pending-child read changed isolated business facts.'

    $pendingReservationFacts = Get-IsolatedBusinessFacts
    $pendingReservationDate = ([TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, [TimeZoneInfo]::FindSystemTimeZoneById('China Standard Time'))).AddDays(60).ToString('yyyy-MM-dd')
    $pendingReservationResponse = Invoke-Api -Method 'POST' -Path '/api/v1/service-reservations' `
        -BearerToken $pendingChildAccount.Token -IdempotencyKey ('SYSHTTP-PENDING-RESERVATION-' + [Guid]::NewGuid().ToString('N')) -Body @{
        elderId = 1
        serviceType = 'DAILY_CARE'
        serviceDate = $pendingReservationDate
        slotCode = 'MORNING'
        serviceAddress = '隔离负例测试地址'
        specialNotes = 'PENDING-AUTH-NEGATIVE-' + [Guid]::NewGuid().ToString('N')
    }
    $pendingReservationAfterFacts = Get-IsolatedBusinessFacts
    Confirm-Chain '待审核子女创建预约返回 HTTP 404' ($pendingReservationResponse.StatusCode -eq 404) `
        'A pending child relationship was allowed to create a reservation or returned the wrong HTTP status.'
    Confirm-Chain '待审核子女创建预约返回 HTTP 404 且业务事实未变化' ($pendingReservationFacts -eq $pendingReservationAfterFacts) `
        'A denied pending-child reservation changed reservations, work orders, notifications, or relationship/archive facts.'

    $frozenFacts = Get-IsolatedBusinessFacts
    $frozenResponse = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/3' -BearerToken $frozenChildAccount.Token
    $frozenAfterFacts = Get-IsolatedBusinessFacts
    $frozenJson = $null
    try { $frozenJson = ConvertFrom-Json -InputObject $frozenResponse.Content } catch { }
    Confirm-Chain '冻结亲情访问返回 HTTP 403 KINSHIP_NOT_ACTIVE' `
        ($frozenResponse.StatusCode -eq 403 -and $null -ne $frozenJson -and $frozenJson.code -eq 'KINSHIP_NOT_ACTIVE') `
        'A frozen family relationship did not receive HTTP 403 KINSHIP_NOT_ACTIVE.'
    Confirm-Chain '冻结亲情拒绝没有业务数据副作用' ($frozenFacts -eq $frozenAfterFacts) `
        'A denied frozen-child read changed isolated business facts.'

    $unboundFacts = Get-IsolatedBusinessFacts
    $unboundResponse = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/2' -BearerToken $childAccount.Token
    $unboundAfterFacts = Get-IsolatedBusinessFacts
    Confirm-Chain '有效子女读取同社区未绑定老人返回 HTTP 404' ($unboundResponse.StatusCode -eq 404) `
        'An active child was able to read a same-community elder without a family link.'
    Confirm-Chain '同社区未绑定老人拒绝没有业务数据副作用' ($unboundFacts -eq $unboundAfterFacts) `
        'A denied unbound-elder read changed isolated business facts.'

    $communityFacts = Get-IsolatedBusinessFacts
    $adminCrossRead = Invoke-Api -Method 'GET' -Path "/api/v1/elder/archives/$crossCommunityElderId"
    $workerCrossRead = Invoke-Api -Method 'GET' -Path "/api/v1/elder/archives/$crossCommunityElderId" -BearerToken $workerAccount.Token
    $communityAfterReads = Get-IsolatedBusinessFacts
    Confirm-Chain '社区管理员和网格员读取另一社区老人均返回 HTTP 404' `
        ($adminCrossRead.StatusCode -eq 404 -and $workerCrossRead.StatusCode -eq 404 -and $communityFacts -eq $communityAfterReads) `
        'A community-1 staff session could read community-2 elder data or caused a business side effect.'

    $crossWriteFacts = Get-IsolatedBusinessFacts
    $crossTargetBefore = Invoke-AppDatabaseQuery "SELECT CONCAT(status, ':', row_version) FROM elder_profile WHERE elder_id = $crossCommunityElderId;"
    $crossStatusResponse = Invoke-Api -Method 'POST' -Path "/api/v1/elder/archives/$crossCommunityElderId/status" -CsrfToken $adminAccount.Csrf -Body @{
        status = 'INACTIVE'
        reason = '隔离跨社区越权负例'
    }
    $crossTargetAfter = Invoke-AppDatabaseQuery "SELECT CONCAT(status, ':', row_version) FROM elder_profile WHERE elder_id = $crossCommunityElderId;"
    $crossWriteAfterFacts = Get-IsolatedBusinessFacts
    Confirm-Chain '跨社区管理员写入返回 HTTP 404 且数据库业务事实未变化' `
        ($crossStatusResponse.StatusCode -eq 404 -and $crossTargetBefore.ExitCode -eq 0 -and
            $crossTargetAfter.ExitCode -eq 0 -and $crossTargetBefore.StdOut.Trim() -eq $crossTargetAfter.StdOut.Trim() -and
            $crossWriteFacts -eq $crossWriteAfterFacts) `
        'A cross-community status mutation was not rejected without changing its target or business facts.'

    $adminLocalRead = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/1'
    $workerLocalRead = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/1' -BearerToken $workerAccount.Token
    $adminLocalJson = $null
    $workerLocalJson = $null
    try { $adminLocalJson = ConvertFrom-Json -InputObject $adminLocalRead.Content } catch { }
    try { $workerLocalJson = ConvertFrom-Json -InputObject $workerLocalRead.Content } catch { }
    Confirm-Chain '同社区管理员和网格员仍可读取本社区老人档案' `
        ($adminLocalRead.StatusCode -eq 200 -and $workerLocalRead.StatusCode -eq 200 -and
            [string]$adminLocalJson.data.elderId -eq '1' -and [string]$workerLocalJson.data.elderId -eq '1') `
        'A same-community staff positive read failed after cross-community denial checks.'

    $pendingSessionCurrent = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $pendingChildAccount.Token
    Confirm-Chain '待审核子女未过期会话可通过当前用户接口' ($pendingSessionCurrent.StatusCode -eq 200) `
        'The pending-child test session was not valid before setting its absolute expiry in the isolated DB.'
    $pendingIdleSession = Get-SessionStateForToken -UserId 5 -Token $pendingChildAccount.Token
    $pendingIdleSessionId = ($pendingIdleSession -split '\|', 5)[0]
    $expirePendingIdle = Invoke-AppDatabaseQuery "UPDATE auth_session SET last_activity_at = DATE_SUB(NOW(3), INTERVAL 8 DAY) WHERE session_id = '$pendingIdleSessionId' AND revoked_at IS NULL; SELECT ROW_COUNT();"
    Confirm-That ($expirePendingIdle.ExitCode -eq 0 -and $expirePendingIdle.StdOut.Trim() -eq '1') `
        'Could not set exactly one MINIAPP test session beyond its seven-day idle limit.'
    $idleSessionFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $idleSessionStateBefore = Get-SessionStateForToken -UserId 5 -Token $pendingChildAccount.Token
    $idleSessionResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $pendingChildAccount.Token
    $idleSessionFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $idleSessionStateAfter = Get-SessionStateForToken -UserId 5 -Token $pendingChildAccount.Token
    Confirm-Chain '空闲超过会话上限的旧 Bearer 返回 HTTP 401 且会话事实不变' `
        ($idleSessionResponse.StatusCode -eq 401 -and $idleSessionFactsBefore -eq $idleSessionFactsAfter -and
            $idleSessionStateBefore -eq $idleSessionStateAfter) `
        'An idle-expired MINIAPP session was accepted or the rejected request changed session, audit, notification, or business facts.'

    $pendingAbsoluteLogin = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Body @{
        phone = $pendingChildAccount.Phone
        password = $pendingChildAccount.Password
        clientType = 'MINIAPP'
    }
    $pendingAbsoluteJson = Assert-OkApiResponse $pendingAbsoluteLogin 'Pending child absolute-expiry test login'
    $pendingAbsoluteToken = [string]$pendingAbsoluteJson.data.token
    $pendingAbsoluteCurrent = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $pendingAbsoluteToken
    Confirm-Chain '待审核子女新会话在绝对过期前可认证' ($pendingAbsoluteCurrent.StatusCode -eq 200) `
        'The pending-child absolute-expiry test session was not valid before setting its deadline.'
    $pendingAbsoluteSession = Get-SessionStateForToken -UserId 5 -Token $pendingAbsoluteToken
    $pendingAbsoluteSessionId = ($pendingAbsoluteSession -split '\|', 5)[0]
    $expirePendingSession = Invoke-AppDatabaseQuery "UPDATE auth_session SET absolute_expires_at = '2000-01-01 00:00:00.000' WHERE session_id = '$pendingAbsoluteSessionId' AND revoked_at IS NULL; SELECT ROW_COUNT();"
    Confirm-That ($expirePendingSession.ExitCode -eq 0 -and $expirePendingSession.StdOut.Trim() -eq '1') `
        'Could not set exactly one pending-child test session to an expired absolute deadline.'
    $expiredSessionFacts = Get-IsolatedTestSideEffectSnapshot
    $expiredSessionStateBefore = Get-SessionStateForToken -UserId 5 -Token $pendingAbsoluteToken
    $expiredSessionResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $pendingAbsoluteToken
    $expiredSessionAfterFacts = Get-IsolatedTestSideEffectSnapshot
    $expiredSessionStateAfter = Get-SessionStateForToken -UserId 5 -Token $pendingAbsoluteToken
    Confirm-Chain '过期绝对期限会话访问返回 HTTP 401' ($expiredSessionResponse.StatusCode -eq 401) `
        'An absolute-expired authentication session was not rejected with HTTP 401.'
    Confirm-Chain '过期会话拒绝没有业务/会话/通知/审计副作用' `
        ($expiredSessionFacts -eq $expiredSessionAfterFacts -and $expiredSessionStateBefore -eq $expiredSessionStateAfter) `
        'An absolute-expired session request changed isolated business, session, notification, or audit facts.'

    $pendingReloginResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Body @{
        phone = $pendingChildAccount.Phone
        password = $pendingChildAccount.Password
        clientType = 'MINIAPP'
    }
    $pendingReloginJson = Assert-OkApiResponse $pendingReloginResponse 'Pending child role-revocation test login'
    $pendingRoleToken = [string]$pendingReloginJson.data.token
    $revokePendingRole = Invoke-AppDatabaseQuery "UPDATE sys_user_role SET status = 'REVOKED', revoked_at = NOW(3), revoked_by = 1, revoke_reason = 'SYSHTTP_TEST' WHERE user_id = 5 AND role_code = 'CHILD' AND status = 'ACTIVE'; SELECT ROW_COUNT();"
    Confirm-That ($revokePendingRole.ExitCode -eq 0 -and $revokePendingRole.StdOut.Trim() -eq '1') `
        'Could not revoke exactly one pending-child test role assignment in the isolated DB.'
    $revokedRoleFacts = Get-IsolatedBusinessFacts
    $revokedRoleResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $pendingRoleToken
    $revokedRoleAfterFacts = Get-IsolatedBusinessFacts
    Confirm-Chain '撤销当前角色分配后旧会话返回 HTTP 401' ($revokedRoleResponse.StatusCode -eq 401) `
        'An authentication session with no currently active role assignment was not rejected with HTTP 401.'
    Confirm-Chain '撤销角色会话拒绝没有业务数据副作用' ($revokedRoleFacts -eq $revokedRoleAfterFacts) `
        'A revoked-role session request changed isolated business facts.'
    $restorePendingRole = Invoke-AppDatabaseQuery @'
UPDATE auth_session SET revoked_at = COALESCE(revoked_at, NOW(3)), revoke_reason = COALESCE(revoke_reason, 'ROLE_CHANGED')
WHERE user_id = 5;
UPDATE sys_user_role
   SET status = 'ACTIVE', revoked_at = NULL, revoked_by = NULL, revoke_reason = NULL
 WHERE user_id = 5 AND role_code = 'CHILD';
SELECT CONCAT(
    (SELECT COUNT(*) FROM sys_user_role WHERE user_id = 5 AND role_code = 'CHILD' AND status = 'ACTIVE'),
    ':',
    (SELECT COUNT(*) FROM auth_session WHERE user_id = 5 AND revoked_at IS NULL)
);
'@
    Confirm-That ($restorePendingRole.ExitCode -eq 0 -and $restorePendingRole.StdOut.Trim() -eq '1:0') `
        'Could not restore the seed role while ensuring all role-matrix test sessions remained revoked.'

    $elderProfileResponse = Invoke-Api -Method 'GET' -Path '/api/v1/elder/profile/me' -BearerToken $elderAccount.Token
    $elderProfileJson = Assert-OkApiResponse $elderProfileResponse 'Elder self-profile request'
    Confirm-Chain '老人本人资料接口读回 elderId=1' ([string]$elderProfileJson.data.elderId -eq '1') `
        'The elder self-profile endpoint did not return the synthetic elder identity.'

    $medicationResponse = Invoke-Api -Method 'GET' -Path '/api/v1/medication/tasks/today/1' -BearerToken $elderAccount.Token
    $medicationJson = Assert-OkApiResponse $medicationResponse 'Elder medication box request'
    $medicationList = @($medicationJson.data)
    Confirm-Chain '老人今日药盒接口返回成功列表' ($medicationJson.data -is [System.Array]) `
        'The elder medication box endpoint did not return a task list.'
    $actionableMedicationTasks = @($medicationList | Where-Object { $_.status -ne 'CONFIRMED' })
    $medicationStatuses = @($medicationList | ForEach-Object { [string]$_.status }) -join ', '
    Confirm-Chain '今日药盒存在可打卡任务' ($actionableMedicationTasks.Count -gt 0) `
        "GET /api/v1/medication/tasks/today/1 returned no task with status PENDING or OVER_WINDOW_UNCONFIRMED; observed statuses: $medicationStatuses."
    $medicationTask = $actionableMedicationTasks[0]
    $medicationIdempotencyKey = 'SYSHTTP-MEDICATION-' + [Guid]::NewGuid().ToString('N')
    $medicationCheckinBody = @{
        taskId = [long]$medicationTask.taskId
        checkinMode = 'SELF_CONFIRMED'
    }
    $medicationCheckinResponse = Invoke-Api -Method 'POST' -Path '/api/v1/medication/tasks/checkin' `
        -BearerToken $elderAccount.Token -IdempotencyKey $medicationIdempotencyKey -Body $medicationCheckinBody
    $medicationCheckinJson = Assert-OkApiResponse $medicationCheckinResponse 'Elder medication task check-in'
    $medicationIdempotencyBeforeReplay = Invoke-AppDatabaseQuery @"
SELECT CONCAT(record_state, '|', response_status, '|', LEFT(SHA2(response_body, 256), 12), '|',
              TIMESTAMPDIFF(SECOND, NOW(3), expires_at), '|', RIGHT(trace_id, 8))
  FROM idempotency_record
 WHERE idempotency_key = '$medicationIdempotencyKey'
   AND operation_scope = 'POST /api/v1/medication/tasks/checkin';
"@
    Confirm-Chain '老人将今日一条未确认药盒任务打卡为 CONFIRMED' `
        ([string]$medicationCheckinJson.data.status -eq 'CONFIRMED') `
        'POST /api/v1/medication/tasks/checkin did not transition the selected task to CONFIRMED.'
    $medicationCheckinReplay = Invoke-Api -Method 'POST' -Path '/api/v1/medication/tasks/checkin' `
        -BearerToken $elderAccount.Token -IdempotencyKey $medicationIdempotencyKey -Body $medicationCheckinBody
    $medicationIdempotencyState = Invoke-AppDatabaseQuery @"
SELECT GROUP_CONCAT(CONCAT(operation_scope, ':', record_state, ':', response_status, ':', LEFT(SHA2(response_body, 256), 12)) ORDER BY record_id SEPARATOR ',')
  FROM idempotency_record
 WHERE idempotency_key = '$medicationIdempotencyKey';
"@
    $medicationCheckinCount = Invoke-AppDatabaseQuery "SELECT COUNT(*) FROM medication_checkin WHERE idempotency_key = '$medicationIdempotencyKey';"
    $medicationFirstJson = try { ConvertFrom-Json -InputObject $medicationCheckinResponse.Content } catch { $null }
    $medicationReplayJson = try { ConvertFrom-Json -InputObject $medicationCheckinReplay.Content } catch { $null }
    $medicationFirstCode = if ($medicationFirstJson) { [string]$medicationFirstJson.code } else { 'INVALID_JSON' }
    $medicationReplayCode = if ($medicationReplayJson) { [string]$medicationReplayJson.code } else { 'INVALID_JSON' }
    $medicationFirstTrace = if ($medicationFirstJson -and $medicationFirstJson.traceId) { ([string]$medicationFirstJson.traceId).Substring(([string]$medicationFirstJson.traceId).Length - 8) } else { 'NONE' }
    $medicationReplayTrace = if ($medicationReplayJson -and $medicationReplayJson.traceId) { ([string]$medicationReplayJson.traceId).Substring(([string]$medicationReplayJson.traceId).Length - 8) } else { 'NONE' }
    $medicationTraceEqual = ($medicationFirstJson -and $medicationReplayJson -and [string]$medicationFirstJson.traceId -ceq [string]$medicationReplayJson.traceId)
    $medicationBodyDiff = Get-RedactedJsonDiff -FirstJson $medicationCheckinResponse.Content -SecondJson $medicationCheckinReplay.Content
    $medicationFirstBodyHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($medicationCheckinResponse.Content))).Substring(0, 12)
    $medicationReplayBodyHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($medicationCheckinReplay.Content))).Substring(0, 12)
    $medicationFirstContentType = [string]$medicationCheckinResponse.Response.Content.Headers.ContentType
    $medicationReplayContentType = [string]$medicationCheckinReplay.Response.Content.Headers.ContentType
    Confirm-Chain '药盒打卡通过 Idempotency-Key 重放原状态码与响应正文' `
        ($medicationCheckinReplay.StatusCode -eq $medicationCheckinResponse.StatusCode -and
            $medicationCheckinReplay.Content -ceq $medicationCheckinResponse.Content) `
        ("The medication retry differed (status {0}/{1}, bodyEqual={2}, bodyHash={3}/{4}, contentType={5}/{6}, code={7}/{8}, traceEqual={9}({10}/{11}), diff={12}, before={13}, after={14}, checkins={15})." -f `
            $medicationCheckinResponse.StatusCode, $medicationCheckinReplay.StatusCode,
            ($medicationCheckinReplay.Content -ceq $medicationCheckinResponse.Content),
            $medicationFirstBodyHash, $medicationReplayBodyHash, $medicationFirstContentType, $medicationReplayContentType,
            $medicationFirstCode, $medicationReplayCode, $medicationTraceEqual, $medicationFirstTrace, $medicationReplayTrace,
            $medicationBodyDiff,
            $medicationIdempotencyBeforeReplay.StdOut.Trim(), $medicationIdempotencyState.StdOut.Trim(),
            $medicationCheckinCount.StdOut.Trim())

    $boundEldersResponse = Invoke-Api -Method 'GET' -Path '/api/v1/family/links/my-elders' -BearerToken $childAccount.Token
    $boundEldersJson = Assert-OkApiResponse $boundEldersResponse 'Child bound-elder request'
    $hasSyntheticElder = @($boundEldersJson.data | Where-Object { [string]$_.elderId -eq '1' }).Count -gt 0
    Confirm-Chain '有效子女账号读回 elderId=1 的绑定关系' $hasSyntheticElder `
        'The active child account did not return the expected bound elder.'

    $catalogResponse = Invoke-Api -Method 'GET' -Path '/api/v1/service-catalogs'
    $catalogJson = Assert-OkApiResponse $catalogResponse 'Active service-catalog request'
    $activeCatalogs = @($catalogJson.data | Where-Object { $_.status -eq 'ACTIVE' -and $_.serviceType })
    Confirm-That ($activeCatalogs.Count -gt 0) 'No active service catalog is available for the isolated appointment flow.'
    $selectedCatalog = $activeCatalogs[0]
    $dateBase = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, [TimeZoneInfo]::FindSystemTimeZoneById('China Standard Time'))
    $serviceDate = $dateBase.AddDays((Get-Random -Minimum 30 -Maximum 366)).ToString('yyyy-MM-dd')
    $slotCode = if ((Get-Random -Minimum 0 -Maximum 2) -eq 0) { 'MORNING' } else { 'AFTERNOON' }
    $address = if ($elderProfileJson.data.address) { [string]$elderProfileJson.data.address } else { '隔离环境系统冒烟地址' }
    $reservationIdempotencyKey = 'SYSHTTP-RESERVATION-' + [Guid]::NewGuid().ToString('N')
    $reservationSpecialNotes = 'system-http-smoke-' + [Guid]::NewGuid().ToString('N')
    $reservationBody = @{
        elderId = 1
        serviceType = [string]$selectedCatalog.serviceType
        serviceDate = $serviceDate
        slotCode = $slotCode
        serviceAddress = $address
        specialNotes = $reservationSpecialNotes
    }
    $reservationResponse = Invoke-Api -Method 'POST' -Path '/api/v1/service-reservations' `
        -BearerToken $childAccount.Token -IdempotencyKey $reservationIdempotencyKey -Body $reservationBody
    $reservationJson = Assert-OkApiResponse $reservationResponse 'Child reservation create request'
    $reservationId = [long]$reservationJson.data
    Confirm-That ($reservationId -gt 0) 'The child reservation response did not contain a valid reservation ID.'

    Stop-TestApi
    $reservationApiPortReleased = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (-not (Test-TcpPortOpen $script:httpPort)) { $reservationApiPortReleased = $true; break }
        Start-Sleep -Milliseconds 250
    }
    Confirm-Chain '预约首次写入后 API 已停止且随机 HTTP 端口释放' $reservationApiPortReleased `
        'The first API process still owned its random HTTP port before the persistence replay check.'
    $script:apiProcess = Start-TestApi 'reservation-idempotency-replay'
    $null = Wait-ApiReady -Process $script:apiProcess -PhaseName 'reservation idempotency API restart'
    $reservationReplayResponse = Invoke-Api -Method 'POST' -Path '/api/v1/service-reservations' `
        -BearerToken $childAccount.Token -IdempotencyKey $reservationIdempotencyKey -Body $reservationBody
    $reservationIdempotencyState = Invoke-AppDatabaseQuery @"
SELECT CONCAT(record_state, '|', response_status, '|', JSON_VALID(response_body))
  FROM idempotency_record
 WHERE idempotency_key = '$reservationIdempotencyKey'
   AND operation_scope = 'POST /api/v1/service-reservations';
"@
    $reservationCountAfterReplay = Invoke-AppDatabaseQuery "SELECT COUNT(*) FROM service_reservation WHERE special_notes = '$reservationSpecialNotes';"
    $expectedReservationState = "COMPLETED|$($reservationResponse.StatusCode)|1"
    Confirm-Chain 'API 重启后同键预约重放原 HTTP 状态码与 JSON 正文' `
        ($reservationReplayResponse.StatusCode -eq $reservationResponse.StatusCode -and
            $reservationReplayResponse.Content -ceq $reservationResponse.Content -and
            $reservationIdempotencyState.ExitCode -eq 0 -and
            $reservationIdempotencyState.StdOut.Trim() -eq $expectedReservationState -and
            $reservationCountAfterReplay.ExitCode -eq 0 -and
            $reservationCountAfterReplay.StdOut.Trim() -eq '1') `
        'The restarted API did not replay the completed reservation response exactly once from the isolated database.'
    $reservationReadResponse = Invoke-Api -Method 'GET' -Path "/api/v1/service-reservations/$reservationId" -BearerToken $childAccount.Token
    $reservationReadJson = Assert-OkApiResponse $reservationReadResponse 'Child reservation readback'
    Confirm-Chain '子女创建唯一未来预约并读回待确认状态' `
        ([string]$reservationReadJson.data.status -eq 'PENDING_CONFIRMATION' -and [string]$reservationReadJson.data.elderId -eq '1') `
        'The child-created reservation did not read back in PENDING_CONFIRMATION for elderId=1.'

    $pendingResponse = Invoke-Api -Method 'GET' -Path '/api/v1/admin/reservations/pending'
    $pendingJson = Assert-OkApiResponse $pendingResponse 'Administrator pending reservations request'
    $pendingContainsReservation = @($pendingJson.data | Where-Object { [string]$_.reservationId -eq [string]$reservationId }).Count -gt 0
    Confirm-Chain '管理员待确认列表包含本次创建的预约' $pendingContainsReservation `
        'Administrator pending reservations did not include the child-created reservation.'

    $confirmResponse = Invoke-Api -Method 'POST' -Path "/api/v1/admin/reservations/$reservationId/confirm" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-RESERVATION-CONFIRM-' + [Guid]::NewGuid().ToString('N'))
    $confirmJson = Assert-OkApiResponse $confirmResponse 'Administrator reservation confirmation'
    $workOrderId = [long]$confirmJson.data
    Confirm-Chain '管理员通过 Web Cookie 与 CSRF 确认预约并生成工单' ($workOrderId -gt 0) `
        'Administrator confirmation did not return a generated work-order ID.'

    $confirmedReservationResponse = Invoke-Api -Method 'GET' -Path "/api/v1/service-reservations/$reservationId" -BearerToken $childAccount.Token
    $confirmedReservationJson = Assert-OkApiResponse $confirmedReservationResponse 'Confirmed reservation readback'
    $workOrderResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $childAccount.Token
    $workOrderJson = Assert-OkApiResponse $workOrderResponse 'Generated work-order readback'
    Confirm-Chain '读回预约为 CONFIRMED，关联工单为 PENDING_DISPATCH' `
        ([string]$confirmedReservationJson.data.status -eq 'CONFIRMED' -and
            [string]$confirmedReservationJson.data.associatedWorkOrderId -eq [string]$workOrderId -and
            [string]$confirmedReservationJson.data.associatedWorkOrderStatus -eq 'PENDING_DISPATCH' -and
            [string]$workOrderJson.data.status -eq 'PENDING_DISPATCH' -and
            [string]$workOrderJson.data.sourceType -eq 'RESERVATION') `
        'Reservation and generated work-order readback did not match the expected confirmed/pending-dispatch state.'

    $dispatchResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/dispatch" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-WORK-DISPATCH-' + [Guid]::NewGuid().ToString('N')) -Body @{
        assigneeUserId = $workerAccount.UserId
        reason = '隔离 HTTP 系统验收派单'
    }
    $null = Assert-OkApiResponse $dispatchResponse 'Administrator work-order dispatch'
    $dispatchedOrderResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $workerAccount.Token
    $dispatchedOrderJson = Assert-OkApiResponse $dispatchedOrderResponse 'Dispatched work-order readback'
    Confirm-Chain '管理员派单后工单读回 DISPATCHED' `
        ([string]$dispatchedOrderJson.data.status -eq 'DISPATCHED' -and
            [string]$dispatchedOrderJson.data.currentAssigneeId -eq [string]$workerAccount.UserId) `
        'POST /api/v1/work-orders/{id}/dispatch did not assign the order to the active community worker.'

    $acceptResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/accept" `
        -BearerToken $workerAccount.Token -IdempotencyKey ('SYSHTTP-WORK-ACCEPT-' + [Guid]::NewGuid().ToString('N'))
    $null = Assert-OkApiResponse $acceptResponse 'Grid-worker accept request'
    $acceptedOrderResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $workerAccount.Token
    $acceptedOrderJson = Assert-OkApiResponse $acceptedOrderResponse 'Accepted work-order readback'
    Confirm-Chain '网格员接单后状态为 ACCEPTED' `
        ([string]$acceptedOrderJson.data.status -eq 'ACCEPTED') `
        'POST /api/v1/work-orders/{id}/accept did not transition the assigned order to ACCEPTED.'
    $startServiceResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/start" `
        -BearerToken $workerAccount.Token -IdempotencyKey ('SYSHTTP-WORK-START-' + [Guid]::NewGuid().ToString('N'))
    $null = Assert-OkApiResponse $startServiceResponse 'Grid-worker start-service request'
    $processingOrderResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $workerAccount.Token
    $processingOrderJson = Assert-OkApiResponse $processingOrderResponse 'Processing work-order readback'
    Confirm-Chain '网格员开始服务后状态为 PROCESSING' `
        ([string]$processingOrderJson.data.status -eq 'PROCESSING') `
        'POST /api/v1/work-orders/{id}/start did not transition the accepted order to PROCESSING.'

    $evidencePath = Join-Path $tempRoot 'system-http-evidence.png'
    $evidenceBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l5sAAAAASUVORK5CYII=')
    [IO.File]::WriteAllBytes($evidencePath, $evidenceBytes)
    $uploadResponse = Invoke-PhotoUpload -Path $evidencePath -BearerToken $workerAccount.Token
    $uploadJson = Assert-OkApiResponse $uploadResponse 'Grid-worker evidence upload'
    $photoUrl = [string]$uploadJson.data.url
    $photoFilename = [string]$uploadJson.data.filename
    $photoViewResponse = if ($photoUrl) { Invoke-Api -Method 'GET' -Path $photoUrl } else { $null }
    Confirm-Chain '网格员上传现场照片并得到临时文件 URL' `
        ($photoUrl -match '^/api/v1/files/view/[^/]+\.png$' -and $photoFilename -match '^[A-Fa-f0-9]+\.png$' -and
            $null -ne $photoViewResponse -and $photoViewResponse.StatusCode -eq 200) `
        'POST /api/v1/files/upload failed to return a viewable PNG URL from the isolated application working directory.'

    $executionResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/execution" `
        -BearerToken $workerAccount.Token -IdempotencyKey ('SYSHTTP-WORK-EXECUTION-' + [Guid]::NewGuid().ToString('N')) -Body @{
        resultSummary = '已按预约完成上门照护与现场核验。'
        photoUrls = @($photoUrl)
        isExemptPhoto = $false
    }
    $null = Assert-OkApiResponse $executionResponse 'Grid-worker execution submission'
    $reviewPendingResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $childAccount.Token
    $reviewPendingJson = Assert-OkApiResponse $reviewPendingResponse 'Pending-review work-order readback'
    Confirm-Chain '网格员提交服务总结与照片后进入 PENDING_RESULT_REVIEW' `
        ([string]$reviewPendingJson.data.status -eq 'PENDING_RESULT_REVIEW' -and
            @($reviewPendingJson.data.photoUrls | Where-Object { $_ -eq $photoUrl }).Count -gt 0) `
        'POST /api/v1/work-orders/{id}/execution did not persist the summary photo and pending-review status.'

    $reviewResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/review" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-WORK-REVIEW-' + [Guid]::NewGuid().ToString('N')) -Body @{
        action = 'APPROVE'
        reviewComment = '现场履约凭据完整，审核通过。'
    }
    $null = Assert-OkApiResponse $reviewResponse 'Administrator work-order review'
    $closedOrderResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $childAccount.Token
    $closedOrderJson = Assert-OkApiResponse $closedOrderResponse 'Closed work-order readback'
    Confirm-Chain '管理员审核履约结果后工单关闭' ([string]$closedOrderJson.data.status -eq 'CLOSED') `
        'POST /api/v1/work-orders/{id}/review did not close the approved work order.'
    $completedReservationResponse = Invoke-Api -Method 'GET' -Path "/api/v1/service-reservations/$reservationId" -BearerToken $childAccount.Token
    $completedReservationJson = Assert-OkApiResponse $completedReservationResponse 'Completed reservation readback'
    Confirm-Chain '子女读回预约 COMPLETED 与关联工单 CLOSED' `
        ([string]$completedReservationJson.data.status -eq 'COMPLETED' -and
            [string]$completedReservationJson.data.associatedWorkOrderStatus -eq 'CLOSED' -and
            [string]$closedOrderJson.data.sourceType -eq 'RESERVATION') `
        'GET /api/v1/service-reservations/{id} did not read back COMPLETED with its CLOSED reservation work order.'

    $sosIdempotencyKey = 'SYSHTTP-SOS-' + [Guid]::NewGuid().ToString('N')
    $sosTriggerResponse = Invoke-Api -Method 'POST' -Path '/api/v1/emergency/sos/trigger' `
        -BearerToken $elderAccount.Token -IdempotencyKey $sosIdempotencyKey -Body @{
        pressDurationMs = 2500
        locationAddress = $address
    }
    $sosTriggerJson = Assert-OkApiResponse $sosTriggerResponse 'Elder SOS trigger'
    $sosId = [long]$sosTriggerJson.data.sosId
    $sosWorkOrderId = [long]$sosTriggerJson.data.workOrderId
    Confirm-Chain '老人触发 SOS 并原子生成求助工单' `
        ($sosId -gt 0 -and $sosWorkOrderId -gt 0 -and [string]$sosTriggerJson.data.status -eq 'ACCEPTED') `
        'POST /api/v1/emergency/sos/trigger did not return an accepted SOS and generated emergency work order.'

    $sosDispatchResponse = Invoke-Api -Method 'POST' -Path "/api/v1/emergency/sos/$sosId/dispatch" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-SOS-DISPATCH-' + [Guid]::NewGuid().ToString('N')) -Body @{
        assigneeId = $workerAccount.UserId
        dispatchReason = '隔离 HTTP 系统验收 SOS 调度'
    }
    $null = Assert-OkApiResponse $sosDispatchResponse 'Administrator SOS dispatch'
    $sosCloseResponse = Invoke-Api -Method 'POST' -Path "/api/v1/emergency/sos/$sosId/close" `
        -CsrfToken $adminAccount.Csrf -IdempotencyKey ('SYSHTTP-SOS-CLOSE-' + [Guid]::NewGuid().ToString('N')) -Body @{
        resultSummary = '已电话联系并现场确认老人安全，完成紧急协助处置。'
        isFalseAlarm = $false
    }
    $null = Assert-OkApiResponse $sosCloseResponse 'Administrator SOS close'
    $sosReadResponse = Invoke-Api -Method 'GET' -Path '/api/v1/emergency/sos/list?status=CLOSED'
    $sosReadJson = Assert-OkApiResponse $sosReadResponse 'Closed SOS readback'
    $closedSos = @($sosReadJson.data | Where-Object { [string]$_.sosId -eq [string]$sosId })
    $sosOrderReadResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$sosWorkOrderId" -BearerToken $elderAccount.Token
    $sosOrderReadJson = Assert-OkApiResponse $sosOrderReadResponse 'Closed SOS work-order readback'
    Confirm-Chain '管理员派发并处置 SOS 后求助与工单均 CLOSED' `
        ($closedSos.Count -eq 1 -and [string]$closedSos[0].workOrderId -eq [string]$sosWorkOrderId -and
            [string]$sosOrderReadJson.data.status -eq 'CLOSED' -and [string]$sosOrderReadJson.data.sourceType -eq 'SOS') `
        'POST /api/v1/emergency/sos/{id}/dispatch or /close did not leave both the SOS and generated work order closed.'

    $sosCancelTriggerResponse = Invoke-Api -Method 'POST' -Path '/api/v1/emergency/sos/trigger' `
        -BearerToken $elderAccount.Token -IdempotencyKey ('SYSHTTP-SOS-CANCEL-TRIGGER-' + [Guid]::NewGuid().ToString('N')) -Body @{
        pressDurationMs = 2500
        locationAddress = $address
    }
    $sosCancelTriggerJson = Assert-OkApiResponse $sosCancelTriggerResponse 'Elder SOS trigger for cancellation replay'
    $sosCancelId = [long]$sosCancelTriggerJson.data.sosId
    $sosCancelWorkOrderId = [long]$sosCancelTriggerJson.data.workOrderId
    Confirm-Chain '取消重放夹具已生成 SOS 与关联工单' `
        ($sosCancelId -gt 0 -and $sosCancelWorkOrderId -gt 0 -and [string]$sosCancelTriggerJson.data.status -eq 'ACCEPTED') `
        'The isolated cancellation replay fixture did not create an accepted SOS and its work order.'
    $sosCancelKey = 'SYSHTTP-SOS-CANCEL-' + [Guid]::NewGuid().ToString('N')
    $sosCancelBody = @{ cancelReason = '隔离 HTTP 幂等验收：老人确认误触' }
    $sosCancelResponse = Invoke-Api -Method 'POST' -Path "/api/v1/emergency/sos/$sosCancelId/cancel" `
        -BearerToken $elderAccount.Token -IdempotencyKey $sosCancelKey -Body $sosCancelBody
    $null = Assert-OkApiResponse $sosCancelResponse 'Elder SOS cancellation'
    $sosCancelReplay = Invoke-Api -Method 'POST' -Path "/api/v1/emergency/sos/$sosCancelId/cancel" `
        -BearerToken $elderAccount.Token -IdempotencyKey $sosCancelKey -Body $sosCancelBody
    $null = Assert-OkApiResponse $sosCancelReplay 'Elder SOS cancellation replay'
    $sosCancelReadResponse = Invoke-Api -Method 'GET' -Path '/api/v1/emergency/sos/active' -BearerToken $elderAccount.Token
    $sosCancelReadJson = Assert-OkApiResponse $sosCancelReadResponse 'Cancelled SOS readback'
    $sosCancelHistoryCount = Invoke-AppDatabaseQuery @"
SELECT COUNT(*) FROM work_order_action_history
 WHERE work_order_id = $sosCancelWorkOrderId AND action_name = 'ELDER_CANCEL';
"@
    Confirm-Chain '老人 SOS 取消通过同键原响应重放且业务历史只写入一次' `
        ($sosCancelResponse.StatusCode -eq $sosCancelReplay.StatusCode -and
            $sosCancelResponse.Content -ceq $sosCancelReplay.Content -and
            [string]$sosCancelReadJson.data.sosId -eq [string]$sosCancelId -and
            [string]$sosCancelReadJson.data.status -eq 'CANCELLED' -and
            [int]$sosCancelHistoryCount.StdOut.Trim() -eq 1) `
        'Elder SOS cancellation replay did not preserve the original response or created more than one cancellation history row.'

    $closureWebContext = New-TestHttpClient
    $closureMiniContext = New-TestHttpClient
    $closureWebLogin = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $closureWebContext.Client -Body @{
        phone = $staffPhone; password = $staffPassword; clientType = 'WEB'
    }
    $closureWebLoginJson = Assert-OkApiResponse $closureWebLogin 'Task 2 password-change Web login'
    $closureMiniLogin = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $closureMiniContext.Client -Body @{
        phone = $staffPhone; password = $staffPassword; clientType = 'MINIAPP'
    }
    $closureMiniLoginJson = Assert-OkApiResponse $closureMiniLogin 'Task 2 password-change MINIAPP login'
    $closureMiniToken = [string]$closureMiniLoginJson.data.token
    $closureWebCookies = @($closureWebContext.Cookies.GetCookies([Uri]($script:apiBase + '/api/v1/auth/login')) |
        Where-Object { $_.Name -eq 'SESSION_TOKEN' })
    Confirm-That ($closureWebCookies.Count -eq 1 -and $closureWebCookies[0].Value) `
        'Could not capture the isolated staff Web session cookie before password change.'
    $closureOldCookieValue = [string]$closureWebCookies[0].Value
    $closureWebSessionBefore = Get-SessionStateForToken -UserId $staffTargetUserId -Token $closureOldCookieValue
    $closureMiniSessionBefore = Get-SessionStateForToken -UserId $staffTargetUserId -Token $closureMiniToken
    $newStaffPassword = 'isolated-reset-' + [Guid]::NewGuid().ToString('N')
    $passwordChangeResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/change-password' `
        -Client $closureWebContext.Client -CsrfToken $closureWebLoginJson.data.csrfToken -Body @{
            currentPassword = $staffPassword; newPassword = $newStaffPassword
        }
    $null = Assert-OkApiResponse $passwordChangeResponse 'Task 2 staff password change'
    $closureWebSessionAfter = Get-SessionStateForToken -UserId $staffTargetUserId -Token $closureOldCookieValue
    $closureMiniSessionAfter = Get-SessionStateForToken -UserId $staffTargetUserId -Token $closureMiniToken
    $webSessionAfterParts = $closureWebSessionAfter -split '\|', 5
    $miniSessionAfterParts = $closureMiniSessionAfter -split '\|', 5
    $passwordRevocationFactsOk = $webSessionAfterParts.Count -eq 5 -and $miniSessionAfterParts.Count -eq 5 -and
        $webSessionAfterParts[3] -ne 'NULL' -and $webSessionAfterParts[4] -eq 'PASSWORD_RESET' -and
        $miniSessionAfterParts[3] -ne 'NULL' -and $miniSessionAfterParts[4] -eq 'PASSWORD_RESET'
    $passwordDeniedCookieContext = New-TestHttpClient
    $passwordDeniedCookieContext.Cookies.Add([Uri]$script:apiBase,
        [Net.Cookie]::new('SESSION_TOKEN', $closureOldCookieValue, '/api/v1'))
    $passwordDeniedFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $passwordDeniedAuditFactsBefore = Get-NotificationAndAuditFacts
    $passwordOldCookieResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -Client $passwordDeniedCookieContext.Client
    $passwordOldBearerResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' `
        -BearerToken $closureMiniToken -Client $closureMiniContext.Client
    $passwordDeniedFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $passwordDeniedAuditFactsAfter = Get-NotificationAndAuditFacts
    Confirm-Chain '改密后旧 Web Cookie 与 Bearer 返回 HTTP 401 且撤销原因/时间可查询' `
        ($passwordOldCookieResponse.StatusCode -eq 401 -and $passwordOldBearerResponse.StatusCode -eq 401 -and
            $passwordRevocationFactsOk -and $passwordDeniedFactsBefore -eq $passwordDeniedFactsAfter -and
            $passwordDeniedAuditFactsBefore -eq $passwordDeniedAuditFactsAfter) `
        'A password change left an old Cookie/Bearer usable, failed to persist PASSWORD_RESET with revoked_at, or a denied request changed isolated facts.'

    $disabledWebContext = New-TestHttpClient
    $disabledMiniContext = New-TestHttpClient
    $disabledWebLogin = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $disabledWebContext.Client -Body @{
        phone = $staffPhone; password = $newStaffPassword; clientType = 'WEB'
    }
    $null = Assert-OkApiResponse $disabledWebLogin 'Task 2 disabled-account Web login'
    $disabledMiniLogin = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Client $disabledMiniContext.Client -Body @{
        phone = $staffPhone; password = $newStaffPassword; clientType = 'MINIAPP'
    }
    $disabledMiniLoginJson = Assert-OkApiResponse $disabledMiniLogin 'Task 2 disabled-account MINIAPP login'
    $disabledMiniToken = [string]$disabledMiniLoginJson.data.token
    $disabledWebCookies = @($disabledWebContext.Cookies.GetCookies([Uri]($script:apiBase + '/api/v1/auth/login')) |
        Where-Object { $_.Name -eq 'SESSION_TOKEN' })
    Confirm-That ($disabledWebCookies.Count -eq 1 -and $disabledWebCookies[0].Value) `
        'Could not capture the isolated staff Web session cookie before disabling the account.'
    $disabledOldCookieValue = [string]$disabledWebCookies[0].Value
    $disabledWebSessionBefore = Get-SessionStateForToken -UserId $staffTargetUserId -Token $disabledOldCookieValue
    $disabledMiniSessionBefore = Get-SessionStateForToken -UserId $staffTargetUserId -Token $disabledMiniToken
    $disableAccount = Invoke-AppDatabaseQuery "UPDATE sys_user SET status = 'DISABLED', disabled_at = NOW(3), disabled_reason = '隔离账号停用验收' WHERE user_id = $staffTargetUserId AND status = 'ACTIVE'; SELECT ROW_COUNT();"
    Confirm-That ($disableAccount.ExitCode -eq 0 -and $disableAccount.StdOut.Trim() -eq '1') `
        'Could not mark exactly one synthetic staff account DISABLED in the isolated database.'
    $disabledFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $disabledAuditFactsBefore = Get-NotificationAndAuditFacts
    $disabledCookieProbe = New-TestHttpClient
    $disabledCookieProbe.Cookies.Add([Uri]$script:apiBase,
        [Net.Cookie]::new('SESSION_TOKEN', $disabledOldCookieValue, '/api/v1'))
    $disabledCookieResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -Client $disabledCookieProbe.Client
    $disabledBearerResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' `
        -BearerToken $disabledMiniToken -Client $disabledMiniContext.Client
    $disabledFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $disabledAuditFactsAfter = Get-NotificationAndAuditFacts
    $disabledWebSessionAfter = Get-SessionStateForToken -UserId $staffTargetUserId -Token $disabledOldCookieValue
    $disabledMiniSessionAfter = Get-SessionStateForToken -UserId $staffTargetUserId -Token $disabledMiniToken
    Confirm-Chain 'DISABLED 账号的旧 Web Cookie 与 Bearer 返回 HTTP 401 且无请求副作用' `
        ($disabledCookieResponse.StatusCode -eq 401 -and $disabledBearerResponse.StatusCode -eq 401 -and
            $disabledWebSessionBefore -eq $disabledWebSessionAfter -and
            $disabledMiniSessionBefore -eq $disabledMiniSessionAfter -and
            $disabledFactsBefore -eq $disabledFactsAfter -and $disabledAuditFactsBefore -eq $disabledAuditFactsAfter) `
        'A disabled account session was accepted or the denied request changed session, business, notification, or audit facts.'

    # Keep logout checks after all elder/child business assertions so their
    # tokens remain available for the complete isolated business flow above.
    $elderLogoutResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/logout' -BearerToken $elderAccount.Token
    $null = Assert-OkApiResponse $elderLogoutResponse 'Elder logout'
    Confirm-Chain '老人注销成功并撤销服务端会话' ($elderLogoutResponse.StatusCode -eq 200) `
        'POST /api/v1/auth/logout did not return HTTP 200 for the active elder session.'
    $elderOldTokenResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $elderAccount.Token
    Confirm-Chain '老人注销后的旧 Bearer Token 请求返回 HTTP 401' ($elderOldTokenResponse.StatusCode -eq 401) `
        'The elder Bearer Token remained valid after logout.'

    $childUserLookup = Invoke-AppDatabaseQuery "SELECT user_id FROM auth_session WHERE token_hash = UNHEX(SHA2('$($childAccount.Token)', 256)) AND revoked_at IS NULL LIMIT 1;"
    $childUserId = 0L
    $childUserIdParsed = $childUserLookup.ExitCode -eq 0 -and [long]::TryParse($childUserLookup.StdOut.Trim(), [ref]$childUserId)
    Confirm-That ($childUserIdParsed -and $childUserId -gt 0) 'Could not resolve the active synthetic child user for the UNLINKED check.'
    $childLinkLookup = Invoke-AppDatabaseQuery "SELECT link_id FROM elder_family_link WHERE elder_id = 1 AND child_user_id = $childUserId AND status = 'ACTIVE';"
    $childLinkId = 0L
    $childLinkIdParsed = $childLinkLookup.ExitCode -eq 0 -and [long]::TryParse($childLinkLookup.StdOut.Trim(), [ref]$childLinkId)
    Confirm-That ($childLinkIdParsed -and $childLinkId -gt 0) 'Could not resolve exactly one active synthetic child-to-elder relationship.'
    $unlinkFixture = Invoke-AppDatabaseQuery "UPDATE elder_family_link SET status = 'UNLINKED', row_version = row_version + 1 WHERE link_id = $childLinkId AND status = 'ACTIVE'; SELECT ROW_COUNT();"
    Confirm-That ($unlinkFixture.ExitCode -eq 0 -and $unlinkFixture.StdOut.Trim() -eq '1') `
        'Could not mark exactly one synthetic child-to-elder relationship UNLINKED.'
    $unlinkedFactsBefore = Get-IsolatedTestSideEffectSnapshot
    $unlinkedAuditFactsBefore = Get-NotificationAndAuditFacts
    $unlinkedSessionBefore = Get-SessionStateForToken -UserId $childUserId -Token $childAccount.Token
    $unlinkedElderRead = Invoke-Api -Method 'GET' -Path '/api/v1/elder/archives/1' -BearerToken $childAccount.Token
    $unlinkedFactsAfter = Get-IsolatedTestSideEffectSnapshot
    $unlinkedAuditFactsAfter = Get-NotificationAndAuditFacts
    $unlinkedSessionAfter = Get-SessionStateForToken -UserId $childUserId -Token $childAccount.Token
    $unlinkedSessionBeforeParts = $unlinkedSessionBefore -split '\|', 5
    $unlinkedSessionAfterParts = $unlinkedSessionAfter -split '\|', 5
    $unlinkedRevocationStateUnchanged = $unlinkedSessionBeforeParts.Count -eq 5 -and
        $unlinkedSessionAfterParts.Count -eq 5 -and
        $unlinkedSessionBeforeParts[0] -eq $unlinkedSessionAfterParts[0] -and
        $unlinkedSessionBeforeParts[3] -eq $unlinkedSessionAfterParts[3] -and
        $unlinkedSessionBeforeParts[4] -eq $unlinkedSessionAfterParts[4]
    Confirm-Chain 'UNLINKED 亲情访问返回 HTTP 404 且业务/会话/通知/审计不变' `
        ($unlinkedElderRead.StatusCode -eq 404 -and $unlinkedFactsBefore -eq $unlinkedFactsAfter -and
            $unlinkedAuditFactsBefore -eq $unlinkedAuditFactsAfter -and $unlinkedRevocationStateUnchanged) `
        'An UNLINKED family relationship remained readable or the denied request changed isolated facts.'

    $childLogoutResponse = Invoke-Api -Method 'POST' -Path '/api/v1/auth/logout' -BearerToken $childAccount.Token
    $null = Assert-OkApiResponse $childLogoutResponse 'Child logout'
    Confirm-Chain '子女注销成功并撤销服务端会话' ($childLogoutResponse.StatusCode -eq 200) `
        'POST /api/v1/auth/logout did not return HTTP 200 for the active child session.'
    $childOldTokenResponse = Invoke-Api -Method 'GET' -Path '/api/v1/auth/current-user' -BearerToken $childAccount.Token
    Confirm-Chain '子女注销后的旧 Bearer Token 请求返回 HTTP 401' ($childOldTokenResponse.StatusCode -eq 401) `
        'The child Bearer Token remained valid after logout.'

    $script:httpSmokeResult = '通过'

    if ($HoldSeconds -gt 0) {
        Write-Output "HTTP 全链路通过；临时 API 已在 127.0.0.1:$($script:httpPort) 运行，将在 $HoldSeconds 秒后自动停止并清理。未输出任何账号凭据。"
        for ($remaining = $HoldSeconds; $remaining -gt 0; $remaining--) { Start-Sleep -Seconds 1 }
    }
    Stop-TestApi
    if ($script:junitResult -eq '通过') { $script:scriptExitCode = 0 }
    else { $script:scriptExitCode = 1 }
} catch {
    $script:failureMessage = $_.Exception.Message
    $script:scriptExitCode = 1
} finally {
    try {
        Stop-TestApi
    } catch {
        $script:servicesStopped = $false
        $script:scriptExitCode = 1
        if (-not $script:failureMessage) { $script:failureMessage = 'Cleanup could not stop an isolated service safely.' }
    }
    try {
        if (Remove-SyntheticDatabaseFixtures) {
            $script:fixtureCleanupMessage = 'Task 4 新增员工、家庭身份、社区 2 和工单夹具已从临时库删除。'
            $script:checks['Task 4 新增员工/家庭/工单夹具在清理前移除'] = '通过'
            Write-Output '通过：Task 4 新增员工/家庭/工单夹具在清理前移除'
        } else {
            $script:fixtureCleanupMessage = 'Task 4 合成夹具未能在删除 tempRoot 前完成数据库删除。'
            $script:checks['Task 4 新增员工/家庭/工单夹具在清理前移除'] = '失败'
            $script:scriptExitCode = 1
            if (-not $script:failureMessage) { $script:failureMessage = 'Task 4 synthetic fixture cleanup failed.' }
        }
    } catch {
        $script:fixtureCleanupMessage = 'Task 4 合成夹具清理检查失败；请检查本次隔离数据库。'
        $script:checks['Task 4 新增员工/家庭/工单夹具在清理前移除'] = '失败'
        $script:scriptExitCode = 1
        if (-not $script:failureMessage) { $script:failureMessage = 'Task 4 synthetic fixture cleanup failed.' }
    }
    if ($script:apiProcess) {
        try {
            $script:apiProcess.Refresh()
            if (-not $script:apiProcess.HasExited) { $script:servicesStopped = $false }
        } catch { $script:servicesStopped = $false }
    }
    if ($script:mysqlProcess) {
        try {
            foreach ($isolatedMysql in @(Get-IsolatedMysqlProcesses)) {
                Stop-Process -Id ([int]$isolatedMysql.ProcessId) -Force -ErrorAction SilentlyContinue
            }
            $script:mysqlProcess.Refresh()
            if (-not $script:mysqlProcess.HasExited) {
                Stop-Process -Id $script:mysqlProcess.Id -Force -ErrorAction SilentlyContinue
                [void]$script:mysqlProcess.WaitForExit(10000)
                $script:mysqlProcess.Refresh()
                if (-not $script:mysqlProcess.HasExited) { $script:servicesStopped = $false }
            }
            for ($attempt = 0; $attempt -lt 40; $attempt++) {
                if (@(Get-IsolatedMysqlProcesses).Count -eq 0) { break }
                Start-Sleep -Milliseconds 250
            }
            if (@(Get-IsolatedMysqlProcesses).Count -gt 0) { $script:servicesStopped = $false }
        } catch { $script:servicesStopped = $false }
    }
    foreach ($port in @($script:httpPort, $script:dbPort)) {
        if ($port -and (Test-TcpPortOpen $port)) { $script:servicesStopped = $false }
    }
    if (-not $script:servicesStopped) {
        $script:scriptExitCode = 1
        if (-not $script:failureMessage) { $script:failureMessage = 'An isolated service did not stop; temporary files are retained for safety.' }
    }

    foreach ($key in $script:envKeys) {
        [Environment]::SetEnvironmentVariable($key, $script:previousValues[$key], 'Process')
    }
    foreach ($httpClient in $script:extraHttpClients) { $httpClient.Dispose() }

    try {
        $resolvedDataDirectory = [IO.Path]::GetFullPath($dataDirectory)
        $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
        $resolvedTempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $rootPrefix = $resolvedTempRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        $tempPrefix = $resolvedTempBase.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        $safeToRemove = ([IO.Path]::GetFileName($resolvedTempRoot) -eq $tempRootLeaf) -and
            $resolvedTempRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -and
            $resolvedDataDirectory.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -and
            $script:servicesStopped
        if (Test-Path -LiteralPath $tempRoot) {
            if ($safeToRemove) {
                $rootItem = Get-Item -LiteralPath $resolvedTempRoot -Force
                if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    throw 'Temporary root became a reparse point; recursive deletion refused.'
                }
                if (Test-Path -LiteralPath $resolvedDataDirectory) {
                    $dataItem = Get-Item -LiteralPath $resolvedDataDirectory -Force
                    if (($dataItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                        throw 'Temporary datadir became a reparse point; recursive deletion refused.'
                    }
                }
                Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
                $script:cleanupMessage = '已验证 datadir 属于本次唯一 tempRoot，停止服务后完成清理。'
                $script:checks['停止临时 API/MySQL，并核验 datadir 位于专属 tempRoot 后清理'] = '通过'
            } else {
                $script:cleanupMessage = '路径归属验证未通过，拒绝递归删除；临时目录保留待人工检查。'
                $script:checks['停止临时 API/MySQL，并核验 datadir 位于专属 tempRoot 后清理'] = '失败'
                $script:scriptExitCode = 1
            }
        } else {
            $script:cleanupMessage = 'tempRoot 未建立或已不存在；未执行递归删除。'
            if ($safeToRemove) { $script:checks['停止临时 API/MySQL，并核验 datadir 位于专属 tempRoot 后清理'] = '通过' }
        }
    } catch {
        $script:cleanupMessage = '清理失败或被路径安全检查拒绝；请检查系统临时目录。'
        $script:checks['停止临时 API/MySQL，并核验 datadir 位于专属 tempRoot 后清理'] = '失败'
        $script:scriptExitCode = 1
        if (-not $script:failureMessage) { $script:failureMessage = 'Temporary directory cleanup failed its safety check.' }
    }

    try { Write-RunReport -ExitCode $script:scriptExitCode }
    catch {
        $script:scriptExitCode = 1
        $script:failureMessage = 'Could not write the isolated HTTP smoke report.'
        Write-Error $script:failureMessage
    }
}

if ($script:httpPort) { Write-Output "临时 HTTP 端口：127.0.0.1:$($script:httpPort)（API 已停止；未输出任何凭据）" }
if ($script:scriptExitCode -eq 0) { Write-Output "报告：$reportPath"; exit 0 }
$finalErrorMessage = if ($script:failureMessage) { $script:failureMessage } elseif ($script:junitFailureMessage) { $script:junitFailureMessage } else { 'Isolated system HTTP smoke failed.' }
[Console]::Error.WriteLine($finalErrorMessage)
Write-Output "报告：$reportPath"
exit 1
