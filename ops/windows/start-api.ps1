$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envLocal = Join-Path $repoRoot '.env.local'
$allowedKeys = @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD', 'APP_STORAGE_ROOT', 'AUTH_RATE_LIMIT_HMAC_KEY', 'AUTH_COOKIE_SECURE', 'PHONE_VERIFICATION_ENABLED')

if (-not (Test-Path -LiteralPath $envLocal)) {
    throw '缺少 .env.local。请先运行 ./ops/windows/init-local.ps1 并填写本机配置。'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $envLocal) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw '本地环境文件存在无效行。' }
    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if ($key -notin $allowedKeys) { throw "本地环境文件包含未支持的变量名：$key" }
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $values[$key] = $value
}

$requiredDbKeys = @('DB_HOST', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD')
foreach ($key in $requiredDbKeys) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$values[$key])) {
        throw "请在 .env.local 中配置 $key。"
    }
}

$dbHost = $values['DB_HOST']
$dbName = $values['DB_NAME']
$dbUsername = $values['DB_USERNAME']
if ($dbHost -notin @('localhost', '127.0.0.1', '::1')) { throw '仅允许连接本机 MySQL；DB_HOST 必须是 localhost、127.0.0.1 或 ::1。' }
if ($dbName -cne 'elderly_care_demo') { throw '仅允许连接隔离演示库 elderly_care_demo。' }
if ($dbUsername -cne 'elderly_app') { throw '仅允许使用 elderly_app 演示账号。' }

$previousValues = @{}
foreach ($key in $allowedKeys) { $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

try {
    foreach ($entry in $values.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
    [Environment]::SetEnvironmentVariable('DB_HOST', $dbHost, 'Process')
    [Environment]::SetEnvironmentVariable('DB_NAME', $dbName, 'Process')
    $storageValue = if ($values.ContainsKey('APP_STORAGE_ROOT') -and $values['APP_STORAGE_ROOT']) {
        $values['APP_STORAGE_ROOT']
    } else {
        '.local-data/files'
    }
    if (-not [System.IO.Path]::IsPathRooted($storageValue)) {
        $storageValue = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $storageValue))
    }
    [Environment]::SetEnvironmentVariable('APP_STORAGE_ROOT', $storageValue, 'Process')

    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot 'mvnw.cmd') -f (Join-Path $repoRoot 'services\api\pom.xml') '-Dspring-boot.run.profiles=local' spring-boot:run
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    foreach ($key in $allowedKeys) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
}
