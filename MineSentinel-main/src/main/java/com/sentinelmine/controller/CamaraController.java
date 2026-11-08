package com.sentinelmine.controller;

import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpServletResponse;
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
    public String vistaCamara(HttpSession session, Model model, HttpServletResponse response) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        // Aislamiento entre orígenes: habilita SharedArrayBuffer y con ello WASM multihilo de onnxruntime
        // (~4x más rápido sin GPU). "credentialless" deja cargar los scripts de CDN sin cabeceras CORP.
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Embedder-Policy", "credentialless");
        model.addAttribute("turno", eventoTurnoService.obtenerTurnoTopbar());
        return "camara";
    }
}
