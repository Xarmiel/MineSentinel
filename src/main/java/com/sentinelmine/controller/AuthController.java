package com.sentinelmine.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String usuario,
                                 @RequestParam String password,
                                 HttpSession session,
                                 Model model) {
        // Autenticación simplificada, suficiente para un prototipo académico
        if ("admin".equalsIgnoreCase(usuario) && "sentinel123".equals(password)) {
            session.setAttribute("usuario", usuario);
            session.setAttribute("turno", "Turno A · Guardia Noche");
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
