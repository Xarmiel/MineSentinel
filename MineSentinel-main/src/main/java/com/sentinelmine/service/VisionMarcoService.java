package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Último frame anotado producido por el motor de visión.
 *
 * Existe para que la vista de cámara pueda mostrar el video con los recuadros
 * de gravedad SIN abrir la webcam del navegador. Eso resuelve un conflicto real:
 * la webcam sólo puede estar en un proceso a la vez, y si la ocupa el navegador
 * el motor de YOLOv8 se queda sin señal (y viceversa). Al ser el motor el
 * propietario de la cámara, él publica el resultado ya dibujado y el navegador
 * sólo lo pinta.
 *
 * Se guarda un único frame en memoria, no un histórico: es la imagen actual.
 */
@Service
public class VisionMarcoService {

    private static final Logger log = LoggerFactory.getLogger(VisionMarcoService.class);

    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final CuadroEppService cuadroEppService;

    @Value("${minesentinel.cuadro.expiracion-segundos:8.0}")
    private double expiracionSegundos = 8.0;

    private volatile byte[] ultimo;
    private volatile Instant recibido = Instant.EPOCH;

    public VisionMarcoService(CuadroEppService cuadroEppService) {
        this.cuadroEppService = cuadroEppService;
    }

    public void guardar(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            return;
        }
        if (jpeg.length > MAX_BYTES) {
            log.warn("Frame descartado: {} bytes excede el máximo permitido", jpeg.length);
            return;
        }
        this.ultimo = jpeg;
        this.recibido = Instant.now();
    }

    public byte[] obtener() {
        return vigente() ? ultimo : null;
    }

    public boolean vigente() {
        return ultimo != null
                && Instant.now().getEpochSecond() - recibido.getEpochSecond() <= expiracionSegundos;
    }

    public Map<String, Object> estado() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vigente", vigente());
        m.put("bytes", ultimo != null ? ultimo.length : 0);
        m.put("recibido", recibido.toString());
        m.put("msDesdeUltimoFrame", ultimo == null ? -1
                : Math.max(0, System.currentTimeMillis() - recibido.toEpochMilli()));
        m.put("cuadroVigente", cuadroEppService.hayCuadroVigente());
        return m;
    }
}
