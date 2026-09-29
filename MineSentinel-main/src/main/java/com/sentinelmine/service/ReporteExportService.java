package com.sentinelmine.service;

import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.entity.CierreAuditoriaTurno;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.MovimientoAforo;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio de generación y exportación de reportes de seguridad y auditoría minera.
 * Soporta exportación en CSV (compatible con Microsoft Excel) y generación de Actas Oficiales de Cierre de Guardia.
 */
@Service
public class ReporteExportService {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Genera un archivo CSV con el historial de alertas e infracciones registradas.
     */
    public byte[] generarCsvHistorialAlertas(List<AlertaViewDTO> alertas) {
        StringBuilder sb = new StringBuilder();
        // BOM UTF-8 para apertura correcta en Microsoft Excel
        sb.append('\ufeff');
        sb.append("ID;Tipo;Titulo;Hora;Descripcion;Trabajador;Prioridad;Notificado_Jefe_Turno;Snapshot_URL\n");

        for (AlertaViewDTO a : alertas) {
            sb.append(a.getId() != null ? a.getId() : "").append(";")
                    .append(escaparCsv(a.getTipo())).append(";")
                    .append(escaparCsv(a.getTituloVisual())).append(";")
                    .append(escaparCsv(a.getHoraFormateada())).append(";")
                    .append(escaparCsv(a.getDescripcion())).append(";")
                    .append(escaparCsv("#" + a.getTrabajadorCodigo())).append(";")
                    .append(escaparCsv(a.getPrioridadTexto())).append(";")
                    .append(a.isNotificadoJefeTurno() ? "SI" : "NO").append(";")
                    .append(escaparCsv(a.getSnapshotUrl() != null ? a.getSnapshotUrl() : "N/A"))
                    .append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Genera un archivo CSV con el detalle de movimientos de aforo (Event Ledger).
     */
    public byte[] generarCsvMovimientosAforo(List<MovimientoAforo> movimientos) {
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append("ID_Movimiento;ID_Evento_Turno;Nombre_Turno;Tipo_Movimiento;Rol_Personal;Requiere_Aforo;Fecha_Hora\n");

        for (MovimientoAforo m : movimientos) {
            String turnoNombre = m.getEventoTurno() != null && m.getEventoTurno().getTurno() != null ?
                    m.getEventoTurno().getTurno().getNombre() : "N/A";
            String rolNombre = m.getRol() != null ? m.getRol().getNombre() : "N/A";
            boolean reqAforo = m.getRol() != null && Boolean.TRUE.equals(m.getRol().getRequiereAforo());
            String hora = m.getFechaHora() != null ? m.getFechaHora().format(FORMATO_FECHA_HORA) : "N/A";

            sb.append(m.getMovimientoId()).append(";")
                    .append(m.getEventoTurno() != null ? m.getEventoTurno().getEventoId() : "").append(";")
                    .append(escaparCsv(turnoNombre)).append(";")
                    .append(m.getTipoMovimiento()).append(";")
                    .append(escaparCsv(rolNombre)).append(";")
                    .append(reqAforo ? "SI" : "NO").append(";")
                    .append(hora)
                    .append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Genera el HTML formateado para la impresión / guardado en PDF del Acta Oficial de Cierre de Guardia.
     */
    public String generarActaCierreTurnoHtml(CierreAuditoriaTurno auditoria, EventoTurno evento) {
        String nombreTurno = evento.getTurno() != null ? evento.getTurno().getNombre() : "Turno Operativo";
        String inicio = evento.getFechaInicio() != null ? evento.getFechaInicio().format(FORMATO_FECHA_HORA) : "N/A";
        String fin = evento.getFechaFin() != null ? evento.getFechaFin().format(FORMATO_FECHA_HORA) : "N/A";
        String fechaAuditoria = auditoria.getFechaHora() != null ? auditoria.getFechaHora().format(FORMATO_FECHA_HORA) : "N/A";

        int entradas = auditoria.getTotalEntradas();
        int salidas = auditoria.getTotalSalidas();
        int diff = auditoria.getDiferencia();
        String estadoBalance = diff == 0 ? "CUADRADO (0 DISCREPANCIAS)" : "DESCUADRADO (" + diff + " PENDIENTES EN INTERIOR)";
        String colorBalance = diff == 0 ? "#10b981" : "#ef4444";

        String template = """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <title>Acta Oficial de Auditoría y Cierre de Guardia</title>
            <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; margin: 40px; color: #1e293b; background: #fff; }
                .acta-box { border: 2px solid #0f172a; padding: 30px; border-radius: 8px; max-width: 800px; margin: 0 auto; }
                .header { text-align: center; border-bottom: 2px solid #e2e8f0; padding-bottom: 15px; margin-bottom: 25px; }
                .header h1 { margin: 0; font-size: 22px; color: #0f172a; text-transform: uppercase; }
                .header p { margin: 5px 0 0 0; color: #64748b; font-size: 13px; letter-spacing: 1px; }
                .section-title { font-size: 14px; font-weight: bold; background: #f1f5f9; padding: 6px 12px; border-left: 4px solid #f59e0b; margin: 20px 0 10px 0; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
                th, td { border: 1px solid #cbd5e1; padding: 8px 12px; text-align: left; }
                th { background-color: #f8fafc; color: #475569; }
                .kpi-grid { display: flex; gap: 15px; margin-top: 15px; }
                .kpi { flex: 1; border: 1px solid #cbd5e1; border-radius: 6px; padding: 12px; text-align: center; }
                .kpi .num { font-size: 24px; font-weight: bold; }
                .kpi .label { font-size: 11px; color: #64748b; text-transform: uppercase; margin-top: 4px; }
                .signatures { display: flex; justify-content: space-between; margin-top: 60px; padding-top: 20px; }
                .sig-box { width: 45%; text-align: center; border-top: 1px solid #0f172a; padding-top: 8px; font-size: 12px; }
                @media print {
                    body { margin: 0; }
                    .no-print { display: none; }
                }
            </style>
        </head>
        <body>
            <div class="no-print" style="text-align: center; margin-bottom: 20px;">
                <button onclick="window.print()" style="background: #f59e0b; color: #000; border: none; padding: 10px 20px; font-weight: bold; border-radius: 6px; cursor: pointer;">
                    🖨️ Imprimir / Guardar como PDF
                </button>
            </div>
            <div class="acta-box">
                <div class="header">
                    <h1>MineSentinel · Control de Operaciones Subterráneas</h1>
                    <p>ACTA OFICIAL DE RECONCILIACIÓN Y AUDITORÍA DE TURNO DE GUARDIA</p>
                </div>

                <div class="section-title">1. INFORMACIÓN OPERATIVA DEL TURNO</div>
                <table>
                    <tr><th>Código de Evento</th><td>#{{EVENTO_ID}}</td><th>Auditoría ID</th><td>#{{AUDITORIA_ID}}</td></tr>
                    <tr><th>Turno / Guardia</th><td>{{TURNO_NOMBRE}}</td><th>Fecha Auditoría</th><td>{{FECHA_AUDITORIA}}</td></tr>
                    <tr><th>Hora Inicio Real</th><td>{{HORA_INICIO}}</td><th>Hora Cierre Real</th><td>{{HORA_FIN}}</td></tr>
                </table>

                <div class="section-title">2. BALANCE Y RECONCILIACIÓN DE AFORO (PERSONAL CON INGRESO FISCALIZADO)</div>
                <div class="kpi-grid">
                    <div class="kpi">
                        <div class="num" style="color: #2563eb;">{{TOTAL_ENTRADAS}}</div>
                        <div class="label">Total Entradas</div>
                    </div>
                    <div class="kpi">
                        <div class="num" style="color: #0d9488;">{{TOTAL_SALIDAS}}</div>
                        <div class="label">Total Salidas</div>
                    </div>
                    <div class="kpi" style="border: 2px solid {{COLOR_BALANCE}};">
                        <div class="num" style="color: {{COLOR_BALANCE}};">{{DIFERENCIA}}</div>
                        <div class="label">Diferencia Neta</div>
                    </div>
                </div>

                <p style="font-size: 13px; margin-top: 15px;">
                    <strong>Estado del Balance:</strong> <span style="color: {{COLOR_BALANCE}}; font-weight: bold;">{{ESTADO_BALANCE}}</span>
                </p>

                <div class="section-title">3. DECLARACIÓN JURADA Y CONFORMIDAD</div>
                <p style="font-size: 12px; color: #475569; text-align: justify;">
                    Se certifica que el conteo de aforo ha sido auditado mediante el sistema Append-Only Event Ledger y visión artificial en boca-mina.
                    Cualquier discrepancia de personal en interior ha sido comunicada a la brigada de rescate minero y jefatura de seguridad.
                </p>

                <div class="signatures">
                    <div class="sig-box">
                        <strong>JEFE DE GUARDIA / TURNO</strong><br>
                        Firma y Sello de Conformidad
                    </div>
                    <div class="sig-box">
                        <strong>SUPERVISOR DE SEGURIDAD (SSOMA)</strong><br>
                        Firma y Sello de Auditoría
                    </div>
                </div>
            </div>
        </body>
        </html>
        """;

        return template
                .replace("{{EVENTO_ID}}", String.valueOf(evento.getEventoId() != null ? evento.getEventoId() : 0L))
                .replace("{{AUDITORIA_ID}}", String.valueOf(auditoria.getAuditoriaId() != null ? auditoria.getAuditoriaId() : 0L))
                .replace("{{TURNO_NOMBRE}}", nombreTurno)
                .replace("{{FECHA_AUDITORIA}}", fechaAuditoria)
                .replace("{{HORA_INICIO}}", inicio)
                .replace("{{HORA_FIN}}", fin)
                .replace("{{TOTAL_ENTRADAS}}", String.valueOf(entradas))
                .replace("{{TOTAL_SALIDAS}}", String.valueOf(salidas))
                .replace("{{DIFERENCIA}}", String.valueOf(diff))
                .replace("{{COLOR_BALANCE}}", colorBalance)
                .replace("{{ESTADO_BALANCE}}", estadoBalance);
    }

    private String escaparCsv(String valor) {
        if (valor == null) return "";
        String res = valor.replace("\"", "\"\"");
        if (res.contains(";") || res.contains("\n") || res.contains(",")) {
            return "\"" + res + "\"";
        }
        return res;
    }
}
