package com.tranqui.app.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.tranqui.app.model.GoogleCalendarEventoExterno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.EventoExternoDto;
import com.tranqui.app.repository.GoogleCalendarEventoExternoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

// Reads a médico's Google Calendar and keeps GoogleCalendarEventoExterno (the cache the
// calendar UI / "Próximos Eventos" panel read from) in sync with it. Uses the Calendar API's
// syncToken so each run only fetches what changed since the last one instead of re-listing the
// médico's whole calendar every time. Triggered by GoogleCalendarPollingScheduler; a webhook
// trigger can call the same sincronizarIncremental() later without any change here.
@Service
public class GoogleCalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarSyncService.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Value("${google.calendar.enabled:false}")
    private boolean isEnabled;

    @Autowired
    private GoogleCalendarOAuthService googleCalendarOAuthService;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Autowired
    private GoogleCalendarEventoExternoRepository eventoExternoRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public void sincronizarIncremental(Usuario medico) {
        boolean medicoConectado = medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        if (!isEnabled || !medicoConectado) {
            return;
        }

        String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
        if (accessToken == null) {
            log.warn("No se pudo obtener Access Token para sincronizar el calendario del médico ID {}", medico.getId());
            return;
        }

        if (medico.getGoogleSyncToken() == null) {
            sincronizacionCompleta(medico, accessToken);
            return;
        }

        Calendar service = GoogleCalendarService.construirCliente(accessToken);
        String calendarId = calendarId(medico);

        try {
            String pageToken = null;
            String nextSyncToken = null;
            do {
                Events events = service.events().list(calendarId)
                        .setSyncToken(medico.getGoogleSyncToken())
                        .setShowDeleted(true)
                        .setSingleEvents(true)
                        .setPageToken(pageToken)
                        .execute();

                for (Event item : events.getItems()) {
                    aplicarCambio(medico, item);
                }

                pageToken = events.getNextPageToken();
                if (events.getNextSyncToken() != null) {
                    nextSyncToken = events.getNextSyncToken();
                }
            } while (pageToken != null);

            if (nextSyncToken != null) {
                medico.setGoogleSyncToken(nextSyncToken);
                usuarioRepository.save(medico);
            }

            exportarTurnosPendientesAGoogleCalendar(medico);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 410) {
                // Sync token expired/invalidated by Google — the only recovery is a full resync.
                log.info("Sync token inválido (410) para médico ID {}. Reiniciando con sincronización completa.", medico.getId());
                medico.setGoogleSyncToken(null);
                sincronizacionCompleta(medico, accessToken);
            } else {
                log.error("Error de Google Calendar API al sincronizar médico ID {}: {}", medico.getId(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("Fallo al sincronizar Google Calendar para médico ID {}", medico.getId(), e);
        }
    }

    @Transactional
    public void sincronizacionCompleta(Usuario medico) {
        boolean medicoConectado = medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        if (!isEnabled || !medicoConectado) {
            return;
        }
        String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
        if (accessToken == null) {
            return;
        }
        sincronizacionCompleta(medico, accessToken);
    }

    private void sincronizacionCompleta(Usuario medico, String accessToken) {
        Calendar service = GoogleCalendarService.construirCliente(accessToken);
        String calendarId = calendarId(medico);

        // Bounded window so a médico with years of calendar history doesn't get fully re-read
        // every time the token is invalidated — matches what the UI actually needs to show.
        ZonedDateTime timeMin = ZonedDateTime.now(ZONA).minusDays(7);
        ZonedDateTime timeMax = ZonedDateTime.now(ZONA).plusDays(90);

        try {
            eventoExternoRepository.deleteByMedicoId(medico.getId());

            String pageToken = null;
            String nextSyncToken = null;
            do {
                Events events = service.events().list(calendarId)
                        .setTimeMin(new com.google.api.client.util.DateTime(timeMin.toInstant().toEpochMilli()))
                        .setTimeMax(new com.google.api.client.util.DateTime(timeMax.toInstant().toEpochMilli()))
                        .setSingleEvents(true)
                        .setPageToken(pageToken)
                        .execute();

                for (Event item : events.getItems()) {
                    aplicarCambio(medico, item);
                }

                pageToken = events.getNextPageToken();
                if (events.getNextSyncToken() != null) {
                    nextSyncToken = events.getNextSyncToken();
                }
            } while (pageToken != null);

            medico.setGoogleSyncToken(nextSyncToken);
            usuarioRepository.save(medico);

            exportarTurnosPendientesAGoogleCalendar(medico);
        } catch (Exception e) {
            log.error("Fallo en la sincronización completa de Google Calendar para médico ID {}", medico.getId(), e);
        }
    }

    @Transactional
    public void exportarTurnosPendientesAGoogleCalendar(Usuario medico) {
        boolean medicoConectado = medico != null && medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        if (!isEnabled || !medicoConectado) {
            return;
        }

        java.time.LocalDate hoy = java.time.LocalDate.now();
        List<com.tranqui.app.model.Turno> turnos = turnoRepository.findTurnosFuturosConfirmadosParaGoogle(
                medico.getId(), hoy, com.tranqui.app.model.EstadoTurno.CONFIRMADO
        );

        for (com.tranqui.app.model.Turno turno : turnos) {
            if (turno.getGoogleEventId() == null || turno.getGoogleEventId().isBlank()) {
                try {
                    String meetUrl = googleCalendarService.crearEventoReunion(turno);
                    // crearEventoReunion no tira excepción si Google falla: devuelve un Meet simulado
                    // sin setear googleEventId. Solo pisamos el link del paciente cuando el evento
                    // real se creó — si no, cada pasada (cada 5 min) le cambiaría el link por otro
                    // falso hasta que Google responda.
                    if (turno.getGoogleEventId() == null || turno.getGoogleEventId().isBlank()) {
                        log.warn("Turno ID {} todavía no se pudo exportar a Google Calendar; se reintenta en la próxima sincronización.", turno.getId());
                        continue;
                    }
                    boolean linkNuevo = turno.getModalidad() == com.tranqui.app.model.Modalidad.ONLINE
                            && meetUrl != null && !meetUrl.isBlank() && !meetUrl.equals(turno.getTelemedicinaUrl());
                    if (linkNuevo) {
                        turno.setTelemedicinaUrl(meetUrl);
                    }
                    turnoRepository.save(turno);
                    log.info("Turno ID {} exportado con éxito a Google Calendar (Event ID: {})", turno.getId(), turno.getGoogleEventId());
                    // El paciente recibió la confirmación sin link (o con uno que ya no sirve):
                    // le avisamos que el link real está listo.
                    if (linkNuevo && turno.getPaciente() != null && turno.getPaciente().getEmail() != null
                            && turno.getPaciente().isNotificacionesEmailHabilitadas()) {
                        try {
                            resendEmailService.enviarLinkVideollamadaPaciente(
                                    turno.getPaciente().getEmail(), turno.getPaciente().getNombre(), medico.getNombre(),
                                    turno.getFecha(), turno.getHoraInicio(), meetUrl);
                        } catch (Exception e) {
                            log.error("Error al enviar el link de videollamada al paciente del turno ID {}", turno.getId(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Fallo al exportar turno ID {} a Google Calendar para médico ID {}", turno.getId(), medico.getId(), e);
                }
            }
        }
    }

    private void aplicarCambio(Usuario medico, Event googleEvent) {
        // Events the app itself created (turnos) live in Turno.googleEventId already —
        // never cache them a second time as an "external" event.
        if (turnoRepository.existsByMedicoIdAndGoogleEventId(medico.getId(), googleEvent.getId())) {
            eventoExternoRepository.deleteByGoogleEventId(googleEvent.getId());
            return;
        }

        if ("cancelled".equals(googleEvent.getStatus())) {
            eventoExternoRepository.deleteByGoogleEventId(googleEvent.getId());
            return;
        }

        LocalDateTime inicio = aLocalDateTime(googleEvent.getStart());
        LocalDateTime fin = aLocalDateTime(googleEvent.getEnd());
        if (inicio == null || fin == null) {
            return;
        }
        boolean todoElDia = googleEvent.getStart() != null && googleEvent.getStart().getDate() != null;

        Optional<GoogleCalendarEventoExterno> existenteOpt = eventoExternoRepository.findByGoogleEventId(googleEvent.getId());
        GoogleCalendarEventoExterno evento = existenteOpt.orElseGet(() -> GoogleCalendarEventoExterno.builder()
                .medico(medico)
                .googleEventId(googleEvent.getId())
                .build());

        evento.setTitulo(googleEvent.getSummary() != null ? googleEvent.getSummary() : "(Sin título)");
        evento.setFechaInicio(inicio);
        evento.setFechaFin(fin);
        evento.setTodoElDia(todoElDia);
        evento.setEstado(googleEvent.getStatus());
        evento.setUltimaActualizacion(LocalDateTime.now());

        eventoExternoRepository.save(evento);
    }

    private LocalDateTime aLocalDateTime(EventDateTime eventDateTime) {
        if (eventDateTime == null) {
            return null;
        }
        com.google.api.client.util.DateTime dt = eventDateTime.getDateTime() != null
                ? eventDateTime.getDateTime()
                : eventDateTime.getDate();
        if (dt == null) {
            return null;
        }
        return java.time.Instant.ofEpochMilli(dt.getValue()).atZone(
                eventDateTime.getDateTime() != null ? ZONA : ZoneId.of("UTC")
        ).toLocalDateTime();
    }

    private String calendarId(Usuario medico) {
        return medico.getGoogleCalendarId() != null ? medico.getGoogleCalendarId() : "primary";
    }

    // Deletes every Google Calendar event TranquiApp created for this médico's turnos (the
    // Meet events tracked via Turno.googleEventId — distinct from GoogleCalendarEventoExterno,
    // which caches the médico's own personal events and is never something we'd delete from
    // their real calendar). Must run BEFORE GoogleCalendarOAuthService.desvincular() clears the
    // OAuth tokens, since eliminarEventoReunion needs a still-valid access token to call the
    // Calendar API. A failure deleting one event never blocks the rest — same best-effort
    // pattern eliminarEventoReunion already uses when a turno is cancelled individually.
    @Transactional
    public void eliminarEventosCreadosPorLaApp(Usuario medico) {
        List<com.tranqui.app.model.Turno> turnos = turnoRepository.findByMedicoIdAndGoogleEventIdIsNotNull(medico.getId());
        if (turnos.isEmpty()) {
            return;
        }
        for (com.tranqui.app.model.Turno turno : turnos) {
            try {
                googleCalendarService.eliminarEventoReunion(turno);
            } catch (Exception e) {
                log.error("Fallo al eliminar el evento de Google Calendar del turno ID {} al desvincular al médico ID {}", turno.getId(), medico.getId(), e);
            }
            turno.setGoogleEventId(null);
        }
        turnoRepository.saveAll(turnos);
    }

    @Transactional(readOnly = true)
    public List<EventoExternoDto> obtenerEventosExternosCacheados(Usuario medico) {
        return eventoExternoRepository.findByMedicoId(medico.getId()).stream()
                .map(e -> EventoExternoDto.builder()
                        .id(e.getGoogleEventId())
                        .title(e.getTitulo())
                        .fecha(e.getFechaInicio().toLocalDate().toString())
                        .hour(String.format("%02d:%02d", e.getFechaInicio().getHour(), e.getFechaInicio().getMinute()))
                        .endHour(String.format("%02d:%02d", e.getFechaFin().getHour(), e.getFechaFin().getMinute()))
                        .allDay(Boolean.TRUE.equals(e.getTodoElDia()))
                        .build())
                .toList();
    }
}
