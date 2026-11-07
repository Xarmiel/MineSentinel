package com.sentinelmine.service;

import com.sentinelmine.dto.request.AnomaliaRequestDTO;
import com.sentinelmine.dto.request.FaltaEPPRequestDTO;
import com.sentinelmine.dto.request.InfraccionResolucionRequestDTO;
import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.TipoCatalogoEPP;
import com.sentinelmine.entity.enums.TipoInfraccionEPP;
import com.sentinelmine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeguridadInfraccionService {

    private static final Logger log = LoggerFactory.getLogger(SeguridadInfraccionService.class);

    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final CatalogoEPPRepository catalogoEPPRepository;
    private final CatalogoAnomaliasRepository catalogoAnomaliasRepository;
    private final RolPersonalRepository rolPersonalRepository;
    private final EventoTurnoService eventoTurnoService;
    private final SseNotificationService sseNotificationService;
    private final NotificacionExternaService notificacionExternaService;
    private final MovimientoAforoRepository movimientoAforoRepository;

    public SeguridadInfraccionService(FaltaEPPRepository faltaEPPRepository,
                                     AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                                     EventoTurnoRepository eventoTurnoRepository,
                                     CatalogoEPPRepository catalogoEPPRepository,
                                     CatalogoAnomaliasRepository catalogoAnomaliasRepository,
                                     RolPersonalRepository rolPersonalRepository,
                                     EventoTurnoService eventoTurnoService,
                                     SseNotificationService sseNotificationService,
                                     NotificacionExternaService notificacionExternaService,
                                     MovimientoAforoRepository movimientoAforoRepository) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.catalogoAnomaliasRepository = catalogoAnomaliasRepository;
        this.rolPersonalRepository = rolPersonalRepository;
        this.eventoTurnoService = eventoTurnoService;
        this.sseNotificationService = sseNotificationService;
        this.notificacionExternaService = notificacionExternaService;
        this.movimientoAforoRepository = movimientoAforoRepository;
    }

    @Transactional
    public FaltaEPP registrarFaltaEPP(FaltaEPPRequestDTO dto) {
        LocalDateTime fechaHora = dto.getFechaHora() != null ? dto.getFechaHora() : LocalDateTime.now();

        EventoTurno evento;
        if (dto.getEventoId() != null) {
            evento = eventoTurnoRepository.findById(dto.getEventoId())
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno #" + dto.getEventoId()));
        } else {
            evento = eventoTurnoService.resolverEventoActivoParaMovimiento(fechaHora, com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA);
        }

        CatalogoEPP epp = catalogoEPPRepository.findById(dto.getEppId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el elemento EPP con ID #" + dto.getEppId()));

        RolPersonal rol = null;
        if (dto.getRolId() != null) {
            rol = rolPersonalRepository.findById(dto.getRolId()).orElse(null);
        }

        TipoInfraccionEPP tipo = dto.getTipoInfraccion() != null
                ? dto.getTipoInfraccion()
                : TipoInfraccionEPP.FALTANTE;

        TipoCatalogoEPP tipoCatalogo = epp.getTipo() != null ? epp.getTipo() : TipoCatalogoEPP.CONFORME;
        if (tipo == TipoInfraccionEPP.IMPOSTOR && tipoCatalogo != TipoCatalogoEPP.IMPOSTOR) {
            log.warn("El cliente reportó IMPOSTOR sobre el EPP '{}' (CONFORME); se registra como FALTANTE", epp.getNombre());
            tipo = TipoInfraccionEPP.FALTANTE;
        }

        FaltaEPP falta = new FaltaEPP(
                evento,
                rol,
                epp,
                dto.getNivelConfianza(),
                dto.getSnapshotUrl(),
                EstadoAlerta.PENDIENTE,
                fechaHora,
                dto.getTrackId(),
                tipo,
                dto.getDescripcion()
        );

        FaltaEPP guardada = faltaEPPRepository.save(falta);

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("alerta-nueva", guardada);
            }
        } catch (Exception ignored) {
        }

        return guardada;
    }

    @Transactional
    public AnomaliaMovimiento registrarAnomalia(AnomaliaRequestDTO dto) {
        LocalDateTime fechaHora = dto.getFechaHora() != null ? dto.getFechaHora() : LocalDateTime.now();

        EventoTurno evento;
        if (dto.getEventoId() != null) {
            evento = eventoTurnoRepository.findById(dto.getEventoId())
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno #" + dto.getEventoId()));
        } else {
            evento = eventoTurnoService.resolverEventoActivoParaMovimiento(fechaHora, com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA);
        }

        CatalogoAnomalias catalogo = catalogoAnomaliasRepository.findById(dto.getCatalogoAnomaliaId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la anomalía en catálogo #" + dto.getCatalogoAnomaliaId()));

        RolPersonal rol = null;
        if (dto.getRolId() != null) {
            rol = rolPersonalRepository.findById(dto.getRolId()).orElse(null);
        }

        AnomaliaMovimiento anomalia = new AnomaliaMovimiento(
                evento,
                rol,
                catalogo,
                dto.getNivelConfianza(),
                dto.getSnapshotUrl(),
                EstadoAlerta.PENDIENTE,
                fechaHora
        );

        AnomaliaMovimiento guardada = anomaliaMovimientoRepository.save(anomalia);

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("alerta-nueva", guardada);
            }
        } catch (Exception ignored) {
        }

        return guardada;
    }

    @Transactional
    public FaltaEPP cambiarEstadoFaltaEPP(Long faltaId, EstadoAlerta nuevoEstado) {
        FaltaEPP falta = faltaEPPRepository.findById(faltaId)
                .orElseThrow(() -> new IllegalArgumentException("Falta de EPP no encontrada #" + faltaId));
        falta.setEstadoAlerta(nuevoEstado);
        return faltaEPPRepository.save(falta);
    }

    @Transactional
    public FaltaEPP resolverFaltaEPP(InfraccionResolucionRequestDTO dto) {
        FaltaEPP falta = faltaEPPRepository.findByIdWithDetails(dto.getFaltaId())
                .orElseThrow(() -> new IllegalArgumentException("Falta de EPP no encontrada #" + dto.getFaltaId()));

        if (falta.getEstadoAlerta() == EstadoAlerta.RESUELTA
                || falta.getEstadoAlerta() == EstadoAlerta.DESCARTADA) {
            return falta;
        }

        falta.setEstadoAlerta(EstadoAlerta.RESUELTA);
        if (dto.getSnapshotUrl() != null && !dto.getSnapshotUrl().isBlank()) {
            falta.setSnapshotUrl(dto.getSnapshotUrl());
        }

        FaltaEPP guardada = faltaEPPRepository.save(falta);
        log.info("Falta de EPP #{} marcada como RESUELTA (track {}). Motivo: {}",
                guardada.getFaltaId(), dto.getTrackId(),
                dto.getMotivo() != null ? dto.getMotivo() : "EPP repuesto");

        try {
            if (sseNotificationService != null) {
                sseNotificationService.emitirEvento("alerta-actualizada", guardada);
            }
        } catch (Exception ignored) {
        }

        return guardada;
    }

    @Transactional
    public AnomaliaMovimiento cambiarEstadoAnomalia(Long anomaliaId, EstadoAlerta nuevoEstado) {
        AnomaliaMovimiento anomalia = anomaliaMovimientoRepository.findById(anomaliaId)
                .orElseThrow(() -> new IllegalArgumentException("Anomalía no encontrada #" + anomaliaId));
        anomalia.setEstadoAlerta(nuevoEstado);
        return anomaliaMovimientoRepository.save(anomalia);
    }

    @Transactional(readOnly = true)
    public List<FaltaEPP> obtenerUltimasFaltasEPP(int limit) {
        return faltaEPPRepository.findUltimasFaltas(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<FaltaEPP> obtenerUltimasFaltasEPPPorTipo(TipoInfraccionEPP tipo, int limit) {
        return faltaEPPRepository.findByTipoInfraccion(PageRequest.of(0, limit), tipo);
    }

    @Transactional(readOnly = true)
    public List<AnomaliaMovimiento> obtenerUltimasAnomalias(int limit) {
        return anomaliaMovimientoRepository.findUltimasAnomalias(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerKpisTurnoActivo() {
        Set<Integer> conFalta = new HashSet<>(faltaEPPRepository.trackIdsConFaltaTurnoActivo());
        Set<Integer> detectadas = new HashSet<>(movimientoAforoRepository.trackIdsConEntradaTurnoActivo());
        detectadas.addAll(conFalta);
        int cumplimiento = detectadas.isEmpty() ? 100
                : (int) Math.round(100.0 * (detectadas.size() - conFalta.size()) / detectadas.size());

        List<Object[]> porEpp = faltaEPPRepository.contarInfraccionesPorEppTurnoActivo();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("cumplimientoPorcentaje", cumplimiento);
        kpis.put("personasDetectadas", detectadas.size());
        kpis.put("personasConFalta", conFalta.size());
        kpis.put("infracciones", faltaEPPRepository.contarInfraccionesTurnoActivo());
        kpis.put("eppMasIncumplido", porEpp.isEmpty() ? null : porEpp.get(0)[0]);
        kpis.put("eppMasIncumplidoConteo", porEpp.isEmpty() ? 0L : porEpp.get(0)[1]);
        return kpis;
    }
}
