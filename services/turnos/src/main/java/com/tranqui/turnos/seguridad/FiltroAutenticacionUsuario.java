package com.tranqui.turnos.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Exige un JWT de usuario válido en {@code /api/**} y deja el usuario como atributo del pedido (sin token o
 * con uno inválido: 401). Las mismas rutas públicas que el monolito ({@code SecurityConfig}): los horarios
 * libres y el conteo de la home, la reserva y su abandono (no exigen cuenta), y los dos links del mail de
 * asistencia (con su token).
 */
@Component
public class FiltroAutenticacionUsuario extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tranqui.usuario";

    private static final Pattern PUBLICAS = Pattern.compile(
            "^/api/(medicos/[^/]+/turnos-disponibles|medicos/turnos-disponibles-conteo|turnos/reservar"
                    + "|turnos/[^/]+/(abandonar-pago|confirmar-asistencia|no-asistira))$");

    private final UsuarioJwt usuarioJwt;

    public FiltroAutenticacionUsuario(UsuarioJwt usuarioJwt) {
        this.usuarioJwt = usuarioJwt;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || PUBLICAS.matcher(uri).matches();
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
