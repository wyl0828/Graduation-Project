$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envExample = Join-Path $repoRoot '.env.example'
$envLocal = Join-Path $repoRoot '.env.local'
$storageRoot = Join-Path $repoRoot '.local-data\files'
$manifestExample = Join-Path $repoRoot 'apps\elder-family\src\manifest.example.json'
$manifestLocal = Join-Path $repoRoot 'apps\elder-family\src\manifest.json'

New-Item -ItemType Directory -Force -Path $storageRoot | Out-Null

if (-not (Test-Path -LiteralPath $envLocal)) {
    Copy-Item -LiteralPath $envExample -Destination $envLocal
    Write-Output '已创建 .env.local；请填写本机隔离演示数据库账号。'
} else {
    Write-Output '已保留现有 .env.local。'
}

if (-not (Test-Path -LiteralPath $manifestLocal)) {
    Copy-Item -LiteralPath $manifestExample -Destination $manifestLocal
    Write-Output '已创建小程序本机 manifest.json；请填写 mp-weixin.appid。'
} else {
    Write-Output '已保留现有小程序 manifest.json。'
}

Write-Output "本地文件目录已就绪：$storageRoot"
Write-Output '此脚本不会创建、清空或修改任何数据库。'
