param(

    [switch]$Apply

)



$ErrorActionPreference = 'Stop'

$databaseName = 'elderly_care_perf'

$expectedUser = 'elderly_app'

$hostName = $env:PERF_DB_HOST

$portText = if ($env:PERF_DB_PORT) { $env:PERF_DB_PORT } else { '3306' }

$dbUser = $env:PERF_DB_USERNAME

$dbPassword = $env:PERF_DB_PASSWORD

$seedFile = Join-Path $PSScriptRoot 'fixed_dataset_v1.sql'



if ([string]::IsNullOrWhiteSpace($hostName) -or $hostName.Trim().ToLowerInvariant() -notin @('localhost', '127.0.0.1', '::1')) {

    throw '安全保护：PERF_DB_HOST 必须指向本机。'

}

if ($env:PERF_DB_NAME -cne $databaseName) {

    throw "安全保护：PERF_DB_NAME 必须精确为 $databaseName。"

}

if ($dbUser -cne $expectedUser) {

    throw "安全保护：PERF_DB_USERNAME 必须精确为 $expectedUser。"

}

if ([string]::IsNullOrWhiteSpace($dbPassword)) {

    throw '请在当前 PowerShell 进程的 PERF_DB_PASSWORD 环境变量中设置本机数据库密码。'

}

$port = 0

if (-not [int]::TryParse($portText, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {

    throw 'PERF_DB_PORT 必须是 1 到 65535 之间的端口号。'

}

if (-not (Test-Path -LiteralPath $seedFile)) {

    throw '缺少固定性能数据集 SQL 文件。'

}



$mysql = Get-Command mysql -ErrorAction Stop

$mysqlArgs = @(

    '--protocol=TCP',

    "--host=$hostName",

    "--port=$port",

    "--user=$expectedUser",

    "--database=$databaseName",

    '--default-character-set=utf8mb4',

    '--batch',

    '--skip-column-names'

)



$expectedCounts = [ordered]@{

    sys_community = 2

    sys_user = 1

    sys_user_role = 1

    elder_profile = 100

    vital_record = 5000

    alert_rule_version = 1

    service_catalog = 4

    work_order = 500

    service_reservation = 500

}

$selects = foreach ($table in $expectedCounts.Keys) {

    "SELECT '$table', COUNT(*) FROM $table"

}

$countQuery = $selects -join ([string][char]10 + 'UNION ALL' + [string][char]10)



function Invoke-PerformanceMysql([string]$Sql) {

    $rows = & $mysql.Source @mysqlArgs '--execute' $Sql

    if ($LASTEXITCODE -ne 0) {

        throw '本机 MySQL 查询失败；请检查服务、迁移版本和 elderly_app 权限。密码不会输出。'

    }

    return $rows

}



$previousMysqlPassword = $env:MYSQL_PWD

try {

    $env:MYSQL_PWD = $dbPassword



    $migrationCount = Invoke-PerformanceMysql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '3.10';"

    if (-not $migrationCount -or [int]$migrationCount[0] -lt 1) {

        throw '目标库尚未成功应用 Flyway V3.10 迁移。'

    }



    $rows = Invoke-PerformanceMysql $countQuery

    $currentCounts = @{}

    foreach ($row in $rows) {

        $parts = [string]$row -split [string][char]9

        if ($parts.Count -ne 2 -or $parts[1] -notmatch '^\d+$') {

            throw '无法读取目标库数据规模。'

        }

        $currentCounts[$parts[0]] = [int]$parts[1]

    }



    foreach ($table in $expectedCounts.Keys) {

        if (-not $currentCounts.ContainsKey($table)) {

            throw "目标库缺少必要表：$table。"

        }

        if ($currentCounts[$table] -ne 0) {

            throw "安全保护：$table 当前有数据；不会覆盖或清理。请新建独立的 $databaseName 数据库。"

        }

    }



    Write-Output "本机空库预检通过；Flyway V3.10 已成功应用。"

    Write-Output '预期数据集：2 社区、100 老人、5000 体征、500 工单、500 预约。'

    if (-not $Apply) {

        Write-Output '尚未写入。确认目标是独立空库后，重新运行并添加 -Apply。'

        return

    }



    $seedSql = "SET @perf_fixed_seed_authorized = 'fixed-dataset-v1';" +

        [Environment]::NewLine +

        (Get-Content -LiteralPath $seedFile -Raw -Encoding UTF8)

    $previousOutputEncoding = $OutputEncoding

    try {

        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)

        $seedSql | & $mysql.Source @mysqlArgs

        $seedExitCode = $LASTEXITCODE

    }

    finally {

        $OutputEncoding = $previousOutputEncoding

    }

    if ($seedExitCode -ne 0) {

        throw "固定数据集导入失败。请废弃并重建 $databaseName 后重试；本工具不会清理部分写入。"

    }



    $afterRows = Invoke-PerformanceMysql $countQuery

    $actualCounts = @{}

    foreach ($row in $afterRows) {

        $parts = [string]$row -split [string][char]9

        if ($parts.Count -eq 2 -and $parts[1] -match '^\d+$') {

            $actualCounts[$parts[0]] = [int]$parts[1]

        }

    }

    foreach ($table in $expectedCounts.Keys) {

        if (-not $actualCounts.ContainsKey($table) -or $actualCounts[$table] -ne $expectedCounts[$table]) {

            throw "导入后的 $table 行数与固定数据集 v1 不符。请废弃并重建 $databaseName；本工具不会清理。"

        }

    }



    Write-Output '固定数据集 v1 已装载并通过行数核对：'

    foreach ($table in $expectedCounts.Keys) {

        Write-Output ("  {0}: {1}" -f $table, $actualCounts[$table])

    }

}

finally {

    if ($null -eq $previousMysqlPassword) {

        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

    } else {

        $env:MYSQL_PWD = $previousMysqlPassword

    }

}
