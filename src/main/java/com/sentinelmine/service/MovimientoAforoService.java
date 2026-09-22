package com.sentinelmine.service;

import com.sentinelmine.dto.request.MovimientoRequestDTO;
import com.sentinelmine.dto.response.AforoGlobalResponseDTO;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.MovimientoAforo;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.RolPersonalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de procesamiento de cruces de aforo para operaciones mineras.
 * 
 * ALTA CONCURRENCIA: Implementa el patrón Append-Only Event Ledger.
 * Cada cruce detectado por ByteTrack se inserta como un evento inmutable, eliminando contenciones
 * y bloqueos de fila (evita bloqueos de tipo 'SELECT FOR UPDATE' sobre contadores centralizados).
 */
@Service
public class MovimientoAforoService {

    private final MovimientoAforoRepository movimientoAforoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final RolPersonalRepository rolPersonalRepository;

    @Value("${minesentinel.aforo.maximo:50}")
    private int aforoMaximo;

    @Value("${minesentinel.aforo.umbral-critico:90}")
    private int umbralCriticoPorcentaje;

    public MovimientoAforoService(MovimientoAforoRepository movimientoAforoRepository,
                                  EventoTurnoRepository eventoTurnoRepository,
                                  RolPersonalRepository rolPersonalRepository) {
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.rolPersonalRepository = rolPersonalRepository;
    }

    /**
     * Registra un cruce individual de línea de aforo en la base de datos.
     * Operación ligera O(1) de inserción libre de locks.
     */
    @Transactional
    public MovimientoAforo registrarMovimiento(MovimientoRequestDTO dto) {
        EventoTurno evento = eventoTurnoRepository.findById(dto.getEventoId())
                .orElseThrow(() -> new IllegalArgumentException("No existe el evento de turno #" + dto.getEventoId()));

        if (evento.getEstado() != EstadoTurno.ACTIVO) {
            throw new IllegalStateException("No se pueden registrar movimientos en un turno inactivo (Estado: " + evento.getEstado() + ")");
        }

        RolPersonal rol = rolPersonalRepository.findById(dto.getRolId())
                .orElseThrow(() -> new IllegalArgumentException("No existe el rol con ID #" + dto.getRolId()));

        LocalDateTime fechaHora = dto.getFechaHora() != null ? dto.getFechaHora() : LocalDateTime.now();

        MovimientoAforo movimiento = new MovimientoAforo(evento, rol, dto.getTipoMovimiento(), fechaHora);
        return movimientoAforoRepository.save(movimiento);
    }

    /**
     * Calcula métricas consolidadas de aforo en tiempo real sumando los turnos activos en paralelo.
     */
    @Transactional(readOnly = true)
    public AforoGlobalResponseDTO obtenerAforoGlobalEnTiempoReal() {
        Integer aforoCalculado = movimientoAforoRepository.calcularAforoNetoGlobalActivo();
        int aforoActual = Math.max(0, aforoCalculado != null ? aforoCalculado : 0);

        List<EventoTurno> turnosActivos = eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO);
        int cantidadTurnosActivos = turnosActivos.size();

        int porcentaje = (int) Math.round((aforoActual * 100.0) / aforoMaximo);

        String estadoAforo;
        if (porcentaje >= 100) {
            estadoAforo = "AFORO MÁXIMO";
        } else if (porcentaje >= umbralCriticoPorcentaje) {
            estadoAforo = "AFORO CRÍTICO";
        } else {
            estadoAforo = "AFORO NORMAL";
        }

        // Desglose por roles y colores de casco
        Map<String, Integer> conteoPorRol = new HashMap<>();
        List<Object[]> resultadosRoles = movimientoAforoRepository.findAforoPorRolActivo();
        for (Object[] fila : resultadosRoles) {
            String nombreRol = (String) fila[0];
            Number total = (Number) fila[2];
            int count = total != null ? Math.max(0, total.intValue()) : 0;
            conteoPorRol.put(nombreRol, count);
        }

        return new AforoGlobalResponseDTO(
                aforoActual,
                aforoMaximo,
                porcentaje,
                estadoAforo,
                cantidadTurnosActivos,
                conteoPorRol
        );
    }

    /**
     * Obtiene el aforo específico de un evento de turno particular.
     */
    @Transactional(readOnly = true)
    public int calcularAforoPorEvento(Long eventoId) {
        Integer aforo = movimientoAforoRepository.calcularAforoNetoPorEvento(eventoId);
        return Math.max(0, aforo != null ? aforo : 0);
    }

    /**
     * Obtiene los últimos cruces de aforo para el stream de eventos del dashboard.
     */
    @Transactional(readOnly = true)
    public List<MovimientoAforo> obtenerUltimosMovimientos(int limit) {
        return movimientoAforoRepository.findUltimosMovimientos(PageRequest.of(0, limit));
    }
}
