param(
    [string]$Label = "B",
    [string]$SharedDir = "./shared-b",
    [string]$DownloadDir = "./downloads-b",
    [int]$TcpPort = 9002,
    [int]$HttpPort = 7002,
    [int]$TrackerPort = 8080
)
. (Join-Path $PSScriptRoot "common.ps1")

Write-Host "=============================================="
Write-Host "  P2P File Sharing - PEER $Label"
Write-Host "=============================================="

$settings = Get-LauncherSettings
$saved = $settings.trackerHost
$prompt = if ([string]::IsNullOrWhiteSpace($saved)) { "Tracker IP" } else { "Tracker IP [$saved]" }
$trackerHost = (Read-Host $prompt).Trim()
if ([string]::IsNullOrWhiteSpace($trackerHost)) { $trackerHost = $saved }
if ([string]::IsNullOrWhiteSpace($trackerHost)) {
    Write-Host "Tracker IP is required." -ForegroundColor Red
    exit 1
}

Set-LauncherSetting -Name "trackerHost" -Value $trackerHost
$trackerUrl = "http://${trackerHost}:$TrackerPort"
Write-Host "Tracker: $trackerUrl"
if (-not (Test-PortListening -Port $TrackerPort -TargetHost $trackerHost -TimeoutMs 2000)) {
    Write-Host "Tracker is not reachable." -ForegroundColor Yellow
}

& (Join-Path $PSScriptRoot "start-peer.ps1") `
    -Label $Label `
    -SharedDir $SharedDir `
    -DownloadDir $DownloadDir `
    -TcpPort $TcpPort `
    -HttpPort $HttpPort `
    -TrackerUrl $trackerUrl
