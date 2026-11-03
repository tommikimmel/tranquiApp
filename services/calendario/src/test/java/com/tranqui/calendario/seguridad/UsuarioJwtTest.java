package com.tranqui.calendario.seguridad;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UsuarioJwtTest {

    @Test
    void tokenHs256_conHs256Rechazado_filtroDevuelve401() throws Exception {
        UsuarioJwt usuarioJwt = new UsuarioJwt(
                JwtTestHelper.CLAVE_PUBLICA_BASE64,
                JwtTestHelper.CLAVE_JWT,
                false
        );
        FiltroAutenticacionUsuario filtro = new FiltroAutenticacionUsuario(usuarioJwt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("Authorization", "Bearer " + JwtTestHelper.tokenHs256("usuario@test.com", "PACIENTE"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void tokenRs256_firmadoConOtraClave_filtroDevuelve401() throws Exception {
        UsuarioJwt usuarioJwt = new UsuarioJwt(
                JwtTestHelper.CLAVE_PUBLICA_BASE64,
                JwtTestHelper.CLAVE_JWT,
                true
        );
        FiltroAutenticacionUsuario filtro = new FiltroAutenticacionUsuario(usuarioJwt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("Authorization", "Bearer " + JwtTestHelper.tokenRs256OtraClave("usuario@test.com", "PACIENTE"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void tokenHs256_conHs256Aceptado_filtroPasaOk() throws Exception {
        UsuarioJwt usuarioJwt = new UsuarioJwt(
                JwtTestHelper.CLAVE_PUBLICA_BASE64,
                JwtTestHelper.CLAVE_JWT,
                true
        );
        FiltroAutenticacionUsuario filtro = new FiltroAutenticacionUsuario(usuarioJwt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("Authorization", "Bearer " + JwtTestHelper.tokenHs256("usuario@test.com", "PACIENTE"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void tokenRs256_valido_filtroPasaOk() throws Exception {
        UsuarioJwt usuarioJwt = new UsuarioJwt(
                JwtTestHelper.CLAVE_PUBLICA_BASE64,
                JwtTestHelper.CLAVE_JWT,
                true
        );
        FiltroAutenticacionUsuario filtro = new FiltroAutenticacionUsuario(usuarioJwt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("Authorization", "Bearer " + JwtTestHelper.tokenRs256("usuario@test.com", "PACIENTE", 42L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO)).isNotNull();
        UsuarioJwt.Usuario u = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        assertThat(u.email()).isEqualTo("usuario@test.com");
        assertThat(u.rol()).isEqualTo("PACIENTE");
        assertThat(u.uid()).isEqualTo(42L);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void arranque_soloConClavePublica_cuandoHs256Desactivado() {
        assertThatNoException().isThrownBy(() -> new UsuarioJwt(JwtTestHelper.CLAVE_PUBLICA_BASE64, "", false));
    }

    @Test
    void arranque_soloConSecretoHs256_cuandoHs256Activo() {
        assertThatNoException().isThrownBy(() -> new UsuarioJwt("", JwtTestHelper.CLAVE_JWT, true));
    }

    @Test
    void arranque_sinNingunaClave_falla() {
        assertThatThrownBy(() -> new UsuarioJwt("", "", true))
                .isInstanceOf(IllegalStateException.class);
    }
}
