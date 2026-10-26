package com.sentinelmine.dto;

import java.time.LocalDateTime;

/**
 * DTO unificado para representar alertas (Faltas de EPP y Anomalías de Movimiento)
 * en las vistas Thymeleaf del dashboard, panel de administración e historial.
 */
public class AlertaViewDTO {

    private Long id;
    private String tipo; // "EPP" o "ANOMALIA"
    private String tituloVisual;
    private String horaFormateada;
    private String descripcion;
    private String trabajadorCodigo;
    private String prioridadTexto;
    private boolean prioridadAlta;
    private boolean notificadoJefeTurno;
    private String snapshotUrl;
    // Estado crudo de la alerta (PENDIENTE, NOTIFICADA, REVISADA, DESCARTADA,
    // RESUELTA) para que el panel admin pueda distinguir las que el motor ya
    // cerró solo de las que siguen abiertas.
    private String estadoAlerta;
    // Datos temporales y de trazabilidad que el historial necesita para no
    // depender solo de la hora: fecha completa, track de ByteTrack y confianza.
    private LocalDateTime fechaHora;
    private String fechaFormateada;
    private Integer trackId;
    private String rolNombre;
    private Float nivelConfianza;
    private String turnoNombre;
    private String tipoInfraccion;

    public AlertaViewDTO() {
    }

    public AlertaViewDTO(Long id, String tipo, String tituloVisual, String horaFormateada,
                         String descripcion, String trabajadorCodigo, String prioridadTexto,
                         boolean prioridadAlta, boolean notificadoJefeTurno, String snapshotUrl) {
        this(id, tipo, tituloVisual, horaFormateada, descripcion, trabajadorCodigo,
                prioridadTexto, prioridadAlta, notificadoJefeTurno, snapshotUrl, null);
    }

    public AlertaViewDTO(Long id, String tipo, String tituloVisual, String horaFormateada,
                         String descripcion, String trabajadorCodigo, String prioridadTexto,
                         boolean prioridadAlta, boolean notificadoJefeTurno, String snapshotUrl,
                         String estadoAlerta) {
        this.id = id;
        this.tipo = tipo;
        this.tituloVisual = tituloVisual;
        this.horaFormateada = horaFormateada;
        this.descripcion = descripcion;
        this.trabajadorCodigo = trabajadorCodigo;
        this.prioridadTexto = prioridadTexto;
        this.prioridadAlta = prioridadAlta;
        this.notificadoJefeTurno = notificadoJefeTurno;
        this.snapshotUrl = snapshotUrl;
        this.estadoAlerta = estadoAlerta;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getTituloVisual() {
        return tituloVisual;
    }

    public void setTituloVisual(String tituloVisual) {
        this.tituloVisual = tituloVisual;
    }

    public String getHoraFormateada() {
        return horaFormateada;
    }

    public void setHoraFormateada(String horaFormateada) {
        this.horaFormateada = horaFormateada;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTrabajadorCodigo() {
        return trabajadorCodigo;
    }

    public void setTrabajadorCodigo(String trabajadorCodigo) {
        this.trabajadorCodigo = trabajadorCodigo;
    }

    public String getPrioridadTexto() {
        return prioridadTexto;
    }

    public void setPrioridadTexto(String prioridadTexto) {
        this.prioridadTexto = prioridadTexto;
    }

    public boolean isPrioridadAlta() {
        return prioridadAlta;
    }

    public void setPrioridadAlta(boolean prioridadAlta) {
        this.prioridadAlta = prioridadAlta;
    }

    public boolean isNotificadoJefeTurno() {
        return notificadoJefeTurno;
    }

    public void setNotificadoJefeTurno(boolean notificadoJefeTurno) {
        this.notificadoJefeTurno = notificadoJefeTurno;
    }

    public String getSnapshotUrl() {
        return snapshotUrl;
    }

    public void setSnapshotUrl(String snapshotUrl) {
        this.snapshotUrl = snapshotUrl;
    }

    public String getEstadoAlerta() {
        return estadoAlerta;
    }

    public void setEstadoAlerta(String estadoAlerta) {
        this.estadoAlerta = estadoAlerta;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getFechaFormateada() {
        return fechaFormateada;
    }

    public void setFechaFormateada(String fechaFormateada) {
        this.fechaFormateada = fechaFormateada;
    }

    public Integer getTrackId() {
        return trackId;
    }

    public void setTrackId(Integer trackId) {
        this.trackId = trackId;
    }

    public String getRolNombre() {
        return rolNombre;
    }

    public void setRolNombre(String rolNombre) {
        this.rolNombre = rolNombre;
    }

    public Float getNivelConfianza() {
        return nivelConfianza;
    }

    public void setNivelConfianza(Float nivelConfianza) {
        this.nivelConfianza = nivelConfianza;
    }

    public String getConfianzaPorcentaje() {
        return nivelConfianza != null ? Math.round(nivelConfianza * 100) + "%" : "—";
    }

    public String getTurnoNombre() {
        return turnoNombre;
    }

    public void setTurnoNombre(String turnoNombre) {
        this.turnoNombre = turnoNombre;
    }

    public String getTipoInfraccion() {
        return tipoInfraccion;
    }

    public void setTipoInfraccion(String tipoInfraccion) {
        this.tipoInfraccion = tipoInfraccion;
    }
}
