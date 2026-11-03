package com.tranqui.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Fase 6: con el flag, las rutas de turnos que lleguen al monolito responden 409; las demás pasan. */
class TurnosExternoFilterTest {

    private static int estado(boolean externo, String metodo, String ruta) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new TurnosExternoFilter(externo).doFilter(pedido, respuesta, new MockFilterChain());
        return respuesta.getStatus();
    }

    @Test
    void conElFlag_lasRutasDeTurnosResponden409() throws Exception {
        for (String ruta : new String[]{"/api/turnos/reservar", "/api/turnos/15/cancelar", "/api/turnos/15/confirmar-asistencia",
                "/api/medicos/turnos", "/api/medicos/turnos/hoy", "/api/medicos/turnos-disponibles-conteo",
                "/api/medicos/16/turnos-disponibles", "/api/pacientes/me/turnos"}) {
            assertThat(estado(true, "GET", ruta)).as(ruta).isEqualTo(409);
        }
    }

    @Test
    void conElFlag_lasDemasRutasPasan() throws Exception {
        for (String ruta : new String[]{"/api/medicos/stats", "/api/medicos/perfil", "/api/pacientes/me/seguimientos",
                "/api/pacientes/16/informes", "/api/turnosx", "/internal/turnos/turnos/1/enlaces-asistencia"}) {
            assertThat(estado(true, "GET", ruta)).as(ruta).isEqualTo(200);
        }
    }

    @Test
    void sinElFlag_todoPasa() throws Exception {
        assertThat(estado(false, "POST", "/api/turnos/reservar")).isEqualTo(200);
        assertThat(estado(false, "GET", "/api/pacientes/me/turnos")).isEqualTo(200);
    }
}
