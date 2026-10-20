package com.tranqui.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro global que garantiza que cualquier respuesta 429 (Too Many Requests)
 * contenga el encabezado Retry-After si no fue establecido previamente por el RateLimiter.
 */
@Component
public class RetryAfterGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            if (HttpStatus.TOO_MANY_REQUESTS.equals(exchange.getResponse().getStatusCode())) {
                if (!exchange.getResponse().getHeaders().containsKey(HttpHeaders.RETRY_AFTER)) {
                    exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
                }
            }
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Ejecución temprana para registrar el hook de beforeCommit antes de cualquier retorno
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
