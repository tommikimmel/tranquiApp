package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppServiceTest {

    private WhatsAppService whatsAppService;
    private Turno turno;

    @BeforeEach
    void setUp() {
        whatsAppService = spy(new WhatsAppService());
        ReflectionTestUtils.setField(whatsAppService, "accountSid", "ACmockaccount");
        ReflectionTestUtils.setField(whatsAppService, "authToken", "mocktoken");
        ReflectionTestUtils.setField(whatsAppService, "fromNumber", "+14155238886");
        whatsAppService.init();

        Usuario paciente = Usuario.builder()
                .nombre("Juan Paciente")
                .telefono("+5491122334455")
                .build();

        Usuario medico = Usuario.builder()
                .nombre("Carlos Medico")
                .build();

        turno = Turno.builder()
                .id(1L)
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.of(2026, 6, 23))
                .horaInicio(LocalTime.of(15, 30))
                .telemedicinaUrl("https://meet.google.com/abc-defg-hij")
                .estado(EstadoTurno.CONFIRMADO)
                .build();
    }

    @Test
    void whenValidTurno_thenBodyIsFormattedCorrectly() {
        String body = whatsAppService.construirCuerpoMensaje(turno);
        
        assertNotNull(body);
        assertTrue(body.contains("Juan Paciente"));
        assertTrue(body.contains("Carlos Medico"));
        assertTrue(body.contains("2026-06-23"));
        assertTrue(body.contains("15:30"));
        assertTrue(body.contains("https://meet.google.com/abc-defg-hij"));
        assertEquals(
            "Hola Juan Paciente, recordatorio de tu turno con el Dr. Carlos Medico mañana 2026-06-23 a las 15:30 hs. Enlace de la videollamada: https://meet.google.com/abc-defg-hij",
            body
        );
    }

    @Test
    void whenTurnoIsNull_thenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(null));
    }

    @Test
    void whenPacienteIsNull_thenThrowsException() {
        turno.setPaciente(null);
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenPacienteNombreIsEmpty_thenThrowsException() {
        turno.getPaciente().setNombre(" ");
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenPacienteTelefonoIsEmpty_thenThrowsException() {
        turno.getPaciente().setTelefono(null);
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenMedicoIsNull_thenThrowsException() {
        turno.setMedico(null);
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenMedicoNombreIsEmpty_thenThrowsException() {
        turno.getMedico().setNombre("");
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenFechaIsNull_thenThrowsException() {
        turno.setFecha(null);
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenHoraInicioIsNull_thenThrowsException() {
        turno.setHoraInicio(null);
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    @Test
    void whenTelemedicinaUrlIsEmpty_thenThrowsException() {
        turno.setTelemedicinaUrl(null);
        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    // Regression test: construirCuerpoMensaje used to throw IllegalArgumentException whenever
    // telemedicinaUrl was blank, with no exception for PRESENCIAL turnos (which never have one).
    // NotificationScheduler.enviarRecordatoriosTurnosSiguienteDia catches exceptions per-turno,
    // so this silently meant presencial patients never got their WhatsApp reminder at all. Fixed
    // by branching on modalidad instead of unconditionally requiring the video-call link.
    @Test
    void whenModalidadIsPresencialAndTelemedicinaUrlIsMissing_thenBodyIsBuiltWithoutVideoLink() {
        turno.setModalidad(Modalidad.PRESENCIAL);
        turno.setTelemedicinaUrl(null);

        String body = whatsAppService.construirCuerpoMensaje(turno);

        assertNotNull(body);
        assertTrue(body.contains("Juan Paciente"));
        assertTrue(body.contains("Carlos Medico"));
        assertTrue(body.contains("consultorio"));
        assertFalse(body.contains("Enlace de la videollamada"));
        assertFalse(body.contains("https://"));
    }

    // ONLINE (and legacy turnos with no modalidad set at all, the pre-existing default covered
    // by whenTelemedicinaUrlIsEmpty_thenThrowsException above) must keep requiring the link —
    // only PRESENCIAL is exempt.
    @Test
    void whenModalidadIsOnlineAndTelemedicinaUrlIsMissing_thenStillThrowsException() {
        turno.setModalidad(Modalidad.ONLINE);
        turno.setTelemedicinaUrl(null);

        assertThrows(IllegalArgumentException.class, () -> whatsAppService.construirCuerpoMensaje(turno));
    }

    // accountSid == "ACmockaccount" is the offline/dev sentinel (set in setUp()) — it short
    // circuits createTwilioMessage before it ever touches the Twilio SDK, which is what lets the
    // whole test suite (and local/dev runs without real Twilio credentials) exercise
    // enviarMensajeRecordatorio without a network call or throwing.
    @Test
    void whenAccountSidIsMockSentinel_thenCreateTwilioMessageDoesNotThrow() {
        assertDoesNotThrow(() ->
                whatsAppService.createTwilioMessage("whatsapp:+123", "whatsapp:+456", "hola"));
    }

    @Test
    void whenEnviarMensaje_thenCallsCreateTwilioMessage() {
        doNothing().when(whatsAppService).createTwilioMessage(anyString(), anyString(), anyString());

        whatsAppService.enviarMensajeRecordatorio(turno);

        verify(whatsAppService, times(1)).createTwilioMessage(
                eq("whatsapp:+5491122334455"),
                eq("whatsapp:+14155238886"),
                eq("Hola Juan Paciente, recordatorio de tu turno con el Dr. Carlos Medico mañana 2026-06-23 a las 15:30 hs. Enlace de la videollamada: https://meet.google.com/abc-defg-hij")
        );
    }
}
