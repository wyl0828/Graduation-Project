$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envLocal = Join-Path $repoRoot '.env.local'

if (-not (Test-Path -LiteralPath $envLocal)) { throw '缺少 .env.local' }

$values = @{}
foreach ($line in Get-Content -LiteralPath $envLocal) {
    $line = $line.Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $val = $Matches[2].Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        $values[$Matches[1]] = $val
    }
}

$dbHost = $values['DB_HOST']
if ([string]::IsNullOrWhiteSpace($dbHost) -or $dbHost.Trim().ToLowerInvariant() -notin @('localhost', '127.0.0.1', '::1')) {
    throw '安全保护：DB_HOST 必须指向本机。'
}
if ($values['DB_NAME'] -cne 'elderly_care_demo') {
    throw '安全保护：仅允许使用隔离演示库 elderly_care_demo。'
}
if ($values['DB_USERNAME'] -cne 'elderly_app') {
    throw '安全保护：仅允许使用 elderly_app 演示账号。'
}
if ([string]::IsNullOrWhiteSpace($values['DB_PASSWORD'])) {
    throw '请在 .env.local 中配置 DB_PASSWORD。'
}

foreach ($k in $values.Keys) {
    [Environment]::SetEnvironmentVariable($k, $values[$k], 'Process')
}

# Do not let an inherited port override the local configuration when the file omits it.
if (-not $values.ContainsKey('DB_PORT')) {
    [Environment]::SetEnvironmentVariable('DB_PORT', $null, 'Process')
}

& (Join-Path $repoRoot 'mvnw.cmd') -f (Join-Path $repoRoot 'services\api\pom.xml') test
$mavenExitCode = $LASTEXITCODE
if ($null -eq $mavenExitCode) { $mavenExitCode = 1 }
exit $mavenExitCode
