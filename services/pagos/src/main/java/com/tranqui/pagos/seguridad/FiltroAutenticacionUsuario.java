package com.tranqui.pagos.seguridad;

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
 * Sin token o con uno inválido: 401, como el monolito. Lo de {@code /api/payments} no lleva sesión, como en
 * el monolito: el webhook lo llama Mercado Pago (se valida con su firma), y la reserva y su pago no exigen
 * estar logueado (verificar y el link del checkout). El callback del OAuth tampoco: lo trae el navegador de
 * vuelta de Mercado Pago y se valida con la firma del state.
 */
@Component
public class FiltroAutenticacionUsuario extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tranqui.usuario";

    private static final java.util.Set<String> SIN_SESION = java.util.Set.of("/api/medicos/mercadopago/callback");

    private final UsuarioJwt usuarioJwt;

    public FiltroAutenticacionUsuario(UsuarioJwt usuarioJwt) {
        this.usuarioJwt = usuarioJwt;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/payments/") || "/api/subscriptions/plans".equals(uri) || SIN_SESION.contains(uri);
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
