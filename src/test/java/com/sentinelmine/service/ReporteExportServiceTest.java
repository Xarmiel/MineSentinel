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
        assertTrue(contenido.contains("ID;Tipo;Titulo;Hora;Descripcion;Trabajador;Prioridad;Notificado_Jefe_Turno;Snapshot_URL"));
        assertTrue(contenido.contains("Falta Casco"));
        assertTrue(contenido.contains("#16"));
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
