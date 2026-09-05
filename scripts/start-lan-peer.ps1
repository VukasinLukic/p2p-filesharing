param(
    [string]$Label = "2",
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
$prompt = if ([string]::IsNullOrWhiteSpace($saved)) { "Tracker adresa (LAN IP, ili ceo link tipa https://xxx.trycloudflare.com)" } else { "Tracker adresa [$saved]" }
$trackerInput = (Read-Host $prompt).Trim()
if ([string]::IsNullOrWhiteSpace($trackerInput)) { $trackerInput = $saved }
if ([string]::IsNullOrWhiteSpace($trackerInput)) {
    Write-Host "Tracker adresa je obavezna." -ForegroundColor Red
    exit 1
}

Set-LauncherSetting -Name "trackerHost" -Value $trackerInput

# Ako je korisnik uneo ceo link sa protokolom (npr. https://xxx.trycloudflare.com, ili
# http://192.168.1.5:9090), koristi ga bukvalno - ne dodaji jos jedan "http://" niti ":8080".
# Cloudflare Quick Tunnel uvek servira preko https na standardnom portu, bez ikakvog dodatog porta.
# Ako je korisnik uneo samo goli host/IP (LAN slucaj), sastavi http://<host>:<TrackerPort> kao ranije.
if ($trackerInput -match '^[a-zA-Z][a-zA-Z0-9+.-]*://') {
    $trackerUrl = $trackerInput.TrimEnd('/')
} else {
    $trackerUrl = "http://${trackerInput}:$TrackerPort".TrimEnd('/')
}
Write-Host "Tracker: $trackerUrl"

$trackerUri = [Uri]$trackerUrl
$reachable = $false
if ($trackerUri.Scheme -eq 'https') {
    try {
        $resp = Invoke-WebRequest -Uri "$trackerUrl/api/peers" -TimeoutSec 5 -UseBasicParsing
        $reachable = $true
    } catch [System.Net.WebException] {
        # Any HTTP response (even an error status) means the tunnel/tracker answered.
        if ($_.Exception.Response) { $reachable = $true }
    } catch {
        $reachable = $false
    }
} else {
    $reachable = Test-PortListening -Port $trackerUri.Port -TargetHost $trackerUri.Host -TimeoutMs 2000
}
if (-not $reachable) {
    Write-Host "Tracker is not reachable." -ForegroundColor Yellow
}

& (Join-Path $PSScriptRoot "start-peer.ps1") `
    -Label $Label `
    -SharedDir $SharedDir `
    -DownloadDir $DownloadDir `
    -TcpPort $TcpPort `
    -HttpPort $HttpPort `
    -TrackerUrl $trackerUrl
