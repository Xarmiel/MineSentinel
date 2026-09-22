package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio de apertura automática del navegador web al iniciar Spring Boot.
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
            return;
        }

        String url = "http://localhost:" + serverPort;
        log.info("====================================================================");
        log.info("🚀 MineSentinel iniciado.");
        log.info("🌐 Abriendo interfaz de usuario en: {}", url);
        log.info("====================================================================");

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                abrirNavegador(url);
            } catch (Exception e) {
                log.warn("Aviso en lanzamiento de navegador: {}", e.getMessage());
            }
        });
    }

    private void abrirNavegador(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();

        // 1. En Windows, ejecutar comando de sistema nativo (100% confiable)
        if (os.contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "start", url).start();
                log.info("✅ Navegador abierto exitosamente.");
                return;
            } catch (Exception ignored) {
            }
        }

        // 2. Intento mediante Desktop API
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless() &&
                Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {
        }

        // 3. Fallbacks alternativos
        try {
            if (os.contains("win")) {
                new ProcessBuilder("powershell", "-Command", "Start-Process '" + url + "'").start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception ex) {
            log.warn("No se pudo abrir automáticamente el navegador: {}", ex.getMessage());
        }
    }
}
