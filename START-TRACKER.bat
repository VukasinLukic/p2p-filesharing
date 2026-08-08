@echo off
title P2P Tracker :8080
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-tracker.ps1"
