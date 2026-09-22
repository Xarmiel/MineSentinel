package com.sentinelmine.controller;

import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final AforoService aforoService;
    private final AlertaService alertaService;

    public DashboardController(AforoService aforoService, AlertaService alertaService) {
        this.aforoService = aforoService;
        this.alertaService = alertaService;
    }

    @GetMapping("/")
    public String dashboard(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", session.getAttribute("turno"));
        model.addAttribute("aforo", aforoService.toDTO());
        model.addAttribute("alertas", alertaService.getAlertasRecientes(3));
        return "dashboard";
    }
}
