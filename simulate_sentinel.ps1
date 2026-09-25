<#
.SYNOPSIS
    MineSentinel — Simulador Nativo de Tráfico y Visión Artificial para Windows PowerShell
.DESCRIPTION
    Envía cruces de aforo (entradas/salidas) e infracciones de EPP al backend Spring Boot en tiempo real.
    No requiere Python ni librerías externas.
.EXAMPLE
    .\simulate_sentinel.ps1
    .\simulate_sentinel.ps1 -IntervaloSegundos 2 -BackendUrl "http://localhost:8080"
#>

param (
    [string]$BackendUrl = "http://localhost:8080",
    [double]$IntervaloSegundos = 3.0
)

$BackendUrl = $BackendUrl.TrimEnd('/')
$TrackId = 100

Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "🛡️  MINESENTINEL — SIMULADOR NATIVO DE VISIÓN (POWERSHELL)" -ForegroundColor Yellow
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "Destino: $BackendUrl" -ForegroundColor Gray
Write-Host "Intervalo: $IntervaloSegundos segundos por evento" -ForegroundColor Gray
Write-Host "Presiona Ctrl+C en cualquier momento para detener la simulación." -ForegroundColor DarkGray
Write-Host ""

# Verificar si el backend está activo
try {
    $resTest = Invoke-RestMethod -Uri "$BackendUrl/api/v1/aforo/tiempo-real" -Method GET -TimeoutSec 3 -ErrorAction Stop
    Write-Host "✅ Conectado a MineSentinel Backend!" -ForegroundColor Green
    Write-Host "   Aforo actual: $($resTest.aforoActual) / $($resTest.aforoMaximo) (Estado: $($resTest.estadoAforo))" -ForegroundColor Cyan
    Write-Host "   Turnos activos: $($resTest.cantidadTurnosActivos)" -ForegroundColor Cyan
    Write-Host ""
} catch {
    Write-Host "⚠️  ADVERTENCIA: No se pudo conectar a $BackendUrl" -ForegroundColor Yellow
    Write-Host "   Asegúrate de haber iniciado Spring Boot en otra terminal con:" -ForegroundColor Yellow
    Write-Host "   .\mvnw.cmd spring-boot:run" -ForegroundColor White
    Write-Host ""
}

$roles = @(1, 2, 3, 4) # Operador, Supervisor, Técnico, Geólogo
$rolesNombres = @{
    1 = "Operador de Maquinaria / Perforista"
    2 = "Supervisor de Seguridad / Ingeniero"
    3 = "Técnico Electricista / Mantenimiento"
    4 = "Geólogo / Topógrafo"
}

while ($true) {
    $TrackId++
    $rolId = $roles | Get-Random
    $rolNombre = $rolesNombres[$rolId]
    
    # 70% probabilidad de ingreso, 30% salida
    $esEntrada = (Get-Random -Minimum 0 -Maximum 100) -lt 70
    $tipoMovimiento = if ($esEntrada) { "ENTRADA" } else { "SALIDA" }

    # 1. Posible Infracción de EPP en el ingreso (25% probabilidad)
    if ($esEntrada -and ((Get-Random -Minimum 0 -Maximum 100) -lt 25)) {
        $eppId = (1..3) | Get-Random
        $confianza = [math]::Round((Get-Random -Minimum 88 -Maximum 98) / 100.0, 2)
        $snapshotUrl = "/api/v1/evidencias/snapshot_simulado.jpg"

        $eppBody = @{
            eppId = $eppId
            rolId = $rolId
            nivelConfianza = $confianza
            snapshotUrl = $snapshotUrl
        } | ConvertTo-Json

        try {
            $resEPP = Invoke-RestMethod -Uri "$BackendUrl/api/v1/seguridad/faltas-epp" -Method POST -Body $eppBody -ContentType "application/json" -TimeoutSec 5 -ErrorAction Stop
            Write-Host "⚠️  [YOLOv8 EPP] Infracción detectada: Falta #$($resEPP.faltaId) (Confianza: $([int]($confianza*100))%) - $rolNombre" -ForegroundColor Red
        } catch {
            Write-Host "⚠️  Fallo al registrar alerta EPP: $($_.Exception.Message)" -ForegroundColor DarkRed
        }
    }

    # 2. Posible Anomalía de Tránsito (10% probabilidad)
    if ((Get-Random -Minimum 0 -Maximum 100) -lt 10) {
        $anomId = (1..3) | Get-Random
        $confianza = [math]::Round((Get-Random -Minimum 85 -Maximum 95) / 100.0, 2)
        $anomBody = @{
            catalogoAnomaliaId = $anomId
            rolId = $rolId
            nivelConfianza = $confianza
            snapshotUrl = "/api/v1/evidencias/snapshot_anomalia.jpg"
        } | ConvertTo-Json

        try {
            $resAnom = Invoke-RestMethod -Uri "$BackendUrl/api/v1/seguridad/anomalias" -Method POST -Body $anomBody -ContentType "application/json" -TimeoutSec 5 -ErrorAction Stop
            Write-Host "🚨 [ANOMALÍA] Alerta de seguridad: ID #$($resAnom.anomaliaId) ($rolNombre)" -ForegroundColor Magenta
        } catch {
            Write-Host "🚨 Fallo al registrar anomalía: $($_.Exception.Message)" -ForegroundColor DarkRed
        }
    }

    # 3. Registrar Cruce de Aforo
    $movBody = @{
        tipoMovimiento = $tipoMovimiento
        trackId = $TrackId
        rolId = $rolId
    } | ConvertTo-Json

    try {
        $resMov = Invoke-RestMethod -Uri "$BackendUrl/api/v1/aforo/movimiento" -Method POST -Body $movBody -ContentType "application/json" -TimeoutSec 5 -ErrorAction Stop
        $color = if ($tipoMovimiento -eq "ENTRADA") { "Green" } else { "Cyan" }
        Write-Host "✅ [BYTETRACK] Cruce $tipoMovimiento registrado -> Movimiento #$($resMov.movimientoId) (Track #$TrackId) [$rolNombre]" -ForegroundColor $color
    } catch {
        Write-Host "❌ Fallo al registrar movimiento: $($_.Exception.Message)" -ForegroundColor DarkYellow
    }

    Start-Sleep -Seconds $IntervaloSegundos
}
