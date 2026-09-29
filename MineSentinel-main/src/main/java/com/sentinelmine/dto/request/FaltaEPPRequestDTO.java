package com.sentinelmine.dto.request;

import com.sentinelmine.entity.enums.TipoInfraccionEPP;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Payload de una infracción de EPP emitida por el módulo de visión (YOLOv8).
 *
 * A diferencia de FaltaEPPRequestDTO, distingue dos situaciones:
 *   FALTANTE  el trabajador no porta un EPP que su rol exige.
 *   IMPOSTOR  porta un objeto no homologado que lo imita (gorra, gafas de sol,
 *             zapatillas). El eppId apunta entonces al elemento IMPOSTOR del
 *             catálogo, no al EPP real que falta.
 */
public class FaltaEPPRequestDTO {

    // Opcional: Si es nulo, el backend asocia automáticamente el evento de turno activo
    private Long eventoId;

    private Long rolId;

    @NotNull(message = "El eppId es obligatorio")
    private Long eppId;

    @NotNull(message = "El nivel de confianza es obligatorio")
    private Float nivelConfianza;

    private String snapshotUrl;
    private LocalDateTime fechaHora;

    // Opcional: track de ByteTrack que originó la detección
    private Integer trackId;

    // Si es nulo se asume FALTANTE, manteniendo la compatibilidad con el cliente legacy
    private TipoInfraccionEPP tipoInfraccion;

    // Texto explicativo generado por el motor de visión (evidencia en el snapshot)
    private String descripcion;

    public FaltaEPPRequestDTO() {
    }

    public FaltaEPPRequestDTO(Long eventoId, Long rolId, Long eppId, Float nivelConfianza,
                              String snapshotUrl, LocalDateTime fechaHora) {
        this(eventoId, rolId, eppId, nivelConfianza, snapshotUrl, fechaHora, null, null, null);
    }

    public FaltaEPPRequestDTO(Long eventoId, Long rolId, Long eppId, Float nivelConfianza,
                              String snapshotUrl, LocalDateTime fechaHora, Integer trackId,
                              TipoInfraccionEPP tipoInfraccion, String descripcion) {
        this.eventoId = eventoId;
        this.rolId = rolId;
        this.eppId = eppId;
        this.nivelConfianza = nivelConfianza;
        this.snapshotUrl = snapshotUrl;
        this.fechaHora = fechaHora;
        this.trackId = trackId;
        this.tipoInfraccion = tipoInfraccion;
        this.descripcion = descripcion;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Long getRolId() {
        return rolId;
    }

    public void setRolId(Long rolId) {
        this.rolId = rolId;
    }

    public Long getEppId() {
        return eppId;
    }

    public void setEppId(Long eppId) {
        this.eppId = eppId;
    }

    public Float getNivelConfianza() {
        return nivelConfianza;
    }

    public void setNivelConfianza(Float nivelConfianza) {
        this.nivelConfianza = nivelConfianza;
    }

    public String getSnapshotUrl() {
        return snapshotUrl;
    }

    public void setSnapshotUrl(String snapshotUrl) {
        this.snapshotUrl = snapshotUrl;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora != null ? fechaHora : LocalDateTime.now();
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public Integer getTrackId() {
        return trackId;
    }

    public void setTrackId(Integer trackId) {
        this.trackId = trackId;
    }

    public TipoInfraccionEPP getTipoInfraccion() {
        return tipoInfraccion;
    }

    public void setTipoInfraccion(TipoInfraccionEPP tipoInfraccion) {
        this.tipoInfraccion = tipoInfraccion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
