package com.tranqui.identidad.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas internas de identidad al monolito y otros servicios, a través del gateway
 * con un JWT de servicio.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "identidad";

    public record UsuarioMonolito(Map<String, Object> valores) {
        public UsuarioMonolito {
            valores = valores != null ? Collections.unmodifiableMap(new HashMap<>(valores)) : Map.of();
        }
    }

    public record TicketMonolito(Map<String, Object> valores) {
        public TicketMonolito {
            valores = valores != null ? Collections.unmodifiableMap(new HashMap<>(valores)) : Map.of();
        }
    }

    public record TicketMensajeMonolito(Map<String, Object> valores) {
        public TicketMensajeMonolito {
            valores = valores != null ? Collections.unmodifiableMap(new HashMap<>(valores)) : Map.of();
        }
    }

    public record NovedadesEnvioMonolito(Map<String, Object> valores) {
        public NovedadesEnvioMonolito {
            valores = valores != null ? Collections.unmodifiableMap(new HashMap<>(valores)) : Map.of();
        }
    }

    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public ClienteInterno(@Value("${tranqui.gateway.url:http://gateway:8080}") String gatewayUrl,
                          @Value("${tranqui.jwt.service-secret:}") String claveServicio) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(30_000);
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        this.serviceJwt = claveServicio != null && !claveServicio.isBlank() ? new ServiceJwt(claveServicio) : null;
    }

    private <T> List<T> lista(String uri, ParameterizedTypeReference<List<T>> tipo, Object... variables) {
        List<T> r = http.get().uri(uri, variables)
                .headers(h -> {
                    if (serviceJwt != null) {
                        h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    }
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo);
        return r != null ? r : List.of();
    }

    public List<UsuarioMonolito> usuarios(int pagina, int tamanio) {
        List<Map<String, Object>> r = lista(
                "/internal/monolito/identidad/usuarios?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r.stream().map(UsuarioMonolito::new).toList();
    }

    public List<TicketMonolito> tickets(int pagina, int tamanio) {
        List<Map<String, Object>> r = lista(
                "/internal/monolito/identidad/tickets?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r.stream().map(TicketMonolito::new).toList();
    }

    public List<TicketMensajeMonolito> ticketMensajes(int pagina, int tamanio) {
        List<Map<String, Object>> r = lista(
                "/internal/monolito/identidad/ticket-mensajes?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r.stream().map(TicketMensajeMonolito::new).toList();
    }

    public List<NovedadesEnvioMonolito> novedadesEnvios(int pagina, int tamanio) {
        List<Map<String, Object>> r = lista(
                "/internal/monolito/identidad/novedades-envios?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r.stream().map(NovedadesEnvioMonolito::new).toList();
    }
}
