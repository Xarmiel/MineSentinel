package com.sentinelmine.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class HistorialTemplateTest {

    private SpringTemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    private WebContext newWebContext() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        return new WebContext(exchange, Locale.forLanguageTag("es"));
    }

    @Test
    @DisplayName("La vista de historial debe renderizar filtros, columnas y paginación")
    void testRenderizaHistorialConDatos() {
        AlertaViewDTO alerta = new AlertaViewDTO(
                7L, "EPP", "Falta de EPP detectada", "14:30:00",
                "Sin casco en boca-mina", "16", "media", false, false,
                "/api/v1/evidencias/snap1.jpg", "RESUELTA");
        alerta.setFechaHora(LocalDateTime.of(2026, 10, 26, 14, 30, 0));
        alerta.setFechaFormateada("2026-10-26 14:30:00");
        alerta.setTrackId(16);
        alerta.setRolNombre("Operador de Maquinaria");
        alerta.setNivelConfianza(0.87f);
        alerta.setTurnoNombre("Guardia Día");

        HistorialFiltro filtro = new HistorialFiltro(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 26),
                "EPP", "RESUELTA", 16, "casco", 0, 25);
        HistorialPagina pagina = new HistorialPagina(List.of(alerta), 1, 0, 25);

        WebContext ctx = newWebContext();
        ctx.setVariable("turno", "Turno A · Guardia Día");
        ctx.setVariable("historial", pagina.getContent());
        ctx.setVariable("pagina", pagina);
        ctx.setVariable("filtro", filtro);

        String html = engine.process("historial", ctx);

        assertNotNull(html);
        assertTrue(html.contains("Historial de Alertas"));
        assertTrue(html.contains("Fecha y hora"));
        assertTrue(html.contains("2026-10-26 14:30:00"));
        assertTrue(html.contains("Track #16"));
        assertTrue(html.contains("Operador de Maquinaria"));
        assertTrue(html.contains("87%"));
        assertTrue(html.contains("RESUELTA"));
        assertTrue(html.contains("Exportar filtrado a CSV"));
        assertTrue(html.contains("paginacion"));
    }

    @Test
    @DisplayName("La vista de historial debe renderizar el estado vacío")
    void testRenderizaHistorialVacio() {
        HistorialFiltro filtro = new HistorialFiltro();
        HistorialPagina pagina = new HistorialPagina(List.of(), 0, 0, 25);

        WebContext ctx = newWebContext();
        ctx.setVariable("turno", "Turno A · Guardia Día");
        ctx.setVariable("historial", pagina.getContent());
        ctx.setVariable("pagina", pagina);
        ctx.setVariable("filtro", filtro);

        String html = engine.process("historial", ctx);

        assertTrue(html.contains("No hay alertas que coincidan con los filtros."));
        assertFalse(html.contains("class=\"paginacion\""));
    }
}
