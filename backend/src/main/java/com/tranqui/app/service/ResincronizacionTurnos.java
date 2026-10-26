package com.tranqui.app.service;

import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fase 6, decisión 2: la copia del monolito de los turnos se actualiza con cada {@code turno.actualizado} y,
 * además, cada {@code turnos.resincronizacion-minutos} (15 por defecto) con lo que cambió en el servicio
 * desde la pasada anterior, como la de profesionales. Si el servicio no responde, la copia sigue como está y
 * la próxima pasada vuelve a pedir desde la última que salió bien.
 *
 * <p>Solo corre con {@code turnos.externo=true}; la primera pasada después de arrancar pide todo.
 */
@Component
public class ResincronizacionTurnos {

    private static final Logger log = LoggerFactory.getLogger(ResincronizacionTurnos.class);

    /** Margen para no perder cambios hechos mientras corría la pasada anterior. */
    private static final long MARGEN_MINUTOS = 2;

    /** Lo que devuelve {@code GET /internal/turnos/turnos}: el turno como está en el servicio, con su versión. */
    public record TurnoServicio(int version, TurnoActualizadoData turno) {}

    private final CopiaTurnos copia;
    private final RestClient http;
    private final ServiceJwt serviceJwt;
    private volatile OffsetDateTime ultimaPasada;

    public ResincronizacionTurnos(CopiaTurnos copia,
                                  @Value("${turnos.externo:false}") boolean externo,
                                  @Value("${tranqui.gateway.url:}") String gatewayUrl,
                                  @Value("${tranqui.jwt.service-secret:}") String claveServicio) {
        this.copia = copia;
        boolean activa = externo && gatewayUrl != null && !gatewayUrl.isBlank()
                && claveServicio != null && !claveServicio.isBlank();
        if (activa) {
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(2_000);
            fabrica.setReadTimeout(60_000);
            this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
            this.serviceJwt = new ServiceJwt(claveServicio);
        } else {
            this.http = null;
            this.serviceJwt = null;
        }
    }

    @Scheduled(initialDelayString = "${turnos.resincronizacion-demora-inicial-ms:60000}",
            fixedDelayString = "#{${turnos.resincronizacion-minutos:15} * 60000}")
    public void resincronizar() {
        if (http == null) {
            return;
        }
        OffsetDateTime inicio = OffsetDateTime.now();
        OffsetDateTime desde = ultimaPasada != null ? ultimaPasada.minusMinutes(MARGEN_MINUTOS) : null;
        List<TurnoServicio> cambios;
        try {
            cambios = http.get()
                    .uri(u -> {
                        u.path("/internal/turnos/turnos");
                        if (desde != null) {
                            u.queryParam("desde", desde.toString());
                        }
                        return u.build();
                    })
                    .headers(h -> {
                        h.setBearerAuth(serviceJwt.emitirToken("monolito"));
                        String correlacion = MDC.get("correlationId");
                        h.set("X-Correlation-Id", correlacion != null ? correlacion : UUID.randomUUID().toString());
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TurnoServicio>>() {});
        } catch (RuntimeException e) {
            log.warn("El servicio de turnos no respondió la resincronización; la copia sigue como está: {}", e.getMessage());
            return;
        }
        int aplicados = aplicar(cambios != null ? cambios : List.of());
        ultimaPasada = inicio;
        log.info("Resincronización de turnos: {} actualizados desde {}", aplicados, desde != null ? desde : "el principio");
    }

    int aplicar(List<TurnoServicio> cambios) {
        int aplicados = 0;
        for (TurnoServicio t : cambios) {
            try {
                copia.aplicar(t.turno(), t.version());
                aplicados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo resincronizar el turno {}: {}", t.turno().turnoId(), e.getMessage());
            }
        }
        return aplicados;
    }
}
