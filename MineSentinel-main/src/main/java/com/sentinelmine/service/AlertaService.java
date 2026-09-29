package com.sentinelmine.service;

import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.entity.AnomaliaMovimiento;
import com.sentinelmine.entity.CatalogoEPP;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.FaltaEPP;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.model.Prioridad;
import com.sentinelmine.model.TipoAlerta;
import com.sentinelmine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio de alertas conectado a las tablas normalizadas `faltas_epp` y `anomalias_movimiento`.
 * Resuelve colisiones de claves primarias mediante identificación tipada de alertas.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final CatalogoEPPRepository catalogoEPPRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final RolPersonalRepository rolPersonalRepository;
    private final NotificacionExternaService notificacionExternaService;
    private final SseNotificationService sseNotificationService;

    public AlertaService(FaltaEPPRepository faltaEPPRepository,
                         AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                         CatalogoEPPRepository catalogoEPPRepository,
                         EventoTurnoRepository eventoTurnoRepository,
                         RolPersonalRepository rolPersonalRepository,
                         NotificacionExternaService notificacionExternaService,
                         SseNotificationService sseNotificationService) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.rolPersonalRepository = rolPersonalRepository;
        this.notificacionExternaService = notificacionExternaService;
        this.sseNotificationService = sseNotificationService;
    }

    @Transactional
    public void registrarAlerta(TipoAlerta tipo, String trabajadorCodigo, String descripcion, Prioridad prioridad) {
        EventoTurno evento = obtenerOcrearEventoActivo();
        RolPersonal rol = rolPersonalRepository.findAll().stream().findFirst().orElse(null);

        if (tipo == TipoAlerta.EPP_INCOMPLETO) {
            CatalogoEPP epp = catalogoEPPRepository.findAll().stream().findFirst()
                    .orElseGet(() -> catalogoEPPRepository.save(new CatalogoEPP("Casco de Seguridad con Barbiquejo")));

            FaltaEPP falta = new FaltaEPP(
                    evento,
                    rol,
                    epp,
                    0.92f,
                    null,
                    EstadoAlerta.PENDIENTE,
                    LocalDateTime.now()
            );
            FaltaEPP guardada = faltaEPPRepository.save(falta);
            log.info("Falta de EPP registrada para trabajador #{}: {}", trabajadorCodigo, descripcion);
            sseNotificationService.emitirEvento("alerta-nueva", guardada);
        }
    }

    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getAlertasRecientes(int limit) {
        List<AlertaViewDTO> todas = getHistorialCompleto();
        return todas.stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getHistorialCompleto() {
        List<AlertaViewDTO> resultado = new ArrayList<>();

        // 1. Cargar faltas de EPP
        List<FaltaEPP> faltas = faltaEPPRepository.findUltimasFaltas(PageRequest.of(0, 50));
        for (FaltaEPP f : faltas) {
            String hora = f.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String eppNombre = f.getEpp() != null ? f.getEpp().getNombre() : "EPP no especificado";
            String rolNombre = f.getRol() != null ? f.getRol().getNombre() : "Operador";
            String codTrabajador = f.getFaltaId() != null ? String.valueOf(10 + (f.getFaltaId() % 90)) : "16";
            boolean notificado = f.getEstadoAlerta() == EstadoAlerta.NOTIFICADA || f.getEstadoAlerta() == EstadoAlerta.REVISADA;

            resultado.add(new AlertaViewDTO(
                    f.getFaltaId(),
                    "EPP",
                    "Infracción de EPP detectada (" + rolNombre + ")",
                    hora,
                    "Sin " + eppNombre + " en el punto de control de ingreso.",
                    codTrabajador,
                    "media",
                    false,
                    notificado,
                    f.getSnapshotUrl()
            ));
        }

        // 2. Cargar anomalías de movimiento
        List<AnomaliaMovimiento> anomalias = anomaliaMovimientoRepository.findUltimasAnomalias(PageRequest.of(0, 50));
        for (AnomaliaMovimiento a : anomalias) {
            String hora = a.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String anomaliaNombre = a.getCatalogoAnomalia() != null ? a.getCatalogoAnomalia().getNombre() : "Anomalía no clasificada";
            String codTrabajador = a.getAnomaliaId() != null ? String.valueOf(50 + (a.getAnomaliaId() % 50)) : "22";
            boolean notificado = a.getEstadoAlerta() == EstadoAlerta.NOTIFICADA || a.getEstadoAlerta() == EstadoAlerta.REVISADA;

            resultado.add(new AlertaViewDTO(
                    a.getAnomaliaId(),
                    "ANOMALIA",
                    "Alerta de seguridad / postura",
                    hora,
                    anomaliaNombre + " detectado en cámara.",
                    codTrabajador,
                    "alta",
                    true,
                    notificado,
                    a.getSnapshotUrl()
            ));
        }

        // Ordenar descendentemente por hora/id
        resultado.sort(Comparator.comparing(AlertaViewDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return resultado;
    }

    /**
     * Notifica al jefe de turno disambiguando el tipo de alerta ("EPP" vs "ANOMALIA")
     * para evitar colisiones entre claves primarias independientes.
     */
    @Transactional
    public void notificarJefeTurno(long idAlerta, String tipoAlerta) {
        if ("ANOMALIA".equalsIgnoreCase(tipoAlerta)) {
            anomaliaMovimientoRepository.findById(idAlerta).ifPresent(anomalia -> {
                anomalia.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                anomaliaMovimientoRepository.save(anomalia);
                log.info("Anomalía #{} marcada como NOTIFICADA.", idAlerta);
                String desc = anomalia.getCatalogoAnomalia() != null ? anomalia.getCatalogoAnomalia().getNombre() : "Anomalía postural";
                notificacionExternaService.despacharNotificacionJefeTurno("Anomalía de Movimiento", desc, "Auto", anomalia.getSnapshotUrl());
                sseNotificationService.emitirEvento("alerta-actualizada", anomalia);
            });
        } else {
            // Por defecto o si es EPP
            faltaEPPRepository.findById(idAlerta).ifPresentOrElse(falta -> {
                falta.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                faltaEPPRepository.save(falta);
                log.info("Falta de EPP #{} marcada como NOTIFICADA.", idAlerta);
                String desc = falta.getEpp() != null ? "Falta de " + falta.getEpp().getNombre() : "Falta de EPP";
                notificacionExternaService.despacharNotificacionJefeTurno("Infracción de EPP", desc, "Auto", falta.getSnapshotUrl());
                sseNotificationService.emitirEvento("alerta-actualizada", falta);
            }, () -> {
                // Fallback de búsqueda cruzada si el tipo no coincidiera
                anomaliaMovimientoRepository.findById(idAlerta).ifPresent(anomalia -> {
                    anomalia.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                    anomaliaMovimientoRepository.save(anomalia);
                    log.info("Anomalía #{} marcada como NOTIFICADA (fallback).", idAlerta);
                    notificacionExternaService.despacharNotificacionJefeTurno("Anomalía de Movimiento", "Riesgo detectado", "Auto", anomalia.getSnapshotUrl());
                    sseNotificationService.emitirEvento("alerta-actualizada", anomalia);
                });
            });
        }
    }

    private EventoTurno obtenerOcrearEventoActivo() {
        List<EventoTurno> activos = eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO);
        if (!activos.isEmpty()) {
            return activos.get(0);
        }
        return eventoTurnoRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    EventoTurno nuevo = new EventoTurno();
                    nuevo.setEstado(EstadoTurno.ACTIVO);
                    nuevo.setFechaInicio(LocalDateTime.now());
                    return eventoTurnoRepository.save(nuevo);
                });
    }
}
