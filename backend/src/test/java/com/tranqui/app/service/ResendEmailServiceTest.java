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

    @Test
    void enviarPreavisoRenovacionSuscripcion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarPreavisoRenovacionSuscripcion(
                "dr.test@gmail.com", "Dr. Pérez", "Clínico",
                new java.math.BigDecimal("35000.00"), java.time.LocalDateTime.now().plusDays(3), "monthly"));
    }

    @Test
    void enviarConfirmacionPagoSuscripcion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarConfirmacionPagoSuscripcion(
                "dr.test@gmail.com", "Dr. Pérez", "Clínico",
                new java.math.BigDecimal("35000.00"), java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusMonths(1), "MP ID 987654321"));
    }

    @Test
    void enviarAvisoPagoFallidoSuscripcion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarAvisoPagoFallidoSuscripcion(
                "dr.test@gmail.com", "Dr. Pérez", "Clínico",
                new java.math.BigDecimal("35000.00"), java.time.LocalDateTime.now().plusDays(7)));
    }

    @Test
    void enviarConfirmacionCancelacionSuscripcion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarConfirmacionCancelacionSuscripcion(
                "dr.test@gmail.com", "Dr. Pérez", "Clínico", java.time.LocalDateTime.now().plusDays(20)));
    }

    @Test
    void enviarAvisoSuspensionSuscripcion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarAvisoSuspensionSuscripcion(
                "dr.test@gmail.com", "Dr. Pérez", "Clínico"));
    }

    @Test
    void enviarAvisoVencimientoManualSuscripcion_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarAvisoVencimientoManualSuscripcion(
                "dr.test@gmail.com", "Dr. Pérez", "Consultorio", java.time.LocalDateTime.now().plusDays(3), 3));
    }

    @Test
    void enviarSolicitudCopiaDatos_noLanzaExcepcion() {
        assertDoesNotThrow(() -> resendEmailService.enviarSolicitudCopiaDatos("Pedro Gomez", "pedro@test.com", "PACIENTE"));
    }

    @Test
    void enviarDocumentoAdjunto_devuelveTrueEnMockMode() {
        boolean ok = resendEmailService.enviarDocumentoAdjunto(
                "paciente@test.com", "Pedro", "Dr. Pérez", "Certificado",
                "data:application/pdf;base64,c29tZS1mYWtlLXBkZi1ieXRlcw==", "certificado.pdf");
        org.junit.jupiter.api.Assertions.assertTrue(ok);

        boolean badData = resendEmailService.enviarDocumentoAdjunto(
                "paciente@test.com", "Pedro", "Dr. Pérez", "Certificado",
                "", "certificado.pdf");
        org.junit.jupiter.api.Assertions.assertFalse(badData);
    }

    @Test
    void mailsDeTurnoAlPaciente_noLanzanExcepcion() {
        java.time.LocalDate fecha = java.time.LocalDate.of(2026, 11, 3);
        java.time.LocalTime hora = java.time.LocalTime.of(10, 30);
        assertDoesNotThrow(() -> resendEmailService.enviarTurnoConfirmadoPaciente("paciente@test.com", "Pedro", "Lic. Gómez",
                fecha, hora, true, "Consulta particular", null, "https://meet.google.com/abc-defg-hij", new java.math.BigDecimal("60000")));
        assertDoesNotThrow(() -> resendEmailService.enviarTurnoConfirmadoPaciente("paciente@test.com", null, "Lic. Gómez",
                fecha, hora, true, "Consulta particular", null, null, null));
        assertDoesNotThrow(() -> resendEmailService.enviarTurnoConfirmadoPaciente("paciente@test.com", "Pedro", "Lic. Gómez",
                fecha, hora, false, "Obra Social", "Av. Santa Fe 1234, CABA", null, new java.math.BigDecimal("45000")));
        assertDoesNotThrow(() -> resendEmailService.enviarDocumentoPagadoPaciente("paciente@test.com", "Pedro", "Dr. Pérez",
                "un certificado", new java.math.BigDecimal("8000")));
        for (ResendEmailService.ResultadoReembolso r : ResendEmailService.ResultadoReembolso.values()) {
            assertDoesNotThrow(() -> resendEmailService.enviarTurnoCanceladoPaciente("paciente@test.com", "Pedro", "Lic. Gómez",
                    fecha, hora, false, r));
        }
        assertDoesNotThrow(() -> resendEmailService.enviarTurnoCanceladoPaciente("paciente@test.com", "Pedro", "Lic. Gómez",
                null, null, true, ResendEmailService.ResultadoReembolso.REEMBOLSADO));
        assertDoesNotThrow(() -> resendEmailService.enviarTurnoReprogramadoPaciente("paciente@test.com", "Pedro", "Lic. Gómez",
                fecha, hora, true, null, null));
        assertDoesNotThrow(() -> resendEmailService.enviarRecetaEmitidaPaciente("paciente@test.com", "Pedro", "Dr. Pérez",
                "MN 49281", "https://recetas.example/receta.pdf", "12345"));
        assertDoesNotThrow(() -> resendEmailService.enviarRecetaEmitidaPaciente("paciente@test.com", "Pedro", "Dr. Pérez",
                null, null, null));
    }
}
