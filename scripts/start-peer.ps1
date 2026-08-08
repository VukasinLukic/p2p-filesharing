param(
    [string]$Label = "A",
    [string]$SharedDir = "./shared-a",
    [string]$DownloadDir = "./downloads-a",
    [int]$TcpPort = 9001,
    [int]$HttpPort = 7001,
    [string]$TrackerUrl = "http://localhost:8080",
    [switch]$NoFrontend,
    [switch]$NoBuild,
    [switch]$SeedDemoFile
)
. (Join-Path $PSScriptRoot "common.ps1")

Write-Host "=============================================="
Write-Host "  P2P File Sharing - PEER $Label"
Write-Host "=============================================="

Assert-Jdk

$absShared = [System.IO.Path]::GetFullPath((Join-Path $PeerDir $SharedDir))
$absDownload = [System.IO.Path]::GetFullPath((Join-Path $PeerDir $DownloadDir))
New-Item -ItemType Directory -Path $absShared -Force | Out-Null
New-Item -ItemType Directory -Path $absDownload -Force | Out-Null
if ($SeedDemoFile) { New-DemoFileIfEmpty -SharedDir $absShared }

Write-Host "  TCP: $TcpPort | API: $HttpPort | Tracker: $TrackerUrl"

if (-not $NoBuild) {
    Write-Step "Kompajliram peer-node"
    Invoke-JavaBuild $PeerDir
}

if (-not $NoFrontend) {
    Start-FrontendIfNeeded -NoWait
    Open-FrontendWhenReady -PeerHttpPort $HttpPort
}

Write-Step "Pokrecem Peer $Label (Ctrl+C za prekid)"
& (Join-Path $PeerDir "run.ps1") `
    -SharedDir $absShared `
    -DownloadDir $absDownload `
    -TcpPort $TcpPort `
    -HttpPort $HttpPort `
    -TrackerUrl $TrackerUrl

