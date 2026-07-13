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
}
