package com.tranqui.notificaciones.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Consultas internas (sección 4.2): siempre a través del gateway ({@code /internal/...}), con un
 * JWT de servicio de 5 minutos y el {@code X-Correlation-Id} del pedido o evento en curso. Solo
 * consultas: los cambios de estado viajan por eventos.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "notificaciones";

    /** Un usuario tal como lo entrega la carga inicial de contactos. */
    public record ContactoInterno(Long id, String email, String nombre, String apellido, String rol,
                                  Boolean emailHabilitado) {}

    /** Enlaces de un solo uso del mail de confirmación de asistencia. */
    public record EnlacesAsistencia(String confirmarUrl, String noAsistiraUrl) {}

    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public ClienteInterno(@Value("${tranqui.gateway.url}") String gatewayUrl,
                          @Value("${tranqui.jwt.service-secret}") String claveServicio) {
        if (claveServicio == null || claveServicio.isBlank()) {
            throw new IllegalStateException("Falta SERVICE_JWT_KEY: el servicio no puede hacer consultas internas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(10_000);
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        this.serviceJwt = new ServiceJwt(claveServicio);
    }

    public List<ContactoInterno> contactos(int pagina, int tamanio) {
        List<ContactoInterno> respuesta = http.get()
                .uri("/internal/monolito/contactos?pagina={p}&tamanio={t}", pagina, tamanio)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return respuesta != null ? respuesta : List.of();
    }

    public EnlacesAsistencia enlacesAsistencia(Long turnoId) {
        EnlacesAsistencia enlaces = http.get()
                .uri("/internal/monolito/turnos/{id}/enlaces-asistencia", turnoId)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve()
                .body(EnlacesAsistencia.class);
        if (enlaces == null) {
            throw new IllegalStateException("El monolito no devolvió los enlaces de asistencia del turno " + turnoId);
        }
        return enlaces;
    }
}
