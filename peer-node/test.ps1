$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path (Split-Path -Parent $root) "scripts\java-tools.ps1")
$mainSrc = Join-Path $root "src\main\java"
$testSrc = Join-Path $root "src\test\java"
$out = Join-Path $root "out-test"
$lib = Join-Path $root "lib"

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null

$sources = Join-Path $root "test-sources.txt"
$files = @()
$files += Get-ChildItem -Path $mainSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$files += Get-ChildItem -Path $testSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
[System.IO.File]::WriteAllLines($sources, $files, [System.Text.UTF8Encoding]::new($false))

$classpath = if ((Test-Path $lib) -and (Get-ChildItem -Path $lib -Filter *.jar -ErrorAction SilentlyContinue)) { Join-Path $lib "*" } else { $null }

Write-Host "Compiling peer-node (main + test)..."
if ($classpath) {
    & (Get-JavacExe) -encoding UTF-8 -cp $classpath -d $out "@$sources"
} else {
    & (Get-JavacExe) -encoding UTF-8 -d $out "@$sources"
}
Remove-Item $sources

Write-Host "Running tests..."
$runCp = if ($classpath) { "$out;$classpath" } else { $out }
& (Get-JavaExe) -cp $runCp rs.rmt.peer.AllTests
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Host "TESTS FAILED" -ForegroundColor Red
} else {
    Write-Host "ALL TESTS PASSED" -ForegroundColor Green
}
exit $exitCode
