package com.tranqui.gateway.ratelimit;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * KeyResolver para el Rate Limiter (sección 4.3 del plan).
 * Extrae el identificador de usuario del JWT (cookie SESSION-TOKEN o header Authorization)
 * y si no está autenticado, recurre a la dirección IP del cliente.
 */
@Component("userOrIpKeyResolver")
@Primary
public class UserOrIpKeyResolver implements KeyResolver {

    public static final String SESSION_COOKIE_NAME = "SESSION-TOKEN";
    private final Key signingKey;

    public UserOrIpKeyResolver(@Value("${jwt.secret:defaultsecretkeymustbe32byteslongforhs256algorithm!}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String token = extractToken(exchange);
        if (token != null) {
            String userId = extractUserIdentifier(token);
            if (userId != null && !userId.trim().isEmpty()) {
                return Mono.just("user:" + userId.trim());
            }
        }

        return Mono.just("ip:" + extractClientIp(exchange));
    }

    private String extractToken(ServerWebExchange exchange) {
        // 1. Revisar cookie SESSION-TOKEN
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie != null && sessionCookie.getValue() != null && !sessionCookie.getValue().trim().isEmpty()) {
            return sessionCookie.getValue().trim();
        }

        // 2. Revisar header Authorization: Bearer <token>
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return null;
    }

    private String extractUserIdentifier(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object idClaim = claims.get("userId");
            if (idClaim == null) {
                idClaim = claims.get("id");
            }
            if (idClaim == null) {
                idClaim = claims.get("usuarioId");
            }

            if (idClaim != null) {
                return String.valueOf(idClaim);
            }

            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }
}
