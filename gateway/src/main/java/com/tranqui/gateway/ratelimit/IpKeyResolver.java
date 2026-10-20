package com.tranqui.gateway.ratelimit;

import com.tranqui.gateway.security.RealIpResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * KeyResolver por IP real del cliente (para login, registro y webhooks de Mercado Pago).
 * Resuelve la IP real detrás de proxies confiables mediante RealIpResolver.
 */
@Component("ipKeyResolver")
public class IpKeyResolver implements KeyResolver {

    private final RealIpResolver realIpResolver;

    public IpKeyResolver(RealIpResolver realIpResolver) {
        this.realIpResolver = realIpResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just("ip:" + realIpResolver.extractClientIp(exchange));
    }
}
