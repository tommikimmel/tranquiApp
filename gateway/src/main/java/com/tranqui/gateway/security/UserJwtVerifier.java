package com.tranqui.gateway.security;

import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

/**
 * Validador unificado de tokens JWT de usuario para el API Gateway.
 * Extrae el token (cookie SESSION-TOKEN o header Authorization Bearer),
 * y delega en VerificadorJwtUsuario (RS256 para identidad, HS256 durante la transición).
 */
@Component
public class UserJwtVerifier {

    public static final String SESSION_COOKIE_NAME = "SESSION-TOKEN";

    private final VerificadorJwtUsuario verificador;

    public UserJwtVerifier(
            @Value("${jwt.public-key:}") String publicKey,
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.hs256-aceptado:true}") boolean hs256Aceptado) {
        this.verificador = new VerificadorJwtUsuario(publicKey, secret, hs256Aceptado);
    }

    /**
     * Extrae el token del intercambio y valida su firma y claims.
     * Retorna el UsuarioToken si el token es válido; en caso contrario, Optional.empty().
     */
    public Optional<UsuarioToken> verify(ServerWebExchange exchange) {
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
     * Valida firma y expiración de un token JWT plano delegando en VerificadorJwtUsuario.
     */
    public Optional<UsuarioToken> verifyToken(String token) {
        return verificador.validar(token);
    }
}

