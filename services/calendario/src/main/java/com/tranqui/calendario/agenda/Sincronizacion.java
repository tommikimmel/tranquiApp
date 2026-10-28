package com.tranqui.calendario.agenda;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.TurnosAgendados;
import com.tranqui.calendario.google.GoogleCalendarApi;
import com.tranqui.calendario.google.TokensGoogle;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.HorarioBloqueadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Mantiene la caché de eventos propios del Google Calendar de cada profesional (antes
 * GoogleCalendarSyncService del monolito): incremental con el syncToken de Google, completa la
 * primera vez o cuando Google invalida el token (410). Cada evento guardado se publica como
 * {@code calendario.horario.bloqueado.v1} (sin consumidores hasta la Fase 6, como hoy). Al final de
 * cada pasada reintenta los turnos que quedaron sin evento.
 */
@Service
public class Sincronizacion {

    private static final Logger log = LoggerFactory.getLogger(Sincronizacion.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final CuentasGoogle cuentas;
    private final EventosExternos externos;
    private final TurnosAgendados turnos;
    private final TokensGoogle tokens;
    private final GoogleCalendarApi google;
    private final AgendaTurnos agenda;
    private final OutboxPublisher outbox;
    private final boolean googleHabilitado;

    public Sincronizacion(CuentasGoogle cuentas, EventosExternos externos, TurnosAgendados turnos, TokensGoogle tokens,
                          GoogleCalendarApi google, AgendaTurnos agenda, OutboxPublisher outbox,
                          @Value("${google.calendar.enabled:false}") boolean googleHabilitado) {
        this.cuentas = cuentas;
        this.externos = externos;
        this.turnos = turnos;
        this.tokens = tokens;
        this.google = google;
        this.agenda = agenda;
        this.outbox = outbox;
        this.googleHabilitado = googleHabilitado;
    }

    @Transactional
    public void incremental(Long profesionalId) {
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(profesionalId).orElse(null);
        if (!googleHabilitado || cuenta == null) {
            return;
        }
        String accessToken = tokens.accessToken(cuenta);
        if (accessToken == null) {
            log.warn("Sin access token para sincronizar el calendario del profesional {}", profesionalId);
            return;
        }
        if (cuenta.syncToken() == null) {
            completa(cuenta, accessToken);
            return;
        }
        try {
            String pagina = null;
            String siguienteSync = null;
            do {
                GoogleCalendarApi.Pagina resultado = google.cambios(accessToken, cuenta.calendario(), cuenta.syncToken(), pagina);
                aplicar(profesionalId, resultado);
                pagina = resultado.siguientePagina();
                if (resultado.siguienteSyncToken() != null) {
                    siguienteSync = resultado.siguienteSyncToken();
                }
            } while (pagina != null);
            if (siguienteSync != null) {
                cuentas.guardarSyncToken(profesionalId, siguienteSync);
            }
            agenda.exportarPendientes(profesionalId);
        } catch (GoogleCalendarApi.SyncTokenVencidoException e) {
            log.info("Sync token vencido (410) del profesional {}: sincronización completa", profesionalId);
            completa(cuenta, accessToken);
        } catch (Exception e) {
            log.error("Fallo al sincronizar el Google Calendar del profesional {}: {}", profesionalId, e.getMessage());
        }
    }

    @Transactional
    public void completa(Long profesionalId) {
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(profesionalId).orElse(null);
        if (!googleHabilitado || cuenta == null) {
            return;
        }
        String accessToken = tokens.accessToken(cuenta);
        if (accessToken != null) {
            completa(cuenta, accessToken);
        }
    }

    private void completa(CuentasGoogle.Cuenta cuenta, String accessToken) {
        // Ventana acotada, como el monolito: lo que muestra la app, no años de historia.
        ZonedDateTime desde = ZonedDateTime.now(ZONA).minusDays(7);
        ZonedDateTime hasta = ZonedDateTime.now(ZONA).plusDays(90);
        try {
            externos.borrarDeProfesional(cuenta.profesionalId());
            String pagina = null;
            String siguienteSync = null;
            do {
                GoogleCalendarApi.Pagina resultado = google.ventana(accessToken, cuenta.calendario(), desde, hasta, pagina);
                aplicar(cuenta.profesionalId(), resultado);
                pagina = resultado.siguientePagina();
                if (resultado.siguienteSyncToken() != null) {
                    siguienteSync = resultado.siguienteSyncToken();
                }
            } while (pagina != null);
            cuentas.guardarSyncToken(cuenta.profesionalId(), siguienteSync);
            agenda.exportarPendientes(cuenta.profesionalId());
        } catch (Exception e) {
            log.error("Fallo en la sincronización completa del profesional {}: {}", cuenta.profesionalId(), e.getMessage());
        }
    }

    private void aplicar(Long profesionalId, GoogleCalendarApi.Pagina pagina) {
        if (pagina.eventos() == null) {
            return;
        }
        for (Event evento : pagina.eventos()) {
            aplicarCambio(profesionalId, evento);
        }
    }

    void aplicarCambio(Long profesionalId, Event evento) {
        // Los eventos que creó la app (turnos) ya están en turno_agendado: no son "externos".
        if (turnos.esEventoDeLaApp(profesionalId, evento.getId()) || "cancelled".equals(evento.getStatus())) {
            externos.borrarPorGoogleId(evento.getId());
            return;
        }
        LocalDateTime inicio = aLocalDateTime(evento.getStart());
        LocalDateTime fin = aLocalDateTime(evento.getEnd());
        if (inicio == null || fin == null) {
            return;
        }
        boolean todoElDia = evento.getStart().getDate() != null && evento.getStart().getDateTime() == null;
        String titulo = evento.getSummary() != null ? evento.getSummary() : "(Sin título)";
        EventosExternos.Guardado guardado = externos.guardar(profesionalId, evento.getId(), titulo, inicio, fin, todoElDia,
                "transparent".equals(evento.getTransparency()), evento.getStatus());
        outbox.publicar(Topicos.CALENDARIO_HORARIO_BLOQUEADO_V1, "evento_externo", String.valueOf(guardado.id()),
                guardado.version() + 1, new HorarioBloqueadoData(profesionalId, evento.getId(),
                        inicio.atZone(ZONA).toOffsetDateTime(), fin.atZone(ZONA).toOffsetDateTime(), titulo));
    }

    private static LocalDateTime aLocalDateTime(EventDateTime momento) {
        if (momento == null) {
            return null;
        }
        com.google.api.client.util.DateTime valor = momento.getDateTime() != null ? momento.getDateTime() : momento.getDate();
        if (valor == null) {
            return null;
        }
        // Los eventos de todo el día vienen como fecha (medianoche UTC).
        return Instant.ofEpochMilli(valor.getValue()).atZone(momento.getDateTime() != null ? ZONA : ZoneId.of("UTC"))
                .toLocalDateTime();
    }
}
