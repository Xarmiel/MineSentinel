package com.sentinelmine.dto;

/**
 * DTO para la visualización del estado de aforo en las vistas Thymeleaf (dashboard, panel-admin).
 */
public class AforoDTO {

    private int actual;
    private int maximo;
    private int minimo;
    private int porcentaje;
    private String estado;
    private String ultimoEvento;

    public AforoDTO() {
    }

    public AforoDTO(int actual, int maximo, int minimo, int porcentaje, String estado, String ultimoEvento) {
        this.actual = actual;
        this.maximo = maximo;
        this.minimo = minimo;
        this.porcentaje = porcentaje;
        this.estado = estado;
        this.ultimoEvento = ultimoEvento;
    }

    public int getActual() {
        return actual;
    }

    public void setActual(int actual) {
        this.actual = actual;
    }

    public int getMaximo() {
        return maximo;
    }

    public void setMaximo(int maximo) {
        this.maximo = maximo;
    }

    public int getMinimo() {
        return minimo;
    }

    public void setMinimo(int minimo) {
        this.minimo = minimo;
    }

    public int getPorcentaje() {
        return porcentaje;
    }

    public void setPorcentaje(int porcentaje) {
        this.porcentaje = porcentaje;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getUltimoEvento() {
        return ultimoEvento;
    }

    public void setUltimoEvento(String ultimoEvento) {
        this.ultimoEvento = ultimoEvento;
    }
}
