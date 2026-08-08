@echo off
title P2P Peer B :7002 (LAN)
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-lan-peer.ps1"
