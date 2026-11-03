package com.tranqui.pagos.seguridad;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fase 10, V2: el uid del token se cruza con el id que la copia tiene para ese mail. */
class FiltroCruceUidTest {

    private static final String MAIL = "ana@mail.com";

    private final UsuarioJwt usuarioJwt = new UsuarioJwt(JwtTestHelper.CLAVE_PUBLICA_BASE64, JwtTestHelper.CLAVE_JWT, true);
    private final com.tranqui.pagos.datos.Participantes copia = mock(com.tranqui.pagos.datos.Participantes.class);

    private int estado(String token, boolean cruzar, FilterChain chain) throws Exception {
        FiltroAutenticacionUsuario filtro = new FiltroAutenticacionUsuario(usuarioJwt, copia, cruzar);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cualquiera");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, chain);
        return response.getStatus();
    }

    private void enCopia(long id) {
        com.tranqui.pagos.datos.Participantes.Participante fila = mock(com.tranqui.pagos.datos.Participantes.Participante.class);
        when(fila.id()).thenReturn(id);
        when(copia.porEmail(MAIL)).thenReturn(Optional.of(fila));
    }

    @Test
    void uidQueCoincideConLaCopia_pasa() throws Exception {
        enCopia(5L);
        FilterChain chain = mock(FilterChain.class);
        assertThat(estado(JwtTestHelper.tokenRs256(MAIL, "PSIQUIATRA", 5L), true, chain)).isEqualTo(200);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void uidDistintoAlDeLaCopia_401() throws Exception {
        enCopia(5L);
        FilterChain chain = mock(FilterChain.class);
        assertThat(estado(JwtTestHelper.tokenRs256(MAIL, "PSIQUIATRA", 7L), true, chain)).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void usuarioQueTodaviaNoEstaEnLaCopia_pasa() throws Exception {
        when(copia.porEmail(anyString())).thenReturn(Optional.empty());
        FilterChain chain = mock(FilterChain.class);
        assertThat(estado(JwtTestHelper.tokenRs256(MAIL, "PSIQUIATRA", 7L), true, chain)).isEqualTo(200);
    }

    @Test
    void conElCruceApagado_noSeMiraLaCopia() throws Exception {
        enCopia(5L);
        FilterChain chain = mock(FilterChain.class);
        assertThat(estado(JwtTestHelper.tokenRs256(MAIL, "PSIQUIATRA", 7L), false, chain)).isEqualTo(200);
    }

    @Test
    void tokenViejoSinUid_pasa() throws Exception {
        enCopia(5L);
        FilterChain chain = mock(FilterChain.class);
        assertThat(estado(JwtTestHelper.tokenHs256(MAIL, "PSIQUIATRA"), true, chain)).isEqualTo(200);
    }
}
