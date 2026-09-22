package com.sentinelmine.controller;

import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PanelAdminController {

    private final AforoService aforoService;
    private final AlertaService alertaService;
    private final EventoTurnoService eventoTurnoService;

    public PanelAdminController(AforoService aforoService,
                                AlertaService alertaService,
                                EventoTurnoService eventoTurnoService) {
        this.aforoService = aforoService;
        this.alertaService = alertaService;
        this.eventoTurnoService = eventoTurnoService;
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
}
