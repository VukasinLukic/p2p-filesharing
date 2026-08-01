param(
    [int]$Port = 8080
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root "out"

if (-not (Test-Path $out)) {
    & (Join-Path $root "build.ps1")
}

java "-Dtracker.port=$Port" -cp $out rs.rmt.tracker.TrackerMain
