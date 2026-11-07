package com.sentinelmine.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_API_KEY = "X-API-Key";

    private final byte[] apiKey;
    private final boolean apiKeyRequerida;

    public AuthInterceptor(@Value("${minesentinel.api.key:}") String apiKey,
                           @Value("${minesentinel.api.key-requerida:false}") boolean apiKeyRequerida) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.apiKeyRequerida = apiKeyRequerida;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/login") || uri.startsWith("/logout") ||
            uri.startsWith("/css") || uri.startsWith("/js") || uri.startsWith("/images") ||
            uri.equals("/favicon.ico")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (uri.startsWith("/api/v1")) {
            return apiPermitida(request, session, response);
        }

        if (session == null || session.getAttribute("usuario") == null) {
            response.sendRedirect("/login");
            return false;
        }

        if (!ROL_ADMIN.equals(session.getAttribute("rol")) && !permitidoSupervisor(uri)) {
            response.sendRedirect("/");
            return false;
        }

        return true;
    }

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_SUPERVISOR = "SUPERVISOR";

    // El navegador usa la API con su sesión; el cliente Python y los scripts, con la clave.
    private boolean apiPermitida(HttpServletRequest request, HttpSession session, HttpServletResponse response) throws Exception {
        if (session != null && session.getAttribute("usuario") != null) {
            return true;
        }
        if (apiKey.length == 0 && !apiKeyRequerida) {
            return true;
        }
        String enviada = request.getHeader(HEADER_API_KEY);
        if (apiKey.length > 0 && enviada != null
                && MessageDigest.isEqual(apiKey, enviada.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"API key inválida o ausente\"}");
        return false;
    }

    static boolean permitidoSupervisor(String uri) {
        return uri.equals("/") || uri.equals("/error")
                || uri.equals("/camara") || uri.startsWith("/camara/")
                || uri.equals("/historial") || uri.startsWith("/historial/");
    }
}
