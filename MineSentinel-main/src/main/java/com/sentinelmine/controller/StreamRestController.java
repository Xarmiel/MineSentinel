package com.sentinelmine.controller;

import com.sentinelmine.service.SseNotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controlador REST para el streaming de eventos en tiempo real hacia los navegadores web (Server-Sent Events).
 */
@RestController
@RequestMapping("/api/v1/stream")
@CrossOrigin(origins = "*")
public class StreamRestController {

    private final SseNotificationService sseNotificationService;

    public StreamRestController(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suscribirseAEventos() {
        return sseNotificationService.registrarCliente();
    }
}
