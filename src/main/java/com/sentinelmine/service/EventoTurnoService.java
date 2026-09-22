package com.sentinelmine.service;

import com.sentinelmine.dto.response.CierreTurnoResponseDTO;
import com.sentinelmine.entity.CierreAuditoriaTurno;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.Turno;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.CierreAuditoriaTurnoRepository;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.TurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio encargado de la gestión del ciclo de vida de los eventos de turno mineros.
 * 
 * ARQUITECTURA: Diseñado para soportar múltiples turnos activos en paralelo sin bloqueos globales,
 * permitiendo el traslape de guardias (shift overlap) durante los relevos operativos.
 */
@Service
public class EventoTurnoService {

    private final EventoTurnoRepository eventoTurnoRepository;
    private final TurnoRepository turnoRepository;
    private final MovimientoAforoRepository movimientoAforoRepository;
    private final CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository;

    public EventoTurnoService(EventoTurnoRepository eventoTurnoRepository,
                              TurnoRepository turnoRepository,
                              MovimientoAforoRepository movimientoAforoRepository,
                              CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository) {
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.cierreAuditoriaTurnoRepository = cierreAuditoriaTurnoRepository;
    }

    /**
     * Inicia una nueva instancia de turno operativa.
     * Permite abrir un nuevo turno aunque existan otros turnos activos en paralelo (solapamiento).
     */
    @Transactional
    public EventoTurno abrirTurno(Long turnoId, LocalDateTime fechaInicio) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el turno con ID: " + turnoId));

        LocalDateTime inicio = fechaInicio != null ? fechaInicio : LocalDateTime.now();
        EventoTurno nuevoEvento = new EventoTurno(turno, inicio);
        nuevoEvento.setEstado(EstadoTurno.ACTIVO);

        return eventoTurnoRepository.save(nuevoEvento);
    }

    /**
     * Cierra un evento de turno de manera transaccional y determinista:
     * 1. Reconcilia los cruces de entrada y salida registrados por visión artificial.
     * 2. Calcula la discrepancia de personal en interior mina.
     * 3. Persiste el registro inmutable en cierres_auditoria_turno.
     * 4. Actualiza el estado a CERRADO con su fecha_fin respectiva.
     */
    @Transactional
    public CierreTurnoResponseDTO cerrarTurno(Long eventoId) {
        EventoTurno evento = eventoTurnoRepository.findByIdWithTurno(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno con ID: " + eventoId));

        if (evento.getEstado() != EstadoTurno.ACTIVO) {
            throw new IllegalStateException("El evento de turno #" + eventoId + " ya no está ACTIVO (Estado actual: " + evento.getEstado() + ")");
        }

        LocalDateTime fechaCierre = LocalDateTime.now();

        // 1. Conteo de entradas y salidas del evento
        int totalEntradas = (int) movimientoAforoRepository.countByEventoTurno_EventoIdAndTipoMovimiento(eventoId, TipoMovimiento.ENTRADA);
        int totalSalidas = (int) movimientoAforoRepository.countByEventoTurno_EventoIdAndTipoMovimiento(eventoId, TipoMovimiento.SALIDA);
        int diferencia = totalEntradas - totalSalidas;

        // 2. Creación y persistencia de la auditoría de cierre
        CierreAuditoriaTurno auditoria = new CierreAuditoriaTurno(
                evento,
                totalEntradas,
                totalSalidas,
                diferencia,
                fechaCierre
        );
        CierreAuditoriaTurno auditoriaGuardada = cierreAuditoriaTurnoRepository.save(auditoria);

        // 3. Cierre del evento
        evento.setEstado(EstadoTurno.CERRADO);
        evento.setFechaFin(fechaCierre);
        eventoTurnoRepository.save(evento);

        // 4. Diagnóstico de auditoría
        String mensajeAuditoria;
        if (diferencia == 0) {
            mensajeAuditoria = "Cierre exitoso: Balance cuadrado de personal (0 remanentes).";
        } else if (diferencia > 0) {
            mensajeAuditoria = String.format("ALERTA DE AUDITORÍA: Quedan %d personas registradas en socavón sin registro de salida.", diferencia);
        } else {
            mensajeAuditoria = String.format("ADVERTENCIA DE VISIÓN: Se registraron %d salidas adicionales a las entradas del turno.", Math.abs(diferencia));
        }

        return new CierreTurnoResponseDTO(
                auditoriaGuardada.getAuditoriaId(),
                evento.getEventoId(),
                evento.getTurno().getNombre(),
                totalEntradas,
                totalSalidas,
                diferencia,
                fechaCierre,
                mensajeAuditoria
        );
    }

    /**
     * Obtiene la lista de todos los turnos que están actualmente operando en paralelo.
     */
    @Transactional(readOnly = true)
    public List<EventoTurno> listarTurnosActivos() {
        return eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO);
    }

    /**
     * Devuelve una descripción legible de los turnos en ejecución,
     * reflejando si existe solapamiento entre guardias.
     */
    @Transactional(readOnly = true)
    public String obtenerDescripcionTurnosActivos() {
        List<EventoTurno> activos = listarTurnosActivos();
        if (activos.isEmpty()) {
            return "Sin guardia activa";
        }
        if (activos.size() == 1) {
            return activos.get(0).getTurno().getNombre();
        }
        return "Solapamiento: " + activos.stream()
                .map(e -> e.getTurno().getNombre())
                .reduce((a, b) -> a + " + " + b)
                .orElse("Relevo de guardia");
    }

    @Transactional(readOnly = true)
    public EventoTurno obtenerPorId(Long eventoId) {
        return eventoTurnoRepository.findByIdWithTurno(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno #" + eventoId));
    }
}
