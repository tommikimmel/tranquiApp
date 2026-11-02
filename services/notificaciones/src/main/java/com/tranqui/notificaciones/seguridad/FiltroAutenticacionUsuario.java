package com.tranqui.notificaciones.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Exige un JWT de usuario válido en {@code /api/**} y deja el usuario como atributo del pedido.
 * Sin token o con uno inválido: 401, como el monolito.
 */
@Component
public class FiltroAutenticacionUsuario extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tranqui.usuario";

    private final UsuarioJwt usuarioJwt;

    public FiltroAutenticacionUsuario(UsuarioJwt usuarioJwt) {
        this.usuarioJwt = usuarioJwt;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<UsuarioJwt.Usuario> usuario = usuarioJwt.extraerToken(request).flatMap(usuarioJwt::validar);
        if (usuario.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(ATRIBUTO_USUARIO, usuario.get());
        chain.doFilter(request, response);
    }
}
