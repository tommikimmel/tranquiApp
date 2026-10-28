package com.tranqui.gateway.portones;

import com.fasterxml.jackson.databind.JsonNode;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pregunta al monolito si un profesional tiene la suscripción al día
 * (/internal/monolito/suscripciones/acceso), con caché de 60 s por mail. Si el monolito no responde,
 * deja pasar: el paywall es comercial, no de seguridad, y un monolito caído no debe cortar los
 * servicios. En la Fase 8 la consulta se queda en el monolito (decisión 2: el monolito mantiene una
 * copia del acceso con eventos) hasta la Fase 9.
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
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    public AccesoSuscripcionCliente(WebClient.Builder builder, ServiceJwt serviceJwt,
                                    @Value("${tranqui.services.monolito.uri}") String monolitoUri) {
        this.webClient = builder.baseUrl(monolitoUri).build();
        this.serviceJwt = serviceJwt;
    }

    public Mono<Boolean> tieneAcceso(String email) {
        Entrada entrada = cache.get(email);
        if (entrada != null && entrada.venceEn() > System.currentTimeMillis()) {
            return Mono.just(entrada.permitido());
        }
        return webClient.get()
                .uri(b -> b.path("/internal/monolito/suscripciones/acceso").queryParam("email", email).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceJwt.emitirToken("gateway"))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(TIEMPO_MAXIMO)
                .map(cuerpo -> cuerpo.path("permitido").asBoolean(true))
                .doOnNext(permitido -> guardar(email, permitido))
                .onErrorResume(e -> {
                    log.warn("No se pudo consultar la suscripción de {} al monolito; se deja pasar: {}", email, e.toString());
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
