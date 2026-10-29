param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$tempRootLeaf = 'auth-session-it-' + [Guid]::NewGuid().ToString('N')
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) $tempRootLeaf
$mysqlCommand = Get-Command mysql -ErrorAction Stop
$mysqlExe = $mysqlCommand.Source
$mysqlServerExe = Join-Path (Split-Path -Parent $mysqlExe) 'mysqld.exe'
$baseDirectory = Split-Path -Parent (Split-Path -Parent $mysqlExe)
$dataDirectory = Join-Path $tempRoot 'mysql-data'
$server = $null
$envKeys = @('AUTH_TEST_DB_URL', 'AUTH_TEST_DB_USERNAME', 'AUTH_TEST_DB_PASSWORD', 'AUTH_TEST_HMAC_KEY', 'AUTH_TEST_DB_PORT', 'AUTH_TEST_DB_MARKER', 'AUTH_TEST_SEED_DOC_PATH')
$previousValues = @{}
foreach ($key in $envKeys) { $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
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

function Invoke-Mysql([string[]]$Arguments, [string]$InputText = '') {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlExe
    $quotedArguments = $Arguments | ForEach-Object { ConvertTo-CommandLineArgument ([string]$_) }
    $startInfo.Arguments = $quotedArguments -join ' '
    $startInfo.WorkingDirectory = $tempRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $InputText.Length -gt 0
    $process = [Diagnostics.Process]::Start($startInfo)
    if ($InputText.Length -gt 0) {
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout + $stderr }
}

function Start-IsolatedMysql([string[]]$Arguments, [string]$StdoutPath, [string]$StderrPath) {
    $quotedArgs = $Arguments | ForEach-Object {
        if ($_ -match '\s' -and $_ -notmatch '^".*"$') { '"' + $_.Replace('"', '\\"') + '"' } else { $_ }
    }
    return Start-Process -FilePath $mysqlServerExe -ArgumentList ($quotedArgs -join ' ') `
        -PassThru -WindowStyle Hidden -RedirectStandardOutput $StdoutPath -RedirectStandardError $StderrPath
}

try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    Assert-That (Test-Path -LiteralPath $mysqlServerExe) 'A local MySQL server binary is required for auth database integration verification.'

    $serverPort = Get-Random -Minimum 34000 -Maximum 48000
    $init = Start-Process -FilePath $mysqlServerExe `
        -ArgumentList @('--no-defaults', '--initialize-insecure', "`"--basedir=$baseDirectory`"", "`"--datadir=$dataDirectory`"", '--console') `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput (Join-Path $tempRoot 'mysql-init.out') `
        -RedirectStandardError (Join-Path $tempRoot 'mysql-init.err')
    Assert-That ($init.ExitCode -eq 0) 'Isolated MySQL initialization failed.'

    $errorLog = Join-Path $tempRoot 'mysql-error.log'
    $server = Start-IsolatedMysql @('--no-defaults', "--basedir=$baseDirectory", "--datadir=$dataDirectory", "--port=$serverPort", '--bind-address=127.0.0.1', "--log-error=$errorLog", '--console') `
        (Join-Path $tempRoot 'mysql-server.out') (Join-Path $tempRoot 'mysql-server.err')
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($server.HasExited) { throw 'Isolated MySQL exited before becoming ready.' }
        $probe = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT 1;')
        if ($probe.ExitCode -eq 0 -and $probe.Output.Trim() -eq '1') { $ready = $true; break }
        Start-Sleep -Milliseconds 250
    }
    Assert-That $ready 'Isolated MySQL did not become ready.'

    $databaseName = 'elderly_care_demo'
    $testUsername = 'elderly_app'
    $testPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $testHmacKey = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $testMarker = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $setupSql = "CREATE DATABASE ``$databaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$testUsername'@'127.0.0.1' IDENTIFIED BY '$testPassword'; GRANT ALL PRIVILEGES ON ``$databaseName``.* TO '$testUsername'@'127.0.0.1'; FLUSH PRIVILEGES;"
    $setup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot') $setupSql
    Assert-That ($setup.ExitCode -eq 0) 'Could not prepare the isolated auth database and test-only account.'

    $targetProbe = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', "--database=$databaseName", '-N', '-e', 'SELECT @@port, DATABASE();')
    Assert-That ($targetProbe.ExitCode -eq 0 -and $targetProbe.Output.Trim() -eq "$serverPort`t$databaseName") 'Temporary MySQL target does not match the expected local port/database.'

    foreach ($migrationName in @(
            'V1__bootstrap_empty_schema.sql',
            'V2__m01_m02_identity_and_archive.sql',
            'V3__core_business_schema.sql',
            'V3_1__auth_login_rate_limit.sql')) {
        $migrationPath = Join-Path $repoRoot "services\api\src\main\resources\db\migration\$migrationName"
        Assert-That (Test-Path -LiteralPath $migrationPath) "Missing migration required by auth integration: $migrationName."
        $migrationSql = Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8
        $migrationResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', "--database=$databaseName") $migrationSql
        Assert-That ($migrationResult.ExitCode -eq 0) "Could not initialize isolated auth schema from $migrationName."
    }

    $markerSetupSql = "CREATE TABLE auth_test_instance_marker (marker_id VARCHAR(64) NOT NULL PRIMARY KEY, marker_value VARCHAR(128) NOT NULL); INSERT INTO auth_test_instance_marker (marker_id, marker_value) VALUES ('task2-auth-it', '$testMarker');"
    $markerResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', "--database=$databaseName") $markerSetupSql
    Assert-That ($markerResult.ExitCode -eq 0) 'Could not install the isolated MySQL identity marker.'

    $historySql = @'
CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL PRIMARY KEY,
    version VARCHAR(50) NULL,
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT NULL,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success BOOLEAN NOT NULL,
    INDEX flyway_schema_history_s_idx (success)
);
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, installed_by, execution_time, success)
VALUES (1, '1', 'bootstrap empty schema', 'SQL', 'V1__bootstrap_empty_schema.sql', 'auth_it', 1, 1),
       (2, '2', 'identity and archive', 'SQL', 'V2__m01_m02_identity_and_archive.sql', 'auth_it', 1, 1),
       (3, '3', 'core business schema', 'SQL', 'V3__core_business_schema.sql', 'auth_it', 1, 1);
'@
    $historyResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', "--database=$databaseName") $historySql
    Assert-That ($historyResult.ExitCode -eq 0) 'Could not prepare the isolated V3 history required by the first-load seed gate.'

    $seedEnvFile = Join-Path $tempRoot 'seed.env'
    @("DB_HOST=127.0.0.1", "DB_PORT=$serverPort", 'DB_NAME=elderly_care_demo', 'DB_USERNAME=elderly_app', "DB_PASSWORD=$testPassword") |
        Set-Content -LiteralPath $seedEnvFile -Encoding ASCII
    $pwshCommand = Get-Command pwsh -ErrorAction Stop
    & $pwshCommand.Source -NoProfile -ExecutionPolicy Bypass -File `
        (Join-Path $repoRoot 'ops\windows\load-demo-seed.ps1') -EnvFile $seedEnvFile
    Assert-That ($LASTEXITCODE -eq 0) 'The isolated first-load demo seed import failed.'

    $env:AUTH_TEST_DB_URL = "jdbc:mysql://127.0.0.1:$serverPort/$databaseName`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    $env:AUTH_TEST_DB_USERNAME = $testUsername
    $env:AUTH_TEST_DB_PASSWORD = $testPassword
    $env:AUTH_TEST_HMAC_KEY = $testHmacKey
    $env:AUTH_TEST_DB_PORT = [string]$serverPort
    $env:AUTH_TEST_DB_MARKER = $testMarker
    $seedCredentialDoc = Join-Path $tempRoot 'seed-credential-contract.md'
    $sourceCredentialDoc = Join-Path $repoRoot 'docs\current\接口契约_M01_M02.md'
    Assert-That (Test-Path -LiteralPath $sourceCredentialDoc) 'The documented synthetic credential contract is missing.'
    Copy-Item -LiteralPath $sourceCredentialDoc -Destination $seedCredentialDoc
    $env:AUTH_TEST_SEED_DOC_PATH = $seedCredentialDoc

    $authTestStartedAt = Get-Date
    Push-Location (Join-Path $repoRoot 'services\api')
    try {
        & mvn '-Dtest=AuthDatabaseIntegrationTest' test
        $mavenExitCode = $LASTEXITCODE
    }
    finally { Pop-Location }
    Assert-That ($mavenExitCode -eq 0) "AuthDatabaseIntegrationTest failed with exit code $mavenExitCode."

    $authSurefireReport = Join-Path $repoRoot 'services\api\target\surefire-reports\TEST-cn.communityelderly.modules.auth.AuthDatabaseIntegrationTest.xml'
    Assert-That (Test-Path -LiteralPath $authSurefireReport) 'AuthDatabaseIntegrationTest did not write its Surefire report.'
    Assert-That ((Get-Item -LiteralPath $authSurefireReport).LastWriteTime -ge $authTestStartedAt) `
        'AuthDatabaseIntegrationTest Surefire report was not refreshed by this isolated run.'
    [xml]$authSuite = Get-Content -LiteralPath $authSurefireReport -Raw -Encoding UTF8
    $authTestCount = [int]$authSuite.testsuite.tests
    $authSkippedCount = [int]$authSuite.testsuite.skipped
    $authFailureCount = [int]$authSuite.testsuite.failures
    $authErrorCount = [int]$authSuite.testsuite.errors
    Assert-That ($authTestCount -gt 0 -and $authSkippedCount -eq 0 -and
            $authFailureCount -eq 0 -and $authErrorCount -eq 0) `
        "AuthDatabaseIntegrationTest did not execute cleanly (tests=$authTestCount, skipped=$authSkippedCount, failures=$authFailureCount, errors=$authErrorCount)."

    Write-Output 'PASS: applied isolated V1/V2/V3/V3_1 migrations to temporary MySQL.'
    Write-Output 'PASS: BCrypt login created a DB-backed Web session, CSRF digest and active role; revoked assignment denied the next request.'
    Write-Output 'PASS: disabled-account access was rejected without request side effects; password change revoked the old session with a queryable reason and timestamp.'
    Write-Output "PASS: AuthDatabaseIntegrationTest executed $authTestCount tests; Skipped: $authSkippedCount."
    Write-Output 'PASS: first-load seed imported in the isolated elderly_care_demo and all six documented synthetic accounts logged in.'
    Write-Output 'PASS: V3_1 phone/IP rate-limit state persisted in MySQL and enforced cooldown.'
}
finally {
    if (Test-Path -LiteralPath $dataDirectory) {
        $resolvedDataDirectory = [IO.Path]::GetFullPath($dataDirectory)
        $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
        $resolvedTempPrefix = $resolvedTempRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if ($resolvedDataDirectory.StartsWith($resolvedTempPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            $isolatedServers = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object {
                $_.CommandLine -and $_.CommandLine.Contains($resolvedDataDirectory) -and $_.CommandLine.Contains('--no-defaults')
            })
            foreach ($isolatedServer in $isolatedServers) {
                Stop-Process -Id $isolatedServer.ProcessId -Force -ErrorAction SilentlyContinue
            }
        }
    }
    if ($server -and -not $server.HasExited) { $server.WaitForExit(5000) | Out-Null }

    foreach ($key in $envKeys) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
    $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
    $resolvedTempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $resolvedTempPrefix = $resolvedTempBase.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (([IO.Path]::GetFileName($resolvedTempRoot) -eq $tempRootLeaf) -and
            ($resolvedTempRoot.StartsWith($resolvedTempPrefix, [StringComparison]::OrdinalIgnoreCase))) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
