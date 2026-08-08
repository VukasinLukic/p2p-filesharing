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
    Write-Host "  LAN IP: $ip" -ForegroundColor Green
    Set-LauncherSetting -Name "lastTrackerHostSelf" -Value $ip
}

Write-Step "Pokrecem tracker na portu $Port (Ctrl+C za prekid)"
Write-Host "   pregled peer-ova: http://localhost:$Port/api/peers" -ForegroundColor DarkGray
& (Join-Path $TrackerDir "run.ps1") -Port $Port

