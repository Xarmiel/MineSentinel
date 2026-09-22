package com.sentinelmine.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AforoDTOTest {

    @Test
    @DisplayName("Debe construir y mapear correctamente los campos del DTO de Aforo")
    void testAforoDTOProperties() {
        AforoDTO dto = new AforoDTO(42, 50, 10, 84, "AFORO NORMAL", "20:18:11 - Ingreso autorizado");

        assertEquals(42, dto.getActual());
        assertEquals(50, dto.getMaximo());
        assertEquals(10, dto.getMinimo());
        assertEquals(84, dto.getPorcentaje());
        assertEquals("AFORO NORMAL", dto.getEstado());
        assertEquals("20:18:11 - Ingreso autorizado", dto.getUltimoEvento());
    }

    @Test
    @DisplayName("Debe permitir modificar valores mediante setters")
    void testAforoDTOSetters() {
        AforoDTO dto = new AforoDTO();
        dto.setActual(48);
        dto.setMaximo(50);
        dto.setPorcentaje(96);
        dto.setEstado("AFORO CRÍTICO");
        dto.setUltimoEvento("Cruce de línea");

        assertEquals(48, dto.getActual());
        assertEquals(96, dto.getPorcentaje());
        assertEquals("AFORO CRÍTICO", dto.getEstado());
    }
}
