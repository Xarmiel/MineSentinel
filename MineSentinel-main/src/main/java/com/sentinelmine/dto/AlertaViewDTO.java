package com.sentinelmine.dto;

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

    public AlertaViewDTO() {
    }

    public AlertaViewDTO(Long id, String tipo, String tituloVisual, String horaFormateada,
                         String descripcion, String trabajadorCodigo, String prioridadTexto,
                         boolean prioridadAlta, boolean notificadoJefeTurno, String snapshotUrl) {
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
}
