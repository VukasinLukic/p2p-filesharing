param(
    [string]$SharedDir = "./shared",
    [string]$DownloadDir = "./downloads",
    [int]$TcpPort = 9001,
    [int]$HttpPort = 7001,
    [string]$TrackerUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root "out"

if (-not (Test-Path $out)) {
    & (Join-Path $root "build.ps1")
}

java -cp $out rs.rmt.peer.PeerMain `
    --shared-dir $SharedDir `
    --download-dir $DownloadDir `
    --tcp-port $TcpPort `
    --http-port $HttpPort `
    --tracker-url $TrackerUrl
