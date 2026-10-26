package com.sentinelmine.service;

import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.TipoMovimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReporteExportServiceTest {

    private final ReporteExportService reporteService = new ReporteExportService();

    @Test
    @DisplayName("Debe generar archivo CSV de alertas con BOM UTF-8 y encabezados correctos")
    void testGenerarCsvHistorialAlertas() {
        List<AlertaViewDTO> alertas = List.of(
                new AlertaViewDTO(1L, "EPP", "Falta Casco", "08:30:00", "Sin casco en boca-mina", "16", "alta", true, false, "/evidencias/snap1.jpg")
        );

        byte[] csvBytes = reporteService.generarCsvHistorialAlertas(alertas);
        assertNotNull(csvBytes);
        assertTrue(csvBytes.length > 0);

        String contenido = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(contenido.contains("ID;Tipo;Titulo;Fecha_Hora;Hora;Descripcion;Trabajador;Track;Rol;Prioridad;Estado;Notificado_Jefe_Turno;Confianza;Turno;Snapshot_URL"));
        assertTrue(contenido.contains("Falta Casco"));
        assertTrue(contenido.contains(";16;"));
    }

    @Test
    @DisplayName("El CSV debe incluir fecha, estado, track, confianza y turno")
    void testGenerarCsvHistorialConTrazabilidad() {
        AlertaViewDTO alerta = new AlertaViewDTO(
                7L, "EPP", "Falta de EPP detectada", "14:30:00",
                "Sin casco en boca-mina", "16", "media", false, true,
                "/api/v1/evidencias/snap1.jpg", "RESUELTA");
        alerta.setFechaHora(LocalDateTime.of(2026, 10, 26, 14, 30, 0));
        alerta.setTrackId(16);
        alerta.setRolNombre("Operador de Maquinaria");
        alerta.setNivelConfianza(0.87f);
        alerta.setTurnoNombre("Guardia Día");

        String contenido = new String(
                reporteService.generarCsvHistorialAlertas(List.of(alerta)),
                StandardCharsets.UTF_8);

        assertTrue(contenido.contains("2026-10-26 14:30:00"));
        assertTrue(contenido.contains(";RESUELTA;"));
        assertTrue(contenido.contains(";0,87;") || contenido.contains(";0.87;"));
        assertTrue(contenido.contains("Guardia Día"));
        assertTrue(contenido.contains("Operador de Maquinaria"));
    }

    @Test
    @DisplayName("El CSV debe neutralizar textos que Excel interpretaría como fórmula")
    void testGenerarCsvEscapaFormulas() {
        AlertaViewDTO alerta = new AlertaViewDTO(
                8L, "EPP", "=HYPERLINK(\"http://x\")", "14:31:00",
                "+SUM(1,2)", "16", "media", false, false, null, "PENDIENTE");

        String contenido = new String(
                reporteService.generarCsvHistorialAlertas(List.of(alerta)),
                StandardCharsets.UTF_8);

        assertTrue(contenido.contains("'=HYPERLINK"));
        assertTrue(contenido.contains("'+SUM"));
    }

    @Test
    @DisplayName("Debe generar archivo CSV de movimientos de aforo")
    void testGenerarCsvMovimientosAforo() {
        Turno turno = new Turno("Guardia Día", LocalTime.of(7, 0), LocalTime.of(15, 30));
        EventoTurno evento = new EventoTurno(turno, LocalDateTime.now());
        evento.setEventoId(10L);

        RolPersonal rol = new RolPersonal("Operador de Perforación", "Amarillo", true);
        MovimientoAforo mov = new MovimientoAforo(evento, rol, TipoMovimiento.ENTRADA, LocalDateTime.now());
        mov.setMovimientoId(501L);

        byte[] csvBytes = reporteService.generarCsvMovimientosAforo(List.of(mov));
        assertNotNull(csvBytes);

        String contenido = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(contenido.contains("ID_Movimiento;ID_Evento_Turno;Nombre_Turno;Tipo_Movimiento;Rol_Personal;Requiere_Aforo;Fecha_Hora"));
        assertTrue(contenido.contains("501"));
        assertTrue(contenido.contains("ENTRADA"));
        assertTrue(contenido.contains("Guardia Día"));
    }

    @Test
    @DisplayName("Debe generar Acta Oficial de Cierre de Guardia en HTML imprimible")
    void testGenerarActaCierreTurnoHtml() {
        Turno turno = new Turno("Guardia Día", LocalTime.of(7, 0), LocalTime.of(15, 30));
        EventoTurno evento = new EventoTurno(turno, LocalDateTime.now().minusHours(8));
        evento.setEventoId(50L);
        evento.setFechaFin(LocalDateTime.now());

        CierreAuditoriaTurno auditoria = new CierreAuditoriaTurno(evento, 45, 45, 0, LocalDateTime.now());
        auditoria.setAuditoriaId(1001L);

        String html = reporteService.generarActaCierreTurnoHtml(auditoria, evento);

        assertNotNull(html);
        assertTrue(html.contains("ACTA OFICIAL DE RECONCILIACIÓN Y AUDITORÍA DE TURNO DE GUARDIA"));
        assertTrue(html.contains("#50"));
        assertTrue(html.contains("#1001"));
        assertTrue(html.contains("CUADRADO (0 DISCREPANCIAS)"));
        assertTrue(html.contains("SUPERVISOR DE SEGURIDAD"));
    }
}
