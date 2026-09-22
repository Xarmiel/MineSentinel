package com.sentinelmine.controller;

import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistorialController {

    private final AlertaService alertaService;
    private final EventoTurnoService eventoTurnoService;

    public HistorialController(AlertaService alertaService,
                               EventoTurnoService eventoTurnoService) {
        this.alertaService = alertaService;
        this.eventoTurnoService = eventoTurnoService;
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
}
