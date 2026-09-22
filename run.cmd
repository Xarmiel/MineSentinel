@REM ==============================================================================
@REM  Script de Arranque Rápido para MineSentinel
@REM ==============================================================================
@echo off
setlocal

echo ==============================================================================
echo  Iniciando MineSentinel (Spring Boot + Supabase PostgreSQL)...
echo ==============================================================================

call mvnw.cmd compile -o
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo en la compilacion.
    pause
    exit /b %ERRORLEVEL%
)

call mvnw.cmd spring-boot:run
