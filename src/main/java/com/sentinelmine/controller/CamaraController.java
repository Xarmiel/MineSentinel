package com.sentinelmine.controller;

import com.sentinelmine.model.Prioridad;
import com.sentinelmine.model.TipoAlerta;
import com.sentinelmine.service.AforoService;
import com.sentinelmine.service.AlertaService;
import com.sentinelmine.service.DeteccionEppService;
import com.sentinelmine.service.EventoTurnoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CamaraController {

    private final DeteccionEppService deteccionService;
    private final AforoService aforoService;
    private final AlertaService alertaService;
    private final EventoTurnoService eventoTurnoService;

    public CamaraController(DeteccionEppService deteccionService,
                            AforoService aforoService,
                            AlertaService alertaService,
                            EventoTurnoService eventoTurnoService) {
        this.deteccionService = deteccionService;
        this.aforoService = aforoService;
        this.alertaService = alertaService;
        this.eventoTurnoService = eventoTurnoService;
    }

    @GetMapping("/camara")
    public String vistaCamara(HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }
        model.addAttribute("turno", eventoTurnoService.obtenerDescripcionTurnosActivos());
        return "camara";
    }

    @PostMapping("/camara/simular")
    public String simularDeteccion(@RequestParam String codigoTrabajador, HttpSession session, Model model) {
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }

        DeteccionEppService.ResultadoDeteccion resultado = deteccionService.analizarFrame(codigoTrabajador);

        if (resultado.isEppCompleto()) {
            aforoService.registrarIngreso(codigoTrabajador, true);
        } else {
            aforoService.registrarIngreso(codigoTrabajador, false);
            alertaService.registrarAlerta(TipoAlerta.EPP_INCOMPLETO, codigoTrabajador,
                    "Sin " + resultado.getElementoFaltante() + " en el punto de control de ingreso.", Prioridad.MEDIA);
        }

        model.addAttribute("turno", eventoTurnoService.obtenerDescripcionTurnosActivos());
        model.addAttribute("resultado", resultado);
        return "camara";
    }
}
