package com.sentinelmine.dto;

import java.time.LocalDate;

/**
 * Filtros de consulta del historial de alertas. Se aplican en la base de datos
 * (no en el navegador), de modo que la búsqueda cubre todo el histórico y no
 * solo las filas que ya fueron enviadas a la vista.
 */
public class HistorialFiltro {

    public static final int PAGE_SIZE_DEFAULT = 25;
    public static final int PAGE_SIZE_MAX = 100;

    private LocalDate desde;
    private LocalDate hasta;
    private String tipo;
    private String estado;
    private Integer trackId;
    private String q;
    private int page;
    private int size = PAGE_SIZE_DEFAULT;

    public HistorialFiltro() {
    }

    public HistorialFiltro(LocalDate desde, LocalDate hasta, String tipo, String estado,
                           Integer trackId, String q, int page, int size) {
        this.desde = desde;
        this.hasta = hasta;
        this.tipo = normalizarTipo(tipo);
        this.estado = normalizarEstado(estado);
        this.trackId = trackId;
        this.q = limpiar(q);
        this.page = Math.max(page, 0);
        this.size = normalizarSize(size);
    }

    public LocalDate getDesde() {
        return desde;
    }

    public void setDesde(LocalDate desde) {
        this.desde = desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public void setHasta(LocalDate hasta) {
        this.hasta = hasta;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = normalizarTipo(tipo);
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = normalizarEstado(estado);
    }

    public Integer getTrackId() {
        return trackId;
    }

    public void setTrackId(Integer trackId) {
        this.trackId = trackId;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = limpiar(q);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 0);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = normalizarSize(size);
    }

    public int getOffset() {
        return page * size;
    }

    public HistorialFiltro conPagina(int nuevaPagina) {
        HistorialFiltro copia = copiar();
        copia.setPage(nuevaPagina);
        return copia;
    }

    public HistorialFiltro conTamano(int nuevoTamano) {
        HistorialFiltro copia = copiar();
        copia.setSize(nuevoTamano);
        return copia;
    }

    public HistorialFiltro sinPaginacion() {
        HistorialFiltro copia = copiar();
        copia.setPage(0);
        copia.setSize(PAGE_SIZE_MAX);
        return copia;
    }

    private HistorialFiltro copiar() {
        HistorialFiltro copia = new HistorialFiltro();
        copia.desde = desde;
        copia.hasta = hasta;
        copia.tipo = tipo;
        copia.estado = estado;
        copia.trackId = trackId;
        copia.q = q;
        copia.page = page;
        copia.size = size;
        return copia;
    }

    private static String normalizarTipo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim().toUpperCase();
        return ("EPP".equals(limpio) || "ANOMALIA".equals(limpio)) ? limpio : null;
    }

    private static String normalizarEstado(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim().toUpperCase();
        return switch (limpio) {
            case "PENDIENTE", "NOTIFICADA", "REVISADA", "DESCARTADA", "RESUELTA" -> limpio;
            default -> null;
        };
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static int normalizarSize(int size) {
        if (size <= 0) {
            return PAGE_SIZE_DEFAULT;
        }
        return Math.min(size, PAGE_SIZE_MAX);
    }
}
