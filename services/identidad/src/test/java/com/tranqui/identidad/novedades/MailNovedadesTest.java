package com.tranqui.identidad.novedades;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plantilla y envío individual del mail de novedades: un fallo se registra y no corta el envío masivo. */
class MailNovedadesTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final HttpClient http = mock(HttpClient.class);
    private MailNovedades mail;

    @BeforeEach
    void setUp() {
        mail = new MailNovedades(provider);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        ReflectionTestUtils.setField(mail, "fromEmail", "novedades@tranqui.local");
        ReflectionTestUtils.setField(mail, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(mail, "apiKey", "");
        ReflectionTestUtils.setField(mail, "transporte", "smtp");
        ReflectionTestUtils.setField(mail, "http", http);
    }

    @Test
    void plantilla_escapaElContenidoYLlevaLaBaja() {
        String html = mail.htmlNovedades("Mejoras <b>", List.of("Agenda \"nueva\" & más"), mail.urlMiCuenta());
        assertThat(html).contains("Mejoras &lt;b&gt;", "Agenda &quot;nueva&quot; &amp; más", "http://localhost:5173/mi-cuenta",
                "Ir a Tranqui App");
        assertThat(mail.htmlNovedades(null, List.of(), "x")).contains("<h1");
        assertThat(mail.remitente()).isEqualTo("Tranqui App <novedades@tranqui.local>");
    }

    @Test
    void smtp_enviaElHtml() throws Exception {
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Novedades");
    }

    @Test
    void smtp_sinServidorOConError_soloRegistra() {
        doThrow(new MailSendException("caído")).when(sender).send(any(MimeMessage.class));
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
        when(provider.getIfAvailable()).thenReturn(null);
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
    }

    @Test
    void resend_sinApiKey_simula() throws Exception {
        ReflectionTestUtils.setField(mail, "transporte", "resend");
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
        ReflectionTestUtils.setField(mail, "apiKey", null);
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
        verify(http, never()).send(any(), any());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resend_mandaElPedidoYToleraErrores() throws Exception {
        ReflectionTestUtils.setField(mail, "transporte", "resend");
        ReflectionTestUtils.setField(mail, "apiKey", "re_test");
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200, 500);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, org.mockito.Mockito.times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer re_test");

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("red"));
        mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resend_interrumpido_restauraLaInterrupcion() throws Exception {
        ReflectionTestUtils.setField(mail, "transporte", "resend");
        ReflectionTestUtils.setField(mail, "apiKey", "re_test");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {
            mail.enviarHtml("a@b.com", "Novedades", "<p>hola</p>");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
