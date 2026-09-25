package com.sentinelmine.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor de seguridad que asegura que todas las rutas web requieran una sesión activa,
 * redirigiendo a /login si el usuario no está autenticado.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // Rutas públicas y de estáticos / API REST
        if (uri.startsWith("/login") || uri.startsWith("/logout") ||
            uri.startsWith("/css") || uri.startsWith("/js") || uri.startsWith("/images") ||
            uri.startsWith("/api/v1") || uri.equals("/favicon.ico")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("usuario") == null) {
            response.sendRedirect("/login");
            return false;
        }

        return true;
    }
}
