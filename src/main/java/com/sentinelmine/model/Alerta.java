package com.sentinelmine.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Alerta {

    private static int contador = 0;

    private final int id;
    private final TipoAlerta tipo;
    private final String trabajadorCodigo;
    private final String descripcion;
    private final Prioridad prioridad;
    private final LocalDateTime hora;
    private boolean notificadoJefeTurno;

    public Alerta(TipoAlerta tipo, String trabajadorCodigo, String descripcion, Prioridad prioridad) {
        this.id = ++contador;
        this.tipo = tipo;
        this.trabajadorCodigo = trabajadorCodigo;
        this.descripcion = descripcion;
        this.prioridad = prioridad;
        this.hora = LocalDateTime.now();
        this.notificadoJefeTurno = false;
    }

    public int getId() {
        return id;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public String getTrabajadorCodigo() {
        return trabajadorCodigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public String getHoraFormateada() {
        return hora.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public boolean isNotificadoJefeTurno() {
        return notificadoJefeTurno;
    }

    public void marcarNotificado() {
        this.notificadoJefeTurno = true;
    }

    public boolean isPrioridadAlta() {
        return prioridad == Prioridad.ALTA;
    }

    public String getTituloVisual() {
        return tipo == TipoAlerta.SALUD_POSTURA ? "Alerta de salud / postura" : "Infracción de EPP detectada";
    }

    public String getPrioridadTexto() {
        return prioridad.name().toLowerCase();
    }
}
