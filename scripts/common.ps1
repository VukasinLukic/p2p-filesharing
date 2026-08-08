# Shared helpers for the one-click launcher scripts (START-*.bat -> scripts\start-*.ps1).
# Deliberately ASCII-only: .bat/.ps1 files are read with the console codepage, so diacritics
# would render as garbage in the launcher windows on a default Windows install.

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "java-tools.ps1")

$RepoRoot = Split-Path -Parent $PSScriptRoot
$TrackerDir = Join-Path $RepoRoot "tracker"
$PeerDir = Join-Path $RepoRoot "peer-node"
$FrontendDir = Join-Path $RepoRoot "frontend"
$SettingsFile = Join-Path $PSScriptRoot "settings.json"
$FrontendPort = 5173

function Write-Step($message) {
    Write-Host ""
    Write-Host ">> $message" -ForegroundColor Cyan
}

function Write-Warn($message) {
    Write-Host "!! $message" -ForegroundColor Yellow
}

# Fails early with a readable message instead of a raw "term not recognized" stack.
function Assert-Command($name, $hint) {
    $found = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $found) {
        Write-Host ""
        Write-Host "GRESKA: '$name' nije pronadjen u PATH-u." -ForegroundColor Red
        Write-Host "        $hint" -ForegroundColor Red
        Write-Host ""
        Read-Host "Pritisni Enter da zatvoris"
        exit 1
    }
}

# TcpClient instead of Test-NetConnection: same answer, ~300ms instead of several seconds.
function Test-PortListening([int]$Port, [string]$TargetHost = "127.0.0.1", [int]$TimeoutMs = 400) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-ForPort([int]$Port, [string]$TargetHost = "127.0.0.1", [int]$TimeoutSeconds = 30, [string]$Label = "servis") {
    Write-Host "   cekam da se $Label podigne na portu $Port ..." -NoNewline
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port -TargetHost $TargetHost) {
            Write-Host " OK" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Milliseconds 500
        Write-Host "." -NoNewline
    }
    Write-Host " ISTEKLO" -ForegroundColor Yellow
    return $false
}

# ---------- Persisted launcher settings (scripts\settings.json, gitignored) ----------

function Get-LauncherSettings {
    if (Test-Path $SettingsFile) {
        try {
            return (Get-Content $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json)
        } catch {
            Write-Warn "settings.json je ostecen, ignorisem ga."
        }
    }
    return [pscustomobject]@{ trackerHost = "" }
}

function Set-LauncherSetting([string]$Name, [string]$Value) {
    $settings = Get-LauncherSettings
    if ($settings.PSObject.Properties.Name -contains $Name) {
        $settings.$Name = $Value
    } else {
        $settings | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
    $settings | ConvertTo-Json | Out-File -FilePath $SettingsFile -Encoding utf8
}

function Get-LocalIPv4 {
    try {
        $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" }
        $preferred = $addresses | Where-Object { $_.PrefixOrigin -eq "Dhcp" } | Select-Object -First 1
        if ($null -eq $preferred) { $preferred = $addresses | Select-Object -First 1 }
        if ($null -ne $preferred) { return $preferred.IPAddress }
    } catch {
        # Get-NetIPAddress is unavailable on some SKUs - the IP is only informational, so ignore.
    }
    return $null
}

# ---------- Java modules ----------

# Same check the build does, but reported up front with a fixable message instead of mid-compile.
function Assert-Jdk {
    try {
        $java = Get-JavaExe
        Write-Host "   JDK: $java" -ForegroundColor DarkGray
    } catch {
        Write-Host ""
        Write-Host "GRESKA: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "        Preuzmi JDK 17+ (npr. https://adoptium.net) i restartuj racunar." -ForegroundColor Red
        Write-Host ""
        Read-Host "Pritisni Enter da zatvoris"
        exit 1
    }
}

function Invoke-JavaBuild([string]$ModuleDir) {
    # Always rebuild: run.ps1 only compiles when out\ is missing, which silently runs stale
    # .class files after a source edit - the exact failure mode that wastes demo time.
    & (Join-Path $ModuleDir "build.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Kompajliranje nije uspelo: $ModuleDir" }
}

# ---------- Frontend ----------

function Install-FrontendDepsIfNeeded {
    if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
        Write-Step "Prvo pokretanje - instaliram npm pakete (moze potrajati par minuta)"
        Push-Location $FrontendDir
        try {
            & cmd /c "npm install"
            if ($LASTEXITCODE -ne 0) { throw "npm install nije uspeo" }
        } finally {
            Pop-Location
        }
    }
}

function Start-FrontendIfNeeded {
    if (Test-PortListening -Port $FrontendPort) {
        Write-Host "   frontend vec radi na http://localhost:$FrontendPort" -ForegroundColor DarkGray
        return
    }
    Assert-Command "node" "Instaliraj Node.js 18+ sa https://nodejs.org"
    Assert-Command "npm" "Instaliraj Node.js 18+ sa https://nodejs.org"
    Install-FrontendDepsIfNeeded

    Write-Step "Pokrecem frontend (Vite dev server) u zasebnom prozoru"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/k", "title P2P Frontend (Vite) && npm run dev" `
        -WorkingDirectory $FrontendDir | Out-Null
    Wait-ForPort -Port $FrontendPort -TimeoutSeconds 60 -Label "frontend" | Out-Null
}

function Open-Frontend([int]$PeerHttpPort) {
    $url = "http://localhost:$FrontendPort/?port=$PeerHttpPort"
    Write-Host "   otvaram $url" -ForegroundColor DarkGray
    Start-Process $url | Out-Null
}

# ---------- Demo data ----------

# The DoD asks for a >=10MB transfer; an empty shared folder is the #1 reason a demo shows nothing.
function New-DemoFileIfEmpty([string]$SharedDir, [string]$FileName = "demo-10mb.bin", [int]$SizeMb = 10) {
    if (-not (Test-Path $SharedDir)) {
        New-Item -ItemType Directory -Path $SharedDir -Force | Out-Null
    }
    $existing = @(Get-ChildItem -Path $SharedDir -File -Recurse -ErrorAction SilentlyContinue)
    if ($existing.Count -gt 0) { return }

    $target = Join-Path $SharedDir $FileName
    Write-Host "   deljeni folder je prazan - pravim test fajl $FileName ($SizeMb MB)" -ForegroundColor DarkGray
    $buffer = [byte[]]::new(1MB)
    [System.Random]::new(42).NextBytes($buffer)
    $stream = [System.IO.File]::Create($target)
    try {
        for ($i = 0; $i -lt $SizeMb; $i++) { $stream.Write($buffer, 0, $buffer.Length) }
    } finally {
        $stream.Close()
    }
}
