package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class NotificationSchedulerTest {

    @Autowired
    private NotificationScheduler notificationScheduler;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockBean
    private WhatsAppService whatsappService;

    private Usuario paciente;
    private Usuario medico;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .nombre("Paciente Pedro")
                .email("pedro.paciente@gmail.com")
                .telefono("+5491100001111")
                .rol(Rol.PACIENTE)
                .build();

        medico = Usuario.builder()
                .nombre("Medico Marta")
                .email("marta.medico@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(medico);
    }



    @Test
    void whenConfirmedTurnoForTomorrowNotSent_shouldSendAndSetFlagTrue() {
        Turno turnoMañana = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(false)
                .build();

        turnoRepository.save(turnoMañana);

        doNothing().when(whatsappService).enviarMensajeRecordatorio(any(Turno.class));

        notificationScheduler.enviarRecordatoriosTurnosSiguienteDia();

        verify(whatsappService, times(1)).enviarMensajeRecordatorio(any(Turno.class));

        Turno updatedTurno = turnoRepository.findById(turnoMañana.getId()).orElseThrow();
        assertTrue(updatedTurno.getRecordatorioEnviado());
    }

    @Test
    void whenTurnoIsAlreadySent_shouldNotSendAgain() {
        Turno turnoMañana = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(true)
                .build();

        turnoRepository.save(turnoMañana);

        notificationScheduler.enviarRecordatoriosTurnosSiguienteDia();

        verify(whatsappService, never()).enviarMensajeRecordatorio(any(Turno.class));
    }

    @Test
    void whenTurnoIsPendingPago_shouldNotSend() {
        Turno turnoMañana = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(false)
                .build();

        turnoRepository.save(turnoMañana);

        notificationScheduler.enviarRecordatoriosTurnosSiguienteDia();

        verify(whatsappService, never()).enviarMensajeRecordatorio(any(Turno.class));
    }

    @Test
    void whenTurnoIsForToday_shouldNotSend() {
        Turno turnoHoy = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(false)
                .build();

        turnoRepository.save(turnoHoy);

        notificationScheduler.enviarRecordatoriosTurnosSiguienteDia();

        verify(whatsappService, never()).enviarMensajeRecordatorio(any(Turno.class));
    }

    @Test
    void whenSendingThrowsException_shouldContinueProcessingOtherTurnos() {
        Turno turnoFalla = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(false)
                .build();

        Turno turnoExito = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(15, 0))
                .horaFin(LocalTime.of(15, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(false)
                .build();

        turnoRepository.save(turnoFalla);
        turnoRepository.save(turnoExito);

        doThrow(new RuntimeException("API Connection timeout"))
                .when(whatsappService).enviarMensajeRecordatorio(argThat(t -> t.getId().equals(turnoFalla.getId())));
        doNothing()
                .when(whatsappService).enviarMensajeRecordatorio(argThat(t -> t.getId().equals(turnoExito.getId())));

        notificationScheduler.enviarRecordatoriosTurnosSiguienteDia();

        verify(whatsappService, times(2)).enviarMensajeRecordatorio(any(Turno.class));

        Turno updatedFalla = turnoRepository.findById(turnoFalla.getId()).orElseThrow();
        assertFalse(updatedFalla.getRecordatorioEnviado());

        Turno updatedExito = turnoRepository.findById(turnoExito.getId()).orElseThrow();
        assertTrue(updatedExito.getRecordatorioEnviado());
    }

    // Regression test for the bug where WhatsAppService.construirCuerpoMensaje threw
    // IllegalArgumentException for any PRESENCIAL turno (no telemedicinaUrl), and this scheduler
    // swallowed that per-turno, so presencial patients silently never got a reminder. whatsappService
    // is a @MockBean class-wide, so it's temporarily switched to run its real methods for this one
    // test (only createTwilioMessage itself is stubbed, to avoid touching the real Twilio SDK) —
    // that's the only way to prove the fix end-to-end through the scheduler instead of just
    // asserting the scheduler "attempted" a call.
    @Test
    void whenConfirmedTurnoForTomorrowIsPresencial_shouldStillSendWhatsAppWithoutVideoLink() {
        Turno turnoPresencial = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .modalidad(Modalidad.PRESENCIAL)
                .telemedicinaUrl(null)
                .recordatorioEnviado(false)
                .build();

        turnoRepository.save(turnoPresencial);

        ReflectionTestUtils.setField(whatsappService, "fromNumber", "+14155238886");
        doCallRealMethod().when(whatsappService).enviarMensajeRecordatorio(any(Turno.class));
        doCallRealMethod().when(whatsappService).construirCuerpoMensaje(any(Turno.class));
        doNothing().when(whatsappService).createTwilioMessage(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificationScheduler.enviarRecordatoriosTurnosSiguienteDia());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsappService).createTwilioMessage(anyString(), anyString(), bodyCaptor.capture());
        assertFalse(bodyCaptor.getValue().contains("Enlace de la videollamada"));

        Turno updated = turnoRepository.findById(turnoPresencial.getId()).orElseThrow();
        assertTrue(updated.getRecordatorioEnviado());
    }

    // Opt-out: a paciente with notificacionesWhatsappHabilitadas=false must never trigger
    // enviarMensajeRecordatorio at all — see NotificationScheduler.enviarRecordatoriosTurnosSiguienteDia,
    // which checks turno.getPaciente().isNotificacionesWhatsappHabilitadas() before sending. The
    // turno should still be marked as processed (recordatorioEnviado=true) so it isn't retried.
    @Test
    void whenPacienteOptedOutOfWhatsapp_shouldNotSendReminder() {
        Usuario pacienteSinWhatsapp = Usuario.builder()
                .nombre("Paciente Sin Whatsapp")
                .email("sinwhatsapp@gmail.com")
                .telefono("+5491100002222")
                .rol(Rol.PACIENTE)
                .notificacionesWhatsappHabilitadas(false)
                .build();
        usuarioRepository.save(pacienteSinWhatsapp);

        Turno turnoMañana = Turno.builder()
                .paciente(pacienteSinWhatsapp)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .telemedicinaUrl("https://meet.google.com/abc-def-ghi")
                .recordatorioEnviado(false)
                .build();

        turnoRepository.save(turnoMañana);

        notificationScheduler.enviarRecordatoriosTurnosSiguienteDia();

        verify(whatsappService, never()).enviarMensajeRecordatorio(any(Turno.class));

        Turno updatedTurno = turnoRepository.findById(turnoMañana.getId()).orElseThrow();
        assertTrue(updatedTurno.getRecordatorioEnviado());
    }
}
