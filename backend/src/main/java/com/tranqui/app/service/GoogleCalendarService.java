package com.tranqui.app.service;

import com.google.api.services.calendar.model.Event;
import com.tranqui.app.model.Turno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.UUID;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    @Value("${google.calendar.enabled:false}")
    private boolean isEnabled;

    public String crearEventoReunion(Turno turno) {
        log.info("Iniciando creación de evento en Google Calendar para el turno ID: {}", turno.getId());

        // For testing simulated connection failures:
        if (turno.getMetadataAfiliado() != null && turno.getMetadataAfiliado().contains("FAIL_CALENDAR")) {
            log.error("Fallo simulado de Google Calendar API.");
            throw new RuntimeException("Simulated Rate Limit / Token Expired on Google Calendar API");
        }

        if (!isEnabled) {
            log.info("Google Calendar API deshabilitada (simulación). Retornando Meet URL mock.");
            return "https://meet.google.com/abc-defg-hij";
        }

        try {
            // Simulated connection to Google API
            Event event = new Event()
                    .setSummary("[OSDE] Consulta - " + (turno.getPaciente() != null ? turno.getPaciente().getNombre() : "Paciente"))
                    .setDescription("Consulta de telemedicina con Tranqui App");

            // Mock return URL simulating Meet creation response
            String meetUrl = "https://meet.google.com/" + UUID.randomUUID().toString().substring(0, 10);
            log.info("Evento creado con éxito en Google Calendar. Meet URL: {}", meetUrl);
            return meetUrl;

        } catch (Exception e) {
            log.error("Fallo al crear evento en Google Calendar para turno ID: {}. Error: {}", turno.getId(), e.getMessage());
            throw new RuntimeException("Error en sincronización con Google Calendar: " + e.getMessage(), e);
        }
    }
}
