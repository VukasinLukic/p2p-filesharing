@echo off
title P2P Peer A :7001
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-peer.ps1" -Label A -SharedDir ./shared-a -DownloadDir ./downloads-a -TcpPort 9001 -HttpPort 7001 -TrackerUrl http://localhost:8080
echo.
echo Peer A je zaustavljen.
pause
