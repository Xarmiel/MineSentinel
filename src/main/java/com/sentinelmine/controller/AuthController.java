package com.sentinelmine.controller;

import com.sentinelmine.entity.Usuario;
import com.sentinelmine.repository.UsuarioRepository;
import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventoTurnoService eventoTurnoService;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          EventoTurnoService eventoTurnoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
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
        String usernameTrim = (usuario != null) ? usuario.trim() : "";
        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsername(usernameTrim);

        if (usuarioOpt.isPresent()) {
            Usuario user = usuarioOpt.get();

            if (!Boolean.TRUE.equals(user.getEstadoActivo())) {
                model.addAttribute("error", "El usuario se encuentra inactivo en el sistema.");
                return "login";
            }

            if (passwordEncoder.matches(password, user.getPassword())) {
                session.setAttribute("usuario", user.getUsername());
                session.setAttribute("nombreCompleto", user.getNombreCompleto());
                session.setAttribute("rol", user.getRol());
                session.setAttribute("turno", eventoTurnoService.obtenerDescripcionTurnosActivos());
                return "redirect:/";
            }
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
