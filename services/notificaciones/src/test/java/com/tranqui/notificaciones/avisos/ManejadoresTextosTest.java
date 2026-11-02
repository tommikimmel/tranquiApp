package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.AsistenciaConfirmadaData;
import com.tranqui.eventos.data.DocumentoEnviadoData;
import com.tranqui.eventos.data.HistoriaClinicaActualizadaData;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los textos y las condiciones de cada aviso tienen que ser los del monolito (criterio de
 * aceptación de la Fase 1: mismo contenido que antes).
 */
class ManejadoresTextosTest {

    private Avisos avisos;
    private ResendEmailService mails;
    private Contacto paciente;
    private Contacto medico;

    @BeforeEach
    void preparar() {
        avisos = mock(Avisos.class);
        mails = mock(ResendEmailService.class);
        paciente = new Contacto(1L, "ana@mail.com");
        paciente.setNombre("Ana");
        medico = new Contacto(2L, "dra@mail.com");
        medico.setNombre("Dra. Ruiz");
        when(avisos.contacto(1L)).thenReturn(paciente);
        when(avisos.contacto(2L)).thenReturn(medico);
        when(avisos.aceptaMails(any())).thenAnswer(i -> ((Contacto) i.getArgument(0)).isEmailHabilitado());
        doAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; })
                .when(avisos).mail(any(), anyString(), any());
    }

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder().eventId(UUID.randomUUID()).eventType("x.y.z").data(data).build();
    }

    private String mensajeA(Contacto destinatario, String titulo) {
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(avisos).notificar(eq(destinatario), eq(titulo), mensaje.capture(), anyString());
        return mensaje.getValue();
    }

    @Test
    void documentoPagado_avisosYMailComoElMonolito() {
        TurnoConfirmadoData d = new TurnoConfirmadoData(5L, 1L, 2L, null, "00:00", "ONLINE", new BigDecimal("3000"),
                false, null, "Certificado", null, null, TurnoConfirmadoData.ORIGEN_PAGO);
        ManejadoresTurnos t = new ManejadoresTurnos();

        t.notifTurnoConfirmadoProfesional(avisos).manejar(evento(d));
        t.notifTurnoConfirmadoPaciente(avisos).manejar(evento(d));
        t.mailTurnoConfirmadoProfesional(avisos, mails).manejar(evento(d));
        t.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(d));

        assertThat(mensajeA(medico, "Nuevo Documento Pendiente"))
                .isEqualTo("El paciente Ana pagó Certificado. Encontralo en \"Documentos solicitados\" en Inicio.");
        assertThat(mensajeA(paciente, "Pago confirmado"))
                .isEqualTo("Tu pago por Certificado con el profesional Dra. Ruiz fue confirmado. Te va a llegar en cuanto el profesional lo prepare.");
        verify(mails, never()).enviarNuevoTurnoProfesional(any(), any(), any(), any(), any(), any(), any());
        verify(mails).enviarDocumentoPagadoPaciente("ana@mail.com", "Ana", "Dra. Ruiz", "Certificado", new BigDecimal("3000"));
    }

    @Test
    void turnoOsde_soloMailAlPacienteSinMonto() {
        TurnoConfirmadoData d = new TurnoConfirmadoData(5L, 1L, 2L, LocalDate.of(2026, 11, 2), "09:30", "PRESENCIAL",
                new BigDecimal("1000"), true, "Obra Social", null, "Av. Corrientes 1234", null, TurnoConfirmadoData.ORIGEN_OSDE);
        ManejadoresTurnos t = new ManejadoresTurnos();

        t.notifTurnoConfirmadoProfesional(avisos).manejar(evento(d));
        t.notifTurnoConfirmadoPaciente(avisos).manejar(evento(d));
        t.mailTurnoConfirmadoProfesional(avisos, mails).manejar(evento(d));
        t.mailTurnoConfirmadoPaciente(avisos, mails).manejar(evento(d));

        verify(avisos, never()).notificar(any(), anyString(), anyString(), anyString());
        verify(mails, never()).enviarNuevoTurnoProfesional(any(), any(), any(), any(), any(), any(), any());
        verify(mails).enviarTurnoConfirmadoPaciente("ana@mail.com", "Ana", "Dra. Ruiz", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 30), false, "Obra Social", "Av. Corrientes 1234", null, null);
    }

    @Test
    void asistenciaYDocumentoEnviado() {
        ManejadoresTurnos t = new ManejadoresTurnos();

        t.notifAsistenciaConfirmada(avisos).manejar(evento(
                new AsistenciaConfirmadaData(5L, 1L, 2L, LocalDate.of(2026, 11, 2), "09:30")));
        t.notifDocumentoEnviado(avisos).manejar(evento(new DocumentoEnviadoData(5L, 1L, 2L, "Certificado", "ADJUNTO")));

        assertThat(mensajeA(medico, "Paciente confirmó su asistencia"))
                .isEqualTo("Ana confirmó que va a asistir al turno del 2026-11-02 a las 09:30 hs.");
        assertThat(mensajeA(paciente, "Documento enviado")).isEqualTo("Tu certificado te llegó por mail.");
    }

    @Test
    void linkDeMeet_soloSiEsNuevoYElPacienteAceptaMails() {
        ManejadoresTurnos t = new ManejadoresTurnos();
        ManejadorEvento<MeetCreadoData> handler = t.mailLinkVideollamada(avisos, mails);

        handler.manejar(evento(new MeetCreadoData(5L, "https://meet/x", "g", 1L, 2L, LocalDate.of(2026, 11, 2), "09:30", false)));
        verify(mails, never()).enviarLinkVideollamadaPaciente(any(), any(), any(), any(), any(), any());

        paciente.setEmailHabilitado(false);
        handler.manejar(evento(new MeetCreadoData(5L, "https://meet/x", "g", 1L, 2L, LocalDate.of(2026, 11, 2), "09:30", true)));
        verify(mails, never()).enviarLinkVideollamadaPaciente(any(), any(), any(), any(), any(), any());

        paciente.setEmailHabilitado(true);
        handler.manejar(evento(new MeetCreadoData(5L, "https://meet/x", "g", 1L, 2L, LocalDate.of(2026, 11, 2), "09:30", true)));
        verify(mails).enviarLinkVideollamadaPaciente("ana@mail.com", "Ana", "Dra. Ruiz", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 30), "https://meet/x");
    }

    @Test
    void historiaClinica_tresVariantes() {
        ManejadorEvento<HistoriaClinicaActualizadaData> h = new ManejadoresClinicos().notifHistoriaClinica(avisos);

        h.manejar(evento(new HistoriaClinicaActualizadaData(2L, 1L, "INFORME", "PROFESIONAL", "EVOLUCION")));
        assertThat(mensajeA(medico, "Informe clínico creado")).isEqualTo("Se creó un nuevo informe de tipo EVOLUCION para Ana");
        assertThat(mensajeA(paciente, "Nuevo Informe Clínico disponible"))
                .isEqualTo("El profesional Dra. Ruiz ha emitido un informe de tipo EVOLUCION.");

        h.manejar(evento(new HistoriaClinicaActualizadaData(2L, 1L, "SEGUIMIENTO", "PACIENTE", null)));
        assertThat(mensajeA(medico, "Seguimiento cargado por paciente")).isEqualTo("Ana registró su seguimiento clínico diario.");

        h.manejar(evento(new HistoriaClinicaActualizadaData(2L, 1L, "SEGUIMIENTO", "PROFESIONAL", null)));
        assertThat(mensajeA(medico, "Seguimiento Diario cargado")).isEqualTo("Se registró un nuevo seguimiento diario para Ana");
    }

    @Test
    void avisosDeSuscripcion_cadaTipoVaASuPlantilla() {
        ManejadorEvento<SuscripcionAvisoData> h = new ManejadoresSuscripciones().mailAvisoSuscripcion(avisos, mails);
        LocalDateTime fecha = LocalDateTime.of(2026, 11, 1, 0, 0);

        h.manejar(evento(new SuscripcionAvisoData(9L, 2L, SuscripcionAvisoData.VENCIMIENTO_MANUAL, "Profesional",
                null, fecha, null, null, null, 3)));
        h.manejar(evento(new SuscripcionAvisoData(9L, 2L, SuscripcionAvisoData.SUSPENSION, "Profesional",
                null, null, null, null, null, null)));

        verify(mails).enviarAvisoVencimientoManualSuscripcion("dra@mail.com", "Dra. Ruiz", "Profesional", fecha, 3);
        verify(mails).enviarAvisoSuspensionSuscripcion("dra@mail.com", "Dra. Ruiz", "Profesional");
        assertThatThrownBy(() -> h.manejar(evento(new SuscripcionAvisoData(9L, 2L, "OTRO", "Profesional",
                null, null, null, null, null, null)))).isInstanceOf(EventoInvalidoException.class);
    }
}
