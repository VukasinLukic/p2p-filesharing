param(
    [int]$Port = 8080
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path (Split-Path -Parent $root) "scripts\java-tools.ps1")
$out = Join-Path $root "out"

if (-not (Test-Path $out)) {
    & (Join-Path $root "build.ps1")
}

# The user store lives next to the tracker module, not in whatever directory this was launched from.
& (Get-JavaExe) "-Dtracker.port=$Port" "-Dtracker.data.dir=$(Join-Path $root 'data')" `
    -cp $out rs.rmt.tracker.TrackerMain
