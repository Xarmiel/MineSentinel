package com.sentinelmine.service;

import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.entity.AnomaliaMovimiento;
import com.sentinelmine.entity.FaltaEPP;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio de alertas conectado a las tablas normalizadas `faltas_epp` y `anomalias_movimiento`.
 * Proporciona el historial y canaliza notificaciones sin fabricar identidades ni catálogos simulados.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final NotificacionExternaService notificacionExternaService;
    private final SseNotificationService sseNotificationService;

    public AlertaService(FaltaEPPRepository faltaEPPRepository,
                         AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                         NotificacionExternaService notificacionExternaService,
                         SseNotificationService sseNotificationService) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.notificacionExternaService = notificacionExternaService;
        this.sseNotificationService = sseNotificationService;
    }

    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getAlertasRecientes(int limit) {
        List<FaltaEPP> faltas = faltaEPPRepository.findUltimasFaltas(PageRequest.of(0, limit));
        List<AlertaViewDTO> resultado = new ArrayList<>();
        for (FaltaEPP f : faltas) {
            String hora = f.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String eppNombre = f.getEpp() != null ? f.getEpp().getNombre() : "EPP no especificado";
            String rolNombre = f.getRol() != null ? f.getRol().getNombre() : "Personal";
            String codTrabajador = f.getRol() != null ? rolNombre : "N/I";
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
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getHistorialCompleto() {
        List<AlertaViewDTO> resultado = new ArrayList<>();

        // 1. Cargar faltas de EPP
        List<FaltaEPP> faltas = faltaEPPRepository.findUltimasFaltas(PageRequest.of(0, 50));
        for (FaltaEPP f : faltas) {
            String hora = f.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String eppNombre = f.getEpp() != null ? f.getEpp().getNombre() : "EPP no especificado";
            String rolNombre = f.getRol() != null ? f.getRol().getNombre() : "Personal";
            String codTrabajador = f.getRol() != null ? rolNombre : "N/I";
            EstadoAlerta estado = f.getEstadoAlerta();
            boolean notificado = estado == EstadoAlerta.NOTIFICADA
                    || estado == EstadoAlerta.REVISADA
                    || estado == EstadoAlerta.RESUELTA;
            String descripcionAlerta = "Sin " + eppNombre + " en el punto de control de ingreso.";
            if (estado == EstadoAlerta.RESUELTA) {
                descripcionAlerta = eppNombre + " repuesto. Infracción cerrada automáticamente por visión.";
            }

            resultado.add(new AlertaViewDTO(
                    f.getFaltaId(),
                    "EPP",
                    "Infracción de EPP detectada (" + rolNombre + ")",
                    hora,
                    descripcionAlerta,
                    codTrabajador,
                    "media",
                    false,
                    notificado,
                    f.getSnapshotUrl(),
                    estado != null ? estado.name() : null
            ));
        }

        // 2. Cargar anomalías de movimiento
        List<AnomaliaMovimiento> anomalias = anomaliaMovimientoRepository.findUltimasAnomalias(PageRequest.of(0, 50));
        for (AnomaliaMovimiento a : anomalias) {
            String hora = a.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String anomaliaNombre = a.getCatalogoAnomalia() != null ? a.getCatalogoAnomalia().getNombre() : "Anomalía no clasificada";
            String codTrabajador = "Punto Control";
            EstadoAlerta estado = a.getEstadoAlerta();
            boolean notificado = estado == EstadoAlerta.NOTIFICADA || estado == EstadoAlerta.REVISADA;

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
                    a.getSnapshotUrl(),
                    estado != null ? estado.name() : null
            ));
        }

        // Ordenar descendentemente por id
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
                notificacionExternaService.despacharNotificacionJefeTurno("Anomalía de Movimiento", desc, "N/I", anomalia.getSnapshotUrl());
                sseNotificationService.emitirEvento("alerta-actualizada", anomalia);
            });
        } else {
            // Por defecto o si es EPP
            faltaEPPRepository.findById(idAlerta).ifPresentOrElse(falta -> {
                falta.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                faltaEPPRepository.save(falta);
                log.info("Falta de EPP #{} marcada como NOTIFICADA.", idAlerta);
                String desc = falta.getEpp() != null ? "Falta de " + falta.getEpp().getNombre() : "Falta de EPP";
                String rol = falta.getRol() != null ? falta.getRol().getNombre() : "Personal";
                notificacionExternaService.despacharNotificacionJefeTurno("Infracción de EPP", desc, rol, falta.getSnapshotUrl());
                sseNotificationService.emitirEvento("alerta-actualizada", falta);
            }, () -> {
                // Fallback de búsqueda cruzada si el tipo no coincidiera
                anomaliaMovimientoRepository.findById(idAlerta).ifPresent(anomalia -> {
                    anomalia.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                    anomaliaMovimientoRepository.save(anomalia);
                    log.info("Anomalía #{} marcada como NOTIFICADA (fallback).", idAlerta);
                    notificacionExternaService.despacharNotificacionJefeTurno("Anomalía de Movimiento", "Riesgo detectado", "N/I", anomalia.getSnapshotUrl());
                    sseNotificationService.emitirEvento("alerta-actualizada", anomalia);
                });
            });
        }
    }
}
