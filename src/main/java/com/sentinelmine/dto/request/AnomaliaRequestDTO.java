package com.sentinelmine.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class AnomaliaRequestDTO {

    // Opcional: Si es nulo, el backend asocia automáticamente el evento de turno activo
    private Long eventoId;

    private Long rolId;

    @NotNull(message = "El catalogoAnomaliaId es obligatorio")
    private Long catalogoAnomaliaId;

    @NotNull(message = "El nivel de confianza es obligatorio")
    private Float nivelConfianza;

    private String snapshotUrl;
    private LocalDateTime fechaHora;

    public AnomaliaRequestDTO() {
    }

    public AnomaliaRequestDTO(Long eventoId, Long rolId, Long catalogoAnomaliaId, Float nivelConfianza, String snapshotUrl, LocalDateTime fechaHora) {
        this.eventoId = eventoId;
        this.rolId = rolId;
        this.catalogoAnomaliaId = catalogoAnomaliaId;
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

    public Long getCatalogoAnomaliaId() {
        return catalogoAnomaliaId;
    }

    public void setCatalogoAnomaliaId(Long catalogoAnomaliaId) {
        this.catalogoAnomaliaId = catalogoAnomaliaId;
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
