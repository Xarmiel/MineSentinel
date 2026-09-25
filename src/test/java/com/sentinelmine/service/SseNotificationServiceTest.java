package com.sentinelmine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class SseNotificationServiceTest {

    @Test
    @DisplayName("Debe registrar cliente SSE e incrementar el conteo de clientes activos")
    void testRegistrarClienteSSE() {
        SseNotificationService service = new SseNotificationService();

        assertEquals(0, service.getCantidadClientesConectados());
        SseEmitter emitter = service.registrarCliente();

        assertNotNull(emitter);
        assertEquals(1, service.getCantidadClientesConectados());
    }

    @Test
    @DisplayName("Debe emitir eventos sin lanzar excepciones cuando no hay clientes o con clientes conectados")
    void testEmitirEventos() {
        SseNotificationService service = new SseNotificationService();

        assertDoesNotThrow(() -> service.emitirEvento("aforo-update", "{\"aforo\": 10}"));

        service.registrarCliente();
        assertDoesNotThrow(() -> service.emitirEvento("alerta-nueva", "{\"faltaId\": 100}"));
    }
}
