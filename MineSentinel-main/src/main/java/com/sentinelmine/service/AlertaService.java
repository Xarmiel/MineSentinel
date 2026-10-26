package com.sentinelmine.service;

import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.dto.HistorialFiltro;
import com.sentinelmine.dto.HistorialPagina;
import com.sentinelmine.entity.AnomaliaMovimiento;
import com.sentinelmine.entity.FaltaEPP;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.repository.AnomaliaMovimientoRepository;
import com.sentinelmine.repository.FaltaEPPRepository;
import com.sentinelmine.repository.HistorialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de alertas conectado a las tablas normalizadas `faltas_epp` y `anomalias_movimiento`.
 * Proporciona el historial y canaliza notificaciones sin fabricar identidades ni catálogos simulados.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    private static final int LIMITE_EXPORTACION = 10_000;

    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final HistorialRepository historialRepository;
    private final NotificacionExternaService notificacionExternaService;
    private final SseNotificationService sseNotificationService;

    public AlertaService(FaltaEPPRepository faltaEPPRepository,
                         AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                         HistorialRepository historialRepository,
                         NotificacionExternaService notificacionExternaService,
                         SseNotificationService sseNotificationService) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.historialRepository = historialRepository;
        this.notificacionExternaService = notificacionExternaService;
        this.sseNotificationService = sseNotificationService;
    }

    /**
     * Alertas más recientes para el dashboard y el panel de administración.
     * Usa la misma consulta unificada del historial para que EPP y anomalías
     * aparezcan ordenados por fecha real y no por claves primarias separadas.
     */
    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getAlertasRecientes(int limit) {
        int tamano = Math.max(1, Math.min(limit, HistorialFiltro.PAGE_SIZE_MAX));
        HistorialFiltro filtro = new HistorialFiltro();
        filtro.setPage(0);
        filtro.setSize(tamano);
        return historialRepository.buscarLimitado(filtro, tamano);
    }

    /**
     * Página del historial con filtros aplicados en base de datos.
     */
    @Transactional(readOnly = true)
    public HistorialPagina getHistorial(HistorialFiltro filtro) {
        return historialRepository.buscar(filtro);
    }

    /**
     * Resultado completo (hasta un límite de seguridad) de la consulta filtrada,
     * para exportaciones. No reutiliza la página visible porque el usuario espera
     * descargar todo lo que coincide con los filtros, no solo la página actual.
     */
    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getHistorialParaExportar(HistorialFiltro filtro) {
        return historialRepository.buscarLimitado(filtro, LIMITE_EXPORTACION);
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
