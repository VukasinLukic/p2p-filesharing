$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path (Split-Path -Parent $root) "scripts\java-tools.ps1")
$src = Join-Path $root "src\main\java"
$out = Join-Path $root "out"
$sources = Join-Path $root ("sources-" + $PID + ".txt")

if (-not (Test-Path $out)) {
    New-Item -ItemType Directory -Path $out | Out-Null
}

try {
    $files = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    [System.IO.File]::WriteAllLines($sources, $files, [System.Text.UTF8Encoding]::new($false))

    Write-Host "Compiling peer-node..."
    & (Get-JavacExe) -encoding UTF-8 -d $out "@$sources"
    $compileExit = $LASTEXITCODE
} finally {
    if (Test-Path $sources) { Remove-Item -LiteralPath $sources -Force }
}

if ($compileExit -ne 0) {
    Write-Host "BUILD FAILED (javac exit $compileExit)" -ForegroundColor Red
    exit $compileExit
}

Write-Host "Build OK -> $out"
