package com.sentinelmine.service;

import com.sentinelmine.dto.request.AnomaliaRequestDTO;
import com.sentinelmine.dto.request.FaltaEPPRequestDTO;
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
import java.util.List;

/**
 * Servicio encargado de la ingestión y gestión de alertas de seguridad industrial
 * (Infracciones de EPP y Anomalías de Movimiento) detectadas por YOLOv8.
 */
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

    public SeguridadInfraccionService(FaltaEPPRepository faltaEPPRepository,
                                     AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                                     EventoTurnoRepository eventoTurnoRepository,
                                     CatalogoEPPRepository catalogoEPPRepository,
                                     CatalogoAnomaliasRepository catalogoAnomaliasRepository,
                                     RolPersonalRepository rolPersonalRepository,
                                     EventoTurnoService eventoTurnoService,
                                     SseNotificationService sseNotificationService,
                                     NotificacionExternaService notificacionExternaService) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.catalogoAnomaliasRepository = catalogoAnomaliasRepository;
        this.rolPersonalRepository = rolPersonalRepository;
        this.eventoTurnoService = eventoTurnoService;
        this.sseNotificationService = sseNotificationService;
        this.notificacionExternaService = notificacionExternaService;
    }

    /**
     * Registra una infracción de EPP detectada en tiempo real por YOLOv8.
     *
     * Admite los dos tipos de hallazgo del motor de visión:
     *   FALTANTE  el trabajador no porta un EPP obligatorio de su rol.
     *   IMPOSTOR  porta un objeto no homologado que lo imita (gorra, gafas de sol).
     *              En ese caso el eppId apunta al elemento IMPOSTOR del catálogo.
     */
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

        // Un hallazgo IMPOSTOR debe apuntar a un elemento marcado como IMPOSTOR en el
        // catálogo (y viceversa). Si el cliente clasifica mal, se corrige en servidor
        // para que el histórico no mezcle ambas situaciones.
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

    /**
     * Registra una anomalía de desplazamiento o riesgo en boca-mina/interior detectada por visión.
     */
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

    /**
     * Actualiza el estado de una falta de EPP (e.g. marcar como NOTIFICADA o REVISADA).
     */
    @Transactional
    public FaltaEPP cambiarEstadoFaltaEPP(Long faltaId, EstadoAlerta nuevoEstado) {
        FaltaEPP falta = faltaEPPRepository.findById(faltaId)
                .orElseThrow(() -> new IllegalArgumentException("Falta de EPP no encontrada #" + faltaId));
        falta.setEstadoAlerta(nuevoEstado);
        return faltaEPPRepository.save(falta);
    }

    /**
     * Actualiza el estado de una anomalía detectada.
     */
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

    /**
     * Obtiene las infracciones más recientes filtrando por tipo, útil para auditar
     * por separado los elementos no aptos (IMPOSTOR) de las faltas de EPP ordinarias.
     */
    @Transactional(readOnly = true)
    public List<FaltaEPP> obtenerUltimasFaltasEPPPorTipo(TipoInfraccionEPP tipo, int limit) {
        return faltaEPPRepository.findByTipoInfraccion(PageRequest.of(0, limit), tipo);
    }

    @Transactional(readOnly = true)
    public List<AnomaliaMovimiento> obtenerUltimasAnomalias(int limit) {
        return anomaliaMovimientoRepository.findUltimasAnomalias(PageRequest.of(0, limit));
    }
}
