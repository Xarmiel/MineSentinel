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
 * Provee la información para el dashboard, panel de administración e historial.
 */
@Service
public class AlertaService {

    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final CatalogoEPPRepository catalogoEPPRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final RolPersonalRepository rolPersonalRepository;

    public AlertaService(FaltaEPPRepository faltaEPPRepository,
                         AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                         CatalogoEPPRepository catalogoEPPRepository,
                         EventoTurnoRepository eventoTurnoRepository,
                         RolPersonalRepository rolPersonalRepository) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.rolPersonalRepository = rolPersonalRepository;
    }

    @Transactional
    public void registrarAlerta(TipoAlerta tipo, String trabajadorCodigo, String descripcion, Prioridad prioridad) {
        EventoTurno evento = obtenerOcrearEventoActivo();
        RolPersonal rol = rolPersonalRepository.findAll().stream().findFirst().orElse(null);

        if (tipo == TipoAlerta.EPP_INCOMPLETO) {
            CatalogoEPP epp = catalogoEPPRepository.findAll().stream().findFirst()
                    .orElseGet(() -> catalogoEPPRepository.save(new CatalogoEPP("Casco de Seguridad")));

            FaltaEPP falta = new FaltaEPP(
                    evento,
                    rol,
                    epp,
                    0.92f,
                    null,
                    EstadoAlerta.PENDIENTE,
                    LocalDateTime.now()
            );
            faltaEPPRepository.save(falta);
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
            String codTrabajador = f.getFaltaId() != null ? String.valueOf(10 + (f.getFaltaId() % 90)) : "16";
            boolean notificado = f.getEstadoAlerta() == EstadoAlerta.NOTIFICADA || f.getEstadoAlerta() == EstadoAlerta.REVISADA;

            resultado.add(new AlertaViewDTO(
                    f.getFaltaId(),
                    "EPP",
                    "Infracción de EPP detectada",
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

    @Transactional
    public void notificarJefeTurno(long idAlerta) {
        // Intenta actualizar en faltas_epp
        faltaEPPRepository.findById(idAlerta).ifPresentOrElse(falta -> {
            falta.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
            faltaEPPRepository.save(falta);
        }, () -> {
            // Si no está en faltas_epp, buscar en anomalias_movimiento
            anomaliaMovimientoRepository.findById(idAlerta).ifPresent(anomalia -> {
                anomalia.setEstadoAlerta(EstadoAlerta.NOTIFICADA);
                anomaliaMovimientoRepository.save(anomalia);
            });
        });
    }

    private EventoTurno obtenerOcrearEventoActivo() {
        List<EventoTurno> activos = eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO);
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
