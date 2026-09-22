package com.sentinelmine.service;

import com.sentinelmine.entity.CatalogoEPP;
import com.sentinelmine.repository.CatalogoEPPRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * Servicio de detección de EPP conectado con el catálogo relacional de la base de datos.
 * Simula y procesa la inferencia del modelo YOLOv8 sobre un frame de cámara.
 */
@Service
public class DeteccionEppService {

    private final CatalogoEPPRepository catalogoEPPRepository;
    private final Random random = new Random();

    public DeteccionEppService(CatalogoEPPRepository catalogoEPPRepository) {
        this.catalogoEPPRepository = catalogoEPPRepository;
    }

    public ResultadoDeteccion analizarFrame(String trabajadorCodigo) {
        List<CatalogoEPP> listaEpp = catalogoEPPRepository.findAll();
        boolean eppCompleto = random.nextInt(100) > 25; // 75% probabilidad de cumplimiento en simulación

        String elementoFaltante = null;
        if (!eppCompleto) {
            if (!listaEpp.isEmpty()) {
                elementoFaltante = listaEpp.get(random.nextInt(listaEpp.size())).getNombre();
            } else {
                elementoFaltante = "Casco de Seguridad con Barbiquejo";
            }
        }

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
