package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter registrarCliente() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        this.emitters.add(emitter);
        log.info("Nuevo cliente SSE suscrito. Total clientes activos: {}", emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("Conexión SSE completada. Clientes restantes: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
            log.debug("Conexión SSE expirada por inactividad. Clientes restantes: {}", emitters.size());
        });

        emitter.onError((e) -> {
            emitters.remove(emitter);
            log.debug("Error en conexión SSE (cliente desconectado): {}", e.getMessage());
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("{\"status\":\"CONNECTED\",\"message\":\"SentinelMine Realtime Stream Activo\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void emitirEvento(String nombreEvento, Object data) {
        if (emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> muertos = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(nombreEvento)
                        .data(data));
            } catch (Exception e) {
                muertos.add(emitter);
            }
        }

        if (!muertos.isEmpty()) {
            emitters.removeAll(muertos);
            log.debug("Eliminados {} emisores SSE desconectados. Restantes: {}", muertos.size(), emitters.size());
        }
    }

    public int getCantidadClientesConectados() {
        return emitters.size();
    }
}
