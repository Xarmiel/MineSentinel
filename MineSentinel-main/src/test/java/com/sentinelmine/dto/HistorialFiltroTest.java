package com.sentinelmine.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class HistorialFiltroTest {

    @Test
    @DisplayName("Debe normalizar tipo, estado y tamaño de página")
    void testNormalizacion() {
        HistorialFiltro filtro = new HistorialFiltro(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 26),
                " epp ",
                " resuelta ",
                16,
                "  casco  ",
                -5,
                999);

        assertEquals("EPP", filtro.getTipo());
        assertEquals("RESUELTA", filtro.getEstado());
        assertEquals("casco", filtro.getQ());
        assertEquals(0, filtro.getPage());
        assertEquals(HistorialFiltro.PAGE_SIZE_MAX, filtro.getSize());
        assertEquals(0, filtro.getOffset());
    }

    @Test
    @DisplayName("Debe descartar tipos y estados no reconocidos")
    void testValoresInvalidos() {
        HistorialFiltro filtro = new HistorialFiltro();
        filtro.setTipo("OTRO");
        filtro.setEstado("CERRADA");
        filtro.setQ("   ");
        filtro.setSize(0);

        assertNull(filtro.getTipo());
        assertNull(filtro.getEstado());
        assertNull(filtro.getQ());
        assertEquals(HistorialFiltro.PAGE_SIZE_DEFAULT, filtro.getSize());
    }

    @Test
    @DisplayName("Debe calcular el offset de paginación")
    void testOffset() {
        HistorialFiltro filtro = new HistorialFiltro(null, null, null, null, null, null, 3, 25);
        assertEquals(75, filtro.getOffset());
        assertEquals(4, filtro.conPagina(4).getPage());
        assertEquals(100, filtro.conPagina(4).getOffset());
    }
}
