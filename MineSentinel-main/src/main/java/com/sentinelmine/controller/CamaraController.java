package com.sentinelmine.controller;

import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CamaraController {

    private final EventoTurnoService eventoTurnoService;

    public CamaraController(EventoTurnoService eventoTurnoService) {
        this.eventoTurnoService = eventoTurnoService;
    }

    @GetMapping("/camara")
    public String vistaCamara(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", eventoTurnoService.obtenerTurnoTopbar());
        return "camara";
    }
}
