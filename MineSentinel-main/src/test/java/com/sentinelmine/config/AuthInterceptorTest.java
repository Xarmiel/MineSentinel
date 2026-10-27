package com.sentinelmine.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    private MockHttpServletResponse pedir(String uri, String rol) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (rol != null) {
            request.getSession(true).setAttribute("usuario", "u");
            request.getSession().setAttribute("rol", rol);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pasa = interceptor.preHandle(request, response, null);
        assertEquals(pasa, response.getRedirectedUrl() == null);
        return response;
    }

    @Test
    void supervisorSoloVeDashboardCamaraEHistorial() throws Exception {
        for (String uri : new String[]{"/", "/camara", "/historial", "/historial/exportar/csv"}) {
            assertNull(pedir(uri, "SUPERVISOR").getRedirectedUrl(), uri);
        }
        assertEquals("/", pedir("/panel-admin", "SUPERVISOR").getRedirectedUrl());
        assertEquals("/", pedir("/panel-admin/aforo", "SUPERVISOR").getRedirectedUrl());
        assertEquals("/", pedir("/historialx", "SUPERVISOR").getRedirectedUrl());
    }

    @Test
    void adminVeTodo() throws Exception {
        assertNull(pedir("/panel-admin", "ADMIN").getRedirectedUrl());
        assertNull(pedir("/panel-admin/aforo", "ADMIN").getRedirectedUrl());
    }

    @Test
    void rolDesconocidoQuedaRestringido() throws Exception {
        assertEquals("/", pedir("/panel-admin", "OTRO").getRedirectedUrl());
    }

    @Test
    void sinSesionVaAlLogin() throws Exception {
        assertEquals("/login", pedir("/historial", null).getRedirectedUrl());
    }
}
