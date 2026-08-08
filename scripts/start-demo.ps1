# Full local demo: tracker + two peers + frontend, each in its own window, on one machine.
. (Join-Path $PSScriptRoot "common.ps1")

Write-Host "=============================================="
Write-Host "  P2P File Sharing - LOKALNI DEMO (sve odjednom)"
Write-Host "=============================================="

Assert-Jdk

if (Test-PortListening -Port 8080) {
    Write-Warn "Port 8080 je vec zauzet - verovatno tracker jos radi od ranije."
    Write-Host "   Zatvori stari prozor trackera pa pokreni ponovo, ili nastavi ako je to bas taj tracker."
    Read-Host "Pritisni Enter da nastavis"
}

# Compile both modules once, up front: two peers starting at the same time would otherwise
# race on the same out\ directory.
Write-Step "Kompajliram tracker i peer-node"
Invoke-JavaBuild $TrackerDir
Invoke-JavaBuild $PeerDir

# Peer A is the seeder in this demo, so it gets the sample file; Peer B starts empty and
# downloads it - that is the transfer the committee sees.
New-DemoFileIfEmpty -SharedDir (Join-Path $PeerDir "shared-a")

function Start-InNewWindow([string]$Title, [string]$ScriptFile, [string[]]$ScriptArgs) {
    $argList = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-NoExit",
        "-Command", "`$Host.UI.RawUI.WindowTitle = '$Title'; & '$(Join-Path $PSScriptRoot $ScriptFile)' $($ScriptArgs -join ' ')"
    )
    Start-Process -FilePath "powershell.exe" -ArgumentList $argList -WorkingDirectory $RepoRoot | Out-Null
}

Write-Step "Prozor 1/4: Tracker (port 8080)"
Start-InNewWindow "P2P Tracker :8080" "start-tracker.ps1" @()
if (-not (Wait-ForPort -Port 8080 -TimeoutSeconds 40 -Label "tracker")) {
    Write-Warn "Tracker se nije podigao na vreme - pogledaj njegov prozor za gresku."
}

Write-Step "Prozor 2/4: Peer A (tcp 9001 / api 7001, deli shared-a)"
Start-InNewWindow "P2P Peer A :7001" "start-peer.ps1" @(
    "-Label A", "-SharedDir ./shared-a", "-DownloadDir ./downloads-a",
    "-TcpPort 9001", "-HttpPort 7001", "-NoFrontend")
Wait-ForPort -Port 7001 -TimeoutSeconds 40 -Label "Peer A" | Out-Null

Write-Step "Prozor 3/4: Peer B (tcp 9002 / api 7002, deli shared-b)"
Start-InNewWindow "P2P Peer B :7002" "start-peer.ps1" @(
    "-Label B", "-SharedDir ./shared-b", "-DownloadDir ./downloads-b",
    "-TcpPort 9002", "-HttpPort 7002", "-NoFrontend")
Wait-ForPort -Port 7002 -TimeoutSeconds 40 -Label "Peer B" | Out-Null

Write-Step "Prozor 4/4: Frontend"
Start-FrontendIfNeeded

Write-Step "Otvaram GUI za oba peer-a"
Open-Frontend -PeerHttpPort 7001
Start-Sleep -Milliseconds 800
Open-Frontend -PeerHttpPort 7002

Write-Host ""
Write-Host "=============================================="
Write-Host "  Sve je pokrenuto." -ForegroundColor Green
Write-Host "=============================================="
Write-Host "  Peer A (deli fajl):    http://localhost:5173/?port=7001"
Write-Host "  Peer B (preuzima):     http://localhost:5173/?port=7002"
Write-Host "  Tracker (debug):       http://localhost:8080/api/peers"
Write-Host ""
Write-Host "  Demo: u tabu Peer B -> Pretraga -> 'demo' -> Preuzmi."
Write-Host "  Zaustavljanje: zatvori sve otvorene prozore (ili Ctrl+C u svakom)."
Write-Host ""

