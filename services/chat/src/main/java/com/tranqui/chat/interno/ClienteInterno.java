package com.tranqui.chat.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas internas del chat al dueño de cada dato (hoy el monolito), siempre por el gateway con un
 * JWT de servicio (sección 4.2). Solo para la carga inicial de las copias y de los mensajes.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "chat";

    public record Contacto(Long id, String email, String nombre, String apellido, String rol, Boolean emailHabilitado) {}

    public record MensajeMonolito(Long id, Long remitenteId, Long destinatarioId, String contenido, boolean leido,
                                  LocalDateTime fechaEnvio) {}

    public record TurnoConfirmado(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha, String horaInicio) {}

    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public ClienteInterno(@Value("${tranqui.gateway.url}") String gatewayUrl,
                          @Value("${tranqui.jwt.service-secret}") String claveServicio) {
        if (claveServicio == null || claveServicio.isBlank()) {
            throw new IllegalStateException("Falta SERVICE_JWT_KEY: el servicio no puede hacer consultas internas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(30_000);
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        this.serviceJwt = new ServiceJwt(claveServicio);
    }

    private <T> T get(String uri, ParameterizedTypeReference<T> tipo, Object... variables) {
        return http.get().uri(uri, variables)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo);
    }

    public List<Contacto> contactos(int pagina, int tamanio) {
        List<Contacto> r = get("/internal/monolito/contactos?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {},
                pagina, tamanio);
        return r != null ? r : List.of();
    }

    public List<MensajeMonolito> mensajes(long desdeId, int tamanio) {
        List<MensajeMonolito> r = get("/internal/monolito/mensajes?desdeId={d}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, desdeId, tamanio);
        return r != null ? r : List.of();
    }

    public List<TurnoConfirmado> turnosConfirmados() {
        List<TurnoConfirmado> r = get("/internal/monolito/turnos-confirmados", new ParameterizedTypeReference<>() {});
        return r != null ? r : List.of();
    }
}
