package com.tranqui.gateway.ratelimit;

import com.tranqui.eventos.jwt.ServiceJwt;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * KeyResolver para rutas internas (/internal/**): extrae el nombre del servicio desde el ServiceJwt.
 */
@Component("serviceKeyResolver")
public class ServiceKeyResolver implements KeyResolver {

    private final ServiceJwt serviceJwt;

    public ServiceKeyResolver(ServiceJwt serviceJwt) {
        this.serviceJwt = serviceJwt;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        // Puede venir del header mutado X-Service-Name por InternalSecurityFilter
        String serviceName = exchange.getRequest().getHeaders().getFirst("X-Service-Name");
        if (serviceName != null && !serviceName.trim().isEmpty()) {
            return Mono.just("service:" + serviceName.trim());
        }

        // Si aún no se mutó, parsearlo del Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String parsed = serviceJwt.validarYObtenerServicio(authHeader.substring(7).trim());
                return Mono.just("service:" + parsed);
            } catch (Exception ignored) {
            }
        }

        return Mono.just("service:unknown");
    }
}
