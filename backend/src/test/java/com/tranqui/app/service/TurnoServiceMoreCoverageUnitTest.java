package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoServiceMoreCoverageUnitTest {

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private GoogleCalendarService calendarService;

    @Mock
    private AgendaService agendaService;

    @Mock
    private DisponibilidadRepository disponibilidadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TarifaMedicoRepository tarifaRepository;

    @Mock
    private MercadoPagoService mercadoPagoService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private ReembolsoService reembolsoService;

    @Mock
    private ResendEmailService resendEmailService;

    @InjectMocks
    private TurnoService turnoService;

    private Usuario medico;
    private Usuario paciente;
    private Turno turno;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .id(1L)
                .nombre("Dr. Medico")
                .email("medico@example.com")
                .rol(Rol.PSIQUIATRA)
                .ofrecePresencial(true)
                .ofreceOnline(true)
                .duracionTurnoMinutos(45)
                .build();

        paciente = Usuario.builder()
                .id(2L)
                .nombre("Paciente Juan")
                .email("paciente@example.com")
                .telefono("+5491112345678")
                .rol(Rol.PACIENTE)
                .build();

        turno = Turno.builder()
                .id(10L)
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .ocupaAgenda(false)
                .servicioId("certificado")
                .build();
    }

    @Test
    void testObtenerConteosDisponibilidad() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(disponibilidadRepository.findByMedicoIdAndModalidadOLegacy(any(), any())).thenReturn(Collections.emptyList());
        when(turnoRepository.findByMedicoIdAndFechaAndEstadoNot(any(), any(), any())).thenReturn(Collections.emptyList());
        when(agendaService.calcularBloquesDisponibles(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(LocalTime.of(10, 0)));

        Map<Long, Integer> conteos = turnoService.obtenerConteosDisponibilidad(List.of(1L, 99L), LocalDate.now(), null);
        assertNotNull(conteos);
        assertEquals(1, conteos.get(1L));
        assertEquals(0, conteos.get(99L));

        Map<Long, Integer> conModalidad = turnoService.obtenerConteosDisponibilidad(List.of(1L), LocalDate.now(), Modalidad.ONLINE);
        assertEquals(1, conModalidad.get(1L));
    }

    @Test
    void testEsPrimeraConsulta() {
        when(turnoRepository.existsByPacienteEmailAndEstadoNot("paciente@example.com", EstadoTurno.CANCELADO))
                .thenReturn(false);
        assertTrue(turnoService.esPrimeraConsulta("paciente@example.com"));

        when(turnoRepository.existsByPacienteEmailAndEstadoNot("paciente@example.com", EstadoTurno.CANCELADO))
                .thenReturn(true);
        assertFalse(turnoService.esPrimeraConsulta("paciente@example.com"));
    }

    @Test
    void testAbandonarReservaPendiente() {
        turno.setTokenReserva("tok-10");
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));

        // Sin token o con uno ajeno -> rechazado, la reserva sigue pendiente
        assertThrows(IllegalStateException.class, () -> turnoService.abandonarReservaPendiente(10L, null));
        assertThrows(IllegalStateException.class, () -> turnoService.abandonarReservaPendiente(10L, "otro"));
        assertEquals(EstadoTurno.PENDIENTE_PAGO, turno.getEstado());

        turnoService.abandonarReservaPendiente(10L, "tok-10");
        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());

        // Already confirmed -> untouched
        turno.setEstado(EstadoTurno.CONFIRMADO);
        turnoService.abandonarReservaPendiente(10L, "tok-10");
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
    }

    @Test
    void testCancelarTurno_profesionalAndPaciente() throws Exception {
        Pago pago = Pago.builder().estado(EstadoPago.APROBADO).build();
        turno.setPago(pago);
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Profesional cancels
        turnoService.cancelarTurno(10L, "medico@example.com");
        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        verify(reembolsoService).procesarReembolso(turno, medico, true);
        verify(notificacionService, times(2)).crearNotificacion(any(), anyString(), anyString(), anyString());

        // Paciente cancels
        turnoService.cancelarTurno(10L, "paciente@example.com");
        verify(reembolsoService).procesarReembolso(turno, medico, false);
    }

    @Test
    void cancelarTurno_mailAlPacienteInformaElResultadoDelReembolso() throws Exception {
        turno.setOcupaAgenda(true);
        turno.setPago(Pago.builder().estado(EstadoPago.APROBADO).build());
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Con 48 hs o más: reembolsado
        when(reembolsoService.procesarReembolso(turno, medico, false)).thenReturn(true);
        turnoService.cancelarTurno(10L, "paciente@example.com");
        verify(resendEmailService).enviarTurnoCanceladoPaciente(eq("paciente@example.com"), anyString(), anyString(),
                eq(turno.getFecha()), eq(turno.getHoraInicio()), eq(false), eq(ResendEmailService.ResultadoReembolso.REEMBOLSADO));

        // Con menos de 48 hs: sin reembolso
        when(reembolsoService.procesarReembolso(turno, medico, false)).thenReturn(false);
        turnoService.cancelarTurno(10L, "paciente@example.com");
        verify(resendEmailService).enviarTurnoCanceladoPaciente(eq("paciente@example.com"), anyString(), anyString(),
                any(), any(), eq(false), eq(ResendEmailService.ResultadoReembolso.SIN_REEMBOLSO));

        // Falla Mercado Pago: reembolso pendiente
        when(reembolsoService.procesarReembolso(turno, medico, false)).thenThrow(new RuntimeException("MP caído"));
        turnoService.cancelarTurno(10L, "paciente@example.com");
        verify(resendEmailService).enviarTurnoCanceladoPaciente(eq("paciente@example.com"), anyString(), anyString(),
                any(), any(), eq(false), eq(ResendEmailService.ResultadoReembolso.PENDIENTE));
    }

    @Test
    void cancelarTurno_porElProfesional_yPacienteSinPago() throws Exception {
        turno.setOcupaAgenda(true);
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        turnoService.cancelarTurno(10L, "medico@example.com");

        verify(resendEmailService).enviarTurnoCanceladoPaciente(eq("paciente@example.com"), anyString(), anyString(),
                any(), any(), eq(true), eq(ResendEmailService.ResultadoReembolso.SIN_PAGO));
    }

    @Test
    void cancelarTurno_noMandaMailSiElPacienteDesactivoLasNotificaciones() {
        paciente.setNotificacionesEmailHabilitadas(false);
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        turnoService.cancelarTurno(10L, "paciente@example.com");

        verify(resendEmailService, never()).enviarTurnoCanceladoPaciente(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void testActualizarAsistencia() {
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        turnoService.actualizarAsistencia(10L, "LLEGO");
        assertEquals(EstadoAsistencia.LLEGO, turno.getAsistencia());

        assertThrows(IllegalArgumentException.class, () -> turnoService.actualizarAsistencia(10L, "INVALIDA"));
    }

    @Test
    void testMarcarDocumentoEnviado_validationsAndSuccess() {
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));

        // Wrong medico
        assertThrows(IllegalStateException.class, () ->
                turnoService.marcarDocumentoEnviado(10L, "otro@example.com", "data", "file.pdf"));

        // Ocupa agenda true
        turno.setOcupaAgenda(true);
        assertThrows(IllegalStateException.class, () ->
                turnoService.marcarDocumentoEnviado(10L, "medico@example.com", "data", "file.pdf"));

        // Not confirmed
        turno.setOcupaAgenda(false);
        turno.setEstado(EstadoTurno.PENDIENTE_PAGO);
        assertThrows(IllegalStateException.class, () ->
                turnoService.marcarDocumentoEnviado(10L, "medico@example.com", "data", "file.pdf"));

        // Success
        turno.setEstado(EstadoTurno.CONFIRMADO);
        when(resendEmailService.enviarDocumentoAdjunto(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        turnoService.marcarDocumentoEnviado(10L, "medico@example.com", "data:application/pdf;base64,QUJD", "file.pdf");
        assertTrue(turno.isDocumentoEnviado());
        verify(notificacionService).crearNotificacion(eq(paciente), anyString(), anyString(), eq("DOCUMENTO_ENVIADO"));
    }

    @Test
    void testMarcarRecetasEnviadasParaPaciente() {
        when(turnoRepository.findByMedicoIdAndPacienteIdAndServicioIdAndEstadoAndDocumentoEnviado(
                1L, 2L, "receta-fuera", EstadoTurno.CONFIRMADO, false))
                .thenReturn(List.of(turno));

        turnoService.marcarRecetasEnviadasParaPaciente(1L, 2L);
        assertTrue(turno.isDocumentoEnviado());
        verify(turnoRepository).save(turno);
    }

    @Test
    void testReprogramarTurno() {
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        turnoService.reprogramarTurno(10L, "2026-11-15", "14:00");
        assertEquals(LocalDate.of(2026, 11, 15), turno.getFecha());
        assertEquals(LocalTime.of(14, 0), turno.getHoraInicio());
        verify(calendarService).actualizarEventoReunion(turno);
        verify(resendEmailService).enviarTurnoReprogramadoPaciente(eq("paciente@example.com"), anyString(), anyString(),
                eq(LocalDate.of(2026, 11, 15)), eq(LocalTime.of(14, 0)), anyBoolean(), any(), any());
    }

    @Test
    void testReservarTurno_simulationFallback() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(turnoService, "paymentSimulationEnabled", true);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(usuarioRepository.findByEmail("paciente@example.com")).thenReturn(Optional.of(paciente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(i -> {
            Turno t = i.getArgument(0);
            if (t.getId() == null) t.setId(55L);
            return t;
        });
        when(agendaService.calcularBloquesDisponibles(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(LocalTime.of(11, 0)));
        when(mercadoPagoService.crearPreferenciaPago(any(), any())).thenThrow(new IllegalStateException("Sin cuenta"));

        com.tranqui.app.model.dto.ReservaTurnoDto dto = new com.tranqui.app.model.dto.ReservaTurnoDto();
        dto.setMedicoId(1L);
        dto.setEmailPaciente("paciente@example.com");
        dto.setNombrePaciente("Paciente Juan");
        dto.setTelefonoPaciente("+5491112345678");
        dto.setFecha(LocalDate.of(2026, 10, 20));
        dto.setHora(LocalTime.of(11, 0));
        dto.setTipo(TipoTurno.PARTICULAR);
        dto.setModalidad(Modalidad.ONLINE);

        com.tranqui.app.model.dto.TurnoResponseDto res = turnoService.reservarTurno(dto);
        assertNotNull(res);
        assertEquals(55L, res.getTurnoId());
    }

    @Test
    void testObtenerTurnosMedico_and_Todos() {
        when(usuarioRepository.findByEmail("medico@example.com")).thenReturn(Optional.of(medico));
        turno.setEstado(EstadoTurno.CONFIRMADO);
        turno.setOcupaAgenda(true);
        turno.setFecha(LocalDate.now());
        turno.setHoraFin(LocalTime.now().plusHours(1));

        when(turnoRepository.findByMedicoIdAndFechaAndEstadoNot(eq(1L), any(), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of(turno));
        when(turnoRepository.findPacienteEmailsConTurnoNoCancelado(anyList())).thenReturn(List.of("paciente@example.com"));

        var dtos = turnoService.obtenerTurnosDeHoy("medico@example.com");
        assertNotNull(dtos);
        assertEquals(1, dtos.size());

        when(turnoRepository.findByMedicoIdAndEstadoNot(eq(1L), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of(turno));
        var todos = turnoService.obtenerTodosTurnos("medico@example.com");
        assertNotNull(todos);
        assertEquals(1, todos.size());
    }
}
