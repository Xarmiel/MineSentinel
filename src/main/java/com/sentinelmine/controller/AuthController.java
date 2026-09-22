package com.sentinelmine.controller;

import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final EventoTurnoService eventoTurnoService;

    public AuthController(EventoTurnoService eventoTurnoService) {
        this.eventoTurnoService = eventoTurnoService;
    }

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String usuario,
                                 @RequestParam String password,
                                 HttpSession session,
                                 Model model) {
        if ("admin".equalsIgnoreCase(usuario) && "sentinel123".equals(password)) {
            session.setAttribute("usuario", usuario);
            session.setAttribute("turno", eventoTurnoService.obtenerDescripcionTurnosActivos());
            return "redirect:/";
        }
        model.addAttribute("error", "Usuario o contraseña incorrectos");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
