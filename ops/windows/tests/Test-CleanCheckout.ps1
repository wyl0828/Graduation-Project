[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$script:repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$script:runId = [Guid]::NewGuid().ToString('N')
$script:tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([char[]]@(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
))
$script:tempRoot = Join-Path $script:tempBase ('clean-checkout-' + $script:runId)
$script:checkoutRoot = Join-Path $script:tempRoot 'checkout'
$script:ownerMarkerPath = Join-Path $script:tempRoot '.clean-checkout-owner'
$script:ownerMarkerValue = 'clean-checkout-owner:' + $script:runId
$script:logPath = Join-Path $script:tempRoot 'run.log'
$script:failurePath = Join-Path $script:tempRoot 'failure.txt'
$script:sourceCommit = $null
$script:worktreeAdded = $false
$script:success = $false
$script:failureMessage = $null
$script:completedSteps = [Collections.Generic.List[string]]::new()
$script:gitExe = $null
$script:pwshExe = $null
$script:npmExe = $null
$script:javaExe = $null
$script:nodeExe = $null
$script:mysqlExe = $null

function ConvertTo-SafeDiagnosticLine([string]$Line) {
    $safeLine = [string]$Line
    $safeLine = [regex]::Replace(
        $safeLine,
        '(?i)(["'']?[A-Z0-9_.-]*(?:PASSWORD|PASSWD|PWD|SECRET|TOKEN|API[_-]?KEY|HMAC[_-]?KEY|_AUTH|COOKIE|CSRF|SESSION|AUTHORIZATION)[A-Z0-9_.-]*["'']?\s*[:=]\s*)(?:"[^"]*"|''[^'']*''|[^\r\n,;}\]]+)',
        '$1[REDACTED]'
    )
    $safeLine = [regex]::Replace(
        $safeLine,
        '(?i)(["'']?(?:SET-COOKIE|COOKIE|AUTHORIZATION|PROXY-AUTHORIZATION|X-CSRF-TOKEN)["'']?\s*:\s*)(?:"[^"]*"|''[^'']*''|[^\r\n]+)',
        '$1[REDACTED]'
    )
    $safeLine = [regex]::Replace(
        $safeLine,
        '(?i)(--(?:password|passwd|token|secret|api-key)(?:=|\s+))\S+',
        '$1[REDACTED]'
    )
    $safeLine = [regex]::Replace(
        $safeLine,
        '(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]+',
        '$1 [REDACTED]'
    )
    $safeLine = [regex]::Replace(
        $safeLine,
        '(?i)(?:jdbc:)?(mysql|https?)://[^/\s:@]+:[^@\s/]+@',
        '$1://[REDACTED]@'
    )
    return $safeLine
}

function Add-RunLog([string]$Message) {
    if (-not (Test-Path -LiteralPath $script:logPath)) { return }
    $safeMessage = ConvertTo-SafeDiagnosticLine $Message
    $timestamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz')
    Add-Content -LiteralPath $script:logPath -Value ('[{0}] {1}' -f $timestamp, $safeMessage) -Encoding UTF8
}

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory)][string]$WorkingDirectory
    )

    Write-Host "开始：$Name"
    Add-RunLog "START: $Name"

    $outputLines = @()
    $safeLines = @()
    $exitCode = 1
    $locationPushed = $false
    try {
        Push-Location -LiteralPath $WorkingDirectory
        $locationPushed = $true
        $outputLines = @(& $FilePath @Arguments 2>&1)
        $exitCode = [int]$LASTEXITCODE
    } catch {
        $outputLines = @($_.Exception.Message)
        $exitCode = 1
    } finally {
        if ($locationPushed) { Pop-Location }
    }

    $safeLines = @($outputLines | ForEach-Object {
        ConvertTo-SafeDiagnosticLine ([string]$_)
    })
    foreach ($line in $safeLines) {
        Add-RunLog ("| " + [string]$line)
    }
    Add-RunLog "END: $Name (exit code $exitCode)"
    $script:completedSteps.Add("$Name (exit code $exitCode)")
    Write-Host ("完成：{0}（退出码 {1}）" -f $Name, $exitCode)

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = [string[]]$safeLines
    }
}

function Invoke-RequiredCommand {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory)][string]$WorkingDirectory
    )

    $result = Invoke-LoggedCommand -Name $Name -FilePath $FilePath -Arguments $Arguments -WorkingDirectory $WorkingDirectory
    if ($result.ExitCode -ne 0) {
        throw "$Name 失败，退出码 $($result.ExitCode)。"
    }
}

function Assert-OwnedTempRoot {
    $resolvedRoot = [IO.Path]::GetFullPath($script:tempRoot)
    $expectedLeaf = 'clean-checkout-' + $script:runId
    $rootItem = Get-Item -LiteralPath $resolvedRoot -Force -ErrorAction Stop
    if ($rootItem.PSIsContainer -ne $true -or
        ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw '临时根目录不是本次创建的普通目录，拒绝清理。'
    }
    $resolvedParent = [IO.Path]::GetDirectoryName($resolvedRoot).TrimEnd([char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    ))
    if (-not [string]::Equals($resolvedParent, $script:tempBase, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolvedRoot) -cne $expectedLeaf) {
        throw '临时根目录路径归属校验失败，拒绝清理。'
    }
    if (-not (Test-Path -LiteralPath $script:ownerMarkerPath -PathType Leaf)) {
        throw '临时根目录所有权标记缺失，拒绝清理。'
    }
    $markerItem = Get-Item -LiteralPath $script:ownerMarkerPath -Force -ErrorAction Stop
    if (($markerItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw '临时根目录所有权标记被替换，拒绝清理。'
    }
    $markerValue = (Get-Content -LiteralPath $script:ownerMarkerPath -Raw -ErrorAction Stop).Trim()
    if ($markerValue -cne $script:ownerMarkerValue) {
        throw '临时根目录所有权标记不匹配，拒绝清理。'
    }
}

function Remove-OwnedCheckout {
    Assert-OwnedTempRoot

    if ($script:worktreeAdded) {
        if (-not (Test-Path -LiteralPath $script:checkoutRoot -PathType Container)) {
            throw '本次 Git worktree 路径缺失，拒绝执行清理。'
        }
        $checkoutItem = Get-Item -LiteralPath $script:checkoutRoot -Force -ErrorAction Stop
        if (($checkoutItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw '临时 checkout 路径变成了重解析点，拒绝清理。'
        }

        $removeResult = Invoke-LoggedCommand -Name '移除本次临时 Git worktree' -FilePath $script:gitExe -Arguments @('-C', $script:repoRoot, 'worktree', 'remove', '--force', $script:checkoutRoot) -WorkingDirectory $script:repoRoot
        if ($removeResult.ExitCode -ne 0 -or (Test-Path -LiteralPath $script:checkoutRoot)) {
            throw 'Git 未确认移除本次临时 worktree；临时目录保留。'
        }
        $script:worktreeAdded = $false
    }

    Assert-OwnedTempRoot
    $expectedNames = @('.clean-checkout-owner', 'run.log')
    $children = @(Get-ChildItem -LiteralPath $script:tempRoot -Force -ErrorAction Stop)
    if ($children.Count -ne $expectedNames.Count -or
        @($children | Where-Object { $_.Name -notin $expectedNames }).Count -gt 0) {
        throw '临时根目录包含非本脚本创建的内容，拒绝递归清理。'
    }
    foreach ($child in $children) {
        if ($child.PSIsContainer -or ($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw '临时根目录包含目录或重解析点，拒绝清理。'
        }
    }

    Add-RunLog '清理前路径、所有权标记和子项均已核验。'
    Remove-Item -LiteralPath $script:ownerMarkerPath -Force -ErrorAction Stop
    Remove-Item -LiteralPath $script:logPath -Force -ErrorAction Stop
    Remove-Item -LiteralPath $script:tempRoot -Force -ErrorAction Stop
}

function Write-FailureDiagnostics {
    $safeFailure = ConvertTo-SafeDiagnosticLine $script:failureMessage
    if (-not (Test-Path -LiteralPath $script:tempRoot -PathType Container)) {
        [Console]::Error.WriteLine("失败：$safeFailure")
        return
    }

    try {
        Assert-OwnedTempRoot
        $summary = [Collections.Generic.List[string]]::new()
        $summary.Add('Clean checkout validation failed.')
        $commitLabel = if ($script:sourceCommit) { $script:sourceCommit } else { 'unresolved' }
        $summary.Add("Source commit: $commitLabel")
        $summary.Add("Failure: $safeFailure")
        $summary.Add('Completed steps:')
        foreach ($step in $script:completedSteps) { $summary.Add("  $step") }

        if (Test-Path -LiteralPath $script:failurePath) {
            $failureItem = Get-Item -LiteralPath $script:failurePath -Force -ErrorAction Stop
            if (($failureItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw '失败摘要路径是重解析点，拒绝写入。'
            }
        }
        $summary | Set-Content -LiteralPath $script:failurePath -Encoding UTF8 -ErrorAction Stop

        $hasLog = Test-Path -LiteralPath $script:logPath -PathType Leaf
        if ($hasLog) {
            $logItem = Get-Item -LiteralPath $script:logPath -Force -ErrorAction Stop
            if (($logItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw '运行日志路径是重解析点，拒绝读取。'
            }
            Add-RunLog "FAILED: $safeFailure"
        }

        [Console]::Error.WriteLine("失败：$safeFailure")
        [Console]::Error.WriteLine("失败摘要：$script:failurePath")
        if ($hasLog) {
            [Console]::Error.WriteLine("诊断日志：$script:logPath")
            [Console]::Error.WriteLine('日志末尾：')
            foreach ($line in (Get-Content -LiteralPath $script:logPath -Tail 80 -ErrorAction SilentlyContinue)) {
                [Console]::Error.WriteLine((ConvertTo-SafeDiagnosticLine ([string]$line)))
            }
        }
    } catch {
        $diagnosticFailure = ConvertTo-SafeDiagnosticLine ([string]$_.Exception.Message)
        [Console]::Error.WriteLine("失败：$safeFailure")
        [Console]::Error.WriteLine("无法安全写入诊断摘要：$diagnosticFailure")
        [Console]::Error.WriteLine("临时目录保留待检查：$script:tempRoot")
    }
}

$createdRoot = $false
try {
    New-Item -ItemType Directory -Path $script:tempRoot -ErrorAction Stop | Out-Null
    $createdRoot = $true
    Set-Content -LiteralPath $script:ownerMarkerPath -Value $script:ownerMarkerValue -Encoding ASCII
    Assert-OwnedTempRoot
    Set-Content -LiteralPath $script:logPath -Value 'Clean checkout verification log. Command output is redacted before persistence.' -Encoding UTF8
    Add-RunLog "PowerShell version: $($PSVersionTable.PSVersion)"
    Add-RunLog "Source repository: $script:repoRoot"
    Add-RunLog "Temporary checkout root: $script:checkoutRoot"

    $gitCommand = Get-Command git -ErrorAction Stop
    $pwshCommand = Get-Command pwsh -ErrorAction Stop
    $javaCommand = Get-Command java -ErrorAction Stop
    $nodeCommand = Get-Command node -ErrorAction Stop
    $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npmCommand) { $npmCommand = Get-Command npm -ErrorAction Stop }
    $mysqlCommand = Get-Command mysql -ErrorAction Stop
    foreach ($tool in @($gitCommand, $pwshCommand, $javaCommand, $nodeCommand, $npmCommand, $mysqlCommand)) {
        if ([string]::IsNullOrWhiteSpace([string]$tool.Source)) {
            throw "无法解析命令路径：$($tool.Name)"
        }
    }

    $script:gitExe = $gitCommand.Source
    $script:pwshExe = $pwshCommand.Source
    $script:javaExe = $javaCommand.Source
    $script:nodeExe = $nodeCommand.Source
    $script:npmExe = $npmCommand.Source
    $script:mysqlExe = $mysqlCommand.Source
    $mysqlServerPath = Join-Path (Split-Path -Parent $script:mysqlExe) 'mysqld.exe'
    if (-not (Test-Path -LiteralPath $mysqlServerPath -PathType Leaf)) {
        throw '现有隔离数据库验收入口需要与 mysql.exe 同目录的 mysqld.exe。'
    }

    foreach ($versionCheck in @(
        @{ Name = 'Git version'; File = $script:gitExe; Args = @('--version') },
        @{ Name = 'Java version'; File = $script:javaExe; Args = @('-version') },
        @{ Name = 'Node.js version'; File = $script:nodeExe; Args = @('--version') },
        @{ Name = 'npm version'; File = $script:npmExe; Args = @('--version') },
        @{ Name = 'MySQL client version'; File = $script:mysqlExe; Args = @('--version') }
    )) {
        $null = Invoke-RequiredCommand -Name $versionCheck.Name -FilePath $versionCheck.File -Arguments $versionCheck.Args -WorkingDirectory $script:repoRoot
    }

    $headResult = Invoke-LoggedCommand -Name '读取源 HEAD 提交号' -FilePath $script:gitExe -Arguments @('-C', $script:repoRoot, 'rev-parse', '--verify', 'HEAD^{commit}') -WorkingDirectory $script:repoRoot
    if ($headResult.ExitCode -ne 0) { throw '无法从源仓库解析已提交 HEAD。' }
    $headLines = @($headResult.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($headLines.Count -ne 1 -or $headLines[0] -notmatch '^[0-9a-fA-F]{40,64}$') {
        throw '源仓库 HEAD 输出格式异常，拒绝创建临时 checkout。'
    }
    $script:sourceCommit = [string]$headLines[0]

    $addResult = Invoke-LoggedCommand -Name '从已提交 HEAD 建立 detached 临时 worktree' -FilePath $script:gitExe -Arguments @('-C', $script:repoRoot, 'worktree', 'add', '--detach', $script:checkoutRoot, $script:sourceCommit) -WorkingDirectory $script:repoRoot
    if ($addResult.ExitCode -ne 0) { throw 'Git worktree 创建失败。' }
    $script:worktreeAdded = $true

    $checkoutHead = Invoke-LoggedCommand -Name '核对临时 checkout 提交号' -FilePath $script:gitExe -Arguments @('-C', $script:checkoutRoot, 'rev-parse', '--verify', 'HEAD^{commit}') -WorkingDirectory $script:checkoutRoot
    if ($checkoutHead.ExitCode -ne 0 -or
        (@($checkoutHead.Output | Where-Object { $_ }) -join '').Trim() -cne $script:sourceCommit) {
        throw '临时 checkout 的 HEAD 与源仓库已提交 HEAD 不一致。'
    }

    $checkoutStatus = Invoke-LoggedCommand -Name '确认临时 checkout 初始工作区干净' -FilePath $script:gitExe -Arguments @('-C', $script:checkoutRoot, 'status', '--porcelain=v1', '--untracked-files=all') -WorkingDirectory $script:checkoutRoot
    if ($checkoutStatus.ExitCode -ne 0) { throw '无法读取临时 checkout 状态。' }
    if (@($checkoutStatus.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
        throw 'Git 创建的临时 checkout 初始状态不是干净状态。'
    }
    Add-RunLog "Verified clean detached worktree at commit $script:sourceCommit."

    $initLocalPath = Join-Path $script:checkoutRoot 'ops\windows\init-local.ps1'
    $null = Invoke-RequiredCommand -Name '在临时 checkout 初始化被忽略的本地文件' -FilePath $script:pwshExe -Arguments @('-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $initLocalPath) -WorkingDirectory $script:checkoutRoot

    $adminRoot = Join-Path $script:checkoutRoot 'apps\admin-web'
    $elderFamilyRoot = Join-Path $script:checkoutRoot 'apps\elder-family'
    $null = Invoke-RequiredCommand -Name '管理端依赖安装 npm ci' -FilePath $script:npmExe -Arguments @('ci') -WorkingDirectory $adminRoot
    $null = Invoke-RequiredCommand -Name '老人/家属端依赖安装 npm ci' -FilePath $script:npmExe -Arguments @('ci') -WorkingDirectory $elderFamilyRoot

    $systemAcceptancePath = Join-Path $script:checkoutRoot 'ops\windows\tests\Test-SystemHttpTempDb.ps1'
    $null = Invoke-RequiredCommand -Name '临时 MySQL 迁移、合成 seed、后端完整 Maven 测试与 HTTP 验收' -FilePath $script:pwshExe -Arguments @('-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $systemAcceptancePath) -WorkingDirectory $script:checkoutRoot

    $null = Invoke-RequiredCommand -Name '管理端类型检查与生产构建' -FilePath $script:npmExe -Arguments @('run', 'build') -WorkingDirectory $adminRoot
    $null = Invoke-RequiredCommand -Name '老人/家属端类型检查' -FilePath $script:npmExe -Arguments @('run', 'type-check') -WorkingDirectory $elderFamilyRoot
    $null = Invoke-RequiredCommand -Name '老人/家属端 H5 构建' -FilePath $script:npmExe -Arguments @('run', 'build:h5') -WorkingDirectory $elderFamilyRoot

    Remove-OwnedCheckout
    $script:success = $true
} catch {
    $script:failureMessage = ConvertTo-SafeDiagnosticLine ([string]$_.Exception.Message)
    if ($createdRoot) {
        try { Write-FailureDiagnostics }
        catch {
            [Console]::Error.WriteLine("失败：$script:failureMessage")
            [Console]::Error.WriteLine("诊断目录保留：$script:tempRoot")
        }
    } else {
        [Console]::Error.WriteLine("失败：$script:failureMessage")
    }
}

if ($script:success) {
    Write-Output "干净检出验收通过，提交：$script:sourceCommit"
    Write-Output ('完成步骤：' + ($script:completedSteps -join '；'))
    Write-Output '已安全移除本次创建的临时 Git worktree 与诊断目录。'
    Write-Output '范围：本入口验证管理端与老人/家属端 H5；微信小程序 AppID、开发者工具和真机验收留待 Task6 Step3。'
    exit 0
}

exit 1
