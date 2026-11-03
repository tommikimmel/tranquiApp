package com.tranqui.notificaciones.mail;

import com.tranqui.notificaciones.mail.ResendEmailService.ResultadoReembolso;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Todas las plantillas por SMTP (asunto y datos clave) y las ramas del transporte de Resend. */
class PlantillasYTransporteTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 5);
    private static final LocalTime HORA = LocalTime.of(10, 30);
    private static final LocalDateTime MOMENTO = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final BigDecimal MONTO = new BigDecimal("15000");

    private ResendEmailService service;
    private JavaMailSender mailSender;
    private ObjectProvider<JavaMailSender> provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ResendEmailService();
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        ReflectionTestUtils.setField(service, "mailSenderProvider", provider);
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@tranqui.local");
        ReflectionTestUtils.setField(service, "soporteEmail", "soporte@tranquisalud.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "transport", "smtp");
    }

    private MimeMessage enviado() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static String html(MimeMessage msg) throws Exception {
        Object contenido = msg.getContent();
        if (contenido instanceof Multipart multi) {
            Object cuerpo = multi.getBodyPart(0).getContent();
            return cuerpo instanceof Multipart interno ? interno.getBodyPart(0).getContent().toString() : cuerpo.toString();
        }
        return contenido.toString();
    }

    private void verificar(Runnable envio, String asunto, String... textos) throws Exception {
        envio.run();
        MimeMessage msg = enviado();
        assertThat(msg.getSubject()).isEqualTo(asunto);
        assertThat(html(msg)).contains(textos);
    }

    @Test
    void cuenta() throws Exception {
        verificar(() -> service.enviarCodigoVerificacion("a@test.com", "Ana", "123456"),
                "Código de verificación - Tranqui App", "123456", "Ana");
    }

    @Test
    void codigoRecuperacion_sinNombreUsaUsuario() throws Exception {
        service.enviarCodigoRecuperacion("a@test.com", " ", "654321");
        assertThat(html(enviado())).contains("654321", "Usuario");
    }

    @Test
    void respuestaTicket() throws Exception {
        service.enviarRespuestaTicket("a@test.com", "Ana", "No puedo pagar", "Ya lo revisamos");
        assertThat(html(enviado())).contains("No puedo pagar", "Ya lo revisamos");
    }

    @Test
    void turnosAlProfesional() throws Exception {
        verificar(() -> service.enviarNuevoTurnoProfesional("m@test.com", "Dra. Paz", "Juan", FECHA, HORA, "Online", "Primera consulta"),
                "Nuevo turno reservado — Juan", "05/11/2026", "10:30 hs", "Primera consulta", "Dra. Paz");
    }

    @Test
    void turnoCanceladoYModificadoAlProfesional() throws Exception {
        service.enviarTurnoCanceladoProfesional("m@test.com", "Dra. Paz", "Juan", FECHA, HORA);
        service.enviarTurnoModificadoProfesional("m@test.com", null, "Juan", null, null);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).getSubject()).isEqualTo("Turno cancelado — Juan");
        assertThat(captor.getAllValues().get(1).getSubject()).isEqualTo("Turno reprogramado — Juan");
        assertThat(html(captor.getAllValues().get(1))).contains("Usuario").doesNotContain("Nueva fecha");
    }

    @Test
    void confirmacionAsistencia() throws Exception {
        verificar(() -> service.enviarConfirmacionAsistencia("p@test.com", "Juan", "Dra. Paz", FECHA, HORA,
                        "Presencial", "Control", "Av. Siempre Viva 123", "http://x/si", "http://x/no"),
                "Confirmá tu turno del 05/11/2026", "http://x/si", "http://x/no", "Av. Siempre Viva 123");
    }

    @Test
    void turnoConfirmado_onlineConYSinMeetYPresencial() throws Exception {
        service.enviarTurnoConfirmadoPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, true, "Control", null, "https://meet.google.com/abc", MONTO);
        service.enviarTurnoConfirmadoPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, true, "Control", null, " ", null);
        service.enviarTurnoConfirmadoPaciente("p@test.com", "Juan", "Dra. Paz", null, null, false, "Control", "Consultorio 4", null, MONTO);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(captor.capture());
        List<MimeMessage> mails = captor.getAllValues();
        assertThat(mails.get(0).getSubject()).isEqualTo("Turno confirmado con Dra. Paz — 05/11/2026");
        assertThat(html(mails.get(0))).contains("https://meet.google.com/abc", "15.000");
        assertThat(html(mails.get(1))).contains("El link de la videollamada va a estar en Mis Turnos");
        assertThat(html(mails.get(2))).contains("Consultorio 4");
    }

    @Test
    void documentoPagadoYReembolsos() throws Exception {
        service.enviarDocumentoPagadoPaciente("p@test.com", "Juan", "Dra. Paz", "Receta", MONTO);
        service.enviarReembolsoRealizadoPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, MONTO);
        service.enviarReembolsoPendientePaciente("p@test.com", "Juan", "Dra. Paz", null, null);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(captor.capture());
        List<MimeMessage> mails = captor.getAllValues();
        assertThat(mails.get(0).getSubject()).isEqualTo("Pago confirmado — Receta");
        assertThat(mails.get(1).getSubject()).isEqualTo("Reembolso realizado — turno del 05/11/2026");
        assertThat(mails.get(2).getSubject()).isEqualTo("Tu reembolso está en revisión");
    }

    @Test
    void turnoCancelado_cadaResultadoDeReembolso() throws Exception {
        for (ResultadoReembolso r : ResultadoReembolso.values()) {
            service.enviarTurnoCanceladoPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, r == ResultadoReembolso.PENDIENTE, r);
        }
        service.enviarTurnoCanceladoPaciente("p@test.com", "Juan", "Dra. Paz", null, null, false, ResultadoReembolso.SIN_PAGO);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(6)).send(captor.capture());
        List<MimeMessage> mails = captor.getAllValues();
        assertThat(html(mails.get(0))).contains("Se reembolsa el total abonado");
        assertThat(html(mails.get(1))).contains("no corresponde reembolso");
        assertThat(html(mails.get(2))).doesNotContain("reembolso del total");
        assertThat(html(mails.get(3))).contains("tuvo que cancelar", "no pudimos procesarlo");
        assertThat(html(mails.get(4))).contains("Ya lo pedimos a Mercado Pago");
        assertThat(mails.get(5).getSubject()).isEqualTo("Turno cancelado");
    }

    @Test
    void reprogramadoYLinkVideollamada() throws Exception {
        service.enviarTurnoReprogramadoPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, true, null, "https://meet.google.com/x");
        service.enviarTurnoReprogramadoPaciente("p@test.com", "Juan", "Dra. Paz", null, null, true, null, null);
        service.enviarTurnoReprogramadoPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, false, "Consultorio 4", null);
        service.enviarLinkVideollamadaPaciente("p@test.com", "Juan", "Dra. Paz", FECHA, HORA, "https://meet.google.com/y");
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(4)).send(captor.capture());
        List<MimeMessage> mails = captor.getAllValues();
        assertThat(html(mails.get(0))).contains("https://meet.google.com/x");
        assertThat(html(mails.get(1))).contains("va a estar en Mis Turnos");
        assertThat(html(mails.get(2))).contains("Consultorio 4", "Presencial");
        assertThat(mails.get(3).getSubject()).isEqualTo("Ya tenés el link de tu sesión online — 05/11/2026");
    }

    @Test
    void suscripcion_conDatos() throws Exception {
        service.enviarPreavisoRenovacionSuscripcion("m@test.com", "Dra. Paz", "Pro", MONTO, MOMENTO, "annual");
        service.enviarConfirmacionPagoSuscripcion("m@test.com", "Dra. Paz", "Pro", MONTO, MOMENTO, MOMENTO.plusMonths(1), "MP-1");
        service.enviarAvisoPagoFallidoSuscripcion("m@test.com", "Dra. Paz", "Pro", MONTO, MOMENTO);
        service.enviarConfirmacionCancelacionSuscripcion("m@test.com", "Dra. Paz", "Pro", MOMENTO);
        service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro");
        service.enviarAvisoVencimientoManualSuscripcion("m@test.com", "Dra. Paz", "Pro", MOMENTO, 5);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(6)).send(captor.capture());
        List<MimeMessage> mails = captor.getAllValues();
        assertThat(html(mails.get(0))).contains("anual", "15.000,00", "05/11/2026");
        assertThat(html(mails.get(1))).contains("MP-1", "05/12/2026");
        assertThat(mails.get(2).getSubject()).isEqualTo("Problema con el cobro de tu suscripción - Período de gracia activado");
        assertThat(mails.get(3).getSubject()).isEqualTo("Suscripción cancelada - Tranqui App");
        assertThat(mails.get(4).getSubject()).isEqualTo("Suscripción suspendida - Tranqui App");
        assertThat(mails.get(5).getSubject()).isEqualTo("Tu suscripción a Tranqui App vence en 5 días");
    }

    @Test
    void suscripcion_sinDatosUsaTextosPorDefecto() throws Exception {
        service.enviarPreavisoRenovacionSuscripcion("m@test.com", null, null, null, null, "monthly");
        service.enviarConfirmacionPagoSuscripcion("m@test.com", null, null, null, null, null, null);
        service.enviarAvisoPagoFallidoSuscripcion("m@test.com", null, null, null, null);
        service.enviarConfirmacionCancelacionSuscripcion("m@test.com", null, null, null);
        service.enviarAvisoSuspensionSuscripcion("m@test.com", null, null);
        service.enviarAvisoVencimientoManualSuscripcion("m@test.com", null, null, null, 1);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(6)).send(captor.capture());
        List<MimeMessage> mails = captor.getAllValues();
        assertThat(html(mails.get(0))).contains("mensual", "0,00", "en los próximos 3 días");
        assertThat(html(mails.get(1))).contains("Pago Mercado Pago", "próximo período");
        assertThat(html(mails.get(2))).contains("en 7 días");
        assertThat(html(mails.get(3))).contains("el fin del período abonado");
        assertThat(html(mails.get(5))).contains("próximamente");
    }

    @Test
    void documentoAdjunto_viajaComoAdjunto() throws Exception {
        service.enviarDocumentoAdjunto("p@test.com", "Juan", "Dra. Paz", "Certificado",
                "data:application/pdf;base64,JVBERi0xLjQ=", "certificado.pdf");
        MimeMessage msg = enviado();
        assertThat(msg.getSubject()).isEqualTo("Tu certificado de Dra. Paz");
        Multipart multi = (Multipart) msg.getContent();
        assertThat(multi.getCount()).isEqualTo(2);
        assertThat(multi.getBodyPart(1).getFileName()).isEqualTo("certificado.pdf");
    }

    @Test
    void documentoAdjunto_sinNombreNiTipoYSinPrefijo() throws Exception {
        service.enviarDocumentoAdjunto("p@test.com", "Juan", "Dra. Paz", null, "JVBERi0xLjQ=", " ");
        MimeMessage msg = enviado();
        assertThat(msg.getSubject()).isEqualTo("Tu documento de Dra. Paz");
        assertThat(((Multipart) msg.getContent()).getBodyPart(1).getFileName()).isEqualTo("documento");
    }

    @Test
    void documentoAdjunto_vacioSeRechaza() {
        assertThatThrownBy(() -> service.enviarDocumentoAdjunto("p@test.com", "Juan", "Dra. Paz", "Informe", "data:application/pdf;base64,", "x.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarDocumentoAdjunto("p@test.com", "Juan", "Dra. Paz", "Informe", null, "x.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void recetaEmitida_conYSinPdf() throws Exception {
        service.enviarRecetaEmitidaPaciente("p@test.com", "Juan", "Dra. Paz", "MN 123", "https://qbi2/receta.pdf", "R-9");
        service.enviarRecetaEmitidaPaciente("p@test.com", "Juan", "Dra. Paz", "MN 123", null, "R-9");
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        assertThat(html(captor.getAllValues().get(0))).contains("https://qbi2/receta.pdf", "Descargar receta");
        assertThat(html(captor.getAllValues().get(1))).contains("Ver en Tranqui App");
    }

    @Test
    void smtp_llevaLaClaveDeIdempotencia() throws Exception {
        ClaveIdempotencia.con("evt-1", () -> service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro"));
        assertThat(enviado().getHeader("X-Tranqui-Idempotency-Key")).containsExactly("evt-1");
    }

    @Test
    void sinDestinatario_noSeEnvia() {
        service.enviarAvisoSuspensionSuscripcion(" ", "Dra. Paz", "Pro");
        service.enviarAvisoSuspensionSuscripcion(null, "Dra. Paz", "Pro");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtp_sinServidorConfigurado_fallaParaReintentar() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(() -> service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro"))
                .isInstanceOf(EnvioMailException.class);
    }

    @Test
    void smtp_errorDelServidor_fallaParaReintentar() {
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("caído")).when(mailSender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro"))
                .isInstanceOf(EnvioMailException.class);
    }

    @Test
    void resend_sinApiKeyValida_soloLoguea() {
        ReflectionTestUtils.setField(service, "transport", "resend");
        ReflectionTestUtils.setField(service, "apiKey", "clave-invalida");
        service.enviarSolicitudCopiaDatos("Ana", "ana@test.com", "PACIENTE");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @SuppressWarnings("unchecked")
    private HttpClient resendRespondiendo(int status) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(status);
        when(respuesta.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        ReflectionTestUtils.setField(service, "transport", "resend");
        ReflectionTestUtils.setField(service, "apiKey", "re_test");
        ReflectionTestUtils.setField(service, "httpClient", http);
        return http;
    }

    @Test
    @SuppressWarnings("unchecked")
    void resend_mandaElPedidoConClaveReplyToYAdjunto() throws Exception {
        HttpClient http = resendRespondiendo(200);
        ClaveIdempotencia.con("evt-2", () -> service.enviarSolicitudCopiaDatos("Ana", "ana@test.com", "PACIENTE"));
        service.enviarDocumentoAdjunto("p@test.com", "Juan", "Dra. Paz", "Informe", "data:application/pdf;base64,JVBERi0xLjQ=", "i.pdf");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest primero = captor.getAllValues().get(0);
        assertThat(primero.uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(primero.headers().firstValue("Authorization")).contains("Bearer re_test");
        assertThat(primero.headers().firstValue("Idempotency-Key")).contains("evt-2");
        assertThat(captor.getAllValues().get(1).headers().firstValue("Idempotency-Key")).isEmpty();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void resend_respuestaDeError_fallaParaReintentar() throws Exception {
        resendRespondiendo(422);
        assertThatThrownBy(() -> service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro"))
                .isInstanceOf(EnvioMailException.class).hasMessageContaining("422");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resend_sinConexion_fallaParaReintentar() throws Exception {
        HttpClient http = resendRespondiendo(200);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("sin red"));
        assertThatThrownBy(() -> service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro"))
                .isInstanceOf(EnvioMailException.class).hasMessageContaining("No se pudo contactar");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resend_interrumpido_restauraLaInterrupcion() throws Exception {
        HttpClient http = resendRespondiendo(200);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> service.enviarAvisoSuspensionSuscripcion("m@test.com", "Dra. Paz", "Pro"))
                    .isInstanceOf(EnvioMailException.class).hasMessageContaining("interrumpido");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
