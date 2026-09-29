package com.sentinelmine.model;

public class Trabajador {

    private String codigo;
    private String nombre;
    private boolean eppCompleto;

    public Trabajador(String codigo, String nombre, boolean eppCompleto) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.eppCompleto = eppCompleto;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isEppCompleto() {
        return eppCompleto;
    }

    public void setEppCompleto(boolean eppCompleto) {
        this.eppCompleto = eppCompleto;
    }
}
