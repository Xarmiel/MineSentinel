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

/**
 * Servicio encargado de la gestión del ciclo de vida y auditoría de los eventos de turno mineros.
 * 
 * ARQUITECTURA: Diseñado para soportar múltiples turnos activos en paralelo sin bloqueos globales,
 * permitiendo el traslape de guardias (shift overlap) durante los relevos operativos y garantizando
 * consistencia y atomicidad transaccional durante el cierre y conciliación de aforo.
 */
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

    /**
     * Inicia una nueva instancia de turno operativa (EventoTurno).
     * Permite abrir un nuevo turno conviviendo fluidamente y sin bloqueos de concurrencia
     * con cualquier turno precedente que permanezca en estado ACTIVO (solapamiento de guardias).
     *
     * @param turnoId      Identificador del turno base
     * @param fechaInicio  Fecha/hora de inicio programada (o actual si es null)
     * @return El EventoTurno activo creado y persistido
     */
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

    /**
     * Cierra un evento de turno de manera transaccional y determinista:
     * 1. Valida que el evento se encuentre en estado ACTIVO y no posea auditoría previa.
     * 2. Consulta y consolida automáticamente todos los registros de MovimientosAforo de ese evento_id,
     *    filtrando EXCLUSIVAMENTE los roles que tienen requiere_aforo = true.
     * 3. Calcula total_entradas, total_salidas y la diferencia (balance de personal).
     * 4. Inserta el resultado consolidado inmutable en cierres_auditoria_turno.
     * 5. Actualiza el estado a CERRADO con su fecha_fin respectiva en la misma transacción atómica.
     *
     * @param eventoId Identificador del evento de turno a cerrar
     * @return DTO con los detalles consolidados del cierre y balance de auditoría
     */
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

        // 1. Conteo consolidado de entradas y salidas filtrando EXCLUSIVAMENTE roles con requiere_aforo = true
        int totalEntradas = (int) movimientoAforoRepository
                .countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.ENTRADA);
        int totalSalidas = (int) movimientoAforoRepository
                .countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.SALIDA);
        int diferencia = totalEntradas - totalSalidas;

        // 2. Creación y persistencia de la auditoría de cierre en PostgreSQL/Supabase
        CierreAuditoriaTurno auditoria = new CierreAuditoriaTurno(
                evento,
                totalEntradas,
                totalSalidas,
                diferencia,
                fechaCierre
        );
        CierreAuditoriaTurno auditoriaGuardada = cierreAuditoriaTurnoRepository.save(auditoria);

        // 3. Cierre y actualización de estado del evento en la misma unidad transaccional
        evento.setEstado(EstadoTurno.CERRADO);
        evento.setFechaFin(fechaCierre);
        eventoTurnoRepository.save(evento);

        log.info("Evento de turno #{} cerrado exitosamente. Auditoría #{} generada (Entradas: {}, Salidas: {}, Diferencia: {})",
                eventoId, auditoriaGuardada.getAuditoriaId(), totalEntradas, totalSalidas, diferencia);

        // 4. Diagnóstico de auditoría de personal en socavón
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

    /**
     * Resuelve de forma inteligente y determinista el EventoTurno activo correspondiente
     * para un cruce de aforo durante periodos de solapamiento de guardias.
     *
     * Reglas de resolución operativa:
     * - Si hay un único turno activo, se asocia directamente a este.
     * - Si hay solapamiento (2 o más turnos activos):
     *     * ENTRADA: Se asigna a la guardia ENTRANTE (el turno activo más reciente).
     *     * SALIDA: Se asigna a la guardia SALIENTE (el turno activo más antiguo que concluye guardia).
     * - Si no hay turnos activos, crea/recupera un turno de contingencia para no perder telemetría de visión.
     *
     * @param fechaHora        Momento del cruce registrado por ByteTrack
     * @param tipoMovimiento   ENTRADA o SALIDA
     * @return El EventoTurno activo correspondiente
     */
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

        // Caso de Solapamiento de Turnos (Shift Overlap: relevo de personal en boca-mina)
        // turnosActivos está ordenado por fechaInicio DESC (el índice 0 es el más nuevo, el último es el más antiguo)
        if (TipoMovimiento.ENTRADA.equals(tipoMovimiento)) {
            // El personal que ingresa pertenece a la nueva guardia entrante
            EventoTurno turnoEntrante = turnosActivos.get(0);
            log.debug("Solapamiento detectado: Cruce ENTRADA asignado al turno entrante #{} ({})",
                    turnoEntrante.getEventoId(), turnoEntrante.getTurno().getNombre());
            return turnoEntrante;
        } else {
            // El personal que sale pertenece a la guardia saliente
            EventoTurno turnoSaliente = turnosActivos.get(turnosActivos.size() - 1);
            log.debug("Solapamiento detectado: Cruce SALIDA asignado al turno saliente #{} ({})",
                    turnoSaliente.getEventoId(), turnoSaliente.getTurno().getNombre());
            return turnoSaliente;
        }
    }

    /**
     * Obtiene la lista de todos los turnos que están actualmente operando en paralelo.
     */
    @Transactional(readOnly = true)
    public List<EventoTurno> listarTurnosActivos() {
        return eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO);
    }

    /**
     * Indica si actualmente existe solapamiento operativo entre dos o más guardias.
     */
    @Transactional(readOnly = true)
    public boolean haySolapamientoTurnos() {
        return listarTurnosActivos().size() > 1;
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
            // Turno que cruza la medianoche (e.g. 23:00 a 07:30)
            return !hora.isBefore(inicio) || !hora.isAfter(fin);
        }
    }
}
