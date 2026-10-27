package com.tranqui.app.service;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fase 8: resincronización periódica de la copia del acceso a suscripciones en el monolito
 * contra el servicio de pagos vía el gateway interno.
 * Corre al arrancar (ApplicationReadyEvent) y cada 15 minutos solo con {@code suscripciones.externo=true}.
 */
@Component
public class ResincronizacionSuscripciones {

    private static final Logger log = LoggerFactory.getLogger(ResincronizacionSuscripciones.class);

    /**
     * Contrato del endpoint GET /internal/suscripciones/estado?pagina=N&tamanio=200
     * que implementa S2b en el servicio de pagos.
     */
    public record EstadoSuscripcionDto(
            Long professionalId,
            String estado,
            String planCode,
            boolean acceso,
            boolean puedeRecetar,
            @JsonSerialize(using = LocalDateSerializer.class) LocalDate periodoHasta,
            @JsonSerialize(using = LocalDateSerializer.class) LocalDate graciaHasta,
            Long version
    ) {}

    private final CopiaSuscripciones copia;
    private final boolean suscripcionesExterno;
    private final RestClient http;
    private final ServiceJwt serviceJwt;

    @org.springframework.beans.factory.annotation.Autowired
    public ResincronizacionSuscripciones(CopiaSuscripciones copia,
                                         @Value("${suscripciones.externo:false}") boolean suscripcionesExterno,
                                         @Value("${tranqui.gateway.url:}") String gatewayUrl,
                                         @Value("${tranqui.jwt.service-secret:}") String claveServicio) {
        this.copia = copia;
        this.suscripcionesExterno = suscripcionesExterno;
        boolean activa = suscripcionesExterno && gatewayUrl != null && !gatewayUrl.isBlank()
                && claveServicio != null && !claveServicio.isBlank();
        if (activa) {
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(2_000);
            fabrica.setReadTimeout(10_000);
            this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
            this.serviceJwt = new ServiceJwt(claveServicio);
        } else {
            this.http = null;
            this.serviceJwt = null;
        }
    }

    ResincronizacionSuscripciones(CopiaSuscripciones copia, boolean suscripcionesExterno, RestClient http, ServiceJwt serviceJwt) {
        this.copia = copia;
        this.suscripcionesExterno = suscripcionesExterno;
        this.http = http;
        this.serviceJwt = serviceJwt;
    }

    @Scheduled(initialDelayString = "${suscripciones.resincronizacion-demora-inicial-ms:60000}",
            fixedDelayString = "#{${suscripciones.resincronizacion-minutos:15} * 60000}")
    @EventListener(ApplicationReadyEvent.class)
    public void resincronizar() {
        if (!suscripcionesExterno || http == null) {
            return;
        }
        int pagina = 0;
        final int tamanio = 200;
        int totalActualizados = 0;
        while (true) {
            final int p = pagina;
            List<EstadoSuscripcionDto> elementos;
            try {
                elementos = http.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/internal/suscripciones/estado")
                                .queryParam("pagina", p)
                                .queryParam("tamanio", tamanio)
                                .build())
                        .headers(h -> {
                            if (serviceJwt != null) {
                                h.setBearerAuth(serviceJwt.emitirToken("monolito"));
                            }
                            String correlacion = MDC.get("correlationId");
                            h.set("X-Correlation-Id", correlacion != null ? correlacion : UUID.randomUUID().toString());
                        })
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<EstadoSuscripcionDto>>() {});
            } catch (Exception e) {
                log.warn("El servicio de pagos no respondió la resincronización de suscripciones; la copia sigue como está: {}", e.getMessage());
                return;
            }

            if (elementos == null || elementos.isEmpty()) {
                break;
            }

            for (EstadoSuscripcionDto dto : elementos) {
                try {
                    copia.upsert(dto.professionalId(), dto.estado(), dto.planCode(), dto.acceso(),
                            dto.puedeRecetar(), dto.periodoHasta(), dto.graciaHasta(), dto.version());
                    totalActualizados++;
                } catch (Exception e) {
                    log.warn("No se pudo aplicar la suscripción del profesional {}: {}", dto.professionalId(), e.getMessage());
                }
            }

            if (elementos.size() < tamanio) {
                break;
            }
            pagina++;
        }
        log.info("Resincronización de suscripciones completada: {} profesionales actualizados.", totalActualizados);
    }
}
