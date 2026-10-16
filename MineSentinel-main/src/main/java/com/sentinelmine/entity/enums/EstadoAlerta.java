package com.sentinelmine.entity.enums;

/**
 * Estados de atención o resolución de una anomalía o falta de EPP detectada.
 */
public enum EstadoAlerta {
    PENDIENTE,
    NOTIFICADA,
    REVISADA,
    DESCARTADA,
    /**
     * Cerrada automáticamente por el motor de visión: el trabajador volvió a
     * portar el EPP que le faltaba. Distinta de REVISADA para no mezclar la
     * resolución automática con la revisión humana.
     */
    RESUELTA
}
