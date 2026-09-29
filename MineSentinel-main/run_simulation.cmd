@echo off
title MineSentinel - Simulador de Vision y Aforo
echo ===============================================================
echo   MineSentinel - Simulador Nativo de Vision Artificial
echo ===============================================================
echo.
echo Iniciando simulador en PowerShell...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0simulate_sentinel.ps1"
pause
