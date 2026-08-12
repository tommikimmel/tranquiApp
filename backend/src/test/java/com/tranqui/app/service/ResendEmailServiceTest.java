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
    void enviarQueja_conAsuntoYRolCompletos_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarQueja(
                "Marta Rossi", "marta.medico@test.com", "PSIQUIATRA",
                "Problema con un pago", "El pago no se acreditó en mi cuenta de Mercado Pago."));
    }

    @Test
    void enviarQueja_sinAsunto_usaValorPorDefecto() {
        assertDoesNotThrow(() -> resendEmailService.enviarQueja(
                "Pedro Gómez", "pedro@test.com", "PACIENTE", "", "No puedo reservar un turno."));
    }

    @Test
    void enviarQueja_conAsuntoNulo_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarQueja(
                "Pedro Gómez", "pedro@test.com", "PACIENTE", null, "No puedo reservar un turno."));
    }
}
