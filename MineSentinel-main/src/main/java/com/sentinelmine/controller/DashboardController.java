package com.sentinelmine.controller;

import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final AforoService aforoService;
    private final AlertaService alertaService;
    private final EventoTurnoService eventoTurnoService;

    public DashboardController(AforoService aforoService,
                               AlertaService alertaService,
                               EventoTurnoService eventoTurnoService) {
        this.aforoService = aforoService;
        this.alertaService = alertaService;
        this.eventoTurnoService = eventoTurnoService;
    }

    @GetMapping("/")
    public String dashboard(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }

        String turnoDescripcion = eventoTurnoService.obtenerDescripcionTurnosActivos();
        model.addAttribute("turno", turnoDescripcion);
        model.addAttribute("aforo", aforoService.toDTO());
        model.addAttribute("alertas", alertaService.getAlertasRecientes(3));
        return "dashboard";
    }
}
