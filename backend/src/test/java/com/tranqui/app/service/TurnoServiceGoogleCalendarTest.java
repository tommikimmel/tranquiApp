package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class TurnoServiceGoogleCalendarTest {

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private com.tranqui.app.repository.DisponibilidadRepository disponibilidadRepository;

    @MockBean
    private GoogleCalendarService googleCalendarService;

    private Usuario medico;
    private Usuario paciente;
    private com.tranqui.app.model.Disponibilidad disponibilidad;
    private List<Turno> createdTurnos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Medico Google")
                .email("medico.google@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .googleCalendarConnected(true)
                .build();
        usuarioRepository.save(medico);

        paciente = Usuario.builder()
                .nombre("Paciente Google")
                .email("paciente.google@gmail.com")
                .rol(Rol.PACIENTE)
                .build();
        usuarioRepository.save(paciente);

        disponibilidad = com.tranqui.app.model.Disponibilidad.builder()
                .medico(medico)
                .diaSemana(LocalDate.now().plusDays(2).getDayOfWeek().getValue())
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(12, 0))
                .build();
        disponibilidadRepository.save(disponibilidad);
    }

    @AfterEach
    void tearDown() {
        for (Turno t : createdTurnos) {
            try { turnoRepository.delete(t); } catch (Exception e) {}
        }
        try { disponibilidadRepository.delete(disponibilidad); } catch (Exception e) {}
        try { usuarioRepository.delete(paciente); } catch (Exception e) {}
        try { usuarioRepository.delete(medico); } catch (Exception e) {}
    }

    @Test
    void testObtenerHorariosDisponibles_FiltradosPorGoogleCalendar() {
        LocalDate fecha = LocalDate.now().plusDays(2);
        
        // Simular un evento en Google Calendar que ocupa de 9:45 a 10:30
        com.google.api.services.calendar.model.Event eventoOcupado = new com.google.api.services.calendar.model.Event();
        eventoOcupado.setSummary("Reunión Ocupada");
        
        java.time.ZonedDateTime startZoned = java.time.ZonedDateTime.of(fecha, LocalTime.of(9, 45), java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        java.time.ZonedDateTime endZoned = java.time.ZonedDateTime.of(fecha, LocalTime.of(10, 30), java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        
        eventoOcupado.setStart(new com.google.api.services.calendar.model.EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(startZoned.toInstant().toEpochMilli())));
        eventoOcupado.setEnd(new com.google.api.services.calendar.model.EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endZoned.toInstant().toEpochMilli())));

        when(googleCalendarService.obtenerEventosDelDia(any(Usuario.class), eq(fecha)))
                .thenReturn(Collections.singletonList(eventoOcupado));

        List<LocalTime> disponibles = turnoService.obtenerHorariosDisponibles(medico.getId(), fecha, Modalidad.ONLINE);

        // This médico doesn't configure duracionTurnoMinutos/intervaloEntreTurnosMinutos, so
        // TurnoService falls back to duracion=45 and intervalo=0 (see TurnoService and
        // MedicoService's DEFAULT_* constants) — candidates step every 45 minutes with no gap,
        // not the old implicit hourly cadence. La disponibilidad local de 9:00 a 12:00 en
        // bloques de 45 minutos consecutivos es:
        // 9:00  (9:00 - 9:45)   -> libre
        // 9:45  (9:45 - 10:30)  -> Excluido (coincide exactamente con el evento ocupado 9:45-10:30)
        // 10:30 (10:30 - 11:15) -> libre (el evento termina justo cuando empieza este bloque)
        // 11:15 (11:15 - 12:00) -> libre

        assertNotNull(disponibles);
        assertEquals(3, disponibles.size());
        assertTrue(disponibles.contains(LocalTime.of(9, 0)));
        assertFalse(disponibles.contains(LocalTime.of(9, 45))); // Excluido
        assertTrue(disponibles.contains(LocalTime.of(10, 30)));
        assertTrue(disponibles.contains(LocalTime.of(11, 15)));
    }

    @Test
    void testCancelarTurno_TriggerGoogleDelete() {
        Turno turno = Turno.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.now().plusDays(2))
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .googleEventId("mock-google-event-id")
                .build();
        turnoRepository.save(turno);
        createdTurnos.add(turno);

        doNothing().when(googleCalendarService).eliminarEventoReunion(any(Turno.class));

        turnoService.cancelarTurno(turno.getId(), medico.getEmail());

        verify(googleCalendarService, times(1)).eliminarEventoReunion(any(Turno.class));
        Turno dbTurno = turnoRepository.findById(turno.getId()).orElse(null);
        assertNotNull(dbTurno);
        assertEquals(EstadoTurno.CANCELADO, dbTurno.getEstado());
    }

    @Test
    void testReprogramarTurno_TriggerGoogleUpdate() {
        Turno turno = Turno.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.now().plusDays(2))
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .googleEventId("mock-google-event-id")
                .build();
        turnoRepository.save(turno);
        createdTurnos.add(turno);

        doNothing().when(googleCalendarService).actualizarEventoReunion(any(Turno.class));

        LocalDate nuevaFecha = LocalDate.now().plusDays(3);
        turnoService.reprogramarTurno(turno.getId(), nuevaFecha.toString(), "10:30");

        verify(googleCalendarService, times(1)).actualizarEventoReunion(any(Turno.class));
        Turno dbTurno = turnoRepository.findById(turno.getId()).orElse(null);
        assertNotNull(dbTurno);
        assertEquals(nuevaFecha, dbTurno.getFecha());
        assertEquals(LocalTime.of(10, 30), dbTurno.getHoraInicio());
    }
}
