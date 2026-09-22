package com.sentinelmine.dto;

public class AforoDTO {

    private final int actual;
    private final int maximo;
    private final int minimo;
    private final int porcentaje;
    private final String estado;
    private final String ultimoEvento;

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

    public int getMaximo() {
        return maximo;
    }

    public int getMinimo() {
        return minimo;
    }

    public int getPorcentaje() {
        return porcentaje;
    }

    public String getEstado() {
        return estado;
    }

    public String getUltimoEvento() {
        return ultimoEvento;
    }
}
