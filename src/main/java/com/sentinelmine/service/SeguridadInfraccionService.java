package com.sentinelmine.service;

import com.sentinelmine.dto.request.AnomaliaRequestDTO;
import com.sentinelmine.dto.request.FaltaEPPRequestDTO;
import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.repository.*;
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

    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final CatalogoEPPRepository catalogoEPPRepository;
    private final CatalogoAnomaliasRepository catalogoAnomaliasRepository;
    private final RolPersonalRepository rolPersonalRepository;

    public SeguridadInfraccionService(FaltaEPPRepository faltaEPPRepository,
                                     AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                                     EventoTurnoRepository eventoTurnoRepository,
                                     CatalogoEPPRepository catalogoEPPRepository,
                                     CatalogoAnomaliasRepository catalogoAnomaliasRepository,
                                     RolPersonalRepository rolPersonalRepository) {
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.catalogoAnomaliasRepository = catalogoAnomaliasRepository;
        this.rolPersonalRepository = rolPersonalRepository;
    }

    /**
     * Registra una falta de EPP detectada en tiempo real.
     */
    @Transactional
    public FaltaEPP registrarFaltaEPP(FaltaEPPRequestDTO dto) {
        EventoTurno evento = eventoTurnoRepository.findById(dto.getEventoId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno #" + dto.getEventoId()));

        CatalogoEPP epp = catalogoEPPRepository.findById(dto.getEppId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el elemento EPP con ID #" + dto.getEppId()));

        RolPersonal rol = null;
        if (dto.getRolId() != null) {
            rol = rolPersonalRepository.findById(dto.getRolId()).orElse(null);
        }

        LocalDateTime fechaHora = dto.getFechaHora() != null ? dto.getFechaHora() : LocalDateTime.now();

        FaltaEPP falta = new FaltaEPP(
                evento,
                rol,
                epp,
                dto.getNivelConfianza(),
                dto.getSnapshotUrl(),
                EstadoAlerta.PENDIENTE,
                fechaHora
        );

        return faltaEPPRepository.save(falta);
    }

    /**
     * Registra una anomalía de desplazamiento o riesgo en boca-mina/interior detectada por visión.
     */
    @Transactional
    public AnomaliaMovimiento registrarAnomalia(AnomaliaRequestDTO dto) {
        EventoTurno evento = eventoTurnoRepository.findById(dto.getEventoId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el evento de turno #" + dto.getEventoId()));

        CatalogoAnomalias catalogo = catalogoAnomaliasRepository.findById(dto.getCatalogoAnomaliaId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la anomalía en catálogo #" + dto.getCatalogoAnomaliaId()));

        RolPersonal rol = null;
        if (dto.getRolId() != null) {
            rol = rolPersonalRepository.findById(dto.getRolId()).orElse(null);
        }

        LocalDateTime fechaHora = dto.getFechaHora() != null ? dto.getFechaHora() : LocalDateTime.now();

        AnomaliaMovimiento anomalia = new AnomaliaMovimiento(
                evento,
                rol,
                catalogo,
                dto.getNivelConfianza(),
                dto.getSnapshotUrl(),
                EstadoAlerta.PENDIENTE,
                fechaHora
        );

        return anomaliaMovimientoRepository.save(anomalia);
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

    @Transactional(readOnly = true)
    public List<AnomaliaMovimiento> obtenerUltimasAnomalias(int limit) {
        return anomaliaMovimientoRepository.findUltimasAnomalias(PageRequest.of(0, limit));
    }
}
