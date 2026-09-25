package com.sentinelmine.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

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

    public FaltaEPPRequestDTO() {
    }

    public FaltaEPPRequestDTO(Long eventoId, Long rolId, Long eppId, Float nivelConfianza, String snapshotUrl, LocalDateTime fechaHora) {
        this.eventoId = eventoId;
        this.rolId = rolId;
        this.eppId = eppId;
        this.nivelConfianza = nivelConfianza;
        this.snapshotUrl = snapshotUrl;
        this.fechaHora = fechaHora;
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
}
