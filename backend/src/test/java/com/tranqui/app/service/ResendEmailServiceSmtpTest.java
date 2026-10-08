package com.tranqui.app.service;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// El transporte SMTP solo se usa en el entorno local (Mailpit). Producción usa Resend: estos tests
// garantizan que con mail.transport=resend nunca se toca el JavaMailSender.
class ResendEmailServiceSmtpTest {

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
        ReflectionTestUtils.setField(service, "fromEmail", "soporte@tranqui.local");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "apiKey", "");
    }

    @Test
    void conTransporteSmtp_envíaElCorreoPorJavaMailSender() throws Exception {
        ReflectionTestUtils.setField(service, "transport", "smtp");

        service.enviarCodigoVerificacion("paciente@test.com", "Pedro", "123456");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage enviado = captor.getValue();
        assertEquals("paciente@test.com", ((InternetAddress) enviado.getAllRecipients()[0]).getAddress());
        assertTrue(enviado.getFrom()[0].toString().contains("soporte@tranqui.local"));
        assertTrue(enviado.getSubject() != null && !enviado.getSubject().isBlank());
    }

    @Test
    void conTransporteSmtp_adjuntaElDocumento() throws Exception {
        ReflectionTestUtils.setField(service, "transport", "smtp");
        String dataUri = "data:application/pdf;base64," + Base64.getEncoder().encodeToString("pdf de prueba".getBytes());

        boolean ok = service.enviarDocumentoAdjunto("paciente@test.com", "Pedro", "Lucía Fernández", "Certificado", dataUri, "certificado.pdf");

        assertTrue(ok);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage enviado = captor.getValue();
        enviado.saveChanges();
        Multipart partes = (Multipart) enviado.getContent();
        boolean tieneAdjunto = false;
        for (int i = 0; i < partes.getCount(); i++) {
            if ("certificado.pdf".equals(partes.getBodyPart(i).getFileName())) tieneAdjunto = true;
        }
        assertTrue(tieneAdjunto);
    }

    @Test
    void conTransporteResend_nuncaUsaSmtp() {
        ReflectionTestUtils.setField(service, "transport", "resend");

        service.enviarCodigoVerificacion("paciente@test.com", "Pedro", "123456");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void conTransporteSmtpSinServidorConfigurado_noLanzaExcepcion() {
        ReflectionTestUtils.setField(service, "transport", "smtp");
        when(provider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> service.enviarCodigoVerificacion("paciente@test.com", "Pedro", "123456"));
        String dataUri = "data:application/pdf;base64," + Base64.getEncoder().encodeToString("x".getBytes());
        assertFalse(service.enviarDocumentoAdjunto("paciente@test.com", "Pedro", "Lucía", "Certificado", dataUri, "c.pdf"));
    }
}
