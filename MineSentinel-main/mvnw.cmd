@REM ==============================================================================
@REM  Maven Wrapper Script for Windows (MineSentinel)
@REM ==============================================================================
@echo off
setlocal enabledelayedexpansion

REM ==============================================================================
REM  Maven Wrapper Script for Windows (MineSentinel)
REM  Busca Maven en: IntelliJ -> maven wrapper cache -> %USERPROFILE%\.maven
REM  -> PATH -> Rutas conocidas de NetBeans / descargas.
REM =============================================================================

set "IDEA_MAVEN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"
if exist "%IDEA_MAVEN%" (
    call "%IDEA_MAVEN%" %*
    exit /b %ERRORLEVEL%
)

REM Cache del Maven Wrapper (.m2\wrapper\dists)
for /f "delims=" %%I in ('dir /b /s "%USERPROFILE%\.m2\wrapper\dists" 2^>nul ^| findstr /i /c:"\bin\mvn.cmd"') do (
    if not defined WRAPPER_MVN set "WRAPPER_MVN=%%I"
)
if defined WRAPPER_MVN (
    call "%WRAPPER_MVN%" %*
    exit /b %ERRORLEVEL%
)

if exist "%USERPROFILE%\.maven" (
    for /d %%D in ("%USERPROFILE%\.maven\*") do (
        if exist "%%D\bin\mvn.cmd" (
            call "%%D\bin\mvn.cmd" %*
            exit /b %ERRORLEVEL%
        )
    )
)

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

for %%M in (
    "C:\maven\apache-maven-3.9.9\bin\mvn.cmd"
    "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
    "C:\Program Files\NetBeans-20\netbeans\java\maven\bin\mvn.cmd"
) do (
    if exist %%M (
        call %%M %*
        exit /b %ERRORLEVEL%
    )
)

echo [ERROR] No se encontro Maven en PATH ni en la instalacion de IntelliJ.
echo         Instala Maven o define MAVEN_HOME / PATH antes de continuar.
exit /b 1
