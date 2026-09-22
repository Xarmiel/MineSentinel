package com.sentinelmine.entity;

import com.sentinelmine.entity.enums.TipoMovimiento;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 6. MovimientosAforo: Registros individuales de cruce de línea virtual (ENTRADA / SALIDA)
 * emitidos por el módulo de visión computacional y tracking (YOLOv8 + ByteTrack).
 * 
 * ARQUITECTURA: Diseñado bajo el patrón Append-Only Event Ledger para eliminar bloqueos de concurrencia
 * a nivel de fila durante ráfagas de paso de personal por boca-mina.
 */
@Entity
@Table(name = "movimientos_aforo", indexes = {
    @Index(name = "idx_movimientos_evento", columnList = "evento_id"),
    @Index(name = "idx_movimientos_tipo_fecha", columnList = "tipo_movimiento, fecha_hora"),
    @Index(name = "idx_movimientos_evento_tipo", columnList = "evento_id, tipo_movimiento")
})
public class MovimientoAforo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movimiento_id")
    private Long movimientoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private EventoTurno eventoTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    private RolPersonal rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 20)
    private TipoMovimiento tipoMovimiento;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public MovimientoAforo() {
    }

    public MovimientoAforo(EventoTurno eventoTurno, RolPersonal rol, TipoMovimiento tipoMovimiento, LocalDateTime fechaHora) {
        this.eventoTurno = eventoTurno;
        this.rol = rol;
        this.tipoMovimiento = tipoMovimiento;
        this.fechaHora = fechaHora;
    }

    public Long getMovimientoId() {
        return movimientoId;
    }

    public void setMovimientoId(Long movimientoId) {
        this.movimientoId = movimientoId;
    }

    public EventoTurno getEventoTurno() {
        return eventoTurno;
    }

    public void setEventoTurno(EventoTurno eventoTurno) {
        this.eventoTurno = eventoTurno;
    }

    public RolPersonal getRol() {
        return rol;
    }

    public void setRol(RolPersonal rol) {
        this.rol = rol;
    }

    public TipoMovimiento getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(TipoMovimiento tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
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
        MovimientoAforo that = (MovimientoAforo) o;
        return Objects.equals(movimientoId, that.movimientoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(movimientoId);
    }

    @Override
    public String toString() {
        return "MovimientoAforo{" +
                "movimientoId=" + movimientoId +
                ", tipoMovimiento=" + tipoMovimiento +
                ", fechaHora=" + fechaHora +
                '}';
    }
}
