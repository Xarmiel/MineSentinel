package com.sentinelmine.controller;

import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import com.sentinelmine.service.ReporteExportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistorialController {

    private final AlertaService alertaService;
    private final EventoTurnoService eventoTurnoService;
    private final ReporteExportService reporteExportService;

    public HistorialController(AlertaService alertaService,
                               EventoTurnoService eventoTurnoService,
                               ReporteExportService reporteExportService) {
        this.alertaService = alertaService;
        this.eventoTurnoService = eventoTurnoService;
        this.reporteExportService = reporteExportService;
    }

    @GetMapping("/historial")
    public String historial(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", eventoTurnoService.obtenerDescripcionTurnosActivos());
        model.addAttribute("historial", alertaService.getHistorialCompleto());
        return "historial";
    }

    @GetMapping("/historial/exportar/csv")
    public ResponseEntity<byte[]> exportarCsvHistorial(HttpSession session) {
        if (session.getAttribute("usuario") == null) {
            return ResponseEntity.status(401).build();
        }
        byte[] csvData = reporteExportService.generarCsvHistorialAlertas(alertaService.getHistorialCompleto());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial_alertas_minesentinel.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}

