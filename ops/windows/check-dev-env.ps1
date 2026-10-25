$ErrorActionPreference = 'Continue'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$failed = $false

function Report-Tool([string]$name, [string]$value, [bool]$ok) {
    $status = if ($ok) { 'OK' } else { 'MISSING/REVIEW' }
    Write-Output ('[{0}] {1}: {2}' -f $status, $name, $value)
    if (-not $ok) { $script:failed = $true }
}

foreach ($tool in @('git', 'java', 'node', 'npm')) {
    $command = Get-Command $tool -ErrorAction SilentlyContinue
    if (-not $command) {
        Report-Tool $tool 'not found in PATH' $false
        continue
    }
    if ($tool -eq 'git') {
        $version = (& git --version 2>&1 | Out-String).Trim()
        $versionOk = $true
    } elseif ($tool -eq 'java') {
        $version = (& java -version 2>&1 | Out-String).Trim()
        $versionOk = $version -match 'version "17\.0\.10"'
    } elseif ($tool -eq 'node') {
        $version = (& node --version 2>&1 | Out-String).Trim()
        $versionOk = $version -eq 'v24.15.0'
    } else {
        $version = (& npm --version 2>&1 | Out-String).Trim()
        $versionOk = $version -eq '11.12.1'
    }
    Report-Tool $tool $version $versionOk
}

$wrapper = Join-Path $repoRoot 'mvnw.cmd'
if (Test-Path -LiteralPath $wrapper) {
    $mavenVersion = (& $wrapper -version 2>&1 | Out-String).Trim() -join ' '
    Report-Tool 'Maven Wrapper' $mavenVersion (($LASTEXITCODE -eq 0) -and ($mavenVersion -match 'Apache Maven 3\.9\.4'))
} else {
    Report-Tool 'Maven Wrapper' 'mvnw.cmd not found' $false
}

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if ($mysql) {
    $mysqlVersion = (& mysql --version 2>&1 | Out-String).Trim()
    Report-Tool 'MySQL client' $mysqlVersion ($mysqlVersion -match 'Ver 8\.0\.34')
} else {
    Report-Tool 'MySQL client' 'mysql not found in PATH' $false
}

$mysqlServices = @(Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^MySQL' })
if ($mysqlServices.Count -gt 0) {
    $serviceSummary = ($mysqlServices | ForEach-Object { '{0}={1}' -f $_.Name, $_.Status }) -join ', '
    $serviceRunning = @($mysqlServices | Where-Object Status -eq 'Running').Count -gt 0
    Report-Tool 'MySQL Windows service' $serviceSummary $serviceRunning
} else {
    Report-Tool 'MySQL Windows service' 'no service name beginning with MySQL was found' $false
}

if ($failed) {
    Write-Error '环境检查发现缺项；请按 D1 方案补齐后重试。'
    exit 1
}

Write-Output '环境工具检查完成。此结果不代表数据库连接、应用启动或 D1 验收通过。'
