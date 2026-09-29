package com.sentinelmine.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuadro de cumplimiento de EPP publicado por el motor de visión para la vista
 * de cámara en vivo.
 *
 * A diferencia de las infracciones (que son histórico y disparan alerta), esto
 * es estado de pantalla: el backend lo retiene unos segundos y lo reemite por
 * SSE. Por eso no lleva eventoId ni fecha: describe "ahora mismo", no un hecho
 * que haya que archivar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CuadroEPPRequestDTO {

    private Integer rolId;
    private String nombreRol;
    private Long fotograma;
    private Double fps;
    private List<Integer> dimensiones;
    private List<Persona> personas = new ArrayList<>();

    public CuadroEPPRequestDTO() {
    }

    public Integer getRolId() {
        return rolId;
    }

    public void setRolId(Integer rolId) {
        this.rolId = rolId;
    }

    public String getNombreRol() {
        return nombreRol;
    }

    public void setNombreRol(String nombreRol) {
        this.nombreRol = nombreRol;
    }

    public Long getFotograma() {
        return fotograma;
    }

    public void setFotograma(Long fotograma) {
        this.fotograma = fotograma;
    }

    public Double getFps() {
        return fps;
    }

    public void setFps(Double fps) {
        this.fps = fps;
    }

    public List<Integer> getDimensiones() {
        return dimensiones;
    }

    public void setDimensiones(List<Integer> dimensiones) {
        this.dimensiones = dimensiones;
    }

    public List<Persona> getPersonas() {
        return personas;
    }

    public void setPersonas(List<Persona> personas) {
        this.personas = personas != null ? personas : new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Persona {

        private Integer trackId;
        private String estado;
        private Double confianza;
        private String lado;
        private List<String> obligatorios = new ArrayList<>();
        private List<String> conformes = new ArrayList<>();
        private List<Falta> faltantes = new ArrayList<>();

        /** [x1, y1, x2, y2] en píxeles del frame, si el motor los envía. */
        private List<Integer> caja;

        public Integer getTrackId() {
            return trackId;
        }

        public void setTrackId(Integer trackId) {
            this.trackId = trackId;
        }

        public String getEstado() {
            return estado;
        }

        public void setEstado(String estado) {
            this.estado = estado;
        }

        public Double getConfianza() {
            return confianza;
        }

        public void setConfianza(Double confianza) {
            this.confianza = confianza;
        }

        public String getLado() {
            return lado;
        }

        public void setLado(String lado) {
            this.lado = lado;
        }

        public List<String> getObligatorios() {
            return obligatorios;
        }

        public void setObligatorios(List<String> obligatorios) {
            this.obligatorios = obligatorios != null ? obligatorios : new ArrayList<>();
        }

        public List<String> getConformes() {
            return conformes;
        }

        public void setConformes(List<String> conformes) {
            this.conformes = conformes != null ? conformes : new ArrayList<>();
        }

        public List<Falta> getFaltantes() {
            return faltantes;
        }

        public void setFaltantes(List<Falta> faltantes) {
            this.faltantes = faltantes != null ? faltantes : new ArrayList<>();
        }

        public List<Integer> getCaja() {
            return caja;
        }

        public void setCaja(List<Integer> caja) {
            this.caja = caja;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Falta {

        private String clave;
        private String nombre;
        private String tipo;
        private String gravedad;
        private Double confianza;

        @JsonProperty("descripcion")
        private String descripcion;

        public String getClave() {
            return clave;
        }

        public void setClave(String clave) {
            this.clave = clave;
        }

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getGravedad() {
            return gravedad;
        }

        public void setGravedad(String gravedad) {
            this.gravedad = gravedad;
        }

        public Double getConfianza() {
            return confianza;
        }

        public void setConfianza(Double confianza) {
            this.confianza = confianza;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }
    }
}
