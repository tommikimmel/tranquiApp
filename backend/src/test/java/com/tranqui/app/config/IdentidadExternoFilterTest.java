package com.tranqui.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 9: con el flag identidad.externo=true, las rutas de identidad y las escrituras internas
 * responden 409 con el mensaje correspondiente, mientras que las lecturas internas siguen pasando.
 */
class IdentidadExternoFilterTest {

    private static MockHttpServletResponse ejecutar(boolean externo, String metodo, String ruta) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new IdentidadExternoFilter(externo).doFilter(pedido, respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test
    void conElFlag_rutasIdentidadResponden409() throws Exception {
        for (String ruta : new String[]{
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/me",
                "/api/tickets",
                "/api/tickets/1",
                "/api/soporte/solicitud-datos",
                "/api/admin/users",
                "/api/admin/users/1/rol",
                "/api/admin/tickets",
                "/api/admin/tickets/1/estado",
                "/api/site-access",
                "/api/internal/novedades/disparar"
        }) {
            MockHttpServletResponse r = ejecutar(true, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(409);
            assertThat(r.getContentAsString()).isEqualTo("La identidad la administra el servicio de identidad.");
        }
    }

    @Test
    void conElFlag_escriturasInternasResponden409() throws Exception {
        MockHttpServletResponse postPacientes = ejecutar(true, "POST", "/internal/monolito/pacientes");
        assertThat(postPacientes.getStatus()).isEqualTo(409);
        assertThat(postPacientes.getContentAsString()).isEqualTo("La identidad la administra el servicio de identidad.");

        MockHttpServletResponse putPacientes = ejecutar(true, "PUT", "/internal/monolito/pacientes/5");
        assertThat(putPacientes.getStatus()).isEqualTo(409);
        assertThat(putPacientes.getContentAsString()).isEqualTo("La identidad la administra el servicio de identidad.");

        MockHttpServletResponse putProfIdentidad = ejecutar(true, "PUT", "/internal/monolito/profesionales/10/identidad");
        assertThat(putProfIdentidad.getStatus()).isEqualTo(409);
        assertThat(putProfIdentidad.getContentAsString()).isEqualTo("La identidad la administra el servicio de identidad.");
    }

    @Test
    void conElFlag_lecturasInternasPasan() throws Exception {
        for (String ruta : new String[]{
                "/internal/monolito/pacientes/5",
                "/internal/monolito/profesionales",
                "/internal/monolito/profesionales/10",
                "/internal/monolito/profesionales/estado",
                "/internal/monolito/identidad/usuarios"
        }) {
            MockHttpServletResponse r = ejecutar(true, "GET", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(200);
        }
    }

    @Test
    void sinElFlag_todoPasa() throws Exception {
        for (String ruta : new String[]{
                "/api/auth/login",
                "/api/tickets",
                "/api/soporte/solicitud-datos",
                "/api/admin/users",
                "/internal/monolito/pacientes",
                "/internal/monolito/profesionales/10/identidad"
        }) {
            MockHttpServletResponse r = ejecutar(false, "POST", ruta);
            assertThat(r.getStatus()).as(ruta).isEqualTo(200);
        }
    }
}
