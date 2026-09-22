# ==============================================================================
#  Script de Arranque para MineSentinel (PowerShell)
# ==============================================================================
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " Iniciando MineSentinel (Spring Boot + Supabase PostgreSQL)..." -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

cmd /c mvnw.cmd compile -o
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Fallo en la compilación de bytecode Java 17." -ForegroundColor Red
    exit $LASTEXITCODE
}

cmd /c mvnw.cmd spring-boot:run
