package com.sentinelmine.entity.enums;

/**
 * Naturaleza de un elemento del catálogo de EPP.
 *
 * CONFORME  EPP homologado y obligatorio para el cumplimiento normativo.
 * IMPOSTOR  objeto que se hace pasar por un EPP pero no lo es
 *           (gorra, gafas de sol, calzado civil). Genera alertas más graves.
 */
public enum TipoCatalogoEPP {
    CONFORME,
    IMPOSTOR
}
