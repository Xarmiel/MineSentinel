package com.sentinelmine.controller;

import com.sentinelmine.entity.CierreAuditoriaTurno;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.repository.CierreAuditoriaTurnoRepository;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import com.sentinelmine.service.ReporteExportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PanelAdminController {

    private final AforoService aforoService;
    private final AlertaService alertaService;
    private final EventoTurnoService eventoTurnoService;
    private final ReporteExportService reporteExportService;
    private final CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;

    public PanelAdminController(AforoService aforoService,
                                AlertaService alertaService,
                                EventoTurnoService eventoTurnoService,
                                ReporteExportService reporteExportService,
                                CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository,
                                EventoTurnoRepository eventoTurnoRepository) {
        this.aforoService = aforoService;
        this.alertaService = alertaService;
        this.eventoTurnoService = eventoTurnoService;
        this.reporteExportService = reporteExportService;
        this.cierreAuditoriaTurnoRepository = cierreAuditoriaTurnoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
    }

    @GetMapping("/panel-admin")
    public String panelAdmin(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", eventoTurnoService.obtenerDescripcionTurnosActivos());
        model.addAttribute("aforo", aforoService.toDTO());
        model.addAttribute("alertas", alertaService.getAlertasRecientes(5));
        return "panel-admin";
    }

    @PostMapping("/panel-admin/notificar")
    public String notificarJefeTurno(@RequestParam long idAlerta,
                                     @RequestParam(required = false, defaultValue = "EPP") String tipoAlerta,
                                     HttpSession session) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        alertaService.notificarJefeTurno(idAlerta, tipoAlerta);
        return "redirect:/panel-admin";
    }

    @GetMapping(value = "/panel-admin/turnos/{eventoId}/acta", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> verActaCierreTurno(@PathVariable Long eventoId, HttpSession session) {
        if (session.getAttribute("usuario") == null) {
            return ResponseEntity.status(401).body("<h2>Sesión no iniciada</h2>");
        }

        EventoTurno evento = eventoTurnoRepository.findByIdWithTurno(eventoId)
                .orElse(null);
        if (evento == null) {
            return ResponseEntity.notFound().build();
        }

        CierreAuditoriaTurno auditoria = cierreAuditoriaTurnoRepository.findAll().stream()
                .filter(a -> a.getEventoTurno() != null && a.getEventoTurno().getEventoId().equals(eventoId))
                .findFirst()
                .orElseGet(() -> new CierreAuditoriaTurno(evento, 0, 0, 0, java.time.LocalDateTime.now()));

        String html = reporteExportService.generarActaCierreTurnoHtml(auditoria, evento);
        return ResponseEntity.ok(html);
    }
}

