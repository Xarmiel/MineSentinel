package com.sentinelmine.controller;

import com.sentinelmine.dto.HistorialFiltro;
import com.sentinelmine.dto.HistorialPagina;
import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import com.sentinelmine.service.ReporteExportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

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
    public String historial(
            HttpSession session,
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer trackId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }

        HistorialFiltro filtro = new HistorialFiltro(desde, hasta, tipo, estado, trackId, q, page, size);
        HistorialPagina pagina = alertaService.getHistorial(filtro);

        model.addAttribute("turno", eventoTurnoService.obtenerTurnoTopbar());
        model.addAttribute("historial", pagina.getContent());
        model.addAttribute("pagina", pagina);
        model.addAttribute("filtro", filtro);
        return "historial";
    }

    @GetMapping("/historial/exportar/csv")
    public ResponseEntity<byte[]> exportarCsvHistorial(
            HttpSession session,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer trackId,
            @RequestParam(required = false) String q) {

        if (session.getAttribute("usuario") == null) {
            return ResponseEntity.status(401).build();
        }

        HistorialFiltro filtro = new HistorialFiltro(desde, hasta, tipo, estado, trackId, q, 0,
                HistorialFiltro.PAGE_SIZE_MAX);
        byte[] csvData = reporteExportService.generarCsvHistorialAlertas(
                alertaService.getHistorialParaExportar(filtro));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial_alertas_minesentinel.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}
