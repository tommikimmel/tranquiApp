package com.tranqui.app.controller;

import com.tranqui.app.service.novedades.NovedadesService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NovedadesInternoControllerTest {

    private NovedadesInternoController controller(String token) {
        NovedadesInternoController c = new NovedadesInternoController(mock(NovedadesService.class));
        ReflectionTestUtils.setField(c, "token", token);
        return c;
    }

    private MockHttpServletRequest pedido(String token) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        if (token != null) r.addHeader("X-Novedades-Token", token);
        return r;
    }

    @Test
    void conElTokenCorrectoDesdeElServidorEstaAutorizado() {
        assertTrue(controller("secreto").autorizado(pedido("secreto")));
    }

    @Test
    void sinTokenConfiguradoNadieEstaAutorizado() {
        assertFalse(controller("").autorizado(pedido("")));
        assertFalse(controller(null).autorizado(pedido(null)));
    }

    @Test
    void tokenIncorrectoOAusenteNoEstaAutorizado() {
        assertFalse(controller("secreto").autorizado(pedido("otro")));
        assertFalse(controller("secreto").autorizado(pedido(null)));
    }

    @Test
    void unPedidoQuePasoPorTraefikNoEstaAutorizadoAunqueTengaElToken() {
        MockHttpServletRequest r = pedido("secreto");
        r.addHeader("X-Forwarded-For", "1.2.3.4");
        assertFalse(controller("secreto").autorizado(r));
    }
}
