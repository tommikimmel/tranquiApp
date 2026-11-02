package com.tranqui.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Optional;

/**
 * Validador unificado de tokens JWT de usuario para el API Gateway.
 * Extrae el token (cookie SESSION-TOKEN o header Authorization Bearer),
 * valida firma y expiración con la clave compartida, y retorna los claims.
 */
@Component
public class UserJwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(UserJwtVerifier.class);
    public static final String SESSION_COOKIE_NAME = "SESSION-TOKEN";

    private final Key signingKey;

    public UserJwtVerifier(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured. Missing JWT_SECRET environment variable.");
        }
        byte[] bytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits) long.");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * Extrae el token del intercambio y valida su firma y expiración.
     * Retorna los claims si el token es válido; en caso contrario, Optional.empty().
     */
    public Optional<Claims> verify(ServerWebExchange exchange) {
        return extractToken(exchange).flatMap(this::verifyToken);
    }

    /**
     * Extrae la cadena de token desde la cookie SESSION-TOKEN o el encabezado Authorization Bearer.
     */
    public Optional<String> extractToken(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return Optional.empty();
        }

        // 1. Revisar cookie SESSION-TOKEN
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie != null && sessionCookie.getValue() != null && !sessionCookie.getValue().trim().isEmpty()) {
            return Optional.of(sessionCookie.getValue().trim());
        }

        // 2. Revisar header Authorization: Bearer <token>
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                return Optional.of(token);
            }
        }

        return Optional.empty();
    }

    /**
     * Valida firma y expiración de un token JWT plano.
     */
    public Optional<Claims> verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token.trim())
                    .getBody();
            return Optional.of(claims);
        } catch (Exception e) {
            log.debug("Token JWT de usuario inválido o expirado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Obtiene el identificador de usuario desde los claims (userId, id, usuarioId o subject).
     */
    public Optional<String> extractUserIdentifier(Claims claims) {
        if (claims == null) {
            return Optional.empty();
        }
        Object idClaim = claims.get("userId");
        if (idClaim == null) {
            idClaim = claims.get("id");
        }
        if (idClaim == null) {
            idClaim = claims.get("usuarioId");
        }
        if (idClaim != null) {
            String val = String.valueOf(idClaim).trim();
            if (!val.isEmpty()) {
                return Optional.of(val);
            }
        }

        String subject = claims.getSubject();
        if (subject != null && !subject.trim().isEmpty()) {
            return Optional.of(subject.trim());
        }

        return Optional.empty();
    }
}
