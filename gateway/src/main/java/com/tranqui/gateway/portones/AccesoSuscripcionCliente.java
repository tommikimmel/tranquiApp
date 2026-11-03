package com.tranqui.gateway.portones;

import com.fasterxml.jackson.databind.JsonNode;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pregunta al dueño de las suscripciones si un profesional tiene la suscripción al día,
 * con caché de 60 s por mail. Si la fuente no responde o responde con un JSON inválido,
 * deja pasar: el paywall es comercial, no de seguridad, y una falla no debe cortar los servicios.
 *
 * En la Fase 8 la consulta se hacía al monolito (/internal/monolito/suscripciones/acceso).
 * En la Fase 9 pasa opcionalmente al servicio de pagos (/internal/suscripciones/acceso) según
 * tranqui.portones.acceso.fuente (TRANQUI_PAYWALL_FUENTE: monolito|pagos).
 */
@Component
public class AccesoSuscripcionCliente {

    private static final Logger log = LoggerFactory.getLogger(AccesoSuscripcionCliente.class);
    private static final Duration VIGENCIA = Duration.ofSeconds(60);
    private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(2);
    private static final int TAMANIO_MAXIMO_CACHE = 10_000;

    private record Entrada(boolean permitido, long venceEn) {}

    private final WebClient webClient;
    private final ServiceJwt serviceJwt;
    private final String pathConsulta;
    private final String fuente;
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    public AccesoSuscripcionCliente(
            WebClient.Builder builder,
            @Qualifier("loadBalancedWebClientBuilder") ObjectProvider<WebClient.Builder> loadBalancedBuilderProvider,
            ServiceJwt serviceJwt,
            @Value("${tranqui.portones.acceso.fuente:monolito}") String fuente,
            @Value("${tranqui.services.monolito.uri}") String monolitoUri,
            @Value("${tranqui.services.pagos.uri:${tranqui.services.monolito.uri}}") String pagosUri) {

        this.serviceJwt = serviceJwt;
        this.fuente = fuente != null ? fuente.trim() : "monolito";

        String baseUri;
        if ("pagos".equalsIgnoreCase(this.fuente)) {
            baseUri = pagosUri;
            this.pathConsulta = "/internal/suscripciones/acceso";
        } else {
            baseUri = monolitoUri;
            this.pathConsulta = "/internal/monolito/suscripciones/acceso";
        }

        WebClient.Builder clientBuilder = builder;
        if (baseUri != null && baseUri.startsWith("lb://") && loadBalancedBuilderProvider != null) {
            WebClient.Builder lbBuilder = loadBalancedBuilderProvider.getIfAvailable();
            if (lbBuilder != null) {
                clientBuilder = lbBuilder;
                // El WebClient balanceado resuelve http://servicio; lb:// es el esquema de las rutas y quedaba sin cambiar.
                baseUri = "http://" + baseUri.substring("lb://".length());
            }
        }

        this.webClient = clientBuilder.baseUrl(baseUri != null ? baseUri : "").build();
    }

    public Mono<Boolean> tieneAcceso(String email) {
        Entrada entrada = cache.get(email);
        if (entrada != null && entrada.venceEn() > System.currentTimeMillis()) {
            return Mono.just(entrada.permitido());
        }
        return webClient.get()
                .uri(b -> b.path(pathConsulta).queryParam("email", email).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceJwt.emitirToken("gateway"))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(TIEMPO_MAXIMO)
                .map(cuerpo -> {
                    if (cuerpo == null || !cuerpo.hasNonNull("permitido") || !cuerpo.get("permitido").isBoolean()) {
                        throw new IllegalStateException("Respuesta de paywall inválida o sin campo booleano 'permitido': " + cuerpo);
                    }
                    return cuerpo.get("permitido").asBoolean();
                })
                .doOnNext(permitido -> guardar(email, permitido))
                .onErrorResume(e -> {
                    log.warn("No se pudo consultar la suscripción de {} a {}; se deja pasar: {}", email, fuente, e.toString());
                    return Mono.just(true);
                });
    }

    private void guardar(String email, boolean permitido) {
        if (cache.size() >= TAMANIO_MAXIMO_CACHE) {
            long ahora = System.currentTimeMillis();
            cache.values().removeIf(e -> e.venceEn() <= ahora);
            if (cache.size() >= TAMANIO_MAXIMO_CACHE) {
                cache.clear();
            }
        }
        cache.put(email, new Entrada(permitido, System.currentTimeMillis() + VIGENCIA.toMillis()));
    }
}

