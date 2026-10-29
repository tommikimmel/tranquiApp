package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.data.DocumentoEnviadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.TurnoRecordatorioData;
import com.tranqui.eventos.data.TurnoReprogramadoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.interno.ClienteInterno;
import com.tranqui.notificaciones.mail.ResendEmailService;
import com.tranqui.notificaciones.mail.ResendEmailService.ResultadoReembolso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Qué mail sale para cada evento de turnos y cuándo no sale ninguno. */
class ManejadoresTurnosTest {

    private static final LocalDate FECHA = LocalDate.now().plusDays(3);
    private static final LocalTime HORA = LocalTime.of(10, 0);

    private final Avisos avisos = mock(Avisos.class);
    private final ResendEmailService mails = mock(ResendEmailService.class);
    private final ManejadoresTurnos manejadores = new ManejadoresTurnos();
    private final Contacto paciente = new Contacto(1L, "ana@mail.com");
    private final Contacto medico = new Contacto(2L, "lucia@mail.com");

    @BeforeEach
    void setUp() {
        paciente.setNombre("Ana");
        medico.setNombre("Lucía");
        when(avisos.contacto(1L)).thenReturn(paciente);
        when(avisos.contacto(2L)).thenReturn(medico);
        when(avisos.aceptaMails(paciente)).thenReturn(true);
        doAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; }).when(avisos).mail(any(), anyString(), any());
    }

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder().eventId(UUID.randomUUID()).eventType("turnos").data(data).build();
    }

    private static TurnoConfirmadoData confirmado(Boolean ocupaAgenda, String origen, String modalidad) {
        return new TurnoConfirmadoData(9L, 1L, 2L, FECHA, "10:00", modalidad, new BigDecimal("15000"), ocupaAgenda, "Control",
                "Certificado", "Consultorio 4", "https://meet/x", origen);
    }

    @Test
    void confirmadoPorPago_mailAlProfesionalSoloSiTieneEmailYNoEsDocumento() {
        manejadores.mailTurnoConfirmadoProfesional(avisos, mails).manejar(evento(confirmado(true, "PAGO", "PRESENCIAL")));
        verify(mails).enviarNuevoTurnoProfesional("lucia@mail.com", "Lucía", "Ana", FECHA, HORA, "PRESENCIAL", "Control");

        manejadores.mailTurnoConfirmadoProfesional(avisos, mails).manejar(evento(confirmado(false, "PAGO", "PRESENCIAL")));
        manejadores.mailTurnoConfirmadoProfesional(avisos, mails).manejar(evento(confirmado(true, "OSDE", "PRESENCIAL")));
        medico.setEmail(null);
        manejadores.mailTurnoConfirmadoProfesional(avisos, mails).manejar(evento(confirmado(true, "PAGO", "PRESENCIAL")));
        verify(mails).enviarNuevoTurnoProfesional(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmado_mailAlPaciente_turnoDocumentoYOsde() {
        manejadores.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(confirmado(true, "PAGO", "ONLINE")));
        verify(mails).enviarTurnoConfirmadoPaciente("ana@mail.com", "Ana", "Lucía", FECHA, HORA, true, "Control",
                "Consultorio 4", "https://meet/x", new BigDecimal("15000"));

        manejadores.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(confirmado(false, "PAGO", null)));
        verify(mails).enviarDocumentoPagadoPaciente("ana@mail.com", "Ana", "Lucía", "Certificado", new BigDecimal("15000"));

        manejadores.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(confirmado(true, "OSDE", "PRESENCIAL")));
        verify(mails).enviarTurnoConfirmadoPaciente(eq("ana@mail.com"), any(), any(), any(), any(), eq(false), any(), any(), any(), isNull());

        manejadores.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(confirmado(false, "OSDE", null)));
        verify(mails).enviarDocumentoPagadoPaciente(any(), any(), any(), any(), any());
    }

    @Test
    void confirmado_pacienteSinMails_noSeManda() {
        when(avisos.aceptaMails(paciente)).thenReturn(false);
        manejadores.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(confirmado(true, "PAGO", "ONLINE")));
        manejadores.mailTurnoCanceladoPaciente(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PACIENTE", null, false)));
        manejadores.mailTurnoReprogramadoPaciente(avisos, mails).manejar(evento(
                new TurnoReprogramadoData(9L, 1L, 2L, FECHA, "09:00", FECHA, "10:00")));
        manejadores.mailRecordatorioAsistencia(avisos, mails, mock(ClienteInterno.class)).manejar(evento(
                new TurnoRecordatorioData(9L, 1L, 2L, FECHA, "10:00")));
        verifyNoInteractions(mails);
    }

    @Test
    void cancelado_mailAlProfesionalSalvoDocumentoOSinEmail() {
        manejadores.mailTurnoCanceladoProfesional(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PACIENTE", "x", true, FECHA, "10:00", true, null)));
        verify(mails).enviarTurnoCanceladoProfesional("lucia@mail.com", "Lucía", "Ana", FECHA, HORA);

        manejadores.mailTurnoCanceladoProfesional(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PACIENTE", "x", true, null, null, false, null)));
        medico.setEmail(null);
        manejadores.mailTurnoCanceladoProfesional(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PACIENTE", "x", true, FECHA, "10:00", null, null)));
        verify(mails).enviarTurnoCanceladoProfesional(any(), any(), any(), any(), any());
    }

    @Test
    void cancelado_mailAlPacienteConElResultadoDelReembolso() {
        manejadores.mailTurnoCanceladoPaciente(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PROFESIONAL", "x", true, FECHA, "10:00", true, "EN_PROCESO")));
        verify(mails).enviarTurnoCanceladoPaciente("ana@mail.com", "Ana", "Lucía", FECHA, HORA, true, ResultadoReembolso.EN_PROCESO);

        manejadores.mailTurnoCanceladoPaciente(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PACIENTE", "x", true, FECHA, "10:00", false, null)));
        verify(mails).enviarTurnoCanceladoPaciente("ana@mail.com", "Ana", "Lucía", null, null, false, ResultadoReembolso.REEMBOLSADO);

        manejadores.mailTurnoCanceladoPaciente(avisos, mails).manejar(evento(
                new TurnoCanceladoData(9L, 1L, 2L, "PACIENTE", "x", false)));
        verify(mails).enviarTurnoCanceladoPaciente(eq("ana@mail.com"), any(), any(), any(), any(), anyBoolean(), eq(ResultadoReembolso.SIN_PAGO));
    }

    @Test
    void reprogramado_mailsAlProfesionalYAlPaciente() {
        TurnoReprogramadoData d = new TurnoReprogramadoData(9L, 1L, 2L, FECHA, "09:00", FECHA, "10:00", "ONLINE", null, "https://meet/y");
        manejadores.mailTurnoReprogramadoProfesional(avisos, mails).manejar(evento(d));
        manejadores.mailTurnoReprogramadoPaciente(avisos, mails).manejar(evento(d));
        verify(mails).enviarTurnoModificadoProfesional("lucia@mail.com", "Lucía", "Ana", FECHA, HORA);
        verify(mails).enviarTurnoReprogramadoPaciente("ana@mail.com", "Ana", "Lucía", FECHA, HORA, true, null, "https://meet/y");

        medico.setEmail(null);
        manejadores.mailTurnoReprogramadoProfesional(avisos, mails).manejar(evento(d));
        verify(mails).enviarTurnoModificadoProfesional(any(), any(), any(), any(), any());
    }

    @Test
    void recordatorio_usaLosEnlacesDeTurnos() {
        ClienteInterno interno = mock(ClienteInterno.class);
        when(interno.enlacesAsistencia(9L)).thenReturn(new ClienteInterno.EnlacesAsistencia("https://x/si", "https://x/no"));
        manejadores.mailRecordatorioAsistencia(avisos, mails, interno).manejar(evento(
                new TurnoRecordatorioData(9L, 1L, 2L, FECHA, "10:00", "PRESENCIAL", "Control", "Consultorio 4")));
        verify(mails).enviarConfirmacionAsistencia("ana@mail.com", "Ana", "Lucía", FECHA, HORA, "PRESENCIAL", "Control",
                "Consultorio 4", "https://x/si", "https://x/no");
    }

    @Test
    void documentoEnviado_textoSegunElOrigen() {
        manejadores.notifDocumentoEnviado(avisos).manejar(evento(new DocumentoEnviadoData(9L, 1L, 2L, null, "RECETA")));
        manejadores.notifDocumentoEnviado(avisos).manejar(evento(new DocumentoEnviadoData(9L, 1L, 2L, "Certificado", "TURNO")));
        manejadores.notifDocumentoEnviado(avisos).manejar(evento(new DocumentoEnviadoData(9L, 1L, 2L, null, "TURNO")));
        verify(avisos).notificar(paciente, "Documento enviado", "Tu receta fuera de turno ya fue generada.", "DOCUMENTO_ENVIADO");
        verify(avisos).notificar(paciente, "Documento enviado", "Tu certificado te llegó por mail.", "DOCUMENTO_ENVIADO");
        verify(avisos).notificar(paciente, "Documento enviado", "Tu documento te llegó por mail.", "DOCUMENTO_ENVIADO");
        verify(mails, never()).enviarDocumentoPagadoPaciente(any(), any(), any(), any(), any());
    }
}
