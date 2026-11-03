package com.tranqui.notificaciones.seguridad;

import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Valida el JWT de usuario que emite identidad (RS256 post-corte o HS256 histórico del monolito).
 * Durante la transición de siete días se aceptan ambos formatos si {@code jwt.hs256-aceptado=true}.
 * El servicio arranca si hay al menos una clave usable configurada (pública RS256 o secreto HS256).
 *
 * <p>El token incluye el {@code uid} del usuario, pero el servicio sigue buscando al usuario por
 * email en su copia local. Usar directamente el {@code uid} del token queda para la Fase 10.
 */
@Component
public class UsuarioJwt {

    public static final String COOKIE_SESION = "SESSION-TOKEN";

    /**
     * Usuario autenticado: email, rol y el id numérico del usuario (uid).
     * El {@code uid} puede ser null en tokens emitidos antes del corte (HS256).
     */
    public record Usuario(String email, String rol, Long uid) {
        public Usuario(String email, String rol) {
            this(email, rol, null);
        }
    }

    private final VerificadorJwtUsuario verificador;

    @Autowired
    public UsuarioJwt(
            @Value("${jwt.public-key:}") String clavePublica,
            @Value("${jwt.secret:}") String secretoHs256,
            @Value("${jwt.hs256-aceptado:true}") boolean aceptarHs256) {
        this.verificador = new VerificadorJwtUsuario(clavePublica, secretoHs256, aceptarHs256);
    }

    public UsuarioJwt(String secretoHs256) {
        this(null, secretoHs256, true);
    }

    public Optional<Usuario> validar(String token) {
        return verificador.validar(token)
                .map(u -> new Usuario(u.email(), u.rol(), u.uid()));
    }

    public Optional<String> extraerToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_SESION.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        String autorizacion = request.getHeader("Authorization");
        if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
            return Optional.of(autorizacion.substring(7));
        }
        return Optional.empty();
    }
}
