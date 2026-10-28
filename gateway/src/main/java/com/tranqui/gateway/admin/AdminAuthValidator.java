package com.tranqui.gateway.admin;

import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.gateway.security.UserJwtVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Validador de autenticación y autorización para endpoints de administración en el Gateway.
 * Utiliza UserJwtVerifier para validar el JWT del usuario
 * y asegura que tenga rol ADMIN (sección 3 y 4.4 del plan de microservicios).
 */
@Component
public class AdminAuthValidator {

    private final UserJwtVerifier userJwtVerifier;

    public AdminAuthValidator(UserJwtVerifier userJwtVerifier) {
        this.userJwtVerifier = userJwtVerifier;
    }

    /**
     * Valida que la petición contenga un JWT válido con rol ADMIN.
     * Lanza ResponseStatusException con 401 si no hay token o es inválido/expirado,
     * o 403 si el rol no es ADMIN.
     */
    public UsuarioToken requireAdmin(ServerWebExchange exchange) {
        String token = extractToken(exchange);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticación requerido");
        }

        UsuarioToken usuario = userJwtVerifier.verifyToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticación inválido o expirado"));

        if (!"ADMIN".equals(usuario.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a administradores");
        }

        return usuario;
    }

    public String extractToken(ServerWebExchange exchange) {
        return userJwtVerifier.extractToken(exchange).orElse(null);
    }
}

