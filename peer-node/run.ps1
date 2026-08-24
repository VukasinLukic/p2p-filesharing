param(
    [string]$SharedDir = "./shared",
    [string]$DownloadDir = "./downloads",
    [int]$TcpPort = 9001,
    [int]$HttpPort = 7001,
    [string]$TrackerUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path (Split-Path -Parent $root) "scripts\java-tools.ps1")
$out = Join-Path $root "out"
$lib = Join-Path $root "lib"

if (-not (Test-Path $out)) {
    & (Join-Path $root "build.ps1")
}

$classpath = if (Test-Path $lib) { "$out;$lib\*" } else { $out }

& (Get-JavaExe) -cp $classpath rs.rmt.peer.PeerMain `
    --shared-dir $SharedDir `
    --download-dir $DownloadDir `
    --tcp-port $TcpPort `
    --http-port $HttpPort `
    --tracker-url $TrackerUrl
