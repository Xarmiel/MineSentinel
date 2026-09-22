package com.sentinelmine.dto;

/**
 * DTO para representar el resultado de la inferencia de visión artificial sobre un trabajador.
 */
public class ResultadoDeteccionDTO {

    private boolean eppCompleto;
    private String trabajadorCodigo;
    private String elementoFaltante;
    private Float nivelConfianza;

    public ResultadoDeteccionDTO() {
    }

    public ResultadoDeteccionDTO(boolean eppCompleto, String trabajadorCodigo, String elementoFaltante, Float nivelConfianza) {
        this.eppCompleto = eppCompleto;
        this.trabajadorCodigo = trabajadorCodigo;
        this.elementoFaltante = elementoFaltante;
        this.nivelConfianza = nivelConfianza;
    }

    public boolean isEppCompleto() {
        return eppCompleto;
    }

    public void setEppCompleto(boolean eppCompleto) {
        this.eppCompleto = eppCompleto;
    }

    public String getTrabajadorCodigo() {
        return trabajadorCodigo;
    }

    public void setTrabajadorCodigo(String trabajadorCodigo) {
        this.trabajadorCodigo = trabajadorCodigo;
    }

    public String getElementoFaltante() {
        return elementoFaltante;
    }

    public void setElementoFaltante(String elementoFaltante) {
        this.elementoFaltante = elementoFaltante;
    }

    public Float getNivelConfianza() {
        return nivelConfianza;
    }

    public void setNivelConfianza(Float nivelConfianza) {
        this.nivelConfianza = nivelConfianza;
    }
}
