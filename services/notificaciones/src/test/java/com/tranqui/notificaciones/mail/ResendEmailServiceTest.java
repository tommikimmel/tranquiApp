package com.tranqui.notificaciones.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResendEmailServiceTest {

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
        ReflectionTestUtils.setField(service, "soporteEmail", "soporte@tranquisalud.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "transport", "smtp");
    }

    @Test
    void enviarPasswordTemporal_generaMimeMessageCorrecto() throws Exception {
        service.enviarPasswordTemporal("usuario@test.com", "Carlos", "TemporalPass_99");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage msg = captor.getValue();

        assertThat(((InternetAddress) msg.getAllRecipients()[0]).getAddress()).isEqualTo("usuario@test.com");
        assertThat(msg.getSubject()).isEqualTo("Tu nueva contraseña temporal - Tranqui App");
        String contenido = msg.getContent().toString();
        assertThat(contenido).contains("TemporalPass_99");
        assertThat(contenido).contains("Carlos");
        assertThat(contenido).contains("Nueva contraseña temporal");
    }

    @Test
    void enviarSolicitudCopiaDatos_enviaASoporteConReplyTo() throws Exception {
        service.enviarSolicitudCopiaDatos("Laura Martinez", "laura@test.com", "PSIQUIATRA");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage msg = captor.getValue();

        assertThat(((InternetAddress) msg.getAllRecipients()[0]).getAddress()).isEqualTo("soporte@tranquisalud.com");
        assertThat(msg.getReplyTo()).hasSize(1);
        assertThat(((InternetAddress) msg.getReplyTo()[0]).getAddress()).isEqualTo("laura@test.com");
        assertThat(msg.getSubject()).isEqualTo("[Ley 25.326 - Acceso a datos] Solicitud de PSIQUIATRA");
        String contenido = msg.getContent().toString();
        assertThat(contenido).contains("laura@test.com");
        assertThat(contenido).contains("Laura Martinez");
        assertThat(contenido).contains("PSIQUIATRA");
        assertThat(contenido).contains("Solicitud de copia de datos personales");
    }
}
