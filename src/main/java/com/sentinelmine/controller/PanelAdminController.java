package com.sentinelmine.controller;

import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
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

    public PanelAdminController(AforoService aforoService, AlertaService alertaService) {
        this.aforoService = aforoService;
        this.alertaService = alertaService;
    }

    @GetMapping("/panel-admin")
    public String panelAdmin(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", session.getAttribute("turno"));
        model.addAttribute("aforo", aforoService.toDTO());
        model.addAttribute("alertas", alertaService.getAlertasRecientes(5));
        return "panel-admin";
    }

    @PostMapping("/panel-admin/notificar")
    public String notificarJefeTurno(@RequestParam int idAlerta, HttpSession session) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        alertaService.notificarJefeTurno(idAlerta);
        return "redirect:/panel-admin";
    }
}
