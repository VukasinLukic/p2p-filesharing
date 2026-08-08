param(
    [string]$Label = "A",
    [string]$SharedDir = "./shared-a",
    [string]$DownloadDir = "./downloads-a",
    [int]$TcpPort = 9001,
    [int]$HttpPort = 7001,
    [string]$TrackerUrl = "http://localhost:8080",
    # Set when the caller (start-demo.ps1) already started Vite / will open the browser itself.
    [switch]$NoFrontend,
    [switch]$SeedDemoFile
)
. (Join-Path $PSScriptRoot "common.ps1")

Write-Host "=============================================="
Write-Host "  P2P File Sharing - PEER $Label"
Write-Host "=============================================="

Assert-Jdk

# Relative dirs in the params are relative to peer-node\ (that is run.ps1's working directory).
$absShared = [System.IO.Path]::GetFullPath((Join-Path $PeerDir $SharedDir))
$absDownload = [System.IO.Path]::GetFullPath((Join-Path $PeerDir $DownloadDir))
New-Item -ItemType Directory -Path $absShared -Force | Out-Null
New-Item -ItemType Directory -Path $absDownload -Force | Out-Null
if ($SeedDemoFile) { New-DemoFileIfEmpty -SharedDir $absShared }

Write-Host ""
Write-Host "  Deljeni folder:  $absShared"
Write-Host "  Preuzimanja:     $absDownload"
Write-Host "  TCP transfer:    $TcpPort"
Write-Host "  Lokalni REST:    http://localhost:$HttpPort/api"
Write-Host "  Tracker:         $TrackerUrl"
Write-Host ""
Write-Host "  NAPOMENA: deljeni folder se skenira SAMO pri startu." -ForegroundColor Yellow
Write-Host "            Ubaci fajlove pre pokretanja (ili restartuj peer)." -ForegroundColor Yellow

Write-Step "Kompajliram peer-node"
Invoke-JavaBuild $PeerDir

if (-not $NoFrontend) {
    Start-FrontendIfNeeded
    Open-Frontend -PeerHttpPort $HttpPort
}

Write-Step "Pokrecem Peer $Label (Ctrl+C za prekid)"
# Absolute paths on purpose: PeerConfig resolves relative dirs against the *current* working
# directory, which is the repo root when the launcher is started by double-clicking the .bat.
& (Join-Path $PeerDir "run.ps1") `
    -SharedDir $absShared `
    -DownloadDir $absDownload `
    -TcpPort $TcpPort `
    -HttpPort $HttpPort `
    -TrackerUrl $TrackerUrl

