package com.tranqui.pagos.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Valida el JWT de usuario que emite identidad (hoy el monolito, {@code JwtService}): HS256 con
 * {@code JWT_SECRET}, el mail en {@code sub} y el rol en el claim {@code rol}. Lo lee de la cookie
 * {@code SESSION-TOKEN} o del encabezado {@code Authorization: Bearer}, igual que el gateway.
 */
@Component
public class UsuarioJwt {

    public static final String COOKIE_SESION = "SESSION-TOKEN";

    /** Usuario autenticado: el mail y el rol del token. */
    public record Usuario(String email, String rol) {}

    private final SecretKey clave;

    public UsuarioJwt(@Value("${jwt.secret}") String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException("Falta JWT_SECRET: el servicio no puede validar sesiones.");
        }
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET tiene que tener al menos 32 bytes.");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
    }

    public Optional<Usuario> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(clave).build()
                    .parseClaimsJws(token.trim()).getBody();
            if (claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new Usuario(claims.getSubject(), claims.get("rol", String.class)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
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
