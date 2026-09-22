package com.sentinelmine.entity;

import com.sentinelmine.entity.enums.EstadoAlerta;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 8. FaltasEPP: Detecciones de incumplimiento de Elementos de Protección Personal
 * (e.g. Ingreso a socavón sin barbiquejo, sin lámpara, o sin chaleco).
 */
@Entity
@Table(name = "faltas_epp", indexes = {
    @Index(name = "idx_faltas_epp_evento", columnList = "evento_id"),
    @Index(name = "idx_faltas_epp_estado", columnList = "estado_alerta"),
    @Index(name = "idx_faltas_epp_fecha", columnList = "fecha_hora")
})
public class FaltaEPP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "falta_id")
    private Long faltaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private EventoTurno eventoTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id")
    private RolPersonal rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epp_id", nullable = false)
    private CatalogoEPP epp;

    @Column(name = "nivel_confianza", nullable = false)
    private Float nivelConfianza;

    @Column(name = "snapshot_url", length = 500)
    private String snapshotUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_alerta", nullable = false, length = 30)
    private EstadoAlerta estadoAlerta = EstadoAlerta.PENDIENTE;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public FaltaEPP() {
    }

    public FaltaEPP(EventoTurno eventoTurno, RolPersonal rol, CatalogoEPP epp,
                    Float nivelConfianza, String snapshotUrl, EstadoAlerta estadoAlerta, LocalDateTime fechaHora) {
        this.eventoTurno = eventoTurno;
        this.rol = rol;
        this.epp = epp;
        this.nivelConfianza = nivelConfianza;
        this.snapshotUrl = snapshotUrl;
        this.estadoAlerta = estadoAlerta != null ? estadoAlerta : EstadoAlerta.PENDIENTE;
        this.fechaHora = fechaHora;
    }

    public Long getFaltaId() {
        return faltaId;
    }

    public void setFaltaId(Long faltaId) {
        this.faltaId = faltaId;
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

    public CatalogoEPP getEpp() {
        return epp;
    }

    public void setEpp(CatalogoEPP epp) {
        this.epp = epp;
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
        FaltaEPP faltaEPP = (FaltaEPP) o;
        return Objects.equals(faltaId, faltaEPP.faltaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(faltaId);
    }

    @Override
    public String toString() {
        return "FaltaEPP{" +
                "faltaId=" + faltaId +
                ", nivelConfianza=" + nivelConfianza +
                ", estadoAlerta=" + estadoAlerta +
                ", fechaHora=" + fechaHora +
                '}';
    }
}
