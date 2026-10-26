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
    'Flyway 后使用随机 marker 校验目标库身份并删除 marker' = '未执行'
    '首轮 API 已退出，且确认其端口已释放后才加载 seed' = '未执行'
    '现有 first-load seed wrapper 在临时库返回退出码 0' = '未执行'
    'Seed 后 API 在随机 loopback HTTP 端口健康可用（Maven 前）' = '未执行'
    '匿名访问当前用户接口返回 HTTP 401' = '未执行'
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
    'HTTP 冒烟结束后 API 已停止且端口释放，才运行完整 Maven 测试' = '未执行'
    'Maven 全套结束后 API 在隔离端口重新健康可用' = '未执行'
    '临时数据库上的完整 Maven 测试套件通过' = '未执行'
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
    [string]$CsrfToken = $null
) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), ($script:apiBase + $Path))
    if ($BearerToken) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $BearerToken)
    }
    if ($CsrfToken) { [void]$request.Headers.TryAddWithoutValidation('X-CSRF-Token', $CsrfToken) }
    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
        $request.Content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $script:httpClient.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = $content; Response = $response }
    } catch {
        throw "HTTP transport failed for $Method $Path."
    } finally {
        $request.Dispose()
    }
}

function Invoke-PhotoUpload([string]$Path, [string]$BearerToken) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, ($script:apiBase + '/api/v1/files/upload'))
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = $null
    try {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $BearerToken)
        $bytes = [IO.File]::ReadAllBytes($Path)
        $fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
        $multipart.Add($fileContent, 'file', [IO.Path]::GetFileName($Path))
        $request.Content = $multipart
        $response = $script:httpClient.SendAsync($request).GetAwaiter().GetResult()
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

function Assert-OkApiResponse([object]$Response, [string]$Description) {
    if ($Response.StatusCode -ne 200) { throw "$Description returned an unexpected HTTP status." }
    try { $json = ConvertFrom-Json -InputObject $Response.Content }
    catch { throw "$Description returned invalid JSON." }
    if ($json.code -ne 'OK') { throw "$Description returned a non-OK application result." }
    return $json
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
    $lines.Add('- 覆盖 API 健康、匿名认证拒绝、六账号登录、Web Cookie/CSRF、老人资料/服药打卡、子女绑定老人/预约、管理员确认并派单、网格员履约照片提交、管理员审核至预约 COMPLETED，以及老人 SOS 触发/管理员处置；此外在同一临时库上运行完整 `mvn test`，但 HTTP 与 JUnit 结果分别报告。')
    $lines.Add('- Maven 测试前脚本会清空进程内所有 AUTH_TEST_* 变量，故 AuthDatabaseIntegrationTest 按环境条件跳过，不会覆盖同一演示 seed 账号；其跳过计入 JUnit 汇总。')
    $lines.Add('- 图片链使用合成 1x1 PNG 验证上传接口、文件读取与履约 URL 引用，不验证真实相机图片、前端预览/裁剪或完整 H5/管理端 UI；也未覆盖真实短信/验证码、所有业务角色/全部接口和并发/压力场景。')
    $lines.Add('- `-HoldSeconds` 在完整业务 HTTP 链通过后、停止 API/MySQL 前保留隔离 API 与 MySQL，默认 0，最大 300 秒；JUnit 已在此前 API 停止窗口完成。')
    $lines.Add('')
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

    $script:apiBase = "http://127.0.0.1:$($script:httpPort)"
    Add-Type -AssemblyName System.Net.Http
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $script:cookieContainer = [Net.CookieContainer]::new()
    $handler.CookieContainer = $script:cookieContainer
    $handler.UseCookies = $true
    $script:httpClient = [System.Net.Http.HttpClient]::new($handler)
    $script:httpClient.Timeout = [TimeSpan]::FromSeconds(20)

    $script:apiProcess = Start-TestApi 'flyway'
    $null = Wait-ApiReady -Process $script:apiProcess -PhaseName 'Flyway initialization'
    $flywayCount = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-N', '-B', '-e', "SELECT COUNT(DISTINCT version) FROM flyway_schema_history WHERE success=1 AND version IN ('1','2','3','3.1','3.2','3.3');")
    $markerSetup = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo') @"
CREATE TABLE system_http_test_marker (marker_value CHAR(32) NOT NULL PRIMARY KEY);
INSERT INTO system_http_test_marker (marker_value) VALUES ('$marker');
"@
    $markerAfterFlyway = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-N', '-B', '-e', 'SELECT @@port, DATABASE(), (SELECT marker_value FROM system_http_test_marker);')
    $expectedMarkedIdentity = "$($script:dbPort)`telderly_care_demo`t$marker"
    $markerDrop = Invoke-Mysql @('-h127.0.0.1', "-P$($script:dbPort)", '-uelderly_app', '--database=elderly_care_demo', '-e', 'DROP TABLE system_http_test_marker;')
    Confirm-Chain 'Spring Boot 首次启动由 Flyway 初始化隔离库并通过健康检查' `
        ($flywayCount.ExitCode -eq 0 -and $flywayCount.StdOut.Trim() -eq '6' -and
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

    $credentialPath = Join-Path $repoRoot 'docs\current\接口契约_M01_M02.md'
    $accounts = Get-ContractCredentials -ContractPath $credentialPath
    $adminAccount = $null
    $workerAccount = $null
    $childAccount = $null
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

    $webUnsafePath = '/api/v1/admin/reservations/0/confirm'
    $missingCsrfResponse = Invoke-Api -Method 'POST' -Path $webUnsafePath
    Confirm-Chain 'Web Cookie 写请求缺少 CSRF Token 时返回 HTTP 403' ($missingCsrfResponse.StatusCode -eq 403) `
        'A cookie-authenticated unsafe request without CSRF did not return HTTP 403.'

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
    $medicationCheckinResponse = Invoke-Api -Method 'POST' -Path '/api/v1/medication/tasks/checkin' -BearerToken $elderAccount.Token -Body @{
        taskId = [long]$medicationTask.taskId
        checkinMode = 'SELF_CONFIRMED'
        idempotencyKey = 'SYSHTTP-' + [Guid]::NewGuid().ToString('N')
    }
    $medicationCheckinJson = Assert-OkApiResponse $medicationCheckinResponse 'Elder medication task check-in'
    Confirm-Chain '老人将今日一条未确认药盒任务打卡为 CONFIRMED' `
        ([string]$medicationCheckinJson.data.status -eq 'CONFIRMED') `
        'POST /api/v1/medication/tasks/checkin did not transition the selected task to CONFIRMED.'

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
    $reservationResponse = Invoke-Api -Method 'POST' -Path '/api/v1/service-reservations' -BearerToken $childAccount.Token -Body @{
        elderId = 1
        serviceType = [string]$selectedCatalog.serviceType
        serviceDate = $serviceDate
        slotCode = $slotCode
        serviceAddress = $address
        specialNotes = 'system-http-smoke-' + [Guid]::NewGuid().ToString('N')
    }
    $reservationJson = Assert-OkApiResponse $reservationResponse 'Child reservation create request'
    $reservationId = [long]$reservationJson.data
    Confirm-That ($reservationId -gt 0) 'The child reservation response did not contain a valid reservation ID.'
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

    $confirmResponse = Invoke-Api -Method 'POST' -Path "/api/v1/admin/reservations/$reservationId/confirm" -CsrfToken $adminAccount.Csrf
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

    $dispatchResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/dispatch" -CsrfToken $adminAccount.Csrf -Body @{
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

    $acceptResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/accept" -BearerToken $workerAccount.Token
    $null = Assert-OkApiResponse $acceptResponse 'Grid-worker accept request'
    $acceptedOrderResponse = Invoke-Api -Method 'GET' -Path "/api/v1/work-orders/$workOrderId" -BearerToken $workerAccount.Token
    $acceptedOrderJson = Assert-OkApiResponse $acceptedOrderResponse 'Accepted work-order readback'
    Confirm-Chain '网格员接单后状态为 ACCEPTED' `
        ([string]$acceptedOrderJson.data.status -eq 'ACCEPTED') `
        'POST /api/v1/work-orders/{id}/accept did not transition the assigned order to ACCEPTED.'
    $startServiceResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/start" -BearerToken $workerAccount.Token
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

    $executionResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/execution" -BearerToken $workerAccount.Token -Body @{
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

    $reviewResponse = Invoke-Api -Method 'POST' -Path "/api/v1/work-orders/$workOrderId/review" -CsrfToken $adminAccount.Csrf -Body @{
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

    $sosTriggerResponse = Invoke-Api -Method 'POST' -Path '/api/v1/emergency/sos/trigger' -BearerToken $elderAccount.Token -Body @{
        pressDurationMs = 2500
        locationAddress = $address
        idempotencyKey = 'SYSHTTP-SOS-' + [Guid]::NewGuid().ToString('N')
    }
    $sosTriggerJson = Assert-OkApiResponse $sosTriggerResponse 'Elder SOS trigger'
    $sosId = [long]$sosTriggerJson.data.sosId
    $sosWorkOrderId = [long]$sosTriggerJson.data.workOrderId
    Confirm-Chain '老人触发 SOS 并原子生成求助工单' `
        ($sosId -gt 0 -and $sosWorkOrderId -gt 0 -and [string]$sosTriggerJson.data.status -eq 'ACCEPTED') `
        'POST /api/v1/emergency/sos/trigger did not return an accepted SOS and generated emergency work order.'

    $sosDispatchResponse = Invoke-Api -Method 'POST' -Path "/api/v1/emergency/sos/$sosId/dispatch" -CsrfToken $adminAccount.Csrf -Body @{
        assigneeId = $workerAccount.UserId
        dispatchReason = '隔离 HTTP 系统验收 SOS 调度'
    }
    $null = Assert-OkApiResponse $sosDispatchResponse 'Administrator SOS dispatch'
    $sosCloseResponse = Invoke-Api -Method 'POST' -Path "/api/v1/emergency/sos/$sosId/close" -CsrfToken $adminAccount.Csrf -Body @{
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
    if ($script:httpClient) { $script:httpClient.Dispose() }

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
