package com.sentinelmine.entity.enums;

/**
 * Clasificación de una infracción de EPP reportada por el módulo de visión (YOLOv8).
 *
 * FALTANTE  el trabajador no porta un EPP que su rol exige.
 * IMPOSTOR  el trabajador porta un objeto no homologado que imita a un EPP
 *           (gorra en lugar de casco, gafas de sol, zapatillas deseportivas).
 *           Es más grave que FALTANTE: además del riesgo, hay un
 *           intento deliberado de hacer pasar un objeto no apto como seguro.
 */
public enum TipoInfraccionEPP {
    FALTANTE,
    IMPOSTOR
}
