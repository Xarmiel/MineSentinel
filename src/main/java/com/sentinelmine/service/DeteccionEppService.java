package com.sentinelmine.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * Simula la inferencia del modelo YOLOv8 sobre un frame de cámara.
 * En producción, este servicio se conecta al motor de inferencia real
 * (ONNX Runtime / servidor de modelos) en lugar de generar un resultado aleatorio.
 */
@Service
public class DeteccionEppService {

    private static final List<String> ELEMENTOS_EPP =
            List.of("Casco", "Chaleco reflectante", "Botas de seguridad", "Barbiquejo");

    private final Random random = new Random();

    public ResultadoDeteccion analizarFrame(String trabajadorCodigo) {
        boolean eppCompleto = random.nextInt(100) > 25; // 75% de probabilidad de cumplir
        String elementoFaltante = eppCompleto ? null : ELEMENTOS_EPP.get(random.nextInt(ELEMENTOS_EPP.size()));
        return new ResultadoDeteccion(trabajadorCodigo, eppCompleto, elementoFaltante);
    }

    public static class ResultadoDeteccion {

        private final String trabajadorCodigo;
        private final boolean eppCompleto;
        private final String elementoFaltante;

        public ResultadoDeteccion(String trabajadorCodigo, boolean eppCompleto, String elementoFaltante) {
            this.trabajadorCodigo = trabajadorCodigo;
            this.eppCompleto = eppCompleto;
            this.elementoFaltante = elementoFaltante;
        }

        public String getTrabajadorCodigo() {
            return trabajadorCodigo;
        }

        public boolean isEppCompleto() {
            return eppCompleto;
        }

        public String getElementoFaltante() {
            return elementoFaltante;
        }
    }
}
