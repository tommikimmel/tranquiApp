package com.tranqui.gateway.filter;

import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro de seguridad para rutas internas (/internal/**), según sección 4.2 del plan de microservicios.
 * - Rechaza con 404 cualquier petición que provenga del exterior o traiga encabezados X-Forwarded-*.
 * - Exige un JWT de servicio válido emitido con ServiceJwt (rol "SERVICE" y firma HMAC compartida).
 * - Sin token válido o expirado: 401 Unauthorized.
 * - Con token válido: propaga el nombre del servicio en X-Service-Name y deriva la llamada.
 */
@Component
public class InternalSecurityFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalSecurityFilter.class);
    private final ServiceJwt serviceJwt;

    public InternalSecurityFilter(ServiceJwt serviceJwt) {
        this.serviceJwt = serviceJwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        // Nunca se confía en encabezados de identidad que vengan de afuera: el gateway es el único que los pone.
        exchange = sinEncabezadosDeIdentidad(exchange);

        if (!path.startsWith("/internal")) {
            return chain.filter(exchange);
        }

        // 1. Detección de encabezados de proxy externos (Traefik u otros)
        boolean hasForwardedHeaders = exchange.getRequest().getHeaders().keySet().stream()
                .anyMatch(headerName -> headerName.toLowerCase().startsWith("x-forwarded-"));

        if (hasForwardedHeaders) {
            log.warn("Petición a ruta interna {} rechazada con 404 por contener encabezados X-Forwarded-*", path);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        // 2. Validación de token de servicio JWT
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Petición a ruta interna {} rechazada con 401 por falta de Authorization Bearer token", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7).trim();
        try {
            String serviceName = serviceJwt.validarYObtenerServicio(token);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Service-Name", serviceName)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("Petición a ruta interna {} rechazada con 401 por token de servicio inválido: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    static final java.util.List<String> ENCABEZADOS_DE_IDENTIDAD = java.util.List.of("X-Service-Name", "X-User-Id");

    private static ServerWebExchange sinEncabezadosDeIdentidad(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (ENCABEZADOS_DE_IDENTIDAD.stream().noneMatch(headers::containsKey)) {
            return exchange;
        }
        ServerHttpRequest limpio = exchange.getRequest().mutate()
                .headers(h -> ENCABEZADOS_DE_IDENTIDAD.forEach(h::remove))
                .build();
        return exchange.mutate().request(limpio).build();
    }

    @Override
    public int getOrder() {
        // Justo después del CorrelationIdGlobalFilter
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
