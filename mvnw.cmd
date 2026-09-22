@REM ==============================================================================
@REM  Maven Wrapper Script for Windows (MineSentinel)
@REM ==============================================================================
@echo off
setlocal

set "IDEA_MAVEN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"

if exist "%IDEA_MAVEN%" (
    call "%IDEA_MAVEN%" %*
    exit /b %ERRORLEVEL%
)

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

echo [ERROR] No se encontro Maven en PATH ni en la instalacion de IntelliJ.
echo Por favor ejecuta con la ruta completa o usa el script build_test.ps1
exit /b 1
