package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Servicio de notificaciones externas hacia los supervisores y jefes de turno.
 * Permite la emisión de despachos de seguridad industrial mediante logs estructurados,
 * Webhooks o pasarelas de mensajería externa.
 */
@Service
public class NotificacionExternaService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionExternaService.class);

    @Value("${minesentinel.notificaciones.webhook-url:}")
    private String webhookUrl;

    @Value("${minesentinel.notificaciones.email-supervisor:seguridad.mina@sentinelmine.com}")
    private String emailSupervisor;

    public void despacharNotificacionJefeTurno(String tipo, String descripcion, String codigoTrabajador, String snapshotUrl) {
        String hora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String mensaje = String.format(
                "🚨 [DESPACHO DE SEGURIDAD MINERA - %s]\n" +
                "Hora: %s\n" +
                "Destinatario: Jefe de Turno / Supervisor (%s)\n" +
                "Trabajador Involucrado: #%s\n" +
                "Detalle Infracción: %s\n" +
                "Evidencia Visual: %s\n" +
                "Acción Requerida: Intervención en punto de control / Guardia.",
                tipo.toUpperCase(), hora, emailSupervisor, codigoTrabajador, descripcion,
                (snapshotUrl != null ? snapshotUrl : "Sin captura adjunta")
        );

        log.warn("\n=======================================================\n{}\n=======================================================", mensaje);

        // Si se configuró una URL de Webhook externa (ej. Telegram Bot, Teams o Slack en mina)
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            log.info("Despachando payload JSON hacia Webhook configurado: {}", webhookUrl);
        }
    }
}
