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
Write-Host "  P2P File Sharing - PEER $Label (LAN)"
Write-Host "=============================================="
Write-Host ""
Write-Host "Ovaj peer se povezuje na tracker koji radi na DRUGOM racunaru."
Write-Host "Na tom racunaru mora biti pokrenut START-TRACKER.bat - on ispisuje"
Write-Host "svoju IP adresu (npr. 192.168.0.15) koju ovde unosis."
Write-Host ""

$settings = Get-LauncherSettings
$saved = $settings.trackerHost
$prompt = if ([string]::IsNullOrWhiteSpace($saved)) {
    "IP adresa racunara sa trackerom"
} else {
    "IP adresa racunara sa trackerom [Enter = $saved]"
}

$trackerHost = ""
$attempts = 0
while ([string]::IsNullOrWhiteSpace($trackerHost)) {
    # Bail out instead of spinning forever if this ever runs without a console (Read-Host sees EOF).
    if ($attempts -ge 5) {
        Write-Warn "Nije uneta IP adresa - prekidam."
        exit 1
    }
    $attempts++
    $answer = Read-Host $prompt
    if ([string]::IsNullOrWhiteSpace($answer)) { $answer = $saved }
    if ([string]::IsNullOrWhiteSpace($answer)) {
        Write-Warn "Moras uneti IP adresu (ili 'localhost' ako tracker radi na ovom racunaru)."
        continue
    }
    $trackerHost = $answer.Trim()
}

Set-LauncherSetting -Name "trackerHost" -Value $trackerHost
$trackerUrl = "http://${trackerHost}:$TrackerPort"

Write-Step "Proveravam da li je tracker dostupan na $trackerUrl"
if (Test-PortListening -Port $TrackerPort -TargetHost $trackerHost -TimeoutMs 2000) {
    Write-Host "   tracker odgovara" -ForegroundColor Green
} else {
    Write-Warn "Tracker se ne javlja na ${trackerHost}:$TrackerPort."
    Write-Host "   Proveri: (1) da li je START-TRACKER.bat pokrenut na tom racunaru,"
    Write-Host "            (2) da li su oba racunara na istoj mrezi,"
    Write-Host "            (3) Windows Firewall -> dozvoli Java na privatnoj mrezi."
    Write-Host "   Peer se svejedno pokrece i sam ce se povezati cim tracker postane dostupan."
    Write-Host ""
    Read-Host "Pritisni Enter da nastavis"
}

& (Join-Path $PSScriptRoot "start-peer.ps1") `
    -Label $Label `
    -SharedDir $SharedDir `
    -DownloadDir $DownloadDir `
    -TcpPort $TcpPort `
    -HttpPort $HttpPort `
    -TrackerUrl $trackerUrl
