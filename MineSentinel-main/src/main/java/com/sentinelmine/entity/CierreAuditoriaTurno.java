package com.sentinelmine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 9. CierresAuditoriaTurno: Resumen consolidado y balance de personal al finalizar un evento de turno.
 * Permite conciliar que todas las personas que ingresaron en la guardia hayan registrado su salida,
 * o identificar discrepancias de personal que aún permanece dentro de la mina.
 */
@Entity
@Table(name = "cierres_auditoria_turno", indexes = {
    @Index(name = "idx_cierres_auditoria_evento", columnList = "evento_id", unique = true)
})
public class CierreAuditoriaTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auditoria_id")
    private Long auditoriaId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false, unique = true)
    private EventoTurno eventoTurno;

    @Column(name = "total_entradas", nullable = false)
    private Integer totalEntradas;

    @Column(name = "total_salidas", nullable = false)
    private Integer totalSalidas;

    @Column(name = "diferencia", nullable = false)
    private Integer diferencia;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public CierreAuditoriaTurno() {
    }

    public CierreAuditoriaTurno(EventoTurno eventoTurno, Integer totalEntradas, Integer totalSalidas, Integer diferencia, LocalDateTime fechaHora) {
        this.eventoTurno = eventoTurno;
        this.totalEntradas = totalEntradas;
        this.totalSalidas = totalSalidas;
        this.diferencia = diferencia;
        this.fechaHora = fechaHora;
    }

    public Long getAuditoriaId() {
        return auditoriaId;
    }

    public void setAuditoriaId(Long auditoriaId) {
        this.auditoriaId = auditoriaId;
    }

    public EventoTurno getEventoTurno() {
        return eventoTurno;
    }

    public void setEventoTurno(EventoTurno eventoTurno) {
        this.eventoTurno = eventoTurno;
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

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CierreAuditoriaTurno that = (CierreAuditoriaTurno) o;
        return Objects.equals(auditoriaId, that.auditoriaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditoriaId);
    }

    @Override
    public String toString() {
        return "CierreAuditoriaTurno{" +
                "auditoriaId=" + auditoriaId +
                ", totalEntradas=" + totalEntradas +
                ", totalSalidas=" + totalSalidas +
                ", diferencia=" + diferencia +
                ", fechaHora=" + fechaHora +
                '}';
    }
}
