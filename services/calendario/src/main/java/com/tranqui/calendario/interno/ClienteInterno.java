package com.tranqui.calendario.interno;

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
 * Consultas internas del calendario al dueño de cada dato (hoy el monolito), siempre por el gateway
 * con un JWT de servicio (sección 4.2). Solo para la carga inicial.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "calendario";

    public record Contacto(Long id, String email, String nombre, String apellido, String rol, Boolean emailHabilitado) {}

    /** Los tokens llegan cifrados, tal cual están en el monolito. */
    public record Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                         LocalDateTime tokenVenceEn, String calendarId, String syncToken, String watchChannelId,
                         String watchResourceId, LocalDateTime watchVenceEn, boolean watchActivo) {}

    public record EventoExterno(Long id, Long profesionalId, String googleEventId, String titulo, LocalDateTime inicio,
                                LocalDateTime fin, boolean todoElDia, String estado) {}

    public record TurnoConfirmado(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha, String horaInicio,
                                  String horaFin, String modalidad, Boolean ocupaAgenda, String googleEventId,
                                  String meetUrl) {}

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

    private <T> List<T> lista(String uri, ParameterizedTypeReference<List<T>> tipo, Object... variables) {
        List<T> r = http.get().uri(uri, variables)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo);
        return r != null ? r : List.of();
    }

    public List<Contacto> contactos(int pagina, int tamanio) {
        return lista("/internal/monolito/contactos?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<Cuenta> cuentas() {
        return lista("/internal/monolito/calendario/cuentas", new ParameterizedTypeReference<>() {});
    }

    public List<EventoExterno> eventosExternos(long desdeId, int tamanio) {
        return lista("/internal/monolito/calendario/eventos-externos?desdeId={d}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, desdeId, tamanio);
    }

    public List<TurnoConfirmado> turnosConfirmados() {
        return lista("/internal/monolito/turnos-confirmados", new ParameterizedTypeReference<>() {});
    }
}
