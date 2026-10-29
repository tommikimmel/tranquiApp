package com.tranqui.profesionales.interno;

import com.tranqui.eventos.ValidadorEventos;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente para consultar el resumen de chat al servicio de chat a través del gateway (sección 4.2).
 * Si el chat no responde o falla, devuelve un resumen vacío (resiliencia: un servicio caído no tira a otro).
 */
@Component
public class ClienteChat {

    private static final Logger log = LoggerFactory.getLogger(ClienteChat.class);
    static final String SERVICIO = "profesionales";

    public record Resumen(List<Long> pacientesConChat, Map<Long, Integer> noLeidosPorPaciente) {
        public static Resumen vacio() {
            return new Resumen(List.of(), Map.of());
        }
    }

    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public ClienteChat(@Value("${tranqui.gateway.url}") String gatewayUrl,
                       @Value("${tranqui.jwt.service-secret}") String claveServicio) {
        if (claveServicio == null || claveServicio.isBlank()) {
            throw new IllegalStateException("Falta SERVICE_JWT_KEY: el servicio no puede hacer consultas internas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(5_000);
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        this.serviceJwt = new ServiceJwt(claveServicio);
    }

    public Resumen resumen(Long profesionalId) {
        try {
            Resumen r = ValidadorEventos.respuesta(http.get()
                    .uri("/internal/chat/profesionales/{id}/resumen", profesionalId)
                    .headers(h -> {
                        h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                        h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                    })
                    .retrieve()
                    .body(Resumen.class), "/internal/chat/profesionales/{id}/resumen");
            return r != null ? r : Resumen.vacio();
        } catch (RuntimeException e) {
            log.warn("El chat no respondió el resumen del profesional {}; la lista sale sin no leídos: {}",
                    profesionalId, e.getMessage());
            return Resumen.vacio();
        }
    }
}
