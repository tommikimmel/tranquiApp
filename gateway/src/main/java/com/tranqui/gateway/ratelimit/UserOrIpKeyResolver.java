package com.tranqui.gateway.ratelimit;

import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.gateway.security.RealIpResolver;
import com.tranqui.gateway.security.UserJwtVerifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * KeyResolver para el Rate Limiter (sección 4.3 del plan).
 * Extrae el identificador de usuario del JWT (cookie SESSION-TOKEN o header Authorization Bearer)
 * a través de UserJwtVerifier con clave user:<email>; si no está autenticado, recurre a la dirección
 * IP real del cliente mediante RealIpResolver.
 */
@Component("userOrIpKeyResolver")
@Primary
public class UserOrIpKeyResolver implements KeyResolver {

    private final UserJwtVerifier userJwtVerifier;
    private final RealIpResolver realIpResolver;

    public UserOrIpKeyResolver(UserJwtVerifier userJwtVerifier, RealIpResolver realIpResolver) {
        this.userJwtVerifier = userJwtVerifier;
        this.realIpResolver = realIpResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        Optional<UsuarioToken> usuarioOpt = userJwtVerifier.verify(exchange);
        if (usuarioOpt.isPresent() && usuarioOpt.get().email() != null && !usuarioOpt.get().email().isBlank()) {
            return Mono.just("user:" + usuarioOpt.get().email().trim());
        }

        return Mono.just("ip:" + realIpResolver.extractClientIp(exchange));
    }

    public String extractClientIp(ServerWebExchange exchange) {
        return realIpResolver.extractClientIp(exchange);
    }
}

