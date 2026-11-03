package com.tranqui.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 8: con el flag suscripciones.externo, las rutas de suscripciones responden 409.
 */
class SuscripcionesExternoFilterTest {

    private static MockHttpServletResponse ejecutar(boolean externo, String metodo, String ruta) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new SuscripcionesExternoFilter(externo).doFilter(pedido, respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test
    void conElFlag_rutasSuscripcionesResponden409() throws Exception {
        for (String ruta : new String[]{
                "/api/subscriptions",
                "/api/subscriptions/plans",
                "/api/subscriptions/checkout",
                "/api/subscriptions/invoices/1",
                "/api/admin/subscriptions",
                "/api/admin/subscriptions/1/status",
                "/api/admin/subscriptions/reconcile"
        }) {
            MockHttpServletResponse r = ejecutar(true, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(409);
            assertThat(r.getContentAsString()).isEqualTo("Las suscripciones las administra el servicio de pagos.");
        }
    }

    @Test
    void conElFlag_lasDemasRutasPasan() throws Exception {
        for (String ruta : new String[]{
                "/api/medicos",
                "/api/turnos/1/cancelar",
                "/api/auth/login",
                "/api/internal/monolito/suscripciones/planes"
        }) {
            MockHttpServletResponse r = ejecutar(true, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(200);
        }
    }

    @Test
    void sinElFlag_todoPasa() throws Exception {
        for (String ruta : new String[]{
                "/api/subscriptions",
                "/api/subscriptions/plans",
                "/api/admin/subscriptions",
                "/api/medicos",
                "/api/turnos/1/cancelar"
        }) {
            MockHttpServletResponse r = ejecutar(false, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(200);
        }
    }
}
