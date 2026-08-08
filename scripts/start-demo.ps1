. (Join-Path $PSScriptRoot "common.ps1")

Write-Host "=============================================="
Write-Host "  P2P File Sharing - Local Demo"
Write-Host "=============================================="

Assert-Jdk
$occupied = @(8080, 7001, 7002, 8888) | Where-Object { Test-PortListening -Port $_ }
if ($occupied.Count -gt 0) {
    Write-Host "Ports in use: $($occupied -join ', ')" -ForegroundColor Red
    Write-Host "Close existing P2P windows and run the demo again." -ForegroundColor Red
    exit 1
}
Invoke-JavaBuild $TrackerDir
Invoke-JavaBuild $PeerDir
New-DemoFileIfEmpty -SharedDir (Join-Path $PeerDir "shared-a")

function Start-Window([string]$Title, [string]$ScriptFile, [string[]]$ScriptArgs) {
    $command = "`$Host.UI.RawUI.WindowTitle = '$Title'; & '$(Join-Path $PSScriptRoot $ScriptFile)' $($ScriptArgs -join ' ')"
    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-NoExit", "-Command", $command
    ) -WorkingDirectory $RepoRoot | Out-Null
}

Start-Window "P2P Tracker :8080" "start-tracker.ps1" @()
Wait-ForPort -Port 8080 -TimeoutSeconds 40 -Label "tracker" | Out-Null

Start-Window "P2P Peer 1 :7001" "start-peer.ps1" @(
    "-Label 1", "-SharedDir ./shared-a", "-DownloadDir ./downloads-a",
    "-TcpPort 9001", "-HttpPort 7001", "-NoFrontend", "-NoBuild"
)
Wait-ForPort -Port 7001 -TimeoutSeconds 40 -Label "peer 1" | Out-Null

Start-Window "P2P Peer 2 :7002" "start-peer.ps1" @(
    "-Label 2", "-SharedDir ./shared-b", "-DownloadDir ./downloads-b",
    "-TcpPort 9002", "-HttpPort 7002", "-NoFrontend", "-NoBuild"
)
Wait-ForPort -Port 7002 -TimeoutSeconds 40 -Label "peer 2" | Out-Null

Start-FrontendIfNeeded
Open-Frontend -PeerHttpPort 7001
Start-Sleep -Milliseconds 500
Open-Frontend -PeerHttpPort 7002
Write-Host "Demo ready: Tracker 8080 | Peer 1 7001 | Peer 2 7002" -ForegroundColor Green
