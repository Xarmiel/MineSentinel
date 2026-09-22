package com.sentinelmine.dto.response;

import java.util.Map;

public class AforoGlobalResponseDTO {

    private int aforoActual;
    private int aforoMaximo;
    private int porcentajeCapacidad;
    private String estadoAforo;
    private int cantidadTurnosActivos;
    private Map<String, Integer> conteoPorRol;

    public AforoGlobalResponseDTO() {
    }

    public AforoGlobalResponseDTO(int aforoActual, int aforoMaximo, int porcentajeCapacidad,
                                  String estadoAforo, int cantidadTurnosActivos, Map<String, Integer> conteoPorRol) {
        this.aforoActual = aforoActual;
        this.aforoMaximo = aforoMaximo;
        this.porcentajeCapacidad = porcentajeCapacidad;
        this.estadoAforo = estadoAforo;
        this.cantidadTurnosActivos = cantidadTurnosActivos;
        this.conteoPorRol = conteoPorRol;
    }

    public int getAforoActual() {
        return aforoActual;
    }

    public void setAforoActual(int aforoActual) {
        this.aforoActual = aforoActual;
    }

    public int getAforoMaximo() {
        return aforoMaximo;
    }

    public void setAforoMaximo(int aforoMaximo) {
        this.aforoMaximo = aforoMaximo;
    }

    public int getPorcentajeCapacidad() {
        return porcentajeCapacidad;
    }

    public void setPorcentajeCapacidad(int porcentajeCapacidad) {
        this.porcentajeCapacidad = porcentajeCapacidad;
    }

    public String getEstadoAforo() {
        return estadoAforo;
    }

    public void setEstadoAforo(String estadoAforo) {
        this.estadoAforo = estadoAforo;
    }

    public int getCantidadTurnosActivos() {
        return cantidadTurnosActivos;
    }

    public void setCantidadTurnosActivos(int cantidadTurnosActivos) {
        this.cantidadTurnosActivos = cantidadTurnosActivos;
    }

    public Map<String, Integer> getConteoPorRol() {
        return conteoPorRol;
    }

    public void setConteoPorRol(Map<String, Integer> conteoPorRol) {
        this.conteoPorRol = conteoPorRol;
    }
}
