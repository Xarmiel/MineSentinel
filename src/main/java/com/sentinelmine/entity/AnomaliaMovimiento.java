package com.sentinelmine.entity;

import com.sentinelmine.entity.enums.EstadoAlerta;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 7. AnomaliasMovimiento: Registro de comportamientos anómalos o riesgosos detectados por el modelo de visión.
 */
@Entity
@Table(name = "anomalias_movimiento", indexes = {
    @Index(name = "idx_anomalias_evento", columnList = "evento_id"),
    @Index(name = "idx_anomalias_estado", columnList = "estado_alerta"),
    @Index(name = "idx_anomalias_fecha", columnList = "fecha_hora")
})
public class AnomaliaMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "anomalia_id")
    private Long anomaliaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private EventoTurno eventoTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id")
    private RolPersonal rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogo_anomalia_id", nullable = false)
    private CatalogoAnomalias catalogoAnomalia;

    @Column(name = "nivel_confianza", nullable = false)
    private Float nivelConfianza;

    @Column(name = "snapshot_url", length = 500)
    private String snapshotUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_alerta", nullable = false, length = 30)
    private EstadoAlerta estadoAlerta = EstadoAlerta.PENDIENTE;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public AnomaliaMovimiento() {
    }

    public AnomaliaMovimiento(EventoTurno eventoTurno, RolPersonal rol, CatalogoAnomalias catalogoAnomalia,
                              Float nivelConfianza, String snapshotUrl, EstadoAlerta estadoAlerta, LocalDateTime fechaHora) {
        this.eventoTurno = eventoTurno;
        this.rol = rol;
        this.catalogoAnomalia = catalogoAnomalia;
        this.nivelConfianza = nivelConfianza;
        this.snapshotUrl = snapshotUrl;
        this.estadoAlerta = estadoAlerta != null ? estadoAlerta : EstadoAlerta.PENDIENTE;
        this.fechaHora = fechaHora;
    }

    public Long getAnomaliaId() {
        return anomaliaId;
    }

    public void setAnomaliaId(Long anomaliaId) {
        this.anomaliaId = anomaliaId;
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

    public CatalogoAnomalias getCatalogoAnomalia() {
        return catalogoAnomalia;
    }

    public void setCatalogoAnomalia(CatalogoAnomalias catalogoAnomalia) {
        this.catalogoAnomalia = catalogoAnomalia;
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

    public EstadoAlerta getEstadoAlerta() {
        return estadoAlerta;
    }

    public void setEstadoAlerta(EstadoAlerta estadoAlerta) {
        this.estadoAlerta = estadoAlerta;
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
        AnomaliaMovimiento that = (AnomaliaMovimiento) o;
        return Objects.equals(anomaliaId, that.anomaliaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anomaliaId);
    }

    @Override
    public String toString() {
        return "AnomaliaMovimiento{" +
                "anomaliaId=" + anomaliaId +
                ", nivelConfianza=" + nivelConfianza +
                ", estadoAlerta=" + estadoAlerta +
                ", fechaHora=" + fechaHora +
                '}';
    }
}
