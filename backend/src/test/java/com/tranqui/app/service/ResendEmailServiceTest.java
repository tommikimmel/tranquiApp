package com.tranqui.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// No RESEND_API_KEY is configured in the test profile (backend/src/test/resources/application.yml
// doesn't override it, and the main application.yml defaults it to blank), so every call here
// exercises the "no API key configured" mock/dev-mode branch of enviarCorreo — logs and returns
// without hitting the real Resend API. That branch is exactly what runs in local dev too, so it's
// worth covering even without a real HTTP call to assert against.
@SpringBootTest
class ResendEmailServiceTest {

    @Autowired
    private ResendEmailService resendEmailService;

    @Test
    void enviarCodigoVerificacion_conNombre_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarCodigoVerificacion("paciente@test.com", "Pedro", "123456"));
    }

    @Test
    void enviarCodigoVerificacion_sinNombre_usaValorPorDefecto() {
        assertDoesNotThrow(() -> resendEmailService.enviarCodigoVerificacion("paciente@test.com", null, "654321"));
    }

    @Test
    void enviarCodigoRecuperacion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarCodigoRecuperacion("paciente@test.com", "Pedro", "111111"));
    }

    @Test
    void enviarRespuestaTicket_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarRespuestaTicket(
                "pedro@test.com", "Pedro", "Problema con un pago", "Ya lo revisamos, quedó resuelto."));
    }

    @Test
    void enviarPasswordTemporal_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarPasswordTemporal("pedro@test.com", "Pedro", "Ab3xY9kLmP"));
    }
}
