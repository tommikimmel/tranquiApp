package com.tranqui.identidad.seguridad;

import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Filtro de autenticación para las rutas {@code /api/**} de identidad (Fase 9, I2b).
 * Valida el JWT (RS256 o HS256 si está habilitado) y busca el usuario en la base en cada pedido.
 * Si no existe o tiene cuenta_eliminada=true, responde 401 Unauthorized.
 */
@Component
@Order(10)
public class FiltroAutenticacionUsuario extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tranqui.usuario";
    public static final String COOKIE_SESION = "SESSION-TOKEN";

    private static final Set<String> RUTAS_PUBLICAS = Set.of(
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/resend-code",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/login",
            "/api/auth/google"
    );

    private final VerificadorJwtUsuario verificador;
    private final Usuarios usuarios;

    public FiltroAutenticacionUsuario(VerificadorJwtUsuario verificador, Usuarios usuarios) {
        this.verificador = verificador;
        this.usuarios = usuarios;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        return RUTAS_PUBLICAS.contains(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> tokenOpt = extraerToken(request);
        if (tokenOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Optional<UsuarioToken> tokenValido = verificador.validar(tokenOpt.get());
        if (tokenValido.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String email = tokenValido.get().email();
        Optional<Usuario> usuarioOpt = usuarios.buscarPorEmail(email);
        if (usuarioOpt.isEmpty() || usuarioOpt.get().isCuentaEliminada()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        request.setAttribute(ATRIBUTO_USUARIO, usuarioOpt.get());
        filterChain.doFilter(request, response);
    }

    public static Optional<String> extraerToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_SESION.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return Optional.of(cookie.getValue().trim());
                }
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isBlank()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }
}
