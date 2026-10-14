package com.tranqui.gateway.admin;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Validador de autenticación y autorización para endpoints de administración en el Gateway.
 * Valida el JWT del usuario contra la clave secreta compartida con el monolito
 * y asegura que tenga rol ADMIN (sección 3 y 4.4 del plan de microservicios).
 */
@Component
public class AdminAuthValidator {

    public static final String SESSION_COOKIE_NAME = "SESSION-TOKEN";
    private final Key signingKey;

    public AdminAuthValidator(@Value("${jwt.secret:defaultsecretkeymustbe32byteslongforhs256algorithm!}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Valida que la petición contenga un JWT válido con rol ADMIN.
     * Lanza ResponseStatusException con 401 si no hay token o es inválido/expirado,
     * o 403 si el rol no es ADMIN.
     */
    public Claims requireAdmin(ServerWebExchange exchange) {
        String token = extractToken(exchange);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticación requerido");
        }

        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticación inválido o expirado");
        }

        if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticación expirado");
        }

        Object rolObj = claims.get("rol");
        if (rolObj == null) {
            rolObj = claims.get("role");
        }
        String rol = rolObj != null ? String.valueOf(rolObj) : null;
        if (!"ADMIN".equalsIgnoreCase(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a administradores");
        }

        return claims;
    }

    public String extractToken(ServerWebExchange exchange) {
        // 1. Revisar encabezado Authorization: Bearer <token>
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. Revisar cookie SESSION-TOKEN
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie != null && sessionCookie.getValue() != null && !sessionCookie.getValue().isBlank()) {
            return sessionCookie.getValue().trim();
        }

        return null;
    }
}
