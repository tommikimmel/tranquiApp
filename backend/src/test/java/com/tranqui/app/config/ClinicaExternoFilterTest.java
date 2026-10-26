package com.tranqui.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Fase 7: con el flag clinica.externo, las rutas de historia clínica, recetas y conceptos responden 409. */
class ClinicaExternoFilterTest {

    private static MockHttpServletResponse ejecutar(boolean externo, String metodo, String ruta) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new ClinicaExternoFilter(externo).doFilter(pedido, respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test
    void conElFlag_rutasClinicaResponden409() throws Exception {
        for (String ruta : new String[]{
                "/api/pacientes/atendidos",
                "/api/pacientes/5/informes/3",
                "/api/pacientes/me/seguimientos",
                "/api/recetas/financiadores",
                "/api/conceptos/solicitar"
        }) {
            MockHttpServletResponse r = ejecutar(true, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(409);
            assertThat(r.getContentAsString()).isEqualTo("La historia clínica y las recetas las administra el servicio de profesionales.");
        }
    }

    @Test
    void conElFlag_lasDemasRutasPasan() throws Exception {
        for (String ruta : new String[]{
                "/api/pacientes/me/turnos",
                "/api/turnos/1/cancelar",
                "/api/medicos/1"
        }) {
            MockHttpServletResponse r = ejecutar(true, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(200);
        }
    }

    @Test
    void sinElFlag_todoPasa() throws Exception {
        for (String ruta : new String[]{
                "/api/pacientes/atendidos",
                "/api/pacientes/5/informes/3",
                "/api/pacientes/me/seguimientos",
                "/api/recetas/financiadores",
                "/api/conceptos/solicitar",
                "/api/pacientes/me/turnos"
        }) {
            MockHttpServletResponse r = ejecutar(false, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(200);
        }
    }
}
