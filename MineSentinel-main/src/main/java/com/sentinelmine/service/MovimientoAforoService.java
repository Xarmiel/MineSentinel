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

@Service
public class MovimientoAforoService {

    private final MovimientoAforoRepository movimientoAforoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final RolPersonalRepository rolPersonalRepository;
    private final EventoTurnoService eventoTurnoService;
    private final SseNotificationService sseNotificationService;
    private final AforoService aforoService;

    @Value("${minesentinel.aforo.umbral-critico:90}")
    private int umbralCriticoPorcentaje;

    public MovimientoAforoService(MovimientoAforoRepository movimientoAforoRepository,
                                  EventoTurnoRepository eventoTurnoRepository,
                                  RolPersonalRepository rolPersonalRepository,
                                  EventoTurnoService eventoTurnoService,
                                  SseNotificationService sseNotificationService,
                                  AforoService aforoService) {
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.rolPersonalRepository = rolPersonalRepository;
        this.eventoTurnoService = eventoTurnoService;
        this.sseNotificationService = sseNotificationService;
        this.aforoService = aforoService;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MovimientoAforo registrarMovimiento(MovimientoRequestDTO dto) {
        LocalDateTime fechaHora = dto.getFechaHora() != null ? dto.getFechaHora() : LocalDateTime.now();

        EventoTurno evento;
        if (dto.getEventoId() != null) {
            evento = eventoTurnoRepository.findById(dto.getEventoId())
                    .orElseThrow(() -> new IllegalArgumentException("No existe el evento de turno #" + dto.getEventoId()));

            if (evento.getEstado() != EstadoTurno.ACTIVO) {
                throw new IllegalStateException("No se pueden registrar movimientos en un turno inactivo (Estado: " + evento.getEstado() + ")");
            }
        } else {
            evento = eventoTurnoService.resolverEventoActivoParaMovimiento(fechaHora, dto.getTipoMovimiento());
        }

        RolPersonal rol = rolPersonalRepository.findById(dto.getRolId())
                .orElseThrow(() -> new IllegalArgumentException("No existe el rol con ID #" + dto.getRolId()));

        MovimientoAforo movimiento = new MovimientoAforo(evento, rol, dto.getTipoMovimiento(), fechaHora, dto.getTrackId());
        MovimientoAforo guardado = movimientoAforoRepository.save(movimiento);

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("aforo-update", obtenerAforoGlobalEnTiempoReal());
            }
        } catch (Exception ignored) {
        }

        return guardado;
    }

    @Transactional(readOnly = true)
    public AforoGlobalResponseDTO obtenerAforoGlobalEnTiempoReal() {
        Integer aforoCalculado = movimientoAforoRepository.calcularAforoNetoGlobalActivo();
        int aforoActual = Math.max(0, aforoCalculado != null ? aforoCalculado : 0);

        List<EventoTurno> turnosActivos = eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO);
        int cantidadTurnosActivos = turnosActivos.size();

        int aforoMaximo = aforoService.getAforoMaximo();
        int porcentaje = (int) Math.round((aforoActual * 100.0) / aforoMaximo);

        String estadoAforo;
        if (porcentaje >= 100) {
            estadoAforo = "AFORO MÁXIMO";
        } else if (porcentaje >= umbralCriticoPorcentaje) {
            estadoAforo = "AFORO CRÍTICO";
        } else {
            estadoAforo = "AFORO NORMAL";
        }

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

    @Transactional(readOnly = true)
    public int calcularAforoPorEvento(Long eventoId) {
        Integer aforo = movimientoAforoRepository.calcularAforoNetoPorEvento(eventoId);
        return Math.max(0, aforo != null ? aforo : 0);
    }

    @Transactional(readOnly = true)
    public List<MovimientoAforo> obtenerUltimosMovimientos(int limit) {
        return movimientoAforoRepository.findUltimosMovimientos(PageRequest.of(0, limit));
    }
}
