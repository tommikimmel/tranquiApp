package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.eventos.data.ReembolsoFallidoData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.notificaciones.mail.ResendEmailService;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.notificacion.NotificacionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManejadoresPagosTest {

    @Test
    @SuppressWarnings("unchecked")
    void conceptoAprobado_seEmpujaEnVivoAlProfesionalSinGuardarse() {
        Avisos avisos = mock(Avisos.class);
        NotificacionService notificaciones = mock(NotificacionService.class);
        Contacto paciente = new Contacto(1L, "ana@mail.com");
        paciente.setNombre("Ana");
        when(avisos.contacto(1L)).thenReturn(paciente);

        new ManejadoresPagos().enVivoConceptoAprobado(avisos, notificaciones).manejar(EventEnvelope.<ConceptoAprobadoData>builder()
                .eventId(UUID.randomUUID()).eventType("pagos.concepto.aprobado")
                .data(new ConceptoAprobadoData(40L, 2L, 1L, "CERTIFICADO")).build());

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificaciones).empujarSinGuardar(eq(2L), payload.capture());
        assertThat(payload.getValue()).containsEntry("id", 40L).containsEntry("pacienteNombre", "Ana")
                .containsEntry("tipo", "CERTIFICADO").containsKey("fecha");
        verify(avisos, never()).notificar(any(), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- Fase 4: reembolsos

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder().eventId(UUID.randomUUID()).eventType("pagos.reembolso").data(data).build();
    }

    private Avisos avisosConContactos() {
        Avisos avisos = mock(Avisos.class);
        Contacto paciente = new Contacto(1L, "ana@mail.com");
        paciente.setNombre("Ana");
        Contacto medico = new Contacto(2L, "lucia@mail.com");
        medico.setNombre("Lucía");
        when(avisos.contacto(1L)).thenReturn(paciente);
        when(avisos.contacto(2L)).thenReturn(medico);
        when(avisos.aceptaMails(paciente)).thenReturn(true);
        // avisos.mail corre el envío (en producción, con su clave de idempotencia).
        org.mockito.Mockito.doAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; })
                .when(avisos).mail(any(), anyString(), any());
        return avisos;
    }

    @Test
    void reembolsoDelServicio_mandaElSegundoMail() {
        Avisos avisos = avisosConContactos();
        ResendEmailService mails = mock(ResendEmailService.class);
        LocalDate fecha = LocalDate.now().plusDays(5);

        new ManejadoresPagos().mailReembolsoRealizado(avisos, mails).manejar(evento(new ReembolsoRealizadoData(
                9L, 9L, 30L, new BigDecimal("60000"), "Cancelación con anticipación > 48hs", 1L, 2L, fecha, "10:00", true)));

        verify(mails).enviarReembolsoRealizadoPaciente(eq("ana@mail.com"), eq("Ana"), eq("Lucía"), eq(fecha),
                eq(java.time.LocalTime.of(10, 0)), eq(new BigDecimal("60000")));
    }

    @Test
    void reembolsoDelMonolito_noMandaOtroMail() {
        Avisos avisos = avisosConContactos();
        ResendEmailService mails = mock(ResendEmailService.class);

        new ManejadoresPagos().mailReembolsoRealizado(avisos, mails).manejar(evento(
                new ReembolsoRealizadoData(9L, 9L, 30L, BigDecimal.TEN, "x")));

        org.mockito.Mockito.verifyNoInteractions(mails);
    }

    @Test
    void reembolsoFallido_avisaQueSeRevisa() {
        Avisos avisos = avisosConContactos();
        ResendEmailService mails = mock(ResendEmailService.class);
        LocalDate fecha = LocalDate.now().plusDays(5);

        new ManejadoresPagos().mailReembolsoFallido(avisos, mails).manejar(evento(
                new ReembolsoFallidoData(30L, 1L, 2L, fecha, "10:00", "rechazado por Mercado Pago")));

        verify(mails).enviarReembolsoPendientePaciente("ana@mail.com", "Ana", "Lucía", fecha, java.time.LocalTime.of(10, 0));
    }
}
