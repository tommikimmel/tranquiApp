package com.tranqui.app.service;

import com.tranqui.app.model.GoogleCalendarEventoExterno;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.EventoExternoDto;
import com.tranqui.app.repository.GoogleCalendarEventoExternoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncAndWatchUnitTest {

    @Mock
    private GoogleCalendarOAuthService googleCalendarOAuthService;

    @Mock
    private GoogleCalendarService googleCalendarService;

    @Mock
    private GoogleCalendarEventoExternoRepository eventoExternoRepository;

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ResendEmailService resendEmailService;

    @InjectMocks
    private GoogleCalendarSyncService syncService;

    @InjectMocks
    private GoogleCalendarWatchService watchService;

    @InjectMocks
    private GoogleCalendarWatchScheduler watchScheduler;

    @InjectMocks
    private GoogleCalendarPollingScheduler pollingScheduler;

    private Usuario medico;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .id(1L)
                .nombre("Dr. Carlos")
                .email("carlos@example.com")
                .rol(Rol.PSIQUIATRA)
                .googleCalendarConnected(true)
                .googleWatchActive(true)
                .googleWatchExpiresAt(LocalDateTime.now().plusHours(10))
                .build();

        ReflectionTestUtils.setField(syncService, "isEnabled", true);
        ReflectionTestUtils.setField(watchService, "calendarEnabled", true);
        ReflectionTestUtils.setField(watchService, "watchEnabled", true);
        ReflectionTestUtils.setField(watchService, "channelTokenSigningKey", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(watchScheduler, "googleCalendarWatchService", watchService);
        ReflectionTestUtils.setField(pollingScheduler, "googleCalendarSyncService", syncService);
        ReflectionTestUtils.setField(pollingScheduler, "isEnabled", true);
    }

    // --- GoogleCalendarSyncService tests ---

    @Test
    void syncIncremental_guards() {
        // Disabled
        ReflectionTestUtils.setField(syncService, "isEnabled", false);
        syncService.sincronizarIncremental(medico);
        verifyNoInteractions(googleCalendarOAuthService);

        // Not connected
        ReflectionTestUtils.setField(syncService, "isEnabled", true);
        medico.setGoogleCalendarConnected(false);
        syncService.sincronizarIncremental(medico);
        verifyNoInteractions(googleCalendarOAuthService);

        // No token
        medico.setGoogleCalendarConnected(true);
        when(googleCalendarOAuthService.obtenerAccessToken(medico)).thenReturn(null);
        syncService.sincronizarIncremental(medico);
        verify(googleCalendarOAuthService).obtenerAccessToken(medico);
    }

    @Test
    void sync_helpers() {
        // Eliminar eventos creados por la app
        Turno turno = Turno.builder().id(99L).googleEventId("evt-app-1").build();
        when(turnoRepository.findByMedicoIdAndGoogleEventIdIsNotNull(1L)).thenReturn(List.of(turno));
        syncService.eliminarEventosCreadosPorLaApp(medico);
        verify(googleCalendarService).eliminarEventoReunion(turno);
        assertNull(turno.getGoogleEventId());
        verify(turnoRepository).saveAll(any());

        // Obtener eventos cacheados
        GoogleCalendarEventoExterno evt = GoogleCalendarEventoExterno.builder()
                .googleEventId("ext-1")
                .titulo("Evento")
                .fechaInicio(LocalDateTime.now())
                .fechaFin(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoExternoRepository.findByMedicoId(1L)).thenReturn(List.of(evt));
        List<EventoExternoDto> dtos = syncService.obtenerEventosExternosCacheados(medico);
        assertEquals(1, dtos.size());
        assertEquals("ext-1", dtos.get(0).getId());
    }

    @Test
    void syncService_aplicarCambio_and_aLocalDateTime() {
        // 1. Own app turno -> deletes external event
        com.google.api.services.calendar.model.Event appTurnoEvent = new com.google.api.services.calendar.model.Event()
                .setId("app-evt-1");
        when(turnoRepository.existsByMedicoIdAndGoogleEventId(1L, "app-evt-1")).thenReturn(true);
        ReflectionTestUtils.invokeMethod(syncService, "aplicarCambio", medico, appTurnoEvent);
        verify(eventoExternoRepository).deleteByGoogleEventId("app-evt-1");

        // 2. Cancelled event -> deletes external event
        com.google.api.services.calendar.model.Event cancelledEvent = new com.google.api.services.calendar.model.Event()
                .setId("ext-cancel-1")
                .setStatus("cancelled");
        when(turnoRepository.existsByMedicoIdAndGoogleEventId(1L, "ext-cancel-1")).thenReturn(false);
        ReflectionTestUtils.invokeMethod(syncService, "aplicarCambio", medico, cancelledEvent);
        verify(eventoExternoRepository).deleteByGoogleEventId("ext-cancel-1");

        // 3. Normal external event -> saves/updates
        com.google.api.client.util.DateTime startDt = new com.google.api.client.util.DateTime(System.currentTimeMillis());
        com.google.api.client.util.DateTime endDt = new com.google.api.client.util.DateTime(System.currentTimeMillis() + 3600000);
        com.google.api.services.calendar.model.Event normalEvent = new com.google.api.services.calendar.model.Event()
                .setId("ext-normal-1")
                .setSummary("Reunion Externa")
                .setStatus("confirmed")
                .setStart(new com.google.api.services.calendar.model.EventDateTime().setDateTime(startDt))
                .setEnd(new com.google.api.services.calendar.model.EventDateTime().setDateTime(endDt));

        when(turnoRepository.existsByMedicoIdAndGoogleEventId(1L, "ext-normal-1")).thenReturn(false);
        when(eventoExternoRepository.findByGoogleEventId("ext-normal-1")).thenReturn(Optional.empty());
        ReflectionTestUtils.invokeMethod(syncService, "aplicarCambio", medico, normalEvent);
        verify(eventoExternoRepository).save(any(GoogleCalendarEventoExterno.class));

        // 4. aLocalDateTime tests
        assertNull(ReflectionTestUtils.invokeMethod(syncService, "aLocalDateTime", (com.google.api.services.calendar.model.EventDateTime) null));
        com.google.api.services.calendar.model.EventDateTime dateOnly = new com.google.api.services.calendar.model.EventDateTime()
                .setDate(new com.google.api.client.util.DateTime("2026-10-15"));
        LocalDateTime ldt = ReflectionTestUtils.invokeMethod(syncService, "aLocalDateTime", dateOnly);
        assertNotNull(ldt);

        // 5. sincronizacionCompleta guards
        ReflectionTestUtils.setField(syncService, "isEnabled", false);
        syncService.sincronizacionCompleta(medico);
        ReflectionTestUtils.setField(syncService, "isEnabled", true);
        medico.setGoogleCalendarConnected(false);
        syncService.sincronizacionCompleta(medico);
        medico.setGoogleCalendarConnected(true);
        when(googleCalendarOAuthService.obtenerAccessToken(medico)).thenReturn(null);
        syncService.sincronizacionCompleta(medico);
    }

    // --- GoogleCalendarService tests ---

    @Test
    void googleCalendarService_directTests() {
        GoogleCalendarService realService = new GoogleCalendarService();
        ReflectionTestUtils.setField(realService, "googleCalendarOAuthService", googleCalendarOAuthService);
        ReflectionTestUtils.setField(realService, "isEnabled", false);

        Turno turno = Turno.builder()
                .id(100L)
                .medico(medico)
                .fecha(java.time.LocalDate.now().plusDays(1))
                .horaInicio(java.time.LocalTime.of(10, 0))
                .horaFin(java.time.LocalTime.of(10, 45))
                .build();

        // Disabled (entorno local) -> returns mock Meet URL
        String meetUrl = realService.crearEventoReunion(turno);
        assertNotNull(meetUrl);
        assertTrue(meetUrl.contains("meet.google.com"));

        // Enabled (producción) but disconnected -> never a fake link: empty until the real event exists
        ReflectionTestUtils.setField(realService, "isEnabled", true);
        medico.setGoogleCalendarConnected(false);
        assertEquals("", realService.crearEventoReunion(turno));
        assertNull(turno.getGoogleEventId());

        // Enabled, connected, but token is null -> same
        medico.setGoogleCalendarConnected(true);
        when(googleCalendarOAuthService.obtenerAccessToken(medico)).thenReturn(null);
        assertEquals("", realService.crearEventoReunion(turno));

        // obtenerEventosDelDia when disabled or disconnected
        ReflectionTestUtils.setField(realService, "isEnabled", false);
        assertEquals(0, realService.obtenerEventosDelDia(medico, java.time.LocalDate.now()).size());

        // eliminarEventoReunion when disabled or no eventId
        realService.eliminarEventoReunion(turno);
        turno.setGoogleEventId("evt-1");
        realService.eliminarEventoReunion(turno);

        // actualizarEventoReunion when disabled or no eventId
        realService.actualizarEventoReunion(turno);

        // construirCliente
        assertNotNull(GoogleCalendarService.construirCliente("fake-token"));
    }

    // --- GoogleCalendarWatchService tests ---

    @Test
    void watchService_tokensAndValidation() {
        assertTrue(watchService.isWatchEnabled());

        String token = watchService.signChannelToken(1L);
        assertNotNull(token);
        assertEquals(1L, watchService.verificarChannelToken(token));

        // Invalid tokens
        assertNull(watchService.verificarChannelToken(null));
        assertNull(watchService.verificarChannelToken("no-dots"));
        assertNull(watchService.verificarChannelToken("abc.def"));
        assertNull(watchService.verificarChannelToken("1.tamperedhmac"));
    }

    @Test
    void watchService_guardsWhenDisabled() {
        ReflectionTestUtils.setField(watchService, "watchEnabled", false);
        assertFalse(watchService.isWatchEnabled());

        watchService.registrarCanal(medico);
        watchService.detenerCanal(medico);
        watchService.renovarCanal(medico);
    }

    // --- Schedulers ---

    @Test
    void watchScheduler_renewsExpiring() {
        when(usuarioRepository.findByGoogleWatchActiveTrueAndGoogleWatchExpiresAtBefore(any()))
                .thenReturn(List.of(medico));

        watchScheduler.renovarCanalesPorVencer();
        verify(usuarioRepository).findByGoogleWatchActiveTrueAndGoogleWatchExpiresAtBefore(any());
    }

    @Test
    void pollingScheduler_pollsConnectedMedicos() {
        when(usuarioRepository.findByRolAndGoogleCalendarConnectedTrue(Rol.PSIQUIATRA))
                .thenReturn(List.of(medico));

        pollingScheduler.sincronizarCalendariosConectados();
        verify(usuarioRepository).findByRolAndGoogleCalendarConnectedTrue(Rol.PSIQUIATRA);
    }

    // ── backfill de turnos sin evento en Google ──────────────────────

    @Test
    void exportarTurnosPendientes_creaElEventoYLeAvisaAlPacienteDelLinkReal() {
        Usuario paciente = Usuario.builder().id(2L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).build();
        Turno turno = Turno.builder()
                .id(200L).medico(medico).paciente(paciente)
                .fecha(java.time.LocalDate.now().plusDays(1))
                .horaInicio(java.time.LocalTime.of(10, 0)).horaFin(java.time.LocalTime.of(10, 45))
                .modalidad(com.tranqui.app.model.Modalidad.ONLINE)
                .build();
        when(turnoRepository.findTurnosFuturosConfirmadosParaGoogle(eq(1L), any(), any())).thenReturn(List.of(turno));
        when(googleCalendarService.crearEventoReunion(turno)).thenAnswer(i -> {
            turno.setGoogleEventId("evt-real");
            return "https://meet.google.com/abc-defg-hij";
        });

        syncService.exportarTurnosPendientesAGoogleCalendar(medico);

        assertEquals("https://meet.google.com/abc-defg-hij", turno.getTelemedicinaUrl());
        verify(turnoRepository).save(turno);
        verify(resendEmailService).enviarLinkVideollamadaPaciente(eq("ana@mail.com"), eq("Ana"), eq("Dr. Carlos"),
                eq(turno.getFecha()), eq(turno.getHoraInicio()), eq("https://meet.google.com/abc-defg-hij"));
    }

    @Test
    void exportarTurnosPendientes_siGoogleSigueFallandoNoPisaElLinkNiAvisa() {
        Usuario paciente = Usuario.builder().id(2L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).build();
        Turno turno = Turno.builder()
                .id(201L).medico(medico).paciente(paciente)
                .fecha(java.time.LocalDate.now().plusDays(1))
                .horaInicio(java.time.LocalTime.of(10, 0)).horaFin(java.time.LocalTime.of(10, 45))
                .modalidad(com.tranqui.app.model.Modalidad.ONLINE)
                .build();
        when(turnoRepository.findTurnosFuturosConfirmadosParaGoogle(eq(1L), any(), any())).thenReturn(List.of(turno));
        when(googleCalendarService.crearEventoReunion(turno)).thenReturn("");

        syncService.exportarTurnosPendientesAGoogleCalendar(medico);

        assertNull(turno.getTelemedicinaUrl());
        verify(turnoRepository, never()).save(any());
        verifyNoInteractions(resendEmailService);
    }
}
