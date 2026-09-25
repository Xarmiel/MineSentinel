package com.sentinelmine.controller;

import com.sentinelmine.dto.AforoDTO;
import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.dto.request.AnomaliaRequestDTO;
import com.sentinelmine.dto.request.FaltaEPPRequestDTO;
import com.sentinelmine.dto.request.MovimientoRequestDTO;
import com.sentinelmine.dto.response.AforoGlobalResponseDTO;
import com.sentinelmine.dto.response.CierreTurnoResponseDTO;
import com.sentinelmine.entity.AnomaliaMovimiento;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.FaltaEPP;
import com.sentinelmine.entity.MovimientoAforo;
import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import com.sentinelmine.service.MovimientoAforoService;
import com.sentinelmine.service.SeguridadInfraccionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador REST para la integración con los módulos de visión por computador (YOLOv8 + ByteTrack)
 * y el consumo de métricas en tiempo real por el frontend/dashboard.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class AforoRestController {

    private final MovimientoAforoService movimientoAforoService;
    private final EventoTurnoService eventoTurnoService;
    private final SeguridadInfraccionService seguridadInfraccionService;
    private final AforoService aforoService;
    private final AlertaService alertaService;

    public AforoRestController(MovimientoAforoService movimientoAforoService,
                               EventoTurnoService eventoTurnoService,
                               SeguridadInfraccionService seguridadInfraccionService,
                               AforoService aforoService,
                               AlertaService alertaService) {
        this.movimientoAforoService = movimientoAforoService;
        this.eventoTurnoService = eventoTurnoService;
        this.seguridadInfraccionService = seguridadInfraccionService;
        this.aforoService = aforoService;
        this.alertaService = alertaService;
    }

    // =========================================================================
    // ENDPOINTS DE CONTROL DE AFORO (BYTE TRACK INGESTION)
    // =========================================================================

    @PostMapping("/aforo/movimiento")
    public ResponseEntity<MovimientoAforo> registrarMovimiento(@Valid @RequestBody MovimientoRequestDTO request) {
        MovimientoAforo movimiento = movimientoAforoService.registrarMovimiento(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(movimiento);
    }

    @GetMapping("/aforo/tiempo-real")
    public ResponseEntity<AforoGlobalResponseDTO> obtenerAforoTiempoReal() {
        AforoGlobalResponseDTO response = movimientoAforoService.obtenerAforoGlobalEnTiempoReal();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/aforo/movimientos/recientes")
    public ResponseEntity<List<MovimientoAforo>> obtenerMovimientosRecientes(
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(movimientoAforoService.obtenerUltimosMovimientos(limit));
    }

    // =========================================================================
    // ENDPOINTS DE GESTIÓN DE TURNOS (SOLAPAMIENTO Y AUDITORÍA)
    // =========================================================================

    @PostMapping("/turnos/abrir")
    public ResponseEntity<EventoTurno> abrirTurno(
            @RequestParam Long turnoId,
            @RequestParam(required = false) LocalDateTime fechaInicio) {
        EventoTurno nuevoEvento = eventoTurnoService.abrirTurno(turnoId, fechaInicio);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoEvento);
    }

    @PostMapping("/turnos/{eventoId}/cerrar")
    public ResponseEntity<CierreTurnoResponseDTO> cerrarTurno(@PathVariable Long eventoId) {
        CierreTurnoResponseDTO cierre = eventoTurnoService.cerrarTurno(eventoId);
        return ResponseEntity.ok(cierre);
    }

    @GetMapping("/turnos/activos")
    public ResponseEntity<List<EventoTurno>> listarTurnosActivos() {
        return ResponseEntity.ok(eventoTurnoService.listarTurnosActivos());
    }

    // =========================================================================
    // ENDPOINTS DE ALERTAS Y SEGURIDAD (YOLOV8)
    // =========================================================================

    @PostMapping("/seguridad/faltas-epp")
    public ResponseEntity<FaltaEPP> reportarFaltaEPP(@Valid @RequestBody FaltaEPPRequestDTO request) {
        FaltaEPP falta = seguridadInfraccionService.registrarFaltaEPP(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(falta);
    }

    @PostMapping("/seguridad/anomalias")
    public ResponseEntity<AnomaliaMovimiento> reportarAnomalia(@Valid @RequestBody AnomaliaRequestDTO request) {
        AnomaliaMovimiento anomalia = seguridadInfraccionService.registrarAnomalia(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(anomalia);
    }

    @GetMapping("/seguridad/faltas-epp/recientes")
    public ResponseEntity<List<FaltaEPP>> obtenerFaltasRecientes(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(seguridadInfraccionService.obtenerUltimasFaltasEPP(limit));
    }

    @GetMapping("/seguridad/anomalias/recientes")
    public ResponseEntity<List<AnomaliaMovimiento>> obtenerAnomaliasRecientes(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(seguridadInfraccionService.obtenerUltimasAnomalias(limit));
    }

    @GetMapping("/aforo/dashboard")
    public ResponseEntity<AforoDTO> obtenerResumenDashboard() {
        return ResponseEntity.ok(aforoService.toDTO());
    }

    @GetMapping("/alertas/recientes")
    public ResponseEntity<List<AlertaViewDTO>> obtenerAlertasRecientes(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(alertaService.getAlertasRecientes(limit));
    }
}
