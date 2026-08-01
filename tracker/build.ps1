$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$src = Join-Path $root "src\main\java"
$out = Join-Path $root "out"

if (-not (Test-Path $out)) {
    New-Item -ItemType Directory -Path $out | Out-Null
}

$sources = Join-Path $root "sources.txt"
$files = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
[System.IO.File]::WriteAllLines($sources, $files, [System.Text.UTF8Encoding]::new($false))

Write-Host "Compiling tracker..."
javac -encoding UTF-8 -d $out "@$sources"
Remove-Item $sources

Write-Host "Build OK -> $out"
