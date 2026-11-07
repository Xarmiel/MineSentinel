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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class EventoTurnoService {

    private static final Logger log = LoggerFactory.getLogger(EventoTurnoService.class);

    private final EventoTurnoRepository eventoTurnoRepository;
    private final TurnoRepository turnoRepository;
    private final MovimientoAforoRepository movimientoAforoRepository;
    private final CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository;
    private SseNotificationService sseNotificationService;

    public EventoTurnoService(EventoTurnoRepository eventoTurnoRepository,
                              TurnoRepository turnoRepository,
                              MovimientoAforoRepository movimientoAforoRepository,
                              CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository) {
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.cierreAuditoriaTurnoRepository = cierreAuditoriaTurnoRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSseNotificationService(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public EventoTurno abrirTurno(Long turnoId, LocalDateTime fechaInicio) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el turno con ID: " + turnoId));

        LocalDateTime inicio = fechaInicio != null ? fechaInicio : LocalDateTime.now();
        EventoTurno nuevoEvento = new EventoTurno(turno, inicio);
        nuevoEvento.setEstado(EstadoTurno.ACTIVO);

        EventoTurno guardado = eventoTurnoRepository.save(nuevoEvento);
        log.info("Turno operativo abierto exitosamente: #{} - '{}' (Inicio: {})",
                guardado.getEventoId(), turno.getNombre(), inicio);

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("turno-update", guardado);
            }
        } catch (Exception ignored) {
        }

        return guardado;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public CierreTurnoResponseDTO cerrarTurno(Long eventoId) {
        EventoTurno evento = eventoTurnoRepository.findByIdWithTurno(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno con ID: " + eventoId));

        if (evento.getEstado() != EstadoTurno.ACTIVO) {
            throw new IllegalStateException(
                    String.format("El evento de turno #%d ya no está ACTIVO (Estado actual: %s)", eventoId, evento.getEstado())
            );
        }

        if (cierreAuditoriaTurnoRepository.existsByEventoTurno_EventoId(eventoId)) {
            throw new IllegalStateException(
                    String.format("El evento de turno #%d ya cuenta con un registro de auditoría y cierre previo.", eventoId)
            );
        }

        LocalDateTime fechaCierre = LocalDateTime.now();

        int totalEntradas = (int) movimientoAforoRepository
                .countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.ENTRADA);
        int totalSalidas = (int) movimientoAforoRepository
                .countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.SALIDA);
        int diferencia = totalEntradas - totalSalidas;

        CierreAuditoriaTurno auditoria = new CierreAuditoriaTurno(
                evento,
                totalEntradas,
                totalSalidas,
                diferencia,
                fechaCierre
        );
        CierreAuditoriaTurno auditoriaGuardada = cierreAuditoriaTurnoRepository.save(auditoria);

        evento.setEstado(EstadoTurno.CERRADO);
        evento.setFechaFin(fechaCierre);
        eventoTurnoRepository.save(evento);

        log.info("Evento de turno #{} cerrado exitosamente. Auditoría #{} generada (Entradas: {}, Salidas: {}, Diferencia: {})",
                eventoId, auditoriaGuardada.getAuditoriaId(), totalEntradas, totalSalidas, diferencia);

        String mensajeAuditoria;
        if (diferencia == 0) {
            mensajeAuditoria = "Cierre exitoso: Balance cuadrado de personal (0 remanentes en socavón).";
        } else if (diferencia > 0) {
            mensajeAuditoria = String.format("ALERTA DE AUDITORÍA: Quedan %d personas registradas en socavón sin registro de salida.", diferencia);
        } else {
            mensajeAuditoria = String.format("ADVERTENCIA DE VISIÓN: Se registraron %d salidas adicionales a las entradas del turno.", Math.abs(diferencia));
        }

        CierreTurnoResponseDTO responseDTO = new CierreTurnoResponseDTO(
                auditoriaGuardada.getAuditoriaId(),
                evento.getEventoId(),
                evento.getTurno().getNombre(),
                totalEntradas,
                totalSalidas,
                diferencia,
                fechaCierre,
                mensajeAuditoria
        );

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("turno-update", responseDTO);
            }
        } catch (Exception ignored) {
        }

        return responseDTO;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventoTurno resolverEventoActivoParaMovimiento(LocalDateTime fechaHora, TipoMovimiento tipoMovimiento) {
        List<EventoTurno> turnosActivos = eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO);

        if (turnosActivos.isEmpty()) {
            log.warn("No se encontraron turnos activos en base de datos al procesar {}. Activando turno base por horario...", tipoMovimiento);
            return obtenerOcrearTurnoPorHorario(fechaHora != null ? fechaHora : LocalDateTime.now());
        }

        if (turnosActivos.size() == 1) {
            return turnosActivos.get(0);
        }

        if (TipoMovimiento.ENTRADA.equals(tipoMovimiento)) {
            EventoTurno turnoEntrante = turnosActivos.get(0);
            log.debug("Solapamiento detectado: Cruce ENTRADA asignado al turno entrante #{} ({})",
                    turnoEntrante.getEventoId(), turnoEntrante.getTurno().getNombre());
            return turnoEntrante;
        } else {
            EventoTurno turnoSaliente = turnosActivos.get(turnosActivos.size() - 1);
            log.debug("Solapamiento detectado: Cruce SALIDA asignado al turno saliente #{} ({})",
                    turnoSaliente.getEventoId(), turnoSaliente.getTurno().getNombre());
            return turnoSaliente;
        }
    }

    @Transactional(readOnly = true)
    public List<EventoTurno> listarTurnosActivos() {
        return eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO);
    }

    @Transactional(readOnly = true)
    public boolean haySolapamientoTurnos() {
        return listarTurnosActivos().size() > 1;
    }

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
    public String obtenerTurnoTopbar() {
        List<EventoTurno> activos = listarTurnosActivos();
        if (!activos.isEmpty()) {
            return activos.get(0).getTurno().getNombre();
        }
        return turnoRepository.findAll().stream()
                .min((a, b) -> Long.compare(a.getTurnoId(), b.getTurnoId()))
                .map(Turno::getNombre)
                .orElse("Sin guardia activa");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public EventoTurno pasarSiguienteTurno() {
        List<Turno> turnos = turnoRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getTurnoId(), b.getTurnoId()))
                .toList();

        if (turnos.isEmpty()) {
            throw new IllegalStateException("No hay turnos registrados en la base de datos.");
        }

        List<EventoTurno> activos = listarTurnosActivos();
        Long turnoActualId = null;
        if (!activos.isEmpty()) {
            turnoActualId = activos.get(0).getTurno().getTurnoId();
            for (EventoTurno ev : activos) {
                ev.setEstado(EstadoTurno.CERRADO);
                ev.setFechaFin(LocalDateTime.now());
                eventoTurnoRepository.save(ev);
            }
        }

        Turno siguienteTurno = turnos.get(0);
        if (turnoActualId != null) {
            for (int i = 0; i < turnos.size(); i++) {
                if (turnos.get(i).getTurnoId().equals(turnoActualId)) {
                    siguienteTurno = turnos.get((i + 1) % turnos.size());
                    break;
                }
            }
        }

        EventoTurno nuevoEvento = new EventoTurno(siguienteTurno, LocalDateTime.now());
        nuevoEvento.setEstado(EstadoTurno.ACTIVO);
        EventoTurno guardado = eventoTurnoRepository.save(nuevoEvento);

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("turno-update", guardado);
            }
        } catch (Exception ignored) {
        }

        return guardado;
    }

    @Transactional(readOnly = true)
    public EventoTurno obtenerPorId(Long eventoId) {
        return eventoTurnoRepository.findByIdWithTurno(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno #" + eventoId));
    }

    @Transactional(readOnly = true)
    public Optional<EventoTurno> obtenerEventoActivoPorTurnoId(Long turnoId) {
        return eventoTurnoRepository.findFirstByTurno_TurnoIdAndEstado(turnoId, EstadoTurno.ACTIVO);
    }

    private EventoTurno obtenerOcrearTurnoPorHorario(LocalDateTime fechaHora) {
        LocalTime hora = fechaHora.toLocalTime();
        List<Turno> turnos = turnoRepository.findAll();

        Turno turnoCoincidente = turnos.stream()
                .filter(t -> esHoraEnRango(hora, t.getHoraInicio(), t.getHoraFin()))
                .findFirst()
                .orElseGet(() -> turnos.stream().findFirst().orElseGet(() ->
                        turnoRepository.save(new Turno("Guardia General", LocalTime.of(0, 0), LocalTime.of(23, 59)))
                ));

        EventoTurno nuevo = new EventoTurno(turnoCoincidente, fechaHora);
        nuevo.setEstado(EstadoTurno.ACTIVO);
        return eventoTurnoRepository.save(nuevo);
    }

    private boolean esHoraEnRango(LocalTime hora, LocalTime inicio, LocalTime fin) {
        if (inicio.isBefore(fin)) {
            return !hora.isBefore(inicio) && !hora.isAfter(fin);
        } else {
            return !hora.isBefore(inicio) || !hora.isAfter(fin);
        }
    }
}
