$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envExample = Join-Path $repoRoot '.env.example'
$envLocal = Join-Path $repoRoot '.env.local'
$storageRoot = Join-Path $repoRoot '.local-data\files'

New-Item -ItemType Directory -Force -Path $storageRoot | Out-Null

if (-not (Test-Path -LiteralPath $envLocal)) {
    Copy-Item -LiteralPath $envExample -Destination $envLocal
    Write-Output '已创建 .env.local；请填写本机隔离演示数据库账号。'
} else {
    Write-Output '已保留现有 .env.local。'
}

Write-Output "本地文件目录已就绪：$storageRoot"
Write-Output '此脚本不会创建、清空或修改任何数据库。'
