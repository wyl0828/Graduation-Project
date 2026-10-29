# One-time isolated rehearsal using only this run's synthetic database and files.
# It never loads .env.local, connects to an existing database, or reads production storage.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
$dumpCommand = Get-Command mysqldump -ErrorAction SilentlyContinue
$adminCommand = Get-Command mysqladmin -ErrorAction SilentlyContinue
$pwshCommand = Get-Command pwsh -ErrorAction SilentlyContinue
$mysqlExe = if ($mysqlCommand) { $mysqlCommand.Source } else { $null }
$mysqlDumpExe = if ($dumpCommand) { $dumpCommand.Source } else { $null }
$mysqlAdminExe = if ($adminCommand) { $adminCommand.Source } else { $null }
$pwshExe = if ($pwshCommand) { $pwshCommand.Source } else { $null }
$mysqlBin = if ($mysqlExe) { Split-Path -Parent $mysqlExe } else { $null }
$mysqlServerExe = if ($mysqlBin) { Join-Path $mysqlBin 'mysqld.exe' } else { $null }
$mysqlBase = if ($mysqlBin) { Split-Path -Parent $mysqlBin } else { $null }
$mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'
$seedWrapper = Join-Path $repoRoot 'ops\windows\load-demo-seed.ps1'

$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$tempBaseInfo = Get-Item -LiteralPath $tempBase -Force -ErrorAction Stop
if (($tempBaseInfo.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'The Windows temporary directory is a reparse point; refusing to use it.'
}
$tempRootLeaf = 'backup-restore-' + [Guid]::NewGuid().ToString('N')
$tempRoot = Join-Path $tempBase $tempRootLeaf
$mysqlData = Join-Path $tempRoot 'mysql-data'
$sourceFiles = Join-Path $tempRoot 'source\files'
$backupRoot = Join-Path $tempRoot 'backup'
$backupFiles = Join-Path $backupRoot 'files'
$dumpPath = Join-Path $backupRoot 'database.sql'
$manifestPath = Join-Path $backupRoot 'manifest.json'
$restoreFiles = Join-Path $tempRoot 'restore\files'
$damageRoot = Join-Path $tempRoot 'damage'
$script:mysqlProcess = $null
$script:apiProcess = $null
$script:apiLauncherPath = $null
$script:dbPort = $null
$script:apiPort = $null
$script:servicesStopped = $true
$script:failureMessage = $null
$script:cleanupSucceeded = $false
$script:tempRootCreated = $false
$script:appPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$script:hmacKey = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$script:mysqlExe = $mysqlExe
$script:mysqlDumpExe = $mysqlDumpExe
$script:mysqlAdminExe = $mysqlAdminExe
$script:mysqlServerExe = $mysqlServerExe
$script:mysqlBase = $mysqlBase
$script:tempRoot = $tempRoot
$script:mysqlData = $mysqlData
$script:repoRoot = $repoRoot
$script:pwshExe = $pwshExe
$script:mavenWrapper = $mavenWrapper
$script:sourceDb = 'elderly_care_demo'
$script:restoreDb = $null
$script:failedDb = $null
$script:sourceFiles = $sourceFiles
$script:backupRoot = $backupRoot
$script:backupFiles = $backupFiles
$script:restoreFiles = $restoreFiles
$script:dumpPath = $dumpPath
$script:manifestPath = $manifestPath
$script:damageRoot = $damageRoot
$script:rtoStopwatch = $null
$script:rtoSeconds = 0.0
$script:apiEnvClearPatterns = @(
    '^DB_', '^SPRING_', '^APP_', '^SERVER_', '^MANAGEMENT_', '^AUTH_', '^PHONE_',
    '^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_OPTS|MAVEN_ARGS)$'
)
$script:apiEnvKeys = @(
    [Environment]::GetEnvironmentVariables('Process').Keys |
        Where-Object {
            $name = [string]$_
            @($script:apiEnvClearPatterns | Where-Object { $name -match $_ }).Count -gt 0
        } |
        ForEach-Object { [string]$_ }
)
$script:apiEnvKeys += @(
    'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD',
    'SERVER_ADDRESS', 'SERVER_PORT', 'SPRING_PROFILES_ACTIVE',
    'AUTH_RATE_LIMIT_HMAC_KEY', 'AUTH_COOKIE_SECURE',
    'PHONE_VERIFICATION_ENABLED', 'APP_STORAGE_ROOT',
    'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS'
)
$script:apiEnvKeys = @($script:apiEnvKeys | Sort-Object -Unique)
$script:previousEnv = @{}
foreach ($key in $script:apiEnvKeys) {
    $script:previousEnv[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}

function ConvertTo-CommandLineArgument([string]$Argument) {
    $builder = [Text.StringBuilder]::new()
    [void]$builder.Append('"')
    $backslashCount = 0
    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq [char]92) {
            $backslashCount++
            continue
        }
        if ($character -eq [char]34) {
            if ($backslashCount -gt 0) {
                [void]$builder.Append(([string][char]92 * (2 * $backslashCount + 1)))
            } else {
                [void]$builder.Append([char]92)
            }
            [void]$builder.Append([char]34)
            $backslashCount = 0
            continue
        }
        if ($backslashCount -gt 0) {
            [void]$builder.Append(([string][char]92 * $backslashCount))
            $backslashCount = 0
        }
        [void]$builder.Append($character)
    }
    if ($backslashCount -gt 0) {
        [void]$builder.Append(([string][char]92 * (2 * $backslashCount)))
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function ConvertTo-ArgumentString([string[]]$Arguments) {
    return (($Arguments | ForEach-Object { ConvertTo-CommandLineArgument ([string]$_) }) -join ' ')
}

function Assert-PathInsideTempRoot([string]$Path) {
    $rootFull = [IO.Path]::GetFullPath($script:tempRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $candidateFull = [IO.Path]::GetFullPath($Path)
    $prefix = $rootFull + [IO.Path]::DirectorySeparatorChar
    if (-not $candidateFull.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase) -and
        -not $candidateFull.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'A requested path is outside this run-specific temporary root.'
    }
    return $candidateFull
}

function Assert-NoReparseTree([string]$Path) {
    $fullPath = Assert-PathInsideTempRoot $Path
    if (-not (Test-Path -LiteralPath $fullPath)) {
        return
    }
    $rootItem = Get-Item -LiteralPath $fullPath -Force -ErrorAction Stop
    if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'A temporary data path became a reparse point.'
    }
    if ($rootItem.PSIsContainer) {
        foreach ($item in Get-ChildItem -LiteralPath $fullPath -Recurse -Force -ErrorAction Stop) {
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'A child path became a reparse point; refusing to continue.'
            }
        }
    }
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
        if ($port -notin $Exclude) {
            return $port
        }
    }
    throw 'Could not select a free loopback TCP port.'
}

function Test-TcpPortOpen([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync([Net.IPAddress]::Loopback, $Port)
        if (-not $task.Wait(500)) {
            return $false
        }
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Invoke-Process(
    [string]$Executable,
    [string[]]$Arguments,
    [string]$InputText = '',
    [string]$Password = $null
) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.Arguments = ConvertTo-ArgumentString $Arguments
    $startInfo.WorkingDirectory = $script:tempRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = ($InputText.Length -gt 0)
    [void]$startInfo.EnvironmentVariables.Remove('MYSQL_PWD')
    if ($Password) {
        $startInfo.EnvironmentVariables['MYSQL_PWD'] = $Password
    }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'Could not start a required local process.'
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if ($InputText.Length -gt 0) {
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
    }
    $process.WaitForExit()
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        StdOut = $stdoutTask.GetAwaiter().GetResult()
        StdErr = $stderrTask.GetAwaiter().GetResult()
    }
}

function Invoke-MySql(
    [string]$Database,
    [string[]]$Arguments,
    [string]$InputText = '',
    [switch]$AsApp
) {
    if ($Database -and $Database -notmatch '^[a-z0-9_]+$') {
        throw 'Refusing a non-canonical temporary database name.'
    }
    $mysqlArguments = @(
        '--no-defaults',
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$($script:dbPort)",
        '--default-character-set=utf8mb4'
    )
    if ($AsApp) {
        $mysqlArguments += '--user=elderly_app'
    } else {
        $mysqlArguments += '--user=root'
    }
    if ($Database) {
        $mysqlArguments += "--database=$Database"
    }
    $mysqlArguments += $Arguments
    $password = if ($AsApp) { $script:appPassword } else { $null }
    return Invoke-Process -Executable $script:mysqlExe -Arguments $mysqlArguments -InputText $InputText -Password $password
}

function Invoke-MySqlQuery([string]$Database, [string]$Sql, [switch]$AsApp) {
    $result = Invoke-MySql -Database $Database -Arguments @('--batch', '--skip-column-names', '--execute', $Sql) -AsApp:$AsApp
    if ($result.ExitCode -ne 0) {
        throw 'An isolated MySQL query failed.'
    }
    return [string]$result.StdOut.Trim()
}

function New-IsolatedMysql {
    $script:dbPort = Get-RandomLoopbackPort
    $initArgs = @(
        '--no-defaults', '--initialize-insecure',
        "--basedir=$script:mysqlBase",
        "--datadir=$script:mysqlData",
        '--console'
    )
    $initStart = @{
        FilePath = $script:mysqlServerExe
        ArgumentList = ConvertTo-ArgumentString $initArgs
        WorkingDirectory = $script:tempRoot
        PassThru = $true
        WindowStyle = 'Hidden'
        Wait = $true
        RedirectStandardOutput = Join-Path $script:tempRoot 'mysql-init.out'
        RedirectStandardError = Join-Path $script:tempRoot 'mysql-init.err'
    }
    $init = Start-Process @initStart
    if ($init.ExitCode -ne 0) {
        throw 'Could not initialize the isolated MySQL datadir.'
    }

    $serverArgs = @(
        '--no-defaults',
        '--mysqlx=0',
        "--basedir=$script:mysqlBase",
        "--datadir=$script:mysqlData",
        "--port=$script:dbPort",
        '--bind-address=127.0.0.1',
        "--log-error=$(Join-Path $script:tempRoot 'mysql-error.log')",
        '--console'
    )
    $serverStart = @{
        FilePath = $script:mysqlServerExe
        ArgumentList = ConvertTo-ArgumentString $serverArgs
        WorkingDirectory = $script:tempRoot
        PassThru = $true
        WindowStyle = 'Hidden'
        RedirectStandardOutput = Join-Path $script:tempRoot 'mysql-server.out'
        RedirectStandardError = Join-Path $script:tempRoot 'mysql-server.err'
    }
    $script:mysqlProcess = Start-Process @serverStart
    $script:servicesStopped = $false

    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        $script:mysqlProcess.Refresh()
        if ($script:mysqlProcess.HasExited) {
            throw 'The run-specific MySQL process exited before readiness.'
        }
        if (Test-TcpPortOpen $script:dbPort) {
            $probe = Invoke-MySql -Database '' -Arguments @('--batch', '--skip-column-names', '--execute', 'SELECT @@port;')
            if ($probe.ExitCode -eq 0 -and $probe.StdOut.Trim() -eq [string]$script:dbPort) {
                $ready = $true
                break
            }
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) {
        throw 'The run-specific MySQL did not become ready on its loopback port.'
    }

    $dataProbe = Invoke-MySql -Database '' -Arguments @('--batch', '--skip-column-names', '--execute', 'SELECT @@datadir;')
    if ($dataProbe.ExitCode -ne 0) {
        throw 'Could not verify the isolated MySQL datadir.'
    }
    $actualData = [IO.Path]::GetFullPath($dataProbe.StdOut.Trim().TrimEnd([char]92, [char]47))
    $expectedData = [IO.Path]::GetFullPath($script:mysqlData).TrimEnd([char]92, [char]47)
    if (-not $actualData.Equals($expectedData, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The MySQL process is not using this run-specific datadir.'
    }
}

function New-TemporaryDatabase([string]$DatabaseName) {
    if ($DatabaseName -notmatch '^[a-z0-9_]+$') {
        throw 'Refusing to create a non-canonical temporary database name.'
    }
    $sql = "CREATE DATABASE $DatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'elderly_app'@'127.0.0.1' IDENTIFIED BY '$script:appPassword'; ALTER USER 'elderly_app'@'127.0.0.1' IDENTIFIED BY '$script:appPassword'; GRANT ALL PRIVILEGES ON $DatabaseName.* TO 'elderly_app'@'127.0.0.1'; FLUSH PRIVILEGES;"
    $result = Invoke-MySql -Database '' -Arguments @('--execute', $sql)
    if ($result.ExitCode -ne 0) {
        throw 'Could not create a database inside the run-specific MySQL instance.'
    }
}

function Start-IsolatedApi([string]$PhaseName, [string]$DatabaseName, [string]$StorageRoot) {
    Assert-PathInsideTempRoot $StorageRoot | Out-Null
    Assert-NoReparseTree $StorageRoot
    if (-not (Test-Path -LiteralPath $StorageRoot)) {
        New-Item -ItemType Directory -Path $StorageRoot -Force | Out-Null
    }

    $environment = [Environment]::GetEnvironmentVariables('Process')
    foreach ($key in @($environment.Keys)) {
        $name = [string]$key
        if (@($script:apiEnvClearPatterns | Where-Object { $name -match $_ }).Count -gt 0) {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
    }

    $script:apiPort = Get-RandomLoopbackPort -Exclude @($script:dbPort)
    [Environment]::SetEnvironmentVariable('DB_HOST', '127.0.0.1', 'Process')
    [Environment]::SetEnvironmentVariable('DB_PORT', [string]$script:dbPort, 'Process')
    [Environment]::SetEnvironmentVariable('DB_NAME', $DatabaseName, 'Process')
    [Environment]::SetEnvironmentVariable('DB_USERNAME', 'elderly_app', 'Process')
    [Environment]::SetEnvironmentVariable('DB_PASSWORD', $script:appPassword, 'Process')
    [Environment]::SetEnvironmentVariable('SERVER_ADDRESS', '127.0.0.1', 'Process')
    [Environment]::SetEnvironmentVariable('SERVER_PORT', [string]$script:apiPort, 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', 'local', 'Process')
    [Environment]::SetEnvironmentVariable('AUTH_RATE_LIMIT_HMAC_KEY', $script:hmacKey, 'Process')
    [Environment]::SetEnvironmentVariable('AUTH_COOKIE_SECURE', 'false', 'Process')
    [Environment]::SetEnvironmentVariable('PHONE_VERIFICATION_ENABLED', 'false', 'Process')
    [Environment]::SetEnvironmentVariable('APP_STORAGE_ROOT', $StorageRoot, 'Process')

    $launcherPath = Join-Path $script:tempRoot ("start-api-$PhaseName.ps1")
    Assert-PathInsideTempRoot $launcherPath | Out-Null
    $wrapperLiteral = "'" + $script:mavenWrapper.Replace("'", "''") + "'"
    $pomLiteral = "'" + (Join-Path $script:repoRoot 'services\api\pom.xml').Replace("'", "''") + "'"
    $workLiteral = "'" + $script:tempRoot.Replace("'", "''") + "'"
    $workingArg = "'-Dspring-boot.run.workingDirectory=" + $script:tempRoot.Replace("'", "''") + "'"
    $launcherLines = @(
        '$ErrorActionPreference = ''Continue'''
        ('Set-Location -LiteralPath ' + $workLiteral)
        ('& ' + $wrapperLiteral + ' -f ' + $pomLiteral + ' ''-Dspring-boot.run.profiles=local'' ' + $workingArg + ' spring-boot:run')
        'exit $LASTEXITCODE'
    )
    Set-Content -LiteralPath $launcherPath -Value ($launcherLines -join [Environment]::NewLine) -Encoding UTF8
    $pwshArgs = @('-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $launcherPath)
    $script:apiLauncherPath = $launcherPath
    $apiStart = @{
        FilePath = $script:pwshExe
        ArgumentList = ConvertTo-ArgumentString $pwshArgs
        WorkingDirectory = $script:tempRoot
        PassThru = $true
        WindowStyle = 'Hidden'
        RedirectStandardOutput = Join-Path $script:tempRoot "api-$PhaseName.out"
        RedirectStandardError = Join-Path $script:tempRoot "api-$PhaseName.err"
    }
    $script:apiProcess = Start-Process @apiStart
    $script:servicesStopped = $false

    $deadline = [DateTime]::UtcNow.AddMinutes(4)
    $healthUrl = "http://127.0.0.1:$($script:apiPort)/actuator/health"
    while ([DateTime]::UtcNow -lt $deadline) {
        $script:apiProcess.Refresh()
        if ($script:apiProcess.HasExited) {
            throw "The isolated API process exited before health during $PhaseName."
        }
        try {
            $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2
            if ($health.status -eq 'UP') {
                return
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "The isolated API did not become healthy during $PhaseName."
}

function Get-ApiProcessTree([int]$RootProcessId) {
    $processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $rootInfo = $processes | Where-Object { [int]$_.ProcessId -eq $RootProcessId } | Select-Object -First 1
    if (-not $rootInfo -or -not $rootInfo.CommandLine -or
        -not $rootInfo.CommandLine.Contains($script:apiLauncherPath)) {
        throw 'Refusing to stop an API process whose run-specific launcher identity cannot be verified.'
    }
    $ids = [Collections.Generic.List[int]]::new()
    $ids.Add($RootProcessId)
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($processInfo in $processes) {
            $childId = [int]$processInfo.ProcessId
            $parentId = [int]$processInfo.ParentProcessId
            if (($ids -contains $parentId) -and -not ($ids -contains $childId)) {
                $ids.Add($childId)
                $changed = $true
            }
        }
    }
    return $ids.ToArray()
}

function Stop-IsolatedApi {
    if (-not $script:apiProcess) {
        return
    }
    $script:apiProcess.Refresh()
    if (-not $script:apiProcess.HasExited) {
        $processIds = @(Get-ApiProcessTree -RootProcessId ([int]$script:apiProcess.Id))
        for ($index = $processIds.Count - 1; $index -ge 0; $index--) {
            Stop-Process -Id $processIds[$index] -Force -ErrorAction SilentlyContinue
        }
        $script:apiProcess.WaitForExit(10000) | Out-Null
    }
    $script:apiProcess.Refresh()
    if (-not $script:apiProcess.HasExited) {
        throw 'The run-specific API launcher did not stop.'
    }
    $remaining = @(Get-CimInstance Win32_Process -ErrorAction Stop |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($script:apiLauncherPath) })
    if ($remaining.Count -gt 0) {
        throw 'A process still references the run-specific API launcher.'
    }
    $script:apiProcess = $null
    $script:apiLauncherPath = $null
    if ($script:apiPort -and (Test-TcpPortOpen $script:apiPort)) {
        throw 'The run-specific API port is still open after stop.'
    }
    $script:apiPort = $null
}

function Get-MySqlProcesses {
    $dataPath = [IO.Path]::GetFullPath($script:mysqlData).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    return @(
        Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction Stop |
            Where-Object {
                $_.CommandLine -and
                $_.CommandLine.Contains($dataPath) -and
                $_.CommandLine.Contains('--no-defaults')
            }
    )
}

function Stop-IsolatedMysql {
    if ($script:mysqlProcess) {
        $script:mysqlProcess.Refresh()
        if (-not $script:mysqlProcess.HasExited) {
            $shutdown = Invoke-Process -Executable $script:mysqlAdminExe -Arguments @(
                '--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
                "--port=$($script:dbPort)", '--user=root', 'shutdown'
            )
            if ($shutdown.ExitCode -ne 0) {
                $owned = @(Get-MySqlProcesses | Where-Object { [int]$_.ProcessId -eq [int]$script:mysqlProcess.Id })
                if ($owned.Count -ne 1) {
                    throw 'Refusing to terminate MySQL because the process identity is not the isolated datadir.'
                }
                Stop-Process -Id ([int]$script:mysqlProcess.Id) -Force -ErrorAction Stop
            }
            $script:mysqlProcess.WaitForExit(15000) | Out-Null
            $script:mysqlProcess.Refresh()
            if (-not $script:mysqlProcess.HasExited) {
                throw 'The run-specific MySQL process did not stop.'
            }
        }
    }
    $remaining = @(Get-MySqlProcesses)
    if ($remaining.Count -gt 0) {
        throw 'A MySQL process still references the run-specific datadir.'
    }
    $script:mysqlProcess = $null
    $script:dbPort = $null
    $script:servicesStopped = $true
}

function Get-FileManifest([string]$RootPath) {
    $rootFull = Assert-PathInsideTempRoot $RootPath
    Assert-NoReparseTree $rootFull
    if (-not (Test-Path -LiteralPath $rootFull)) {
        throw 'The synthetic file root does not exist.'
    }
    $rootItem = Get-Item -LiteralPath $rootFull -Force -ErrorAction Stop
    if (-not $rootItem.PSIsContainer) {
        throw 'A file root must be a directory.'
    }
    $prefix = $rootFull.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $files = @(
        Get-ChildItem -LiteralPath $rootFull -File -Recurse -Force -ErrorAction Stop |
            Sort-Object FullName
    )
    $entries = foreach ($file in $files) {
        $fileFull = [IO.Path]::GetFullPath($file.FullName)
        if (-not $fileFull.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'A file escaped its synthetic storage root.'
        }
        [pscustomobject][ordered]@{
            Path = $fileFull.Substring($prefix.Length).Replace([IO.Path]::DirectorySeparatorChar, '/')
            Length = [long]$file.Length
            Sha256 = (Get-FileHash -LiteralPath $fileFull -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    return @($entries)
}

function Copy-FileTree([string]$SourcePath, [string]$DestinationPath) {
    $sourceFull = Assert-PathInsideTempRoot $SourcePath
    $destinationFull = Assert-PathInsideTempRoot $DestinationPath
    Assert-NoReparseTree $sourceFull
    if (Test-Path -LiteralPath $destinationFull) {
        Assert-NoReparseTree $destinationFull
        $existing = @(Get-ChildItem -LiteralPath $destinationFull -Force -ErrorAction Stop)
        if ($existing.Count -gt 0) {
            throw 'Refusing to copy into a non-empty temporary file destination.'
        }
    } else {
        New-Item -ItemType Directory -Path $destinationFull -Force | Out-Null
    }

    $sourcePrefix = $sourceFull.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    foreach ($item in Get-ChildItem -LiteralPath $sourceFull -Recurse -Force -ErrorAction Stop) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Refusing to copy a reparse point from the synthetic file root.'
        }
        $relative = $item.FullName.Substring($sourcePrefix.Length)
        $target = Join-Path $destinationFull $relative
        Assert-PathInsideTempRoot $target | Out-Null
        if ($item.PSIsContainer) {
            if (-not (Test-Path -LiteralPath $target)) {
                New-Item -ItemType Directory -Path $target -Force | Out-Null
            }
        } else {
            $targetParent = Split-Path -Parent $target
            if (-not (Test-Path -LiteralPath $targetParent)) {
                New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
            }
            Copy-Item -LiteralPath $item.FullName -Destination $target -ErrorAction Stop
        }
    }
    Assert-NoReparseTree $destinationFull
}

function Assert-FileManifest([string]$RootPath, [object[]]$ExpectedFiles) {
    $actual = @(Get-FileManifest $RootPath | Sort-Object Path)
    $expected = @($ExpectedFiles | Sort-Object Path)
    if ($actual.Count -ne $expected.Count) {
        throw 'The file manifest count does not match the stored file tree.'
    }
    for ($index = 0; $index -lt $actual.Count; $index++) {
        if ($actual[$index].Path -cne $expected[$index].Path -or
            [long]$actual[$index].Length -ne [long]$expected[$index].Length -or
            $actual[$index].Sha256 -cne ([string]$expected[$index].Sha256).ToLowerInvariant()) {
            throw 'A stored file differs from the SHA-256 manifest.'
        }
    }
}

function Assert-DumpManifest([string]$Path, [object]$ExpectedDump) {
    $fullPath = Assert-PathInsideTempRoot $Path
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw 'The database dump is missing.'
    }
    $item = Get-Item -LiteralPath $fullPath -Force -ErrorAction Stop
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'The database dump is a reparse point.'
    }
    $hash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([long]$item.Length -ne [long]$ExpectedDump.Length -or
        $hash -cne ([string]$ExpectedDump.Sha256).ToLowerInvariant()) {
        throw 'The database dump does not match its manifest.'
    }
}

function Get-DatabaseTableCounts([string]$Database) {
    $tableOutput = Invoke-MySqlQuery -Database $Database -AsApp -Sql "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name;"
    $tables = @($tableOutput -split '\r?\n' | Where-Object { $_ })
    if ($tables.Count -eq 0) {
        throw 'The isolated database contains no base tables.'
    }
    $queries = foreach ($table in $tables) {
        if ($table -notmatch '^[A-Za-z0-9_]+$') {
            throw 'The isolated database returned a non-canonical table name.'
        }
        "SELECT '$table', COUNT(*) FROM $table"
    }
    $countSql = $queries -join ' UNION ALL '
    $countOutput = Invoke-MySqlQuery -Database $Database -AsApp -Sql $countSql
    $counts = [ordered]@{}
    foreach ($line in ($countOutput -split '\r?\n' | Where-Object { $_ })) {
        $fields = $line -split [char]9
        if ($fields.Count -ne 2 -or $fields[1] -notmatch '^\d+$') {
            throw 'Could not parse isolated database table counts.'
        }
        $counts[[string]$fields[0]] = [long]$fields[1]
    }
    return $counts
}

function Assert-TableCountsEqual([System.Collections.IDictionary]$Expected, [System.Collections.IDictionary]$Actual) {
    if ($Expected.Count -ne $Actual.Count) {
        throw 'The restored database table count set differs from the snapshot.'
    }
    foreach ($table in $Expected.Keys) {
        if (-not $Actual.Contains($table) -or [long]$Expected[$table] -ne [long]$Actual[$table]) {
            throw 'A restored database table row count differs from the snapshot.'
        }
    }
}

function Get-MarkerRecord([string]$Database, [string]$AssetUuid) {
    $sql = "SELECT CONCAT(DATE_FORMAT(a.created_at, '%Y-%m-%d %H:%i:%s.%f'), '|', v.storage_path, '|', v.file_size_bytes, '|', v.sha256_hash) FROM file_asset a JOIN file_asset_version v ON v.asset_id = a.asset_id WHERE a.asset_uuid = '$AssetUuid';"
    $output = Invoke-MySqlQuery -Database $Database -AsApp -Sql $sql
    if (-not $output) {
        return $null
    }
    $parts = $output -split '\|'
    if ($parts.Count -ne 4) {
        throw 'A synthetic snapshot marker query returned an invalid record.'
    }
    $createdAt = [DateTime]::ParseExact(
        $parts[0],
        'yyyy-MM-dd HH:mm:ss.ffffff',
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal
    )
    return [pscustomobject]@{
        CreatedAtUtc = $createdAt
        StoragePath = [string]$parts[1]
        Length = [long]$parts[2]
        Sha256 = ([string]$parts[3]).ToLowerInvariant()
    }
}

function Add-SyntheticAsset([string]$Database, [string]$StorageRoot, [string]$AssetUuid, [string]$Filename, [string]$CreatedAtUtc) {
    if ($AssetUuid -notmatch '^[0-9a-fA-F-]{36}$' -or $Filename -notmatch '^backup-restore-[0-9a-f]{32}\.png$') {
        throw 'A synthetic asset identifier is not canonical.'
    }
    $filePath = Join-Path $StorageRoot $Filename
    Assert-PathInsideTempRoot $filePath | Out-Null
    Assert-NoReparseTree $StorageRoot
    $pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+nmN8AAAAASUVORK5CYII=')
    [IO.File]::WriteAllBytes($filePath, $pngBytes)
    $sha = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $timestamp = [DateTime]::ParseExact($CreatedAtUtc, 'yyyy-MM-dd HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture).ToString('yyyy-MM-dd HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture)
    $assetName = 'Synthetic-' + $AssetUuid
    $sql = "INSERT INTO file_asset(asset_uuid, category, created_by_user_id, created_at) VALUES ('$AssetUuid', 'WORK_ORDER_PHOTO', 2, '$timestamp'); SET @asset_id = LAST_INSERT_ID(); INSERT INTO file_asset_version(asset_id, version_no, storage_path, original_filename, file_size_bytes, mime_type, sha256_hash, uploaded_at) VALUES (@asset_id, 1, '$Filename', '$assetName.png', $($pngBytes.Length), 'image/png', '$sha', '$timestamp'); SET @version_id = LAST_INSERT_ID(); INSERT INTO file_upload_session(work_order_id, asset_id, version_id, uploaded_by_user_id) VALUES (1, @asset_id, @version_id, 2); INSERT INTO business_file_link(business_type, business_id, asset_id, version_id, tag) VALUES ('WORK_ORDER', '1', @asset_id, @version_id, 'SERVICE_PROOF');"
    $result = Invoke-MySql -Database $Database -Arguments @('--execute', $sql) -AsApp
    if ($result.ExitCode -ne 0) {
        throw 'Could not create the synthetic file asset and business association.'
    }
    $marker = Get-MarkerRecord -Database $Database -AssetUuid $AssetUuid
    if (-not $marker -or $marker.StoragePath -cne $Filename -or $marker.Length -ne $pngBytes.Length -or $marker.Sha256 -cne $sha) {
        throw 'The synthetic file asset metadata did not match its stored bytes.'
    }
    return $marker
}

function Assert-DatabaseFiles([string]$Database, [string]$StorageRoot, [string]$ExpectedAssetUuid, [string]$ExpectedFilename) {
    $rows = Invoke-MySqlQuery -Database $Database -AsApp -Sql "SELECT CONCAT(v.storage_path, '|', v.file_size_bytes, '|', v.sha256_hash) FROM file_asset_version v ORDER BY v.version_id;"
    $rowLines = @($rows -split '\r?\n' | Where-Object { $_ })
    if ($rowLines.Count -eq 0) {
        throw 'The restored database has no file asset versions.'
    }
    foreach ($line in $rowLines) {
        $parts = $line -split '\|'
        if ($parts.Count -ne 3 -or $parts[0] -match '(^|[\\/])\.\.([\\/]|$)' -or [IO.Path]::IsPathRooted($parts[0])) {
            throw 'A file asset path is not a safe storage-relative path.'
        }
        $path = Join-Path $StorageRoot ($parts[0].Replace('/', [IO.Path]::DirectorySeparatorChar))
        $fullPath = Assert-PathInsideTempRoot $path
        $storageFull = [IO.Path]::GetFullPath($StorageRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
        if (-not $fullPath.StartsWith($storageFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'A database file path escaped the restored storage root.'
        }
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw 'A database file asset is missing from the restored storage root.'
        }
        $item = Get-Item -LiteralPath $fullPath -Force -ErrorAction Stop
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            [long]$item.Length -ne [long]$parts[1]) {
            throw 'A database file asset has an invalid file type or size.'
        }
        $hash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -cne ([string]$parts[2]).ToLowerInvariant()) {
            throw 'A database file asset SHA-256 differs from the database metadata.'
        }
    }

    $associationSql = "SELECT COUNT(*) FROM file_asset a JOIN file_asset_version v ON v.asset_id = a.asset_id JOIN file_upload_session s ON s.asset_id = a.asset_id AND s.version_id = v.version_id JOIN business_file_link l ON l.asset_id = a.asset_id AND l.version_id = v.version_id JOIN work_order w ON w.work_order_id = s.work_order_id WHERE a.asset_uuid = '$ExpectedAssetUuid' AND v.storage_path = '$ExpectedFilename' AND l.business_type = 'WORK_ORDER' AND l.business_id = CAST(w.work_order_id AS CHAR) AND s.work_order_id = 1;"
    $associationCount = Invoke-MySqlQuery -Database $Database -AsApp -Sql $associationSql
    if ($associationCount -cne '1') {
        throw 'The restored asset-to-version-to-upload-to-work-order association is incomplete.'
    }

    $orphansSql = "SELECT (SELECT COUNT(*) FROM file_asset_version v LEFT JOIN file_asset a ON a.asset_id = v.asset_id WHERE a.asset_id IS NULL) + (SELECT COUNT(*) FROM business_file_link l LEFT JOIN file_asset a ON a.asset_id = l.asset_id LEFT JOIN file_asset_version v ON v.version_id = l.version_id WHERE a.asset_id IS NULL OR v.version_id IS NULL) + (SELECT COUNT(*) FROM file_upload_session s LEFT JOIN file_asset a ON a.asset_id = s.asset_id LEFT JOIN file_asset_version v ON v.version_id = s.version_id LEFT JOIN work_order w ON w.work_order_id = s.work_order_id WHERE a.asset_id IS NULL OR v.version_id IS NULL OR w.work_order_id IS NULL);"
    $orphans = Invoke-MySqlQuery -Database $Database -AsApp -Sql $orphansSql
    if ($orphans -cne '0') {
        throw 'The restored database contains orphaned file or business references.'
    }
}

function Assert-ExpectedIntegrityFailure([scriptblock]$Action, [string]$Description) {
    $failedAsExpected = $false
    try {
        & $Action
    } catch {
        $failedAsExpected = $true
    }
    if (-not $failedAsExpected) {
        throw "Integrity damage was not rejected: $Description."
    }
    Write-Output "Detected expected integrity failure: $Description."
}

function Get-DatabaseDump([string]$Database, [string]$DestinationPath) {
    Assert-PathInsideTempRoot $DestinationPath | Out-Null
    $dumpArgs = @(
        '--no-defaults',
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$($script:dbPort)",
        '--user=elderly_app',
        '--default-character-set=utf8mb4',
        '--single-transaction',
        '--quick',
        '--hex-blob',
        '--no-tablespaces',
        '--set-gtid-purged=OFF',
        '--routines',
        '--events',
        '--triggers',
        "--result-file=$DestinationPath",
        $Database
    )
    $result = Invoke-Process -Executable $script:mysqlDumpExe -Arguments $dumpArgs -Password $script:appPassword
    if ($result.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $DestinationPath -PathType Leaf)) {
        throw 'The synthetic database dump did not complete.'
    }
    $dumpItem = Get-Item -LiteralPath $DestinationPath -Force -ErrorAction Stop
    if ($dumpItem.Length -le 0 -or ($dumpItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'The synthetic database dump is empty or unsafe.'
    }
}

function Import-DatabaseDump([string]$Database, [string]$Path) {
    $fullPath = Assert-PathInsideTempRoot $Path
    $dumpText = [IO.File]::ReadAllText($fullPath, [Text.Encoding]::UTF8)
    return Invoke-MySql -Database $Database -Arguments @() -InputText $dumpText -AsApp
}

function Find-FirstInsertStatementEnd([string]$SqlText, [int]$StartIndex) {
    $quote = [char]0
    for ($index = $StartIndex; $index -lt $SqlText.Length; $index++) {
        $character = $SqlText[$index]
        if ($quote -ne [char]0) {
            if ($character -eq [char]92 -and $index + 1 -lt $SqlText.Length) {
                $index++
                continue
            }
            if ($character -eq $quote) {
                if ($index + 1 -lt $SqlText.Length -and $SqlText[$index + 1] -eq $quote) {
                    $index++
                    continue
                }
                $quote = [char]0
            }
            continue
        }
        if ($character -eq [char]39 -or $character -eq [char]34 -or $character -eq [char]96) {
            $quote = $character
            continue
        }
        if ($character -eq [char]59) {
            return $index
        }
    }
    return -1
}

function New-TruncatedDump([string]$SourcePath, [string]$DestinationPath) {
    $text = [IO.File]::ReadAllText($SourcePath, [Text.Encoding]::UTF8)
    $insertStart = $text.IndexOf('INSERT INTO ', [StringComparison]::Ordinal)
    if ($insertStart -lt 0) {
        throw 'The synthetic database dump contains no data insert to truncate.'
    }
    $statementEnd = Find-FirstInsertStatementEnd -SqlText $text -StartIndex $insertStart
    if ($statementEnd -le ($insertStart + 16)) {
        throw 'Could not identify a safe synthetic SQL statement to truncate.'
    }
    $truncateAt = $insertStart + [Math]::Min(48, [int](($statementEnd - $insertStart) / 2))
    if ($truncateAt -ge $statementEnd) {
        $truncateAt = $statementEnd - 1
    }
    $prefix = $text.Substring(0, $truncateAt)
    [IO.File]::WriteAllText($DestinationPath, $prefix, [Text.UTF8Encoding]::new($false))
    if ((Get-Item -LiteralPath $DestinationPath).Length -ge (Get-Item -LiteralPath $SourcePath).Length) {
        throw 'The database dump truncation did not shorten the dump.'
    }
}

function New-Manifest(
    [string]$BackupId,
    [DateTime]$SnapshotTakenAtUtc,
    [DateTime]$LatestWriteAtUtc,
    [System.Collections.IDictionary]$TableCounts
) {
    $dumpItem = Get-Item -LiteralPath $script:dumpPath -Force -ErrorAction Stop
    $dumpHash = (Get-FileHash -LiteralPath $script:dumpPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $fileEntries = @(Get-FileManifest $script:backupFiles)
    $tableSnapshot = [ordered]@{}
    foreach ($table in $TableCounts.Keys) {
        $tableSnapshot[[string]$table] = [long]$TableCounts[$table]
    }
    $manifest = [pscustomobject][ordered]@{
        FormatVersion = 1
        BackupId = $BackupId
        CreatedAtUtc = $SnapshotTakenAtUtc.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
        SnapshotLatestWriteAtUtc = $LatestWriteAtUtc.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
        SourceDatabase = $script:sourceDb
        Quiesced = $true
        DatabaseDump = [pscustomobject][ordered]@{
            Path = 'database.sql'
            Length = [long]$dumpItem.Length
            Sha256 = $dumpHash
        }
        TableCounts = $tableSnapshot
        Files = $fileEntries
    }
    $json = ConvertTo-Json -InputObject $manifest -Depth 8
    [IO.File]::WriteAllText($script:manifestPath, $json, [Text.UTF8Encoding]::new($false))
    return $manifest
}

function Assert-BackupBundle([object]$Manifest, [string]$Dump, [string]$Files) {
    if (-not $Manifest.Quiesced -or $Manifest.SourceDatabase -cne $script:sourceDb) {
        throw 'The backup manifest does not identify this run-specific quiesced synthetic database.'
    }
    Assert-DumpManifest -Path $Dump -ExpectedDump $Manifest.DatabaseDump
    Assert-FileManifest -RootPath $Files -ExpectedFiles @($Manifest.Files)
}

function Set-SyntheticAsset([string]$Database, [string]$StorageRoot, [string]$CreatedAtUtc) {
    $assetUuid = [Guid]::NewGuid().ToString()
    $filename = 'backup-restore-' + [Guid]::NewGuid().ToString('N') + '.png'
    $marker = Add-SyntheticAsset -Database $Database -StorageRoot $StorageRoot -AssetUuid $assetUuid -Filename $filename -CreatedAtUtc $CreatedAtUtc
    return [pscustomobject]@{
        AssetUuid = $assetUuid
        Filename = $filename
        Marker = $marker
    }
}

function New-TestMysqlAndSeed {
    New-Item -ItemType Directory -Path $script:tempRoot -ErrorAction Stop | Out-Null
    $script:tempRootCreated = $true
    Assert-NoReparseTree $script:tempRoot
    New-Item -ItemType Directory -Path $script:mysqlData -Force | Out-Null
    New-Item -ItemType Directory -Path $script:sourceFiles -Force | Out-Null
    New-Item -ItemType Directory -Path $script:backupRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $script:restoreFiles -Force | Out-Null
    New-IsolatedMysql
    New-TemporaryDatabase $script:sourceDb

    Start-IsolatedApi -PhaseName 'flyway-source' -DatabaseName $script:sourceDb -StorageRoot $script:sourceFiles
    Stop-IsolatedApi

    $seedEnvPath = Join-Path $script:tempRoot 'seed.env'
    Assert-PathInsideTempRoot $seedEnvPath | Out-Null
    $seedEnvLines = @(
        'DB_HOST=127.0.0.1'
        "DB_PORT=$($script:dbPort)"
        "DB_NAME=$($script:sourceDb)"
        'DB_USERNAME=elderly_app'
        "DB_PASSWORD=$script:appPassword"
    )
    Set-Content -LiteralPath $seedEnvPath -Value $seedEnvLines -Encoding ASCII
    $seedOutput = Join-Path $script:tempRoot 'seed.out'
    $seedError = Join-Path $script:tempRoot 'seed.err'
    $seedArgs = @('-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $seedWrapper, '-EnvFile', $seedEnvPath)
    $seedStart = @{
        FilePath = $script:pwshExe
        ArgumentList = ConvertTo-ArgumentString $seedArgs
        WorkingDirectory = $script:repoRoot
        PassThru = $true
        WindowStyle = 'Hidden'
        Wait = $true
        RedirectStandardOutput = $seedOutput
        RedirectStandardError = $seedError
    }
    $seedProcess = Start-Process @seedStart
    if ($seedProcess.ExitCode -ne 0) {
        throw 'The explicit temporary-environment synthetic seed loader failed.'
    }

    $orderCount = Invoke-MySqlQuery -Database $script:sourceDb -AsApp -Sql 'SELECT COUNT(*) FROM work_order WHERE work_order_id = 1;'
    if ($orderCount -cne '1') {
        throw 'The synthetic seed does not contain the expected isolated work-order fixture.'
    }
    $sourceFileCount = @(Get-ChildItem -LiteralPath $script:sourceFiles -File -Recurse -Force).Count
    if ($sourceFileCount -ne 0) {
        throw 'The isolated source storage root was not empty before its own synthetic asset was created.'
    }
}

function New-ExpectedFailureCases([object]$Manifest) {
    New-Item -ItemType Directory -Path $script:damageRoot -Force | Out-Null
    $truncatedDump = Join-Path $script:damageRoot 'database-truncated.sql'
    New-TruncatedDump -SourcePath $script:dumpPath -DestinationPath $truncatedDump
    Assert-ExpectedIntegrityFailure -Description 'truncated database dump manifest' -Action {
        Assert-DumpManifest -Path $truncatedDump -ExpectedDump $Manifest.DatabaseDump
    }

    $failedDbName = 'elderly_care_failed_' + [Guid]::NewGuid().ToString('N')
    $script:failedDb = $failedDbName
    New-TemporaryDatabase $failedDbName
    $badImport = Import-DatabaseDump -Database $failedDbName -Path $truncatedDump
    if ($badImport.ExitCode -eq 0) {
        throw 'A truncated SQL import unexpectedly succeeded in its disposable failure database.'
    }
    Write-Output 'Detected expected partial-import failure in the isolated disposable database.'

    $damagedFiles = Join-Path $script:damageRoot 'files'
    Copy-FileTree -SourcePath $script:backupFiles -DestinationPath $damagedFiles
    $firstEntry = @($Manifest.Files | Sort-Object Path | Select-Object -First 1)
    if ($firstEntry.Count -ne 1) {
        throw 'The synthetic backup file manifest is empty.'
    }
    $damagedPath = Join-Path $damagedFiles ([string]$firstEntry[0].Path)
    $damagedFull = Assert-PathInsideTempRoot $damagedPath
    $stream = [IO.File]::Open($damagedFull, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    try {
        $originalByte = $stream.ReadByte()
        if ($originalByte -lt 0) {
            throw 'The synthetic file chosen for content corruption is empty.'
        }
        $stream.Position = 0
        $stream.WriteByte([byte]($originalByte -bxor 1))
        $stream.Flush()
    } finally {
        $stream.Dispose()
    }
    Assert-ExpectedIntegrityFailure -Description 'damaged file content SHA-256 manifest' -Action {
        Assert-FileManifest -RootPath $damagedFiles -ExpectedFiles @($Manifest.Files)
    }

    Assert-BackupBundle -Manifest $Manifest -Dump $script:dumpPath -Files $script:backupFiles
    $sourceCountsAfterNegativeCases = Get-DatabaseTableCounts $script:sourceDb
    Assert-TableCountsEqual -Expected $script:snapshotCounts -Actual $sourceCountsAfterNegativeCases
}

function Restore-AndVerify([object]$Manifest, [string]$ExpectedAssetUuid, [string]$ExpectedFilename) {
    $script:restoreDb = 'elderly_care_restore_' + [Guid]::NewGuid().ToString('N')
    New-TemporaryDatabase $script:restoreDb
    $copyResult = Import-DatabaseDump -Database $script:restoreDb -Path $script:dumpPath
    if ($copyResult.ExitCode -ne 0) {
        throw 'The validated synthetic SQL dump did not import into the fresh restore schema.'
    }

    Copy-FileTree -SourcePath $script:backupFiles -DestinationPath $script:restoreFiles
    Assert-BackupBundle -Manifest $Manifest -Dump $script:dumpPath -Files $script:restoreFiles

    $restoredCounts = Get-DatabaseTableCounts $script:restoreDb
    Assert-TableCountsEqual -Expected $script:snapshotCounts -Actual $restoredCounts
    Assert-DatabaseFiles -Database $script:restoreDb -StorageRoot $script:restoreFiles -ExpectedAssetUuid $ExpectedAssetUuid -ExpectedFilename $ExpectedFilename

    $snapshotMarker = Get-MarkerRecord -Database $script:restoreDb -AssetUuid $ExpectedAssetUuid
    if (-not $snapshotMarker -or $snapshotMarker.StoragePath -cne $ExpectedFilename) {
        throw 'The snapshot marker was not recovered from the backup.'
    }
    if (Get-MarkerRecord -Database $script:restoreDb -AssetUuid $script:failureMarker.AssetUuid) {
        throw 'A post-snapshot synthetic failure marker unexpectedly appeared in the restored database.'
    }

    Start-IsolatedApi -PhaseName 'restored' -DatabaseName $script:restoreDb -StorageRoot $script:restoreFiles
    $script:rtoStopwatch.Stop()
    $script:rtoSeconds = $script:rtoStopwatch.Elapsed.TotalSeconds
    $script:restoreHealthAtUtc = [DateTime]::UtcNow
    Stop-IsolatedApi
    return [pscustomobject]@{
        TableCounts = $restoredCounts
        SnapshotMarkerAtUtc = $snapshotMarker.CreatedAtUtc
    }
}

function Stop-And-CleanTempRoot {
    $stopFailure = $null
    try {
        Stop-IsolatedApi
    } catch {
        $stopFailure = $_.Exception.Message
    }
    try {
        Stop-IsolatedMysql
    } catch {
        if (-not $stopFailure) {
            $stopFailure = $_.Exception.Message
        }
    }

    foreach ($key in $script:apiEnvKeys) {
        [Environment]::SetEnvironmentVariable($key, $script:previousEnv[$key], 'Process')
    }

    if (-not $script:tempRootCreated -or -not (Test-Path -LiteralPath $script:tempRoot)) {
        $script:cleanupSucceeded = ($null -eq $stopFailure)
        return
    }
    if ($stopFailure) {
        Write-Output 'Cleanup safety check failed; the run-specific temporary root was retained.'
        return
    }

    try {
        $resolvedTempBase = [IO.Path]::GetFullPath($tempBase).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
        $resolvedTempRoot = [IO.Path]::GetFullPath($script:tempRoot)
        $expectedPrefix = $resolvedTempBase + [IO.Path]::DirectorySeparatorChar
        if ([IO.Path]::GetFileName($resolvedTempRoot) -cne $tempRootLeaf -or
            -not $resolvedTempRoot.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'The cleanup target is not the exact run-specific child of the Windows temporary directory.'
        }
        if (-not $script:servicesStopped) {
            throw 'A run-specific service has not been confirmed stopped.'
        }
        Assert-NoReparseTree $resolvedTempRoot
        $remaining = @(Get-CimInstance Win32_Process -ErrorAction Stop |
            Where-Object { $_.CommandLine -and $_.CommandLine.Contains($resolvedTempRoot) })
        if ($remaining.Count -gt 0) {
            throw 'A process still references the run-specific temporary root.'
        }
        $rootItem = Get-Item -LiteralPath $resolvedTempRoot -Force -ErrorAction Stop
        if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'The cleanup root is a reparse point.'
        }
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force -ErrorAction Stop
        $script:cleanupSucceeded = $true
    } catch {
        Write-Output 'Cleanup safety check failed; the run-specific temporary root was retained.'
    }
}

try {
    if ($PSVersionTable.PSEdition -ne 'Core') {
        throw 'Run this script with PowerShell 7 (pwsh).'
    }
    if (-not $mysqlExe -or -not $mysqlDumpExe -or -not $mysqlAdminExe -or -not $pwshExe -or -not (Test-Path -LiteralPath $mysqlServerExe)) {
        throw 'Required local tools are missing: mysql, mysqldump, mysqladmin, mysqld, or pwsh.'
    }
    if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf) -or -not (Test-Path -LiteralPath $seedWrapper -PathType Leaf)) {
        throw 'The repository Maven wrapper or explicit synthetic seed loader is missing.'
    }
    if (-not ([IO.Path]::GetDirectoryName($mysqlExe)).Equals([IO.Path]::GetDirectoryName($mysqlDumpExe), [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetDirectoryName($mysqlExe)).Equals([IO.Path]::GetDirectoryName($mysqlAdminExe), [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetDirectoryName($mysqlExe)).Equals([IO.Path]::GetDirectoryName($mysqlServerExe), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'mysql, mysqldump, mysqladmin, and mysqld must come from the same local MySQL installation.'
    }
    if (Test-Path -LiteralPath $tempRoot) {
        throw 'The generated run-specific temporary root unexpectedly already exists.'
    }
    New-TestMysqlAndSeed

    $baselineCreatedAt = [DateTime]::UtcNow
    $baselineAsset = Set-SyntheticAsset -Database $script:sourceDb -StorageRoot $script:sourceFiles -CreatedAtUtc $baselineCreatedAt.ToString('yyyy-MM-dd HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture)
    $script:snapshotCounts = Get-DatabaseTableCounts $script:sourceDb
    $snapshotTakenAtUtc = [DateTime]::UtcNow
    $backupId = [Guid]::NewGuid().ToString('N')

    Get-DatabaseDump -Database $script:sourceDb -DestinationPath $script:dumpPath
    Copy-FileTree -SourcePath $script:sourceFiles -DestinationPath $script:backupFiles
    $manifest = New-Manifest -BackupId $backupId -SnapshotTakenAtUtc $snapshotTakenAtUtc -LatestWriteAtUtc $baselineAsset.Marker.CreatedAtUtc -TableCounts $script:snapshotCounts
    $manifestItem = Get-Item -LiteralPath $script:manifestPath -Force -ErrorAction Stop
    if (($manifestItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'The synthetic backup manifest is a reparse point.'
    }
    $manifest = Get-Content -LiteralPath $script:manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json -Depth 8
    Assert-BackupBundle -Manifest $manifest -Dump $script:dumpPath -Files $script:backupFiles
    Write-Output 'Created a quiesced synthetic database-and-files backup set inside the run-specific temporary root.'

    New-ExpectedFailureCases -Manifest $manifest

    $failureCreatedAt = [DateTime]::UtcNow
    $script:failureMarker = Set-SyntheticAsset -Database $script:sourceDb -StorageRoot $script:sourceFiles -CreatedAtUtc $failureCreatedAt.ToString('yyyy-MM-dd HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture)
    $failureMarkerAtUtc = $script:failureMarker.Marker.CreatedAtUtc
    if ($failureMarkerAtUtc -le $baselineAsset.Marker.CreatedAtUtc) {
        throw 'The synthetic failure marker is not later than the backup snapshot write point.'
    }
    $sourceAfterFailureCounts = Get-DatabaseTableCounts $script:sourceDb
    $sourceAfterFailureFiles = @(Get-FileManifest $script:sourceFiles)

    $script:rtoStopwatch = [Diagnostics.Stopwatch]::StartNew()
    $restored = Restore-AndVerify -Manifest $manifest -ExpectedAssetUuid $baselineAsset.AssetUuid -ExpectedFilename $baselineAsset.Filename
    $script:rtoStopwatch = $null
    $sourceCountsAfterRestore = Get-DatabaseTableCounts $script:sourceDb
    Assert-TableCountsEqual -Expected $sourceAfterFailureCounts -Actual $sourceCountsAfterRestore
    Assert-FileManifest -RootPath $script:sourceFiles -ExpectedFiles $sourceAfterFailureFiles

    $rpoSeconds = ($failureMarkerAtUtc - $restored.SnapshotMarkerAtUtc).TotalSeconds
    if ($rpoSeconds -lt 0) {
        throw 'The measured RPO interval is invalid.'
    }
    Write-Output ("Snapshot time (UTC): {0}" -f $snapshotTakenAtUtc.ToString('o', [Globalization.CultureInfo]::InvariantCulture))
    Write-Output ("Snapshot latest recovered synthetic write (UTC): {0}" -f $restored.SnapshotMarkerAtUtc.ToString('o', [Globalization.CultureInfo]::InvariantCulture))
    Write-Output ("Simulated failure marker time (UTC): {0}" -f $failureMarkerAtUtc.ToString('o', [Globalization.CultureInfo]::InvariantCulture))
    Write-Output ("Measured RPO: {0:N3} seconds (one local isolated snapshot only)" -f $rpoSeconds)
    Write-Output ("Measured RTO: {0:N3} seconds to restored table counts, file manifest/SHA checks, asset relationships, and API health UP." -f $script:rtoSeconds)
    Write-Output 'Authorized file retrieval is not covered; daily scheduling and seven-backup retention are not measured.'
    Write-Output 'Detected truncated SQL and modified file content before a restore target can be considered valid.'
    $script:failureMessage = $null
} catch {
    $script:failureMessage = $_.Exception.Message
} finally {
    Stop-And-CleanTempRoot
}

if ($script:failureMessage) {
    [Console]::Error.WriteLine("Backup/restore rehearsal failed: $($script:failureMessage)")
    if (-not $script:cleanupSucceeded -and $script:tempRootCreated) {
        [Console]::Error.WriteLine("Temporary data retained for safety review: $script:tempRoot")
    }
    exit 1
}
if (-not $script:cleanupSucceeded) {
    [Console]::Error.WriteLine('Backup/restore rehearsal did not prove safe temporary cleanup.')
    if ($script:tempRootCreated) {
        [Console]::Error.WriteLine("Temporary data retained for safety review: $script:tempRoot")
    }
    exit 1
}
Write-Output 'Temporary MySQL, API, synthetic backup, restore files, and failure artifacts stopped and safely removed.'
exit 0
