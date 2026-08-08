@echo off
title P2P Lokalni Demo (launcher)
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-demo.ps1"
echo.
echo Ovaj prozor je samo pokretac - tracker, peer-ovi i frontend rade
echo u svojim prozorima. Slobodno ga zatvori.
pause
