$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path (Split-Path -Parent $root) "scripts\java-tools.ps1")
$src = Join-Path $root "src\main\java"
$out = Join-Path $root "out"

if (-not (Test-Path $out)) {
    New-Item -ItemType Directory -Path $out | Out-Null
}

$sources = Join-Path $root "sources.txt"
$files = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
[System.IO.File]::WriteAllLines($sources, $files, [System.Text.UTF8Encoding]::new($false))

Write-Host "Compiling tracker..."
& (Get-JavacExe) -encoding UTF-8 -d $out "@$sources"
$compileExit = $LASTEXITCODE
Remove-Item $sources
if ($compileExit -ne 0) {
    # Otherwise this prints "Build OK" over a failed compile and the error scrolls past unnoticed.
    Write-Host "BUILD FAILED (javac exit $compileExit)" -ForegroundColor Red
    exit $compileExit
}

Write-Host "Build OK -> $out"
