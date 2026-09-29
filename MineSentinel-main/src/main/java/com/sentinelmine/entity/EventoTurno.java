package com.sentinelmine.entity;

import com.sentinelmine.entity.enums.EstadoTurno;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 5. EventosTurno: Instancias operativas reales de un turno en una fecha/hora determinada.
 * IMPORTANTE: Soporta múltiples registros con estado 'ACTIVO' en paralelo para permitir
 * el solapamiento de turnos (shift overlap) durante el relevo de guardias.
 */
@Entity
@Table(name = "eventos_turno", indexes = {
    @Index(name = "idx_eventos_turno_estado", columnList = "estado"),
    @Index(name = "idx_eventos_turno_fechas", columnList = "fecha_inicio, fecha_fin")
})
public class EventoTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evento_id")
    private Long eventoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTurno estado = EstadoTurno.ACTIVO;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    public EventoTurno() {
    }

    public EventoTurno(Turno turno, LocalDateTime fechaInicio) {
        this.turno = turno;
        this.fechaInicio = fechaInicio;
        this.estado = EstadoTurno.ACTIVO;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Turno getTurno() {
        return turno;
    }

    public void setTurno(Turno turno) {
        this.turno = turno;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDateTime fechaFin) {
        this.fechaFin = fechaFin;
    }

    public boolean isActivo() {
        return EstadoTurno.ACTIVO.equals(this.estado);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventoTurno that = (EventoTurno) o;
        return Objects.equals(eventoId, that.eventoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventoId);
    }

    @Override
    public String toString() {
        return "EventoTurno{" +
                "eventoId=" + eventoId +
                ", estado=" + estado +
                ", fechaInicio=" + fechaInicio +
                ", fechaFin=" + fechaFin +
                '}';
    }
}
