package com.sentinelmine.controller;

import com.sentinelmine.service.AlertaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistorialController {

    private final AlertaService alertaService;

    public HistorialController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping("/historial")
    public String historial(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", session.getAttribute("turno"));
        model.addAttribute("historial", alertaService.getHistorialCompleto());
        return "historial";
    }
}
