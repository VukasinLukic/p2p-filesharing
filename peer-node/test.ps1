$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path (Split-Path -Parent $root) "scripts\java-tools.ps1")
$mainSrc = Join-Path $root "src\main\java"
$testSrc = Join-Path $root "src\test\java"
$out = Join-Path $root "out-test"

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null

$sources = Join-Path $root "test-sources.txt"
$files = @()
$files += Get-ChildItem -Path $mainSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$files += Get-ChildItem -Path $testSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
[System.IO.File]::WriteAllLines($sources, $files, [System.Text.UTF8Encoding]::new($false))

Write-Host "Compiling peer-node (main + test)..."
& (Get-JavacExe) -encoding UTF-8 -d $out "@$sources"
Remove-Item $sources

Write-Host "Running tests..."
& (Get-JavaExe) -cp $out rs.rmt.peer.AllTests
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Host "TESTS FAILED" -ForegroundColor Red
} else {
    Write-Host "ALL TESTS PASSED" -ForegroundColor Green
}
exit $exitCode
