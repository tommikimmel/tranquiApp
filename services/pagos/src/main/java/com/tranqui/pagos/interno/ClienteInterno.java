package com.tranqui.pagos.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Llamadas internas de pagos al monolito, siempre por el gateway con un JWT de servicio (sección 4.2):
 * las consultas de la carga inicial y el reenvío de las notificaciones de suscripciones de Mercado Pago.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "pagos";

    public record Contacto(Long id, String email, String nombre, String apellido, String rol, Boolean emailHabilitado) {}

    /** Los tokens llegan cifrados, tal cual están en el monolito. */
    public record Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                         LocalDateTime tokenVenceEn, String mpUserId) {}

    public record Pago(Long id, Long turnoId, Long solicitudId, Long profesionalId, Long pacienteId, String transactionId,
                       String estado, BigDecimal monto, LocalDateTime fechaPago) {}

    public record Solicitud(Long solicitudId, Long profesionalId, Long pacienteId, String tipoConcepto, BigDecimal precio,
                            String estado, String transactionId, String titulo) {}

    public record CobroPendiente(Long turnoId, Long profesionalId, Long pacienteId, BigDecimal monto, String titulo,
                                 String checkoutUrl, LocalDateTime creadoEn) {}

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
        return lista("/internal/monolito/pagos/cuentas", new ParameterizedTypeReference<>() {});
    }

    public List<Pago> pagos(int pagina, int tamanio) {
        return lista("/internal/monolito/pagos/pagos?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<Solicitud> solicitudes() {
        return lista("/internal/monolito/pagos/solicitudes", new ParameterizedTypeReference<>() {});
    }

    public List<CobroPendiente> cobrosPendientes() {
        return lista("/internal/monolito/pagos/cobros-pendientes", new ParameterizedTypeReference<>() {});
    }

    /**
     * Le pasa al monolito una notificación de suscripción de Mercado Pago tal cual llegó (ya con la firma
     * validada acá). Si el monolito no responde, tira la excepción: el webhook responde 500 y Mercado Pago
     * la reintenta.
     */
    public void reenviarSuscripcion(String cuerpo, String dataId) {
        http.post().uri(dataId != null ? "/internal/monolito/pagos/notificacion-suscripcion?dataId={d}"
                        : "/internal/monolito/pagos/notificacion-suscripcion", dataId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(cuerpo)
                .retrieve().toBodilessEntity();
    }
}
