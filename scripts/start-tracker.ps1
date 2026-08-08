param(
    [int]$Port = 8080
)
. (Join-Path $PSScriptRoot "common.ps1")

Write-Host "=============================================="
Write-Host "  P2P File Sharing - TRACKER"
Write-Host "=============================================="

Assert-Jdk

Write-Step "Kompajliram tracker"
Invoke-JavaBuild $TrackerDir

$ip = Get-LocalIPv4
if ($ip) {
    Write-Host ""
    Write-Host "  Tvoja IP adresa u lokalnoj mrezi: $ip" -ForegroundColor Green
    Write-Host "  Koleginica na drugom racunaru unosi bas ovu adresu" -ForegroundColor Green
    Write-Host "  kada pokrene START-KOLEGINICA-LAN-PEER.bat." -ForegroundColor Green
    # Handy when the tracker and a peer run on the same machine: the peer launcher reuses this.
    Set-LauncherSetting -Name "lastTrackerHostSelf" -Value $ip
}

Write-Step "Pokrecem tracker na portu $Port (Ctrl+C za prekid)"
Write-Host "   pregled peer-ova: http://localhost:$Port/api/peers" -ForegroundColor DarkGray
& (Join-Path $TrackerDir "run.ps1") -Port $Port

