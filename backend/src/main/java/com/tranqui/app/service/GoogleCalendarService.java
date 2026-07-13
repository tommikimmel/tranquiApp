package com.tranqui.app.service;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.EntryPoint;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.UUID;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    @Value("${google.calendar.enabled:false}")
    private boolean isEnabled;

    @Autowired
    private GoogleCalendarOAuthService googleCalendarOAuthService;

    private String generarMeetUrl() {
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder("https://meet.google.com/");
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        sb.append("-");
        for (int i = 0; i < 4; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        sb.append("-");
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    public String crearEventoReunion(Turno turno) {
        log.info("Iniciando creación de evento en Google Calendar para el turno ID: {}", turno.getId());

        // For testing simulated connection failures:
        if (turno.getMetadataAfiliado() != null && turno.getMetadataAfiliado().contains("FAIL_CALENDAR")) {
            log.error("Fallo simulado de Google Calendar API.");
            throw new RuntimeException("Simulated Rate Limit / Token Expired on Google Calendar API");
        }

        Usuario medico = turno.getMedico();
        boolean medicoConectado = medico != null && medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();

        if (isEnabled && medicoConectado) {
            try {
                String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
                if (accessToken != null) {
                    HttpRequestInitializer requestInitializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
                    Calendar service = new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
                            .setApplicationName("TranquiApp")
                            .build();

                    Event event = new Event()
                            .setSummary("[Tranqui App] Consulta - " + (turno.getPaciente() != null ? turno.getPaciente().getNombre() : "Paciente"))
                            .setDescription("Consulta de telemedicina con Tranqui App");

                    java.time.ZonedDateTime startZoned = java.time.ZonedDateTime.of(turno.getFecha(), turno.getHoraInicio(), java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
                    java.time.ZonedDateTime endZoned = java.time.ZonedDateTime.of(turno.getFecha(), turno.getHoraFin(), java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

                    com.google.api.client.util.DateTime startDateTime = new com.google.api.client.util.DateTime(startZoned.toInstant().toEpochMilli());
                    EventDateTime start = new EventDateTime()
                            .setDateTime(startDateTime)
                            .setTimeZone("America/Argentina/Buenos_Aires");
                    event.setStart(start);

                    com.google.api.client.util.DateTime endDateTime = new com.google.api.client.util.DateTime(endZoned.toInstant().toEpochMilli());
                    EventDateTime end = new EventDateTime()
                            .setDateTime(endDateTime)
                            .setTimeZone("America/Argentina/Buenos_Aires");
                    event.setEnd(end);

                    ConferenceSolutionKey solutionKey = new ConferenceSolutionKey().setType("hangoutsMeet");
                    CreateConferenceRequest createRequest = new CreateConferenceRequest()
                            .setRequestId(UUID.randomUUID().toString())
                            .setConferenceSolutionKey(solutionKey);
                    ConferenceData conferenceData = new ConferenceData().setCreateRequest(createRequest);
                    event.setConferenceData(conferenceData);

                    event = service.events().insert("primary", event)
                            .setConferenceDataVersion(1)
                            .execute();

                    String meetUrl = "";
                    if (event.getConferenceData() != null && event.getConferenceData().getEntryPoints() != null) {
                        for (EntryPoint entryPoint : event.getConferenceData().getEntryPoints()) {
                            if ("video".equals(entryPoint.getEntryPointType())) {
                                meetUrl = entryPoint.getUri();
                                break;
                            }
                        }
                    }
                    if (meetUrl.isEmpty() && event.getHangoutLink() != null) {
                        meetUrl = event.getHangoutLink();
                    }

                    if (!meetUrl.isEmpty()) {
                        turno.setGoogleEventId(event.getId());
                        log.info("Evento creado con éxito en Google Calendar REAL. Event ID: {}, Meet URL: {}", event.getId(), meetUrl);
                        return meetUrl;
                    }
                }
                log.warn("No se pudo obtener el Access Token para el médico ID: {}. Usando fallback simulado.", medico.getId());
            } catch (Exception e) {
                log.error("Fallo al crear evento REAL en Google Calendar para turno ID: {}. Error: {}", turno.getId(), e.getMessage());
                // Fallback en caso de error para no romper la reserva del turno
            }
        }

        log.info("Usando simulación de Google Calendar API. Retornando Meet URL mock dinámico.");
        return generarMeetUrl();
    }

    public java.util.List<com.google.api.services.calendar.model.Event> obtenerEventosDelDia(Usuario medico, java.time.LocalDate fecha) {
        boolean medicoConectado = medico != null && medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        if (!isEnabled || !medicoConectado) {
            return java.util.Collections.emptyList();
        }

        try {
            String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
            if (accessToken != null) {
                HttpRequestInitializer requestInitializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
                Calendar service = new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
                        .setApplicationName("TranquiApp")
                        .build();

                java.time.ZonedDateTime startOfDay = java.time.ZonedDateTime.of(fecha, java.time.LocalTime.MIN, java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
                java.time.ZonedDateTime endOfDay = java.time.ZonedDateTime.of(fecha, java.time.LocalTime.MAX, java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

                com.google.api.client.util.DateTime startDateTime = new com.google.api.client.util.DateTime(startOfDay.toInstant().toEpochMilli());
                com.google.api.client.util.DateTime endDateTime = new com.google.api.client.util.DateTime(endOfDay.toInstant().toEpochMilli());

                com.google.api.services.calendar.model.Events events = service.events().list("primary")
                        .setTimeMin(startDateTime)
                        .setTimeMax(endDateTime)
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .execute();

                if (events != null && events.getItems() != null) {
                    return events.getItems();
                }
            }
        } catch (Exception e) {
            log.error("Error al obtener eventos de Google Calendar para el médico ID: {}", medico.getId(), e);
        }
        return java.util.Collections.emptyList();
    }

    public void eliminarEventoReunion(Turno turno) {
        Usuario medico = turno.getMedico();
        boolean medicoConectado = medico != null && medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        String eventId = turno.getGoogleEventId();

        if (isEnabled && medicoConectado && eventId != null && !eventId.isEmpty()) {
            try {
                String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
                if (accessToken != null) {
                    HttpRequestInitializer requestInitializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
                    Calendar service = new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
                            .setApplicationName("TranquiApp")
                            .build();

                    service.events().delete("primary", eventId).execute();
                    log.info("Evento eliminado con éxito de Google Calendar. Event ID: {}", eventId);
                }
            } catch (Exception e) {
                log.error("Fallo al eliminar evento en Google Calendar para turno ID: {}. Error: {}", turno.getId(), e.getMessage());
            }
        }
    }

    public void actualizarEventoReunion(Turno turno) {
        Usuario medico = turno.getMedico();
        boolean medicoConectado = medico != null && medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        String eventId = turno.getGoogleEventId();

        if (isEnabled && medicoConectado && eventId != null && !eventId.isEmpty()) {
            try {
                String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
                if (accessToken != null) {
                    HttpRequestInitializer requestInitializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
                    Calendar service = new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
                            .setApplicationName("TranquiApp")
                            .build();

                    Event event = service.events().get("primary", eventId).execute();

                    java.time.ZonedDateTime startZoned = java.time.ZonedDateTime.of(turno.getFecha(), turno.getHoraInicio(), java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
                    java.time.ZonedDateTime endZoned = java.time.ZonedDateTime.of(turno.getFecha(), turno.getHoraFin(), java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

                    com.google.api.client.util.DateTime startDateTime = new com.google.api.client.util.DateTime(startZoned.toInstant().toEpochMilli());
                    EventDateTime start = new EventDateTime()
                            .setDateTime(startDateTime)
                            .setTimeZone("America/Argentina/Buenos_Aires");
                    event.setStart(start);

                    com.google.api.client.util.DateTime endDateTime = new com.google.api.client.util.DateTime(endZoned.toInstant().toEpochMilli());
                    EventDateTime end = new EventDateTime()
                            .setDateTime(endDateTime)
                            .setTimeZone("America/Argentina/Buenos_Aires");
                    event.setEnd(end);

                    service.events().update("primary", eventId, event).execute();
                    log.info("Evento actualizado con éxito en Google Calendar. Event ID: {}", eventId);
                }
            } catch (Exception e) {
                log.error("Fallo al actualizar evento en Google Calendar para turno ID: {}. Error: {}", turno.getId(), e.getMessage());
            }
        }
    }
}
