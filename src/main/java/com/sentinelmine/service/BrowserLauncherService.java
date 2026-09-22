package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.URI;

/**
 * Componente que abre automáticamente el navegador web predeterminado
 * con la URL de la aplicación una vez que Spring Boot ha arrancado satisfactoriamente.
 */
@Service
public class BrowserLauncherService {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncherService.class);

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${minesentinel.browser.auto-launch:true}")
    private boolean autoLaunch;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoLaunch) {
            log.info("Lanzamiento automático de navegador deshabilitado por configuración.");
            return;
        }

        String url = "http://localhost:" + serverPort;
        log.info("====================================================================");
        log.info("🚀 MineSentinel iniciado correctamente.");
        log.info("🌐 Abriendo interfaz de usuario en: {}", url);
        log.info("====================================================================");

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("✅ Navegador abierto exitosamente mediante java.awt.Desktop");
            } else {
                abrirNavegadorPorComando(url);
            }
        } catch (Exception e) {
            log.warn("No se pudo abrir vía java.awt.Desktop ({}), intentando fallback por comando de SO...", e.getMessage());
            abrirNavegadorPorComando(url);
        }
    }

    private void abrirNavegadorPorComando(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else if (os.contains("nix") || os.contains("nux")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
            log.info("✅ Navegador abierto mediante comando del sistema operativo.");
        } catch (Exception ex) {
            log.error("❌ No fue posible abrir el navegador automáticamente: {}", ex.getMessage());
        }
    }
}
