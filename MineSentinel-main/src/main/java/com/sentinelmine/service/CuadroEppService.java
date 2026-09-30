package com.sentinelmine.service;

import com.sentinelmine.dto.request.CuadroEPPRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado en vivo del cuadro de cumplimiento de EPP que se pinta en la vista de
 * cámara.
 *
 * Deliberadamente NO se persiste: es una foto del presente. Guardar cada cuadro
 * en base de datos produciría decenas de filas por minuto que nadie consulta
 * (el histórico con valor es el de las infracciones, en `faltas_epp`). Aquí sólo
 * se retiene el último cuadro, y se considera vencido si el motor deja de
 * publicar, para que la vista no muestre recuadros de gente que ya no está.
 */
@Service
public class CuadroEppService {

    private static final Logger log = LoggerFactory.getLogger(CuadroEppService.class);

    private final SseNotificationService sseNotificationService;

    @Value("${minesentinel.cuadro.expiracion-segundos:8.0}")
    private double expiracionSegundos = 8.0;

    private final AtomicReference<Instant> publicado = new AtomicReference<>(Instant.EPOCH);
    private volatile CuadroEPPRequestDTO ultimo;

    public CuadroEppService(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    /**
     * Registra el cuadro recibido y lo reemite a los clientes SSE.
     */
    public void publicar(CuadroEPPRequestDTO cuadro) {
        this.ultimo = cuadro;
        this.publicado.set(Instant.now());

        int personas = cuadro.getPersonas() != null ? cuadro.getPersonas().size() : 0;
        sseNotificationService.emitirEvento("cuadro-epp", envolver(cuadro, Instant.now()));

        log.debug("Cuadro publicado: {} persona(s), rol {}", personas, cuadro.getNombreRol());
    }

    /**
     * Devuelve el último cuadro vigente, o un cuadro vacío si el motor dejó de
     * publicar. Nunca devuelve datos rancios: es preferible limpiar la vista a
     * mostrar a alguien que ya salió de cámara.
     */
    public Map<String, Object> obtenerVigente() {
        CuadroEPPRequestDTO actual = this.ultimo;
        if (actual == null || expirado()) {
            return envolver(new CuadroEPPRequestDTO(), Instant.now());
        }
        return envolver(actual, publicado.get());
    }

    public boolean hayCuadroVigente() {
        return this.ultimo != null && !expirado();
    }

    private boolean expirado() {
        return Instant.now().getEpochSecond() - publicado.get().getEpochSecond() > expiracionSegundos;
    }

    private Map<String, Object> envolver(CuadroEPPRequestDTO cuadro, Instant instante) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rolId", cuadro.getRolId());
        payload.put("nombreRol", cuadro.getNombreRol());
        payload.put("fotograma", cuadro.getFotograma());
        payload.put("fps", cuadro.getFps());
        payload.put("dimensiones", cuadro.getDimensiones());
        payload.put("personas", cuadro.getPersonas() != null ? cuadro.getPersonas() : List.of());
        payload.put("simulacion", Boolean.TRUE.equals(cuadro.getSimulacion()));
        payload.put("servidor", LocalDateTime.now().toString());
        payload.put("vigente", hayCuadroVigente());
        return payload;
    }
}
