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

    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getAlertasRecientes(int limit) {
        int tamano = Math.max(1, Math.min(limit, HistorialFiltro.PAGE_SIZE_MAX));
        HistorialFiltro filtro = new HistorialFiltro();
        filtro.setPage(0);
        filtro.setSize(tamano);
        return historialRepository.buscarLimitado(filtro, tamano);
    }

    @Transactional(readOnly = true)
    public HistorialPagina getHistorial(HistorialFiltro filtro) {
        return historialRepository.buscar(filtro);
    }

    @Transactional(readOnly = true)
    public List<AlertaViewDTO> getHistorialParaExportar(HistorialFiltro filtro) {
        return historialRepository.buscarLimitado(filtro, LIMITE_EXPORTACION);
    }

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
            faltaEPPRepository.findById(idAlerta).ifPresentOrElse(falta -> {
                falta.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                faltaEPPRepository.save(falta);
                log.info("Falta de EPP #{} marcada como NOTIFICADA.", idAlerta);
                String desc = falta.getEpp() != null ? "Falta de " + falta.getEpp().getNombre() : "Falta de EPP";
                String rol = falta.getRol() != null ? falta.getRol().getNombre() : "Personal";
                notificacionExternaService.despacharNotificacionJefeTurno("Infracción de EPP", desc, rol, falta.getSnapshotUrl());
                sseNotificationService.emitirEvento("alerta-actualizada", falta);
            }, () -> {
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
