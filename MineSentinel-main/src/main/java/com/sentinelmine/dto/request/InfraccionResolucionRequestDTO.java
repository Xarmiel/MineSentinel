package com.sentinelmine.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Payload de cierre de una infracción de EPP: el motor de visión detectó que
 * el trabajador volvió a portar el EPP que le faltaba (o retiró el impostor).
 *
 * El cliente Python lo envía al detectar la transición infracción → conforme
 * de un track, de modo que el panel de admin vea la alerta resolverse sola.
 */
public class InfraccionResolucionRequestDTO {

    @NotNull(message = "El faltaId es obligatorio")
    private Long faltaId;

    // Opcional: track de ByteTrack que originó la infracción (trazabilidad)
    private Integer trackId;

    // Motivo generado por el motor, p. ej. "EPP repuesto: Casco de Seguridad"
    private String motivo;

    // Evidencia opcional del cumplimiento (snapshot del frame conforme)
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
