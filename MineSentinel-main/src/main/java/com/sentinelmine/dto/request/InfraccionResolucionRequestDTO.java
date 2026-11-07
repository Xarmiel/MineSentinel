package com.sentinelmine.dto.request;

import jakarta.validation.constraints.NotNull;

public class InfraccionResolucionRequestDTO {

    @NotNull(message = "El faltaId es obligatorio")
    private Long faltaId;

    private Integer trackId;

    private String motivo;

    private String snapshotUrl;

    public InfraccionResolucionRequestDTO() {
    }

    public InfraccionResolucionRequestDTO(Long faltaId, Integer trackId, String motivo, String snapshotUrl) {
        this.faltaId = faltaId;
        this.trackId = trackId;
        this.motivo = motivo;
        this.snapshotUrl = snapshotUrl;
    }

    public Long getFaltaId() {
        return faltaId;
    }

    public void setFaltaId(Long faltaId) {
        this.faltaId = faltaId;
    }

    public Integer getTrackId() {
        return trackId;
    }

    public void setTrackId(Integer trackId) {
        this.trackId = trackId;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getSnapshotUrl() {
        return snapshotUrl;
    }

    public void setSnapshotUrl(String snapshotUrl) {
        this.snapshotUrl = snapshotUrl;
    }
}
