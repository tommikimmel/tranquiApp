package com.tranqui.app.controller;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.*;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicoAndAdminControllersUnitTest {

    @Mock
    private MedicoService medicoService;

    @Mock
    private DisponibilidadService disponibilidadService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MercadoPagoOAuthService mercadoPagoOAuthService;

    @Mock
    private GoogleCalendarOAuthService googleCalendarOAuthService;

    @Mock
    private GoogleCalendarSyncService googleCalendarSyncService;

    @Mock
    private GoogleCalendarWatchService googleCalendarWatchService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDetails userDetails;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private MedicoController medicoController;

    @InjectMocks
    private AdminController adminController;

    private Usuario medico;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .id(1L)
                .nombre("Dr. Carlos")
                .apellido("Gomez")
                .email("carlos@example.com")
                .rol(Rol.PSIQUIATRA)
                .verificadoAdmin(false)
                .build();

        ReflectionTestUtils.setField(medicoController, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(medicoController, "mercadoPagoEnabled", true);
    }

    // --- MedicoController tests ---

    @Test
    void simularConexionGoogleCalendar_soloEnEntornoLocal() {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(medico));
        ReflectionTestUtils.setField(medicoController, "googleCalendarEnabled", false);
        ReflectionTestUtils.setField(medicoController, "localSimulations", true);

        ResponseEntity<?> resp = medicoController.simularConexionGoogleCalendar(userDetails);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, medico.getGoogleCalendarConnected());
        verify(usuarioRepository).save(medico);
        assertEquals(Boolean.TRUE, medicoController.obtenerEstadoGoogleCalendar(userDetails).getBody().get("simulated"));
    }

    @Test
    void simularConexionGoogleCalendar_rechazadaFueraDelEntornoLocal() {
        // Producción: sin app.local-simulations (default false), aunque Calendar esté apagado.
        ReflectionTestUtils.setField(medicoController, "googleCalendarEnabled", false);
        ReflectionTestUtils.setField(medicoController, "localSimulations", false);
        assertEquals(HttpStatus.FORBIDDEN, medicoController.simularConexionGoogleCalendar(userDetails).getStatusCode());

        // Con Calendar real habilitado tampoco se puede simular.
        ReflectionTestUtils.setField(medicoController, "googleCalendarEnabled", true);
        ReflectionTestUtils.setField(medicoController, "localSimulations", true);
        assertEquals(HttpStatus.FORBIDDEN, medicoController.simularConexionGoogleCalendar(userDetails).getStatusCode());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void testMedicoController_perfilAndStats() {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        MedicoDto dto = MedicoDto.builder().nombre("Carlos").build();
        when(medicoService.obtenerPerfil("carlos@example.com")).thenReturn(dto);
        when(medicoService.actualizarPerfil(eq("carlos@example.com"), any())).thenReturn(dto);

        ResponseEntity<MedicoDto> p = medicoController.obtenerPerfil(userDetails);
        assertEquals(HttpStatus.OK, p.getStatusCode());

        ResponseEntity<MedicoDto> p2 = medicoController.actualizarPerfil(userDetails, dto);
        assertEquals(HttpStatus.OK, p2.getStatusCode());

        DashboardStatsDto stats = DashboardStatsDto.builder().build();
        when(medicoService.obtenerStats("carlos@example.com", "MENSUAL")).thenReturn(stats);
        ResponseEntity<DashboardStatsDto> s = medicoController.obtenerStats(userDetails, "MENSUAL");
        assertEquals(HttpStatus.OK, s.getStatusCode());

        // agenda config
        when(medicoService.actualizarConfigAgenda("carlos@example.com", 30, 10)).thenReturn(dto);
        dto.setDuracionTurnoMinutos(30);
        dto.setIntervaloEntreTurnosMinutos(10);
        ResponseEntity<MedicoDto> cfg = medicoController.actualizarConfigAgenda(userDetails, dto);
        assertEquals(HttpStatus.OK, cfg.getStatusCode());
    }

    @Test
    void testMedicoController_disponibilidad() {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(disponibilidadService.obtenerDisponibilidades("carlos@example.com", Modalidad.ONLINE)).thenReturn(Collections.emptyList());
        ResponseEntity<List<DisponibilidadDto>> d = medicoController.obtenerDisponibilidad(userDetails, Modalidad.ONLINE);
        assertEquals(HttpStatus.OK, d.getStatusCode());

        when(disponibilidadService.guardarDisponibilidades(eq("carlos@example.com"), eq(Modalidad.ONLINE), any())).thenReturn(Collections.emptyList());
        ResponseEntity<List<DisponibilidadDto>> saved = medicoController.actualizarDisponibilidad(userDetails, Modalidad.ONLINE, Collections.emptyList());
        assertEquals(HttpStatus.OK, saved.getStatusCode());
    }

    @Test
    void testMedicoController_oauthMercadoPago() throws Exception {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(medico));
        when(mercadoPagoOAuthService.buildAuthorizationUrl(medico)).thenReturn("http://mp.auth.url");

        ResponseEntity<Map<String, String>> mp = medicoController.obtenerUrlConexionMercadoPago(userDetails);
        assertEquals(HttpStatus.OK, mp.getStatusCode());
        assertEquals("http://mp.auth.url", mp.getBody().get("url"));

        // Status
        ResponseEntity<Map<String, Object>> statusResp = medicoController.obtenerEstadoMercadoPago(userDetails);
        assertEquals(HttpStatus.OK, statusResp.getStatusCode());

        // Callback
        when(mercadoPagoOAuthService.verificarState("state123")).thenReturn(1L);
        medicoController.callbackMercadoPago("code123", "state123", null, response);
        verify(mercadoPagoOAuthService).procesarCallback(1L, "code123");
        verify(response).sendRedirect(contains("mp=success"));

        // Desconectar
        ResponseEntity<Void> disc = medicoController.desvincularMercadoPago(userDetails);
        assertEquals(HttpStatus.NO_CONTENT, disc.getStatusCode());
        verify(mercadoPagoOAuthService).desvincular(medico);
    }

    @Test
    void testMedicoController_googleCalendar() throws Exception {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(medico));
        when(googleCalendarOAuthService.buildAuthorizationUrl(medico)).thenReturn("http://google.auth.url");

        ResponseEntity<Map<String, String>> g = medicoController.obtenerUrlConexionGoogleCalendar(userDetails);
        assertEquals(HttpStatus.OK, g.getStatusCode());
        assertEquals("http://google.auth.url", g.getBody().get("url"));

        // Status
        ResponseEntity<Map<String, Object>> statusResp = medicoController.obtenerEstadoGoogleCalendar(userDetails);
        assertEquals(HttpStatus.OK, statusResp.getStatusCode());

        // Callback
        when(googleCalendarOAuthService.verificarState("state123")).thenReturn(1L);
        when(googleCalendarOAuthService.procesarCallback(1L, "code123")).thenReturn(medico);
        medicoController.callbackGoogleCalendar("code123", "state123", null, response);
        verify(googleCalendarOAuthService).procesarCallback(1L, "code123");
        verify(response).sendRedirect(contains("googleCalendar=success"));

        // Desconectar
        ResponseEntity<Void> disc = medicoController.desvincularGoogleCalendar(userDetails);
        assertEquals(HttpStatus.NO_CONTENT, disc.getStatusCode());
        verify(googleCalendarOAuthService).desvincular(medico);

        // Eventos
        when(googleCalendarSyncService.obtenerEventosExternosCacheados(medico)).thenReturn(Collections.emptyList());
        ResponseEntity<List<EventoExternoDto>> eventos = medicoController.obtenerEventosExternosGoogleCalendar(userDetails);
        assertEquals(HttpStatus.OK, eventos.getStatusCode());
    }

    // --- AdminController tests ---

    @Test
    void testAdminController_updateRol_and_verify() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));

        // Update rol
        ResponseEntity<?> r = adminController.updateRol(1L, Map.of("rol", "ADMIN"));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Rol.ADMIN, medico.getRol());

        // Invalid rol
        ResponseEntity<?> bad = adminController.updateRol(1L, Map.of("rol", "INVALID"));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());

        // Verify professional
        Subscription sub = Subscription.builder().status(SubscriptionStatus.PENDING_VERIFICATION).build();
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        ResponseEntity<?> v = adminController.verifyProfessional(1L);
        assertEquals(HttpStatus.OK, v.getStatusCode());
        assertTrue(medico.getVerificadoAdmin());
        assertEquals(SubscriptionStatus.VERIFIED, sub.getStatus());

        // Reject professional
        ResponseEntity<?> rej = adminController.rejectProfessional(1L);
        assertEquals(HttpStatus.OK, rej.getStatusCode());
        assertFalse(medico.getVerificadoAdmin());
    }

    @Test
    void testAdminController_ticketsAndResetPassword() {
        when(ticketService.listarTodos()).thenReturn(Collections.emptyList());
        ResponseEntity<?> t = adminController.listarTickets();
        assertEquals(HttpStatus.OK, t.getStatusCode());

        CambiarEstadoTicketDto ceDto = new CambiarEstadoTicketDto();
        ceDto.setEstado("RESUELTO");
        ResponseEntity<?> ce = adminController.cambiarEstadoTicket(10L, ceDto);
        assertEquals(HttpStatus.OK, ce.getStatusCode());
        verify(ticketService).cambiarEstado(10L, "RESUELTO");

        // Reset password
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        ResponseEntity<?> rp = adminController.resetPassword(1L);
        assertEquals(HttpStatus.OK, rp.getStatusCode());
        verify(resendEmailService).enviarPasswordTemporal(eq("carlos@example.com"), eq("Dr. Carlos"), anyString());
    }

    @Test
    void testMedicoController_googleCalendarAndAgendaEndpoints() throws Exception {
        // obtenerMedicos
        when(medicoService.obtenerMedicosActivos()).thenReturn(Collections.emptyList());
        assertEquals(HttpStatus.OK, medicoController.obtenerMedicos().getStatusCode());

        // actualizarConfigAgenda
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        MedicoDto aDto = new MedicoDto();
        aDto.setDuracionTurnoMinutos(30);
        aDto.setIntervaloEntreTurnosMinutos(10);
        when(medicoService.actualizarConfigAgenda("carlos@example.com", 30, 10)).thenReturn(aDto);
        assertEquals(HttpStatus.OK, medicoController.actualizarConfigAgenda(userDetails, aDto).getStatusCode());

        // google calendar connect & disconnect
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(medico));
        when(googleCalendarOAuthService.buildAuthorizationUrl(medico)).thenReturn("http://google.auth/url");
        ResponseEntity<Map<String, String>> connectResp = medicoController.obtenerUrlConexionGoogleCalendar(userDetails);
        assertEquals("http://google.auth/url", connectResp.getBody().get("url"));

        ResponseEntity<Void> disconnectResp = medicoController.desvincularGoogleCalendar(userDetails);
        assertEquals(HttpStatus.NO_CONTENT, disconnectResp.getStatusCode());
        verify(googleCalendarWatchService).detenerCanal(medico);
        verify(googleCalendarSyncService).eliminarEventosCreadosPorLaApp(medico);
        verify(googleCalendarOAuthService).desvincular(medico);

        // google calendar webhook
        assertEquals(HttpStatus.OK, medicoController.recibirNotificacionGoogleCalendar(null, null, "sync", null).getStatusCode());

        when(googleCalendarWatchService.verificarChannelToken("invalid-tok")).thenReturn(null);
        assertEquals(HttpStatus.OK, medicoController.recibirNotificacionGoogleCalendar(null, null, "exists", "invalid-tok").getStatusCode());

        when(googleCalendarWatchService.verificarChannelToken("valid-tok")).thenReturn(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        medico.setGoogleWatchChannelId("chan-1");
        medico.setGoogleWatchResourceId("res-1");
        assertEquals(HttpStatus.OK, medicoController.recibirNotificacionGoogleCalendar("chan-1", "res-1", "exists", "valid-tok").getStatusCode());
        verify(googleCalendarSyncService).sincronizarIncremental(medico);

        // eventos
        when(googleCalendarSyncService.obtenerEventosExternosCacheados(medico)).thenReturn(Collections.emptyList());
        assertEquals(HttpStatus.OK, medicoController.obtenerEventosExternosGoogleCalendar(userDetails).getStatusCode());

        // callback
        medicoController.callbackGoogleCalendar(null, null, "access_denied", response);
        verify(response).sendRedirect(contains("error&reason=access_denied"));

        when(googleCalendarOAuthService.verificarState("bad_state")).thenReturn(null);
        medicoController.callbackGoogleCalendar("code", "bad_state", null, response);
        verify(response).sendRedirect(contains("error&reason=invalid_state"));

        when(googleCalendarOAuthService.verificarState("good_state")).thenReturn(1L);
        when(googleCalendarOAuthService.procesarCallback(1L, "good_code")).thenReturn(medico);
        medicoController.callbackGoogleCalendar("good_code", "good_state", null, response);
        verify(response).sendRedirect(contains("googleCalendar=success"));
    }
}
