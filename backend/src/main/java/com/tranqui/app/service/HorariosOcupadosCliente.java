package com.tranqui.app.service;

import com.tranqui.app.model.Usuario;
import com.tranqui.eventos.ValidadorEventos;
import com.tranqui.eventos.jwt.ServiceJwt;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Horarios de un profesional ocupados en su Google Calendar (eventos propios, no turnos de la app),
 * para no ofrecerlos como disponibles.
 *
 * <ul>
 *   <li>Fase 3, con {@code calendario.externo=true}: se los pide al servicio de calendario por la ruta
 *       interna del gateway (sección 4.2), que responde desde su copia sincronizada con Google.</li>
 *   <li>Sin el flag (vuelta atrás, tests): los lee de Google como antes, con
 *       {@link GoogleCalendarService#obtenerEventosDelDia}.</li>
 *   <li>Si el calendario no responde, devuelve una lista vacía: la agenda se ofrece sin ese filtro,
 *       igual que cuando Google falla (sección 7).</li>
 * </ul>
 */
@Component
public class HorariosOcupadosCliente {

    private static final Logger log = LoggerFactory.getLogger(HorariosOcupadosCliente.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Un intervalo ocupado del día; {@code todoElDia} bloquea el día entero. */
    public record Intervalo(@NotNull LocalTime inicio, @NotNull LocalTime fin, boolean todoElDia) {}

    private final GoogleCalendarService googleCalendarService;
    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public HorariosOcupadosCliente(GoogleCalendarService googleCalendarService,
                                   @Value("${calendario.externo:false}") boolean externo,
                                   @Value("${tranqui.gateway.url:}") String gatewayUrl,
                                   @Value("${tranqui.jwt.service-secret:}") String claveServicio) {
        this.googleCalendarService = googleCalendarService;
        boolean remoto = externo && gatewayUrl != null && !gatewayUrl.isBlank()
                && claveServicio != null && !claveServicio.isBlank();
        if (remoto) {
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(2_000);
            fabrica.setReadTimeout(3_000);
            this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
            this.serviceJwt = new ServiceJwt(claveServicio);
        } else {
            this.http = null;
            this.serviceJwt = null;
        }
    }

    // Misma caché corta que tenía la consulta a Google (CacheConfig): la home pública pide la
    // disponibilidad de cada profesional visible en cada carga.
    @Cacheable(value = "horariosOcupadosCalendario", key = "#medico.id + '-' + #fecha")
    public List<Intervalo> ocupados(Usuario medico, LocalDate fecha) {
        if (http == null) {
            return desdeGoogle(medico, fecha);
        }
        try {
            // Fase 10: los intervalos inválidos se saltean.
            List<Intervalo> intervalos = ValidadorEventos.respuesta(http.get()
                    .uri("/internal/calendario/profesionales/{id}/ocupado?fecha={fecha}", medico.getId(), fecha.toString())
                    .headers(h -> {
                        h.setBearerAuth(serviceJwt.emitirToken("monolito"));
                        String correlacion = MDC.get("correlationId");
                        h.set("X-Correlation-Id", correlacion != null ? correlacion : UUID.randomUUID().toString());
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Intervalo>>() {}), "/internal/calendario/profesionales/{id}/ocupado");
            return intervalos != null ? intervalos : Collections.emptyList();
        } catch (RuntimeException e) {
            log.warn("El calendario no respondió los horarios ocupados del profesional {} el {}; se ofrece la agenda sin ese filtro: {}",
                    medico.getId(), fecha, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Intervalo> desdeGoogle(Usuario medico, LocalDate fecha) {
        List<Intervalo> intervalos = new ArrayList<>();
        for (com.google.api.services.calendar.model.Event evento : googleCalendarService.obtenerEventosDelDia(medico, fecha)) {
            // Los eventos "transparent" (disponible) no bloquean la agenda.
            if ("transparent".equals(evento.getTransparency()) || evento.getStart() == null || evento.getEnd() == null) {
                continue;
            }
            if (evento.getStart().getDateTime() == null && evento.getStart().getDate() != null) {
                intervalos.add(new Intervalo(LocalTime.MIN, LocalTime.MAX, true));
            } else if (evento.getStart().getDateTime() != null && evento.getEnd().getDateTime() != null) {
                intervalos.add(new Intervalo(
                        Instant.ofEpochMilli(evento.getStart().getDateTime().getValue()).atZone(ZONA).toLocalTime(),
                        Instant.ofEpochMilli(evento.getEnd().getDateTime().getValue()).atZone(ZONA).toLocalTime(),
                        false));
            }
        }
        return intervalos;
    }
}
