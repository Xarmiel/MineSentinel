package com.sentinelmine.dto.response;

import java.time.LocalDateTime;

public class CierreTurnoResponseDTO {

    private Long auditoriaId;
    private Long eventoId;
    private String nombreTurno;
    private Integer totalEntradas;
    private Integer totalSalidas;
    private Integer diferencia;
    private LocalDateTime fechaCierre;
    private String mensajeAuditoria;

    public CierreTurnoResponseDTO() {
    }

    public CierreTurnoResponseDTO(Long auditoriaId, Long eventoId, String nombreTurno,
                                  Integer totalEntradas, Integer totalSalidas, Integer diferencia,
                                  LocalDateTime fechaCierre, String mensajeAuditoria) {
        this.auditoriaId = auditoriaId;
        this.eventoId = eventoId;
        this.nombreTurno = nombreTurno;
        this.totalEntradas = totalEntradas;
        this.totalSalidas = totalSalidas;
        this.diferencia = diferencia;
        this.fechaCierre = fechaCierre;
        this.mensajeAuditoria = mensajeAuditoria;
    }

    public Long getAuditoriaId() {
        return auditoriaId;
    }

    public void setAuditoriaId(Long auditoriaId) {
        this.auditoriaId = auditoriaId;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public String getNombreTurno() {
        return nombreTurno;
    }

    public void setNombreTurno(String nombreTurno) {
        this.nombreTurno = nombreTurno;
    }

    public Integer getTotalEntradas() {
        return totalEntradas;
    }

    public void setTotalEntradas(Integer totalEntradas) {
        this.totalEntradas = totalEntradas;
    }

    public Integer getTotalSalidas() {
        return totalSalidas;
    }

    public void setTotalSalidas(Integer totalSalidas) {
        this.totalSalidas = totalSalidas;
    }

    public Integer getDiferencia() {
        return diferencia;
    }

    public void setDiferencia(Integer diferencia) {
        this.diferencia = diferencia;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public String getMensajeAuditoria() {
        return mensajeAuditoria;
    }

    public void setMensajeAuditoria(String mensajeAuditoria) {
        this.mensajeAuditoria = mensajeAuditoria;
    }
}
