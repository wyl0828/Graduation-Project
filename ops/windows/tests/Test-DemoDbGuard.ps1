param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$tempRootLeaf = 'demo-db-guard-' + [Guid]::NewGuid().ToString('N')
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) $tempRootLeaf
$childPowerShell = (Get-Process -Id $PID).Path
$mysqlCommand = Get-Command mysql -ErrorAction Stop
$mysqlExe = $mysqlCommand.Source
$mysqlServerExe = Join-Path (Split-Path -Parent $mysqlExe) 'mysqld.exe'

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-ChildPowerShell([string]$ScriptPath, [string]$WorkingDirectory) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $childPowerShell
    $startInfo.Arguments = "-NoProfile -File `"$ScriptPath`""
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($startInfo)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout + $stderr }
}

function Start-ChildPowerShell([string]$ScriptPath, [string]$WorkingDirectory) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $childPowerShell
    $startInfo.Arguments = "-NoProfile -File `"$ScriptPath`""
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    return [Diagnostics.Process]::Start($startInfo)
}

function Invoke-Mysql([string[]]$Arguments, [string]$InputText = '') {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlExe
    foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
    $startInfo.WorkingDirectory = $tempRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $InputText.Length -gt 0
    $process = [Diagnostics.Process]::Start($startInfo)
    if ($InputText.Length -gt 0) {
        try {
            $process.StandardInput.Write($InputText)
            $process.StandardInput.Close()
        }
        catch [IO.IOException] {
            # A correctly failing mysql client can close its input pipe at the guard statement.
        }
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout + $stderr }
}

function Start-MysqlSession([string[]]$Arguments) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlExe
    foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
    $startInfo.WorkingDirectory = $tempRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $true
    return [Diagnostics.Process]::Start($startInfo)
}

function Wait-MysqlLine([Diagnostics.Process]$Process) {
    $lineTask = $Process.StandardOutput.ReadLineAsync()
    if (-not $lineTask.Wait(10000)) { throw 'Timed out waiting for the isolated MySQL session marker.' }
    return $lineTask.Result
}

try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    $sandbox = Join-Path $tempRoot 'repo'
    New-Item -ItemType Directory -Path $sandbox | Out-Null
    foreach ($relativePath in @('ops\windows\start-api.ps1', 'ops\windows\run-test.ps1', 'ops\windows\repair-demo-passwords.ps1', 'ops\windows\repair-demo-activation.ps1', 'ops\windows\load-demo-seed.ps1', 'ops\windows\verify-db-v3.ps1', 'database\seed\demo_synthetic_seed_m01_m02.sql', 'database\seed\demo_synthetic_seed_m03_m12.sql', 'services\api\src\main\resources\application-local.yml', 'services\api\src\main\resources\db\migration\V1__bootstrap_empty_schema.sql', 'services\api\src\main\resources\db\migration\V2__m01_m02_identity_and_archive.sql', 'services\api\src\main\resources\db\migration\V3__core_business_schema.sql')) {
        $source = Join-Path $repoRoot $relativePath
        $destination = Join-Path $sandbox $relativePath
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
    }

    $mvnMarker = Join-Path $tempRoot 'maven-invoked.txt'
    $guardFailures = [System.Collections.Generic.List[string]]::new()
    @('@echo off', 'echo invoked>>"%FAKE_MVN_MARKER%"', 'exit /b %FAKE_MVN_EXIT_CODE%') | Set-Content -LiteralPath (Join-Path $sandbox 'mvnw.cmd') -Encoding ASCII
    $env:FAKE_MVN_MARKER = $mvnMarker
    $env:FAKE_MVN_EXIT_CODE = '0'

    function Set-SandboxDbConfig([string[]]$Lines) {
        Set-Content -LiteralPath (Join-Path $sandbox '.env.local') -Value $Lines -Encoding ASCII
    }

    function Assert-MavenGuardRejects([string]$ScriptRelativePath, [string[]]$ConfigLines, [string]$Description) {
        Remove-Item -LiteralPath $mvnMarker -Force -ErrorAction SilentlyContinue
        Set-SandboxDbConfig $ConfigLines
        $result = Invoke-ChildPowerShell (Join-Path $sandbox $ScriptRelativePath) $sandbox
        if ($result.ExitCode -eq 0) { $guardFailures.Add("$ScriptRelativePath accepted $Description.") }
        if (Test-Path -LiteralPath $mvnMarker) { $guardFailures.Add("$ScriptRelativePath invoked Maven before rejecting $Description.") }
    }

    $validDbConfig = @('DB_HOST=127.0.0.1', 'DB_NAME=elderly_care_demo', 'DB_USERNAME=elderly_app', 'DB_PASSWORD=synthetic-test-only')

    # Positive control: prove the fake Maven command is reachable with valid sandbox config.
    Remove-Item -LiteralPath $mvnMarker -Force -ErrorAction SilentlyContinue
    Set-SandboxDbConfig $validDbConfig
    $startApiPositive = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\start-api.ps1') $sandbox
    Assert-That ($startApiPositive.ExitCode -eq 0) "start-api valid sandbox config returned $($startApiPositive.ExitCode)."
    Assert-That (Test-Path -LiteralPath $mvnMarker) 'start-api positive control did not invoke the fake Maven command.'

    foreach ($requiredKey in @('DB_HOST', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD')) {
        $withoutKey = @($validDbConfig | Where-Object { $_ -notmatch "^$requiredKey=" })
        Assert-MavenGuardRejects 'ops\windows\start-api.ps1' $withoutKey "missing $requiredKey"
        $blankKey = @($validDbConfig | Where-Object { $_ -notmatch "^$requiredKey=" }) + "$requiredKey="
        Assert-MavenGuardRejects 'ops\windows\start-api.ps1' $blankKey "blank $requiredKey"
    }

    $wrongStartApiTargets = @(
        @{ Key = 'DB_HOST'; Value = '198.51.100.17'; Description = 'a non-loopback DB_HOST' },
        @{ Key = 'DB_NAME'; Value = 'ELDERLY_CARE_DEMO'; Description = 'a case-mismatched DB_NAME' },
        @{ Key = 'DB_USERNAME'; Value = 'ELDERLY_APP'; Description = 'a case-mismatched DB_USERNAME' }
    )
    foreach ($target in $wrongStartApiTargets) {
        $wrongConfig = @($validDbConfig | Where-Object { $_ -notmatch "^$($target.Key)=" }) + "$($target.Key)=$($target.Value)"
        Assert-MavenGuardRejects 'ops\windows\start-api.ps1' $wrongConfig $target.Description
    }

    foreach ($target in $wrongStartApiTargets) {
        $wrongConfig = @($validDbConfig | Where-Object { $_ -notmatch "^$($target.Key)=" }) + "$($target.Key)=$($target.Value)"
        Assert-MavenGuardRejects 'ops\windows\run-test.ps1' $wrongConfig $target.Description
    }

    # The password-repair scripts must reject missing/blank DB coordinates and unsafe targets before
    # resolving or invoking mysql. Their config is always the sandbox .env.local created here.
    $repairMysqlShim = Join-Path $tempRoot 'repair-mysql-shim'
    New-Item -ItemType Directory -Path $repairMysqlShim | Out-Null
    $repairMysqlMarker = Join-Path $tempRoot 'repair-mysql-invoked.txt'
    @('@echo off', 'echo invoked>>"%FAKE_MYSQL_MARKER%"', 'exit /b 0') | Set-Content -LiteralPath (Join-Path $repairMysqlShim 'mysql.cmd') -Encoding ASCII
    $env:FAKE_MYSQL_MARKER = $repairMysqlMarker
    $previousRepairPath = $env:PATH
    $env:PATH = "$repairMysqlShim;$previousRepairPath"
    try {
        # Positive controls: the fake MySQL command must resolve and run for a valid target.
        # Its marker-only response contains no rows, so the scripts stop before any write.
        foreach ($repairScript in @('ops\windows\repair-demo-passwords.ps1', 'ops\windows\repair-demo-activation.ps1')) {
            Remove-Item -LiteralPath $repairMysqlMarker -Force -ErrorAction SilentlyContinue
            Set-SandboxDbConfig $validDbConfig
            $positiveResult = Invoke-ChildPowerShell (Join-Path $sandbox $repairScript) $sandbox
            Assert-That (Test-Path -LiteralPath $repairMysqlMarker) "$repairScript positive control did not invoke the fake MySQL command."
        }

        function Assert-RepairGuardRejects([string]$ScriptRelativePath, [string[]]$ConfigLines, [string]$Description) {
            Remove-Item -LiteralPath $repairMysqlMarker -Force -ErrorAction SilentlyContinue
            Set-SandboxDbConfig $ConfigLines
            $result = Invoke-ChildPowerShell (Join-Path $sandbox $ScriptRelativePath) $sandbox
            if ($result.ExitCode -eq 0) { $guardFailures.Add("$ScriptRelativePath accepted $Description.") }
            if (Test-Path -LiteralPath $repairMysqlMarker) { $guardFailures.Add("$ScriptRelativePath invoked mysql before rejecting $Description.") }
        }

        foreach ($repairScript in @('ops\windows\repair-demo-passwords.ps1', 'ops\windows\repair-demo-activation.ps1')) {
            foreach ($requiredKey in @('DB_HOST', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD')) {
                $withoutKey = @($validDbConfig | Where-Object { $_ -notmatch "^$requiredKey=" })
                Assert-RepairGuardRejects $repairScript $withoutKey "missing $requiredKey"
                $blankKey = @($validDbConfig | Where-Object { $_ -notmatch "^$requiredKey=" }) + "$requiredKey="
                Assert-RepairGuardRejects $repairScript $blankKey "blank $requiredKey"
            }
            foreach ($target in $wrongStartApiTargets) {
                $wrongConfig = @($validDbConfig | Where-Object { $_ -notmatch "^$($target.Key)=" }) + "$($target.Key)=$($target.Value)"
                Assert-RepairGuardRejects $repairScript $wrongConfig $target.Description
            }
        }
    }
    finally { $env:PATH = $previousRepairPath }

    # This case must fail before Maven starts when the application password is absent.
    Remove-Item -LiteralPath $mvnMarker -Force -ErrorAction SilentlyContinue
    Set-SandboxDbConfig @('DB_HOST=127.0.0.1', 'DB_NAME=elderly_care_demo', 'DB_USERNAME=elderly_app')
    $missingPassword = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\run-test.ps1') $sandbox
    Assert-That ($missingPassword.ExitCode -ne 0) 'run-test accepted missing DB_PASSWORD.'
    Assert-That (-not (Test-Path -LiteralPath $mvnMarker)) 'run-test launched Maven before rejecting missing DB_PASSWORD.'

    # This case must preserve Maven's failing exit code for calling scripts and CI.
    Set-SandboxDbConfig @('DB_HOST=localhost', 'DB_NAME=elderly_care_demo', 'DB_USERNAME=elderly_app', 'DB_PASSWORD=synthetic-test-only')
    $env:FAKE_MVN_EXIT_CODE = '23'
    $mavenFailure = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\run-test.ps1') $sandbox
    Assert-That ($mavenFailure.ExitCode -eq 23) "run-test returned $($mavenFailure.ExitCode) after Maven returned 23."

    if ($guardFailures.Count -gt 0) { throw "Expected entrypoint guard failures:`n - $($guardFailures -join "`n - ")" }

    Write-Output 'PASS: start-api rejected missing/blank DB config and unsafe targets before Maven'
    Write-Output 'PASS: run-test rejected unsafe DB targets before Maven'
    Write-Output 'PASS: repair scripts rejected missing/blank/unsafe targets before mysql'

    Assert-That (Test-Path -LiteralPath $mysqlServerExe) 'A local MySQL server binary is required for the isolated SQL guard test.'
    $dataDirectory = Join-Path $tempRoot 'mysql-data'
    $errorLog = Join-Path $tempRoot 'mysql-error.log'
    $serverPort = Get-Random -Minimum 34000 -Maximum 48000
    $init = Start-Process -FilePath $mysqlServerExe -ArgumentList @('--no-defaults', '--initialize-insecure', "--basedir=$((Split-Path -Parent (Split-Path -Parent $mysqlExe)))", "--datadir=$dataDirectory", '--console') -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $tempRoot 'mysql-init.out') -RedirectStandardError (Join-Path $tempRoot 'mysql-init.err')
    Assert-That ($init.ExitCode -eq 0) 'Isolated MySQL initialization failed.'

    $serverStdout = Join-Path $tempRoot 'mysql-server.out'
    $serverStderr = Join-Path $tempRoot 'mysql-server.err'
    $server = Start-Process -FilePath $mysqlServerExe -ArgumentList @('--no-defaults', "--basedir=$((Split-Path -Parent (Split-Path -Parent $mysqlExe)))", "--datadir=$dataDirectory", "--port=$serverPort", '--bind-address=127.0.0.1', "--log-error=$errorLog", '--console') -PassThru -WindowStyle Hidden -RedirectStandardOutput $serverStdout -RedirectStandardError $serverStderr
    try {
        $ready = $false
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            if ($server.HasExited) {
                $startupDetail = @($errorLog, $serverStdout, $serverStderr) | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Get-Content -LiteralPath $_ -Raw }
                if (-not $startupDetail) { $startupDetail = 'no server output' }
                throw "Isolated MySQL stopped during startup: $startupDetail"
            }
            $probe = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT 1;')
            if ($probe.ExitCode -eq 0 -and $probe.Output.Trim() -eq '1') { $ready = $true; break }
            Start-Sleep -Milliseconds 250
        }
        if (-not $ready) {
            $readinessDetail = @($errorLog, $serverStdout, $serverStderr) | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Get-Content -LiteralPath $_ -Raw }
            throw "Isolated MySQL did not become ready: $($readinessDetail -join "`n")"
        }

        $setup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-e', 'CREATE DATABASE wrong_seed_target; CREATE TABLE wrong_seed_target.elder_correction_item (id INT PRIMARY KEY); CREATE TABLE wrong_seed_target.business_file_link (id INT PRIMARY KEY); INSERT INTO wrong_seed_target.elder_correction_item VALUES (1); INSERT INTO wrong_seed_target.business_file_link VALUES (1);')
        Assert-That ($setup.ExitCode -eq 0) 'Could not prepare isolated wrong-database fixture.'
        $seedFile = Join-Path $sandbox 'database\seed\demo_synthetic_seed_m01_m02.sql'
        $seedSql = Get-Content -LiteralPath $seedFile -Raw -Encoding UTF8
        $guardResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=wrong_seed_target', '--default-character-set=utf8mb4', "--init-command=SET SESSION sql_mode=''") $seedSql
        Assert-That ($guardResult.ExitCode -ne 0) 'Seed file did not reject an incorrect database.'
        Assert-That ($guardResult.Output -match 'DEMO_SEED_WRONG_DATABASE') 'Wrong-database seed failure did not come from the guard before table operations.'
        $countResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM wrong_seed_target.elder_correction_item;')
        Assert-That ($countResult.ExitCode -eq 0 -and $countResult.Output.Trim() -eq '1') 'Wrong-database seed attempt changed rows before rejecting the target.'
        $secondSeedResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=wrong_seed_target', '--default-character-set=utf8mb4', "--init-command=SET SESSION sql_mode=''") (Get-Content -LiteralPath (Join-Path $sandbox 'database\seed\demo_synthetic_seed_m03_m12.sql') -Raw -Encoding UTF8)
        Assert-That ($secondSeedResult.ExitCode -ne 0 -and $secondSeedResult.Output -match 'DEMO_SEED_WRONG_DATABASE') 'M03-M12 seed file did not reject the incorrect database at its guard.'
        $secondSeedCount = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM wrong_seed_target.business_file_link;')
        Assert-That ($secondSeedCount.ExitCode -eq 0 -and $secondSeedCount.Output.Trim() -eq '1') 'M03-M12 wrong-database attempt changed rows before rejecting the target.'

        $mysqlShim = Join-Path $tempRoot 'mysql-shim'
        New-Item -ItemType Directory -Path $mysqlShim | Out-Null
        $mysqlMarker = Join-Path $tempRoot 'mysql-invoked.txt'
        @('@echo off', 'echo invoked>>"%FAKE_MYSQL_MARKER%"', 'exit /b 0') | Set-Content -LiteralPath (Join-Path $mysqlShim 'mysql.cmd') -Encoding ASCII
        @('DB_HOST=127.0.0.1', "DB_PORT=$serverPort", 'DB_NAME=wrong_seed_target', 'DB_USERNAME=elderly_app', 'DB_PASSWORD=synthetic-test-only') |
            Set-Content -LiteralPath (Join-Path $sandbox '.env.local') -Encoding ASCII
        $previousPath = $env:PATH
        $env:FAKE_MYSQL_MARKER = $mysqlMarker
        try {
            $env:PATH = "$mysqlShim;$previousPath"
            $wrongDatabaseLoad = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        }
        finally { $env:PATH = $previousPath }
        Assert-That ($wrongDatabaseLoad.ExitCode -ne 0) 'load-demo-seed accepted a non-demo DB_NAME.'
        Assert-That (-not (Test-Path -LiteralPath $mysqlMarker)) 'load-demo-seed contacted mysql before rejecting a non-demo DB_NAME.'
        $wrongDatabaseRow = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM wrong_seed_target.elder_correction_item;')
        Assert-That ($wrongDatabaseRow.ExitCode -eq 0 -and $wrongDatabaseRow.Output.Trim() -eq '1') 'Non-demo DB_NAME negative changed a pre-existing row.'

        $dbSetup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-e', "CREATE DATABASE elderly_care_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER 'elderly_app'@'%' IDENTIFIED BY 'synthetic-test-only'; GRANT ALL PRIVILEGES ON elderly_care_demo.* TO 'elderly_app'@'%';")
        Assert-That ($dbSetup.ExitCode -eq 0) 'Could not prepare the isolated demo database and test-only account.'
        foreach ($migration in @('V1__bootstrap_empty_schema.sql', 'V2__m01_m02_identity_and_archive.sql', 'V3__core_business_schema.sql')) {
            $migrationPath = Join-Path $sandbox "services\api\src\main\resources\db\migration\$migration"
            $migrationResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo') (Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8)
            Assert-That ($migrationResult.ExitCode -eq 0) "Could not initialize isolated schema from $migration."
        }
        $missingHistory = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\verify-db-v3.ps1') $sandbox
        Assert-That ($missingHistory.ExitCode -ne 0) 'verify-db-v3 reported success when its migration query failed.'
        $historySetup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL, version VARCHAR(50), description VARCHAR(200), execution_time INT NOT NULL, success BOOLEAN NOT NULL); INSERT INTO flyway_schema_history VALUES (1, '1', 'bootstrap', 1, 1), (2, '2', 'identity', 1, 1), (3, '3', 'business', 1, 1);")
        Assert-That ($historySetup.ExitCode -eq 0) 'Could not prepare the isolated Flyway history fixture.'
        @('DB_HOST=127.0.0.1', "DB_PORT=$serverPort", 'DB_NAME=elderly_care_demo', 'DB_USERNAME=elderly_app', 'DB_PASSWORD=synthetic-test-only') |
            Set-Content -LiteralPath (Join-Path $sandbox '.env.local') -Encoding ASCII

        # Fail after the first seed file has run: the wrapper must leave no partial data behind.
        Add-Content -LiteralPath (Join-Path $sandbox 'database\seed\demo_synthetic_seed_m03_m12.sql') -Value "`nINSERT INTO `_demo_seed_forced_failure` (`id`) VALUES (1);`n" -Encoding UTF8
        $failedImport = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        Assert-That ($failedImport.ExitCode -ne 0) 'load-demo-seed reported success after a forced import failure.'
        $rollbackCounts = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT (SELECT COUNT(*) FROM elderly_care_demo.sys_user), (SELECT COUNT(*) FROM elderly_care_demo.vital_record), (SELECT COUNT(*) FROM elderly_care_demo.service_catalog), (SELECT COUNT(*) FROM elderly_care_demo.notification_delivery), (SELECT COUNT(*) FROM elderly_care_demo.audit_log);')
        Assert-That ($rollbackCounts.ExitCode -eq 0 -and $rollbackCounts.Output.Trim() -eq "0`t0`t0`t0`t0") 'Failed cross-module import left partial rows behind in the isolated database.'
        Copy-Item -LiteralPath (Join-Path $repoRoot 'database\seed\demo_synthetic_seed_m03_m12.sql') -Destination (Join-Path $sandbox 'database\seed\demo_synthetic_seed_m03_m12.sql') -Force

        $loaderPath = Join-Path $sandbox 'ops\windows\load-demo-seed.ps1'
        $loaderText = [IO.File]::ReadAllText($loaderPath)
        $loaderText = $loaderText.Replace('$expectedCounts = @(6, 3, 3, 1, 4, 3, 5', '$expectedCounts = @(7, 3, 3, 1, 4, 3, 5')
        [IO.File]::WriteAllText($loaderPath, $loaderText, [Text.UTF8Encoding]::new($false))
        $statsGateFailure = Invoke-ChildPowerShell $loaderPath $sandbox
        Assert-That ($statsGateFailure.ExitCode -ne 0) 'load-demo-seed committed when the pre-commit statistics gate failed.'
        $statsGateRollback = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT (SELECT COUNT(*) FROM elderly_care_demo.sys_user), (SELECT COUNT(*) FROM elderly_care_demo.vital_record), (SELECT COUNT(*) FROM elderly_care_demo.sys_community);')
        Assert-That ($statsGateRollback.ExitCode -eq 0 -and $statsGateRollback.Output.Trim() -eq "0`t0`t0") 'Failed pre-commit statistics gate left seed rows behind.'
        Copy-Item -LiteralPath (Join-Path $repoRoot 'ops\windows\load-demo-seed.ps1') -Destination $loaderPath -Force

        # Reproduce the race directly: finish the empty-table preflight, insert a
        # concurrent row from another session, then execute both seed files.
        $seedPaths = @((Join-Path $sandbox 'database\seed\demo_synthetic_seed_m01_m02.sql'), (Join-Path $sandbox 'database\seed\demo_synthetic_seed_m03_m12.sql'))
        $loaderSourceForRace = Get-Content -LiteralPath (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') -Raw
        $tableBlockForRace = [regex]::Match($loaderSourceForRace, '(?s)\$seedTables\s*=\s*@\((.*?)\)')
        $seedDeleteTables = @([regex]::Matches($tableBlockForRace.Groups[1].Value, "'([^']+)'") | ForEach-Object { $_.Groups[1].Value })
        $raceEmptyChecks = ($seedDeleteTables | ForEach-Object { "SELECT COUNT(*) AS row_count FROM ``$_``" }) -join "`nUNION ALL`n"
        $racePreflight = @'
CREATE TEMPORARY TABLE `_race_preflight` (`gate_id` TINYINT NOT NULL PRIMARY KEY);
INSERT INTO `_race_preflight` (`gate_id`) VALUES (0);
INSERT INTO `_race_preflight` (`gate_id`) SELECT IF(GET_LOCK('elderly_care_demo_first_load', 0) = 1 AND (SELECT SUM(row_count) FROM (__RACE_EMPTY_CHECKS__) AS seed_rows) = 0, 1, 0);
SELECT 'PREFLIGHT_DONE';
'@ -replace '__RACE_EMPTY_CHECKS__', $raceEmptyChecks
        $raceSession = Start-MysqlSession @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '--batch', '--skip-column-names', '--unbuffered')
        try {
            $raceSession.StandardInput.WriteLine($racePreflight)
            $raceSession.StandardInput.Flush()
            $raceMarker = Wait-MysqlLine $raceSession
            if ($raceMarker -ne 'PREFLIGHT_DONE') {
                $raceDiagnostic = $raceSession.StandardError.ReadToEnd()
                throw "Concurrent-write fixture did not reach the post-preflight point (exit=$($raceSession.ExitCode)): $raceMarker $raceDiagnostic"
            }
            $concurrentInsert = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "INSERT INTO sys_community (community_id, community_code, community_name, status, created_at, updated_at) VALUES (99, 'CONCURRENT_TEST', '并发写入保留行', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));")
            Assert-That ($concurrentInsert.ExitCode -eq 0) 'Could not insert the concurrent row after preflight.'
            $raceSeedSql = "SET @demo_seed_authorized = 'first-load-synthetic-demo-v1';`r`nSTART TRANSACTION;`r`n" + (Get-Content -LiteralPath $seedPaths[0] -Raw -Encoding UTF8) + "`r`n" + (Get-Content -LiteralPath $seedPaths[1] -Raw -Encoding UTF8) + "`r`nCOMMIT;`r`nSELECT 'SEED_DONE';`r`n"
            $raceSession.StandardInput.Write($raceSeedSql)
            $raceSession.StandardInput.Close()
            $raceOutput = $raceSession.StandardOutput.ReadToEnd()
            $raceError = $raceSession.StandardError.ReadToEnd()
            $raceSession.WaitForExit()
            Assert-That ($raceSession.ExitCode -eq 0 -and $raceOutput -match 'SEED_DONE') "Seed SQL failed after the concurrent row was inserted. $raceOutput $raceError"
        }
        finally {
            if (-not $raceSession.HasExited) { Stop-Process -Id $raceSession.Id -Force -ErrorAction SilentlyContinue }
        }
        $preservedConcurrentRow = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', "SELECT COUNT(*) FROM elderly_care_demo.sys_community WHERE community_id = 99 AND community_code = 'CONCURRENT_TEST';")
        Assert-That ($preservedConcurrentRow.ExitCode -eq 0 -and $preservedConcurrentRow.Output.Trim() -eq '1') 'First-load seed SQL deleted a concurrent row created after its empty-table preflight.'

        # Recreate only the temporary test database so the wrapper's normal first-load path can run cleanly.
        $recreate = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-e', "DROP DATABASE elderly_care_demo; CREATE DATABASE elderly_care_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON elderly_care_demo.* TO 'elderly_app'@'%';")
        Assert-That ($recreate.ExitCode -eq 0) 'Could not reset the isolated test database after the concurrency scenario.'
        foreach ($migration in @('V1__bootstrap_empty_schema.sql', 'V2__m01_m02_identity_and_archive.sql', 'V3__core_business_schema.sql')) {
            $migrationPath = Join-Path $sandbox "services\api\src\main\resources\db\migration\$migration"
            $migrationResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo') (Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8)
            Assert-That ($migrationResult.ExitCode -eq 0) "Could not reinitialize isolated schema from $migration."
        }
        $historySetup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL, version VARCHAR(50), description VARCHAR(200), execution_time INT NOT NULL, success BOOLEAN NOT NULL); INSERT INTO flyway_schema_history VALUES (1, '1', 'bootstrap', 1, 1), (2, '2', 'identity', 1, 1), (3, '3', 'business', 1, 1);")
        Assert-That ($historySetup.ExitCode -eq 0) 'Could not recreate the isolated Flyway history fixture.'

        # Exercise the complete wrapper path: inject a concurrent row after the
        # preflight and seed writes, but before the in-transaction 43-table gate.
        $raceSeedPath = Join-Path $sandbox 'database\seed\demo_synthetic_seed_m03_m12.sql'
        Add-Content -LiteralPath $raceSeedPath -Value "`nSELECT SLEEP(10);`n" -Encoding UTF8
        $seedTableSql = ($seedDeleteTables | ForEach-Object { "SELECT COUNT(*) AS row_count FROM ``$_``" }) -join "`nUNION ALL`n"
        $seedTableNamesForDiagnostic = ($seedDeleteTables | ForEach-Object { "'$_'" }) -join ', '
        $preflightDiagnostic = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-N', '-e', "SELECT IS_FREE_LOCK('elderly_care_demo_first_load'), (SELECT SUM(row_count) FROM ($seedTableSql) AS seed_rows), (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ($seedTableNamesForDiagnostic) AND engine = 'InnoDB');")
        Assert-That ($preflightDiagnostic.ExitCode -eq 0 -and $preflightDiagnostic.Output.Trim() -eq "1`t0`t43") "Isolated setup did not meet loader preflight conditions: $($preflightDiagnostic.Output)"
        $raceLoader = Start-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        try {
            $gateWindowOpen = $false
            for ($attempt = 0; $attempt -lt 80; $attempt++) {
                if ($raceLoader.HasExited) { break }
                $sleepProbe = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', "SELECT COUNT(*) FROM information_schema.processlist WHERE STATE = 'User sleep' AND ID <> CONNECTION_ID();")
                if ($sleepProbe.ExitCode -eq 0 -and $sleepProbe.Output.Trim() -eq '1') { $gateWindowOpen = $true; break }
                Start-Sleep -Milliseconds 125
            }
            Assert-That $gateWindowOpen 'The full loader did not reach the controlled pre-commit concurrency window.'
            $concurrentExtraRow = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "INSERT INTO idempotency_record (principal_scope, operation_scope, idempotency_key, request_hash, response_status, response_body, trace_id, expires_at) VALUES ('test-race', 'POST /test', 'gate-race', REPEAT('a', 64), 200, '{}', 'gate-race-test', DATE_ADD(NOW(3), INTERVAL 1 DAY));")
            Assert-That ($concurrentExtraRow.ExitCode -eq 0) 'Could not insert a concurrent extra row in a zero-expected seed table.'
            $raceLoader.WaitForExit()
            $raceOutput = $raceLoader.StandardOutput.ReadToEnd() + $raceLoader.StandardError.ReadToEnd()
            Assert-That ($raceLoader.ExitCode -ne 0) "The complete loader reported success despite an extra row in a zero-expected table. $raceOutput"
            Assert-That ($raceOutput -match '提交状态待核对') "The concurrent-row gate failure did not state that commit status requires verification. $raceOutput"
        }
        finally {
            if (-not $raceLoader.HasExited) { Stop-Process -Id $raceLoader.Id -Force -ErrorAction SilentlyContinue }
        }
        $raceRows = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT (SELECT COUNT(*) FROM elderly_care_demo.idempotency_record), (SELECT COUNT(*) FROM elderly_care_demo.sys_user);')
        Assert-That ($raceRows.ExitCode -eq 0 -and $raceRows.Output.Trim() -eq "1`t0") 'The complete loader did not preserve the concurrent row while rolling back seed data.'
        $loaderConcurrencySource = Get-Content -LiteralPath (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') -Raw
        Assert-That ($loaderConcurrencySource -match '\$expectedSeedCounts\[\$table\]\s*=\s*0' -and $loaderConcurrencySource -match '(?s)foreach\s*\(\$table\s+in\s+\$seedTables\)\s*\{\s*"\(SELECT COUNT\(\*\) FROM ``\$table``\)\s*=\s*\$\(\$expectedSeedCounts\[\$table\]\)') 'The commit gate does not compare every seed target table against its expected count.'
        Copy-Item -LiteralPath (Join-Path $repoRoot 'database\seed\demo_synthetic_seed_m03_m12.sql') -Destination $raceSeedPath -Force

        # The concurrent extra row is intentionally preserved, so recreate
        # only this isolated database before exercising the ordinary success path.
        $recreate = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-e', "DROP DATABASE elderly_care_demo; CREATE DATABASE elderly_care_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON elderly_care_demo.* TO 'elderly_app'@'%';")
        Assert-That ($recreate.ExitCode -eq 0) 'Could not reset the isolated database after the controlled concurrency scenario.'
        foreach ($migration in @('V1__bootstrap_empty_schema.sql', 'V2__m01_m02_identity_and_archive.sql', 'V3__core_business_schema.sql')) {
            $migrationPath = Join-Path $sandbox "services\api\src\main\resources\db\migration\$migration"
            $migrationResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo') (Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8)
            Assert-That ($migrationResult.ExitCode -eq 0) "Could not reinitialize isolated schema from $migration after the concurrency scenario."
        }
        $historySetup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL, version VARCHAR(50), description VARCHAR(200), execution_time INT NOT NULL, success BOOLEAN NOT NULL); INSERT INTO flyway_schema_history VALUES (1, '1', 'bootstrap', 1, 1), (2, '2', 'identity', 1, 1), (3, '3', 'business', 1, 1);")
        Assert-That ($historySetup.ExitCode -eq 0) 'Could not recreate the isolated Flyway history fixture after the concurrency scenario.'

        $successfulImport = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        Assert-That ($successfulImport.ExitCode -eq 0) "load-demo-seed rejected the valid isolated first load. $($successfulImport.Output)"
        Write-Output $successfulImport.Output
        $successCount = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM elderly_care_demo.sys_user;')
        Assert-That ($successCount.ExitCode -eq 0 -and $successCount.Output.Trim() -eq '6') 'Successful first load did not create the expected user count.'
        $verified = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\verify-db-v3.ps1') $sandbox
        Assert-That ($verified.ExitCode -eq 0) "verify-db-v3 rejected the complete isolated V3 seed state. $($verified.Output)"

        # A client-side failure after the mysql process has executed COMMIT is
        # an indeterminate outcome, not proof that the server rolled back.
        $postCommitShim = Join-Path $tempRoot 'mysql-post-commit-shim'
        New-Item -ItemType Directory -Path $postCommitShim | Out-Null
        $postCommitShimScript = @'
@echo off
set "HAS_EXECUTE=0"
for %%A in (%*) do if /I "%%~A"=="--execute" set "HAS_EXECUTE=1"
"%FAKE_MYSQL_REAL%" %*
set "MYSQL_EXIT=%ERRORLEVEL%"
if "%HAS_EXECUTE%"=="0" if "%MYSQL_EXIT%"=="0" exit /b 17
exit /b %MYSQL_EXIT%
'@
        Set-Content -LiteralPath (Join-Path $postCommitShim 'mysql.cmd') -Value $postCommitShimScript -Encoding ASCII
        $preShimPath = $env:PATH
        $env:FAKE_MYSQL_REAL = $mysqlExe
        try {
            $env:PATH = "$postCommitShim;$preShimPath"
            $commitAckFailure = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        }
        finally { $env:PATH = $preShimPath }
        Assert-That ($commitAckFailure.ExitCode -ne 0) 'load-demo-seed accepted a nonzero mysql client exit after COMMIT.'
        Assert-That ($commitAckFailure.Output -match '提交状态待核对') 'A nonzero mysql exit after COMMIT was incorrectly described as a confirmed rollback.'
        $committedAfterClientFailure = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM elderly_care_demo.sys_user;')
        Assert-That ($committedAfterClientFailure.ExitCode -eq 0 -and $committedAfterClientFailure.Output.Trim() -eq '6') 'The post-COMMIT client-failure fixture did not leave committed seed rows for the indeterminate outcome check.'

        # Separately fail the wrapper's read-only post-COMMIT statistics query.
        $recreate = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-e', "DROP DATABASE elderly_care_demo; CREATE DATABASE elderly_care_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON elderly_care_demo.* TO 'elderly_app'@'%';")
        Assert-That ($recreate.ExitCode -eq 0) 'Could not reset the isolated database for the post-commit statistics failure case.'
        foreach ($migration in @('V1__bootstrap_empty_schema.sql', 'V2__m01_m02_identity_and_archive.sql', 'V3__core_business_schema.sql')) {
            $migrationPath = Join-Path $sandbox "services\api\src\main\resources\db\migration\$migration"
            $migrationResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo') (Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8)
            Assert-That ($migrationResult.ExitCode -eq 0) "Could not reinitialize isolated schema from $migration for post-commit statistics failure."
        }
        $historySetup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL, version VARCHAR(50), description VARCHAR(200), execution_time INT NOT NULL, success BOOLEAN NOT NULL); INSERT INTO flyway_schema_history VALUES (1, '1', 'bootstrap', 1, 1), (2, '2', 'identity', 1, 1), (3, '3', 'business', 1, 1);")
        Assert-That ($historySetup.ExitCode -eq 0) 'Could not recreate the isolated Flyway history fixture for post-commit statistics failure.'
        $statsFailureShim = Join-Path $tempRoot 'mysql-stats-failure-shim'
        New-Item -ItemType Directory -Path $statsFailureShim | Out-Null
        $statsFailureScript = @'
@echo off
set "HAS_BATCH=0"
set "HAS_EXECUTE=0"
for %%A in (%*) do (
  if /I "%%~A"=="--batch" set "HAS_BATCH=1"
  if /I "%%~A"=="--execute" set "HAS_EXECUTE=1"
)
if "%HAS_BATCH%"=="1" if "%HAS_EXECUTE%"=="1" exit /b 31
"%FAKE_MYSQL_REAL%" %*
exit /b %ERRORLEVEL%
'@
        Set-Content -LiteralPath (Join-Path $statsFailureShim 'mysql.cmd') -Value $statsFailureScript -Encoding ASCII
        $preShimPath = $env:PATH
        try {
            $env:PATH = "$statsFailureShim;$preShimPath"
            $postStatsFailure = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        }
        finally { $env:PATH = $preShimPath }
        Assert-That ($postStatsFailure.ExitCode -ne 0) 'load-demo-seed reported success when the post-commit statistics query failed.'
        Assert-That ($postStatsFailure.Output -match '事务已提交' -and $postStatsFailure.Output -match '勿直接重试' -and $postStatsFailure.Output -notmatch '提交状态待核对') 'A post-COMMIT statistics query failure did not report the confirmed commit and warn against retrying.'
        $committedAfterStatsFailure = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM elderly_care_demo.sys_user;')
        Assert-That ($committedAfterStatsFailure.ExitCode -eq 0 -and $committedAfterStatsFailure.Output.Trim() -eq '6') 'The post-COMMIT statistics failure fixture did not retain the committed seed rows.'

        # Force a well-formed but mismatching post-COMMIT statistics response.
        $recreate = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-e', "DROP DATABASE elderly_care_demo; CREATE DATABASE elderly_care_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON elderly_care_demo.* TO 'elderly_app'@'%';")
        Assert-That ($recreate.ExitCode -eq 0) 'Could not reset the isolated database for the post-commit statistics mismatch case.'
        foreach ($migration in @('V1__bootstrap_empty_schema.sql', 'V2__m01_m02_identity_and_archive.sql', 'V3__core_business_schema.sql')) {
            $migrationPath = Join-Path $sandbox "services\api\src\main\resources\db\migration\$migration"
            $migrationResult = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo') (Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8)
            Assert-That ($migrationResult.ExitCode -eq 0) "Could not reinitialize isolated schema from $migration for post-commit statistics mismatch."
        }
        $historySetup = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '--database=elderly_care_demo', '-e', "CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL, version VARCHAR(50), description VARCHAR(200), execution_time INT NOT NULL, success BOOLEAN NOT NULL); INSERT INTO flyway_schema_history VALUES (1, '1', 'bootstrap', 1, 1), (2, '2', 'identity', 1, 1), (3, '3', 'business', 1, 1);")
        Assert-That ($historySetup.ExitCode -eq 0) 'Could not recreate the isolated Flyway history fixture for post-commit statistics mismatch.'
        $statsMismatchShim = Join-Path $tempRoot 'mysql-stats-mismatch-shim'
        New-Item -ItemType Directory -Path $statsMismatchShim | Out-Null
        $mismatchingStatsLine = "7`t3`t3`t1`t4`t3`t5`t1`t1`t2`t2`t1`t1`t2`t2`t1"
        $statsMismatchScript = @"
@echo off
set "HAS_BATCH=0"
set "HAS_EXECUTE=0"
for %%A in (%*) do (
  if /I "%%~A"=="--batch" set "HAS_BATCH=1"
  if /I "%%~A"=="--execute" set "HAS_EXECUTE=1"
)
if "%HAS_BATCH%"=="1" if "%HAS_EXECUTE%"=="1" (
  echo $mismatchingStatsLine
  exit /b 0
)
"%FAKE_MYSQL_REAL%" %*
exit /b %ERRORLEVEL%
"@
        Set-Content -LiteralPath (Join-Path $statsMismatchShim 'mysql.cmd') -Value $statsMismatchScript -Encoding ASCII
        $preShimPath = $env:PATH
        try {
            $env:PATH = "$statsMismatchShim;$preShimPath"
            $postStatsMismatch = Invoke-ChildPowerShell (Join-Path $sandbox 'ops\windows\load-demo-seed.ps1') $sandbox
        }
        finally { $env:PATH = $preShimPath }
        Assert-That ($postStatsMismatch.ExitCode -ne 0) 'load-demo-seed reported success when post-commit statistics did not match.'
        Assert-That ($postStatsMismatch.Output -match '事务已提交' -and $postStatsMismatch.Output -match '勿直接重试' -and $postStatsMismatch.Output -notmatch '提交状态待核对') 'A post-COMMIT statistics mismatch did not report the confirmed commit and warn against retrying.'
        $committedAfterStatsMismatch = Invoke-Mysql @('-h127.0.0.1', "-P$serverPort", '-uroot', '-N', '-e', 'SELECT COUNT(*) FROM elderly_care_demo.sys_user;')
        Assert-That ($committedAfterStatsMismatch.ExitCode -eq 0 -and $committedAfterStatsMismatch.Output.Trim() -eq '6') 'The post-COMMIT statistics mismatch fixture did not retain the committed seed rows.'
    }
    finally {
        $isolatedServers = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($dataDirectory) -and $_.CommandLine.Contains('--no-defaults') })
        $isolatedServerIds = @($isolatedServers | ForEach-Object { $_.ProcessId })
        if ($isolatedServerIds.Count -gt 0) { Stop-Process -Id $isolatedServerIds -Force -ErrorAction SilentlyContinue }
        if (-not $server.HasExited) { $server.WaitForExit() }
    }

    Write-Output 'PASS: missing password rejected before Maven'
    Write-Output 'PASS: Maven failure exit code propagated'
    Write-Output 'PASS: wrong database rejected before first business INSERT in isolated MySQL'
    Write-Output 'PASS: load wrapper rejected wrong DB_NAME before mysql and preserved the pre-existing row'
    Write-Output 'PASS: failed two-file import rolled back in isolated MySQL'
    Write-Output 'PASS: a row inserted after preflight survived the first-load seed SQL'
    Write-Output 'PASS: complete loader rejected a concurrent extra row in a zero-expected table and preserved it'
    Write-Output 'PASS: successful two-file import populated isolated MySQL'
    Write-Output 'PASS: verify-db-v3 rejected query failure and accepted complete V3 seed statistics'
    Write-Output 'PASS: post-COMMIT client failure remains indeterminate while statistics failures and mismatches state commit is confirmed'
}
finally {
    $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
    $resolvedTempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ([IO.Path]::GetFileName($resolvedTempRoot) -eq $tempRootLeaf -and $resolvedTempRoot.StartsWith($resolvedTempBase, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
