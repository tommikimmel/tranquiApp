package com.tranqui.gateway.portones;

import com.tranqui.gateway.security.UserJwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Los dos filtros del monolito que los servicios no tienen (Fase 3, G1), aplicados en el gateway a las
 * rutas que van a un servicio. Las que siguen en el monolito no se tocan: ahí actúan
 * SiteAccessFilter y SubscriptionAccessFilter, así que no se chequea dos veces y en la vuelta atrás
 * de una fase (la ruta vuelve al monolito) el gateway deja de aplicarlos solo.
 * <ul>
 *   <li><b>Portón del sitio:</b> con SITE_ACCESS_PASSWORD, exige la cookie SITE-ACCESS (SHA-256 de la
 *   contraseña, la que emite el monolito en /api/site-access).</li>
 *   <li><b>Paywall:</b> un psiquiatra sin suscripción al día recibe 403 SUBSCRIPTION_REQUIRED.</li>
 * </ul>
 * Mismas excepciones y mismas respuestas que en el monolito.
 */
@Component
public class PortonesGlobalFilter implements GlobalFilter, Ordered {

    static final String COOKIE_SITIO = "SITE-ACCESS";

    // SiteAccessFilter.BYPASS_PREFIXES
    private static final List<String> SIN_PORTON = List.of(
            "/api/site-access/",
            "/api/health",
            "/actuator/",
            "/internal/",
            "/api/payments/webhook",
            "/api/medicos/google-calendar/webhook",
            "/error"
    );

    // SubscriptionAccessFilter.BYPASS_PREFIXES y BYPASS_EXACT
    private static final List<String> SIN_PAYWALL = List.of(
            "/api/auth/",
            "/api/subscriptions/",
            "/api/site-access/",
            "/api/health",
            "/api/payments/webhook",
            "/api/medicos/google-calendar/webhook",
            "/api/medicos/google-calendar/callback",
            "/api/medicos/mercadopago/callback",
            "/error"
    );
    private static final List<String> SIN_PAYWALL_EXACTAS = List.of("/api/medicos");

    private static final String CUERPO_SITIO = "{\"error\":\"Acceso restringido. Ingresá la contraseña del sitio.\"}";
    private static final String CUERPO_SUSCRIPCION =
            "{\"error\":\"SUBSCRIPTION_REQUIRED\",\"message\":\"Necesitás una suscripción activa para acceder a esta funcionalidad.\"}";

    private final UserJwtVerifier userJwtVerifier;
    private final AccesoSuscripcionCliente accesoSuscripcion;
    private final URI monolitoUri;
    private final String cookieEsperada;

    public PortonesGlobalFilter(UserJwtVerifier userJwtVerifier, AccesoSuscripcionCliente accesoSuscripcion,
                                @Value("${tranqui.services.monolito.uri}") String monolitoUri,
                                @Value("${tranqui.portones.sitio-password:}") String sitioPassword) {
        this.userJwtVerifier = userJwtVerifier;
        this.accesoSuscripcion = accesoSuscripcion;
        this.monolitoUri = URI.create(monolitoUri);
        this.cookieEsperada = sitioPassword == null || sitioPassword.isBlank() ? null : sha256Hex(sitioPassword);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route ruta = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (ruta == null || vaAlMonolito(ruta) || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (cookieEsperada != null && !empiezaCon(path, SIN_PORTON) && !tieneCookieDelSitio(exchange)) {
            return responder(exchange, CUERPO_SITIO);
        }

        if (empiezaCon(path, SIN_PAYWALL) || SIN_PAYWALL_EXACTAS.contains(path)) {
            return chain.filter(exchange);
        }
        return userJwtVerifier.verify(exchange)
                .filter(claims -> "PSIQUIATRA".equals(claims.get("rol", String.class)) && claims.getSubject() != null)
                .map(claims -> accesoSuscripcion.tieneAcceso(claims.getSubject())
                        .flatMap(permitido -> permitido ? chain.filter(exchange) : responder(exchange, CUERPO_SUSCRIPCION)))
                // Sin sesión válida o con otro rol: lo decide el servicio, como en el monolito.
                .orElseGet(() -> chain.filter(exchange));
    }

    private boolean vaAlMonolito(Route ruta) {
        URI destino = ruta.getUri();
        return destino.getScheme().equalsIgnoreCase(monolitoUri.getScheme())
                && String.valueOf(destino.getHost()).equalsIgnoreCase(String.valueOf(monolitoUri.getHost()))
                && destino.getPort() == monolitoUri.getPort();
    }

    private boolean tieneCookieDelSitio(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_SITIO);
        return cookie != null && cookieEsperada.equals(cookie.getValue());
    }

    private static boolean empiezaCon(String path, List<String> prefijos) {
        return prefijos.stream().anyMatch(path::startsWith);
    }

    private static Mono<Void> responder(ServerWebExchange exchange, String cuerpo) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(cuerpo.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    static String sha256Hex(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getOrder() {
        // Después de InternalSecurityFilter (+10) y antes del enrutamiento al destino.
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
