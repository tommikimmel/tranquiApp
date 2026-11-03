package com.tranqui.app.service;

import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
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
 * Fase 5, decisión 2: la copia del monolito (turnos) del perfil, la disponibilidad y las tarifas se
 * actualiza con cada evento del servicio de profesionales y, además, cada
 * {@code profesionales.resincronizacion-minutos} (15 por defecto) con lo que cambió en el servicio desde la
 * pasada anterior. Corrige un evento perdido o aplicado fuera de orden. Si el servicio no responde, la
 * agenda sigue con la copia que hay y la próxima pasada vuelve a pedir desde la última que salió bien.
 *
 * <p>Solo corre con {@code profesionales.externo=true}; la primera pasada después de arrancar pide todo.
 */
@Component
public class ResincronizacionProfesionales {

    private static final Logger log = LoggerFactory.getLogger(ResincronizacionProfesionales.class);

    /** Margen para no perder cambios hechos mientras corría la pasada anterior. */
    private static final long MARGEN_MINUTOS = 2;

    /** Lo que devuelve {@code GET /internal/profesionales/profesionales}: el estado actual de un profesional. */
    public record ProfesionalServicio(Long profesionalId, int version, PerfilProfesionalData perfil,
                                      List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                      List<TarifasProfesionalData.Tarifa> tarifas) {}

    private final CopiaProfesionales copia;
    private final RestClient http;
    private final ServiceJwt serviceJwt;
    private volatile OffsetDateTime ultimaPasada;

    public ResincronizacionProfesionales(CopiaProfesionales copia,
                                         @Value("${profesionales.externo:false}") boolean externo,
                                         @Value("${tranqui.gateway.url:}") String gatewayUrl,
                                         @Value("${tranqui.jwt.service-secret:}") String claveServicio) {
        this.copia = copia;
        boolean activa = externo && gatewayUrl != null && !gatewayUrl.isBlank()
                && claveServicio != null && !claveServicio.isBlank();
        if (activa) {
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(2_000);
            fabrica.setReadTimeout(30_000);
            this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
            this.serviceJwt = new ServiceJwt(claveServicio);
        } else {
            this.http = null;
            this.serviceJwt = null;
        }
    }

    @Scheduled(initialDelayString = "${profesionales.resincronizacion-demora-inicial-ms:60000}",
            fixedDelayString = "#{${profesionales.resincronizacion-minutos:15} * 60000}")
    public void resincronizar() {
        if (http == null) {
            return;
        }
        OffsetDateTime inicio = OffsetDateTime.now();
        OffsetDateTime desde = ultimaPasada != null ? ultimaPasada.minusMinutes(MARGEN_MINUTOS) : null;
        List<ProfesionalServicio> cambios;
        try {
            cambios = http.get()
                    .uri(u -> {
                        u.path("/internal/profesionales/profesionales");
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
                    .body(new ParameterizedTypeReference<List<ProfesionalServicio>>() {});
        } catch (RuntimeException e) {
            log.warn("El servicio de profesionales no respondió la resincronización; la copia sigue como está: {}", e.getMessage());
            return;
        }
        int aplicados = aplicar(cambios != null ? cambios : List.of());
        ultimaPasada = inicio;
        log.info("Resincronización de profesionales: {} actualizados desde {}", aplicados, desde != null ? desde : "el principio");
    }

    int aplicar(List<ProfesionalServicio> cambios) {
        int aplicados = 0;
        for (ProfesionalServicio p : cambios) {
            try {
                if (p.perfil() != null) {
                    copia.aplicarPerfil(p.profesionalId(), p.version(), p.perfil());
                }
                if (p.disponibilidad() != null) {
                    copia.aplicarDisponibilidad(new DisponibilidadProfesionalData(p.profesionalId(), p.disponibilidad()));
                }
                if (p.tarifas() != null) {
                    copia.aplicarTarifas(new TarifasProfesionalData(p.profesionalId(), p.tarifas()));
                }
                aplicados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo resincronizar el profesional {}: {}", p.profesionalId(), e.getMessage());
            }
        }
        return aplicados;
    }
}
