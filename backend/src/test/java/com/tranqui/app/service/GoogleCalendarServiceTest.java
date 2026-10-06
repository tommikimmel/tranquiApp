package com.tranqui.app.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock private GoogleCalendarOAuthService googleCalendarOAuthService;

    private GoogleCalendarService googleCalendarService;

    @BeforeEach
    void setUp() {
        googleCalendarService = new GoogleCalendarService();
        ReflectionTestUtils.setField(googleCalendarService, "isEnabled", true);
        ReflectionTestUtils.setField(googleCalendarService, "googleCalendarOAuthService", googleCalendarOAuthService);
    }

    @Test
    void testCrearEventoReunionNotEnabled() {
        ReflectionTestUtils.setField(googleCalendarService, "isEnabled", false);
        Turno turno = Turno.builder()
                .id(1L)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR)
                .build();

        String url = googleCalendarService.crearEventoReunion(turno);
        assertNotNull(url);
        assertTrue(url.startsWith("https://meet.google.com/"));
    }

    @Test
    void testObtenerEventosDelDiaNotConnected() {
        Usuario medico = new Usuario();
        medico.setId(123L);
        medico.setGoogleCalendarConnected(false);

        List<Event> eventos = googleCalendarService.obtenerEventosDelDia(medico, LocalDate.now());
        assertTrue(eventos.isEmpty());
    }

    @Test
    void testEliminarEventoReunionNotConnected() {
        Usuario medico = new Usuario();
        medico.setId(123L);
        medico.setGoogleCalendarConnected(false);

        Turno turno = Turno.builder()
                .id(1L)
                .medico(medico)
                .googleEventId("some-event-id")
                .build();

        googleCalendarService.eliminarEventoReunion(turno);
        verify(googleCalendarOAuthService, never()).obtenerAccessToken(any(Usuario.class));
    }

    @Test
    void testActualizarEventoReunionNotConnected() {
        Usuario medico = new Usuario();
        medico.setId(123L);
        medico.setGoogleCalendarConnected(false);

        Turno turno = Turno.builder()
                .id(1L)
                .medico(medico)
                .googleEventId("some-event-id")
                .build();

        googleCalendarService.actualizarEventoReunion(turno);
        verify(googleCalendarOAuthService, never()).obtenerAccessToken(any(Usuario.class));
    }
}
