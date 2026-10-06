package com.tranqui.app.controller;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.resources.payment.Payment;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.MercadoPagoOAuthService;
import com.tranqui.app.service.PagoWebhookHandler;
import com.tranqui.app.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * mercadopago.enabled defaults to false in src/test/resources/application.yml, so
 * MercadoPagoWebhookValidator runs in its lenient/legacy mode: any x-signature other than
 * "invalid-signature" (or one containing "fake") is accepted, and "test-signature" is always
 * accepted explicitly. That's what every test below relies on instead of computing a real HMAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PagoWebhookHandler pagoWebhookHandler;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private com.tranqui.app.service.MercadoPagoService mercadoPagoService;

    @MockBean
    private MercadoPagoOAuthService oauthService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private TurnoRepository turnoRepository;

    // ── /api/payments/webhook ────────────────────────────────────────────────

    @Test
    void receiveWebhook_paymentBranch_medicoEncontradoYAprobado_procesaTurno() throws Exception {
        Usuario medico = Usuario.builder().id(1L).nombre("Dra. Paula").email("dra@mail.com")
                .rol(Rol.PSIQUIATRA).mpUserId("mp-1").build();
        when(usuarioRepository.findByMpUserId("mp-1")).thenReturn(Optional.of(medico));
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn("token-abc");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class,
                (paymentClientMock, context) -> {
                    Payment payment = mock(Payment.class);
                    when(payment.getExternalReference()).thenReturn("55");
                    when(payment.getId()).thenReturn(999888L);
                    when(payment.getStatus()).thenReturn("approved");
                    when(paymentClientMock.get(anyLong(), any())).thenReturn(payment);
                })) {
            String body = "{\"type\":\"payment\",\"data\":{\"id\":\"999888\"},\"user_id\":\"mp-1\"}";
            mockMvc.perform(post("/api/payments/webhook")
                            .header("x-signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        verify(pagoWebhookHandler).procesarAprobacionTurno(55L, "999888");
    }

    @Test
    void receiveWebhook_paymentBranch_medicoNoEncontrado_noProcesaNadaPeroDevuelve200() throws Exception {
        when(usuarioRepository.findByMpUserId("mp-desconocido")).thenReturn(Optional.empty());

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"1\"},\"user_id\":\"mp-desconocido\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verifyNoInteractions(pagoWebhookHandler);
    }

    @Test
    void receiveWebhook_subscriptionPreapproval_delegaASubscriptionService() throws Exception {
        String body = "{\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"preap-1\"},\"status\":\"authorized\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(subscriptionService).processMercadoPagoPreapprovalWebhook("preap-1", "authorized");
        verifyNoInteractions(pagoWebhookHandler);
    }

    @Test
    void receiveWebhook_subscriptionAuthorizedPayment_delegaASubscriptionServiceConMonto() throws Exception {
        String body = "{\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":\"pay-9\"}," +
                "\"preapproval_id\":\"preap-2\",\"transaction_amount\":\"15000.50\",\"status\":\"approved\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(subscriptionService).processMercadoPagoPaymentWebhook(
                eq("pay-9"), eq("preap-2"), eq(new BigDecimal("15000.50")), eq("approved"));
        verifyNoInteractions(pagoWebhookHandler);
    }

    @Test
    void receiveWebhook_subscriptionAuthorizedPayment_usaElIdDelPagoRealParaDeduplicar() throws Exception {
        // El webhook trae el ID del authorized_payment ("ap-1"); el detalle trae el ID del pago real
        // ("777"), que es el mismo que llega por el webhook genérico "payment" del mismo cobro.
        when(mercadoPagoService.obtenerDetalleAuthorizedPayment("ap-1")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"preapproval_id\":\"preap-3\",\"transaction_amount\":9000,\"status\":\"processed\",\"payment\":{\"id\":777,\"status\":\"approved\"}}"));
        String body = "{\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":\"ap-1\"}}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(subscriptionService).processMercadoPagoPaymentWebhook(
                eq("777"), eq("preap-3"), eq(new BigDecimal("9000")), eq("processed"));
    }

    @Test
    void receiveWebhook_simuladorConPrefijoDoc_procesaAprobacionDeConcepto() throws Exception {
        String body = "{\"external_reference\":\"doc-77\",\"transaction_id\":\"tx-doc\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(pagoWebhookHandler).procesarAprobacionConcepto(77L, "tx-doc");
    }

    @Test
    void receiveWebhook_simuladorSinPrefijoDoc_procesaAprobacionDeTurno() throws Exception {
        String body = "{\"external_reference\":\"321\",\"transaction_id\":\"tx-turno\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(pagoWebhookHandler).procesarAprobacionTurno(321L, "tx-turno");
    }

    @Test
    void receiveWebhook_cualquierExcepcionInterna_igualDevuelve200ParaLosReintentosDeMercadoPago() throws Exception {
        // Comportamiento intencional: MP reintenta un webhook que no devuelva 200, así que
        // receiveWebhook envuelve todo el body del try en un catch(Exception) y siempre responde
        // 200 — un error nuestro (DB caída, NPE, lo que sea) nunca debe traducirse en reintentos
        // infinitos de Mercado Pago.
        when(usuarioRepository.findByMpUserId(anyString())).thenThrow(new RuntimeException("DB caída"));

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"1\"},\"user_id\":\"mp-1\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void receiveWebhook_firmaInvalida_devuelve400YNoProcesaNada() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .header("x-signature", "invalid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_reference\":\"1\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pagoWebhookHandler);
        verifyNoInteractions(subscriptionService);
    }

    // ── /api/payments/verificar ──────────────────────────────────────────────

    @Test
    void verificarPago_conPaymentId_procesaYDevuelve200() throws Exception {
        Usuario medico = Usuario.builder().id(1L).build();
        Turno turno = Turno.builder().id(10L).medico(medico).estado(EstadoTurno.PENDIENTE_PAGO).build();
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn("token-x");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class,
                (paymentClientMock, context) -> {
                    Payment payment = mock(Payment.class);
                    when(payment.getExternalReference()).thenReturn("10");
                    when(payment.getId()).thenReturn(555L);
                    when(payment.getStatus()).thenReturn("approved");
                    when(paymentClientMock.get(anyLong(), any())).thenReturn(payment);
                })) {
            mockMvc.perform(post("/api/payments/verificar")
                            .param("externalReference", "10")
                            .param("paymentId", "999"))
                    .andExpect(status().isOk());
        }

        verify(pagoWebhookHandler).procesarAprobacionTurno(10L, "555");
    }

    @Test
    void verificarPago_turnoYaConfirmado_esNoOpYDevuelve200() throws Exception {
        Turno turno = Turno.builder().id(11L).estado(EstadoTurno.CONFIRMADO).build();
        when(turnoRepository.findById(11L)).thenReturn(Optional.of(turno));

        mockMvc.perform(post("/api/payments/verificar")
                        .param("externalReference", "11"))
                .andExpect(status().isOk());

        verifyNoInteractions(pagoWebhookHandler);
        verifyNoInteractions(oauthService);
    }

    @Test
    void verificarPago_sinPaymentId_buscaPorExternalReferenceYProcesa() throws Exception {
        Usuario medico = Usuario.builder().id(2L).build();
        Turno turno = Turno.builder().id(12L).medico(medico).estado(EstadoTurno.PENDIENTE_PAGO).build();
        when(turnoRepository.findById(12L)).thenReturn(Optional.of(turno));
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn("token-y");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class,
                (paymentClientMock, context) -> {
                    Payment payment = mock(Payment.class);
                    when(payment.getExternalReference()).thenReturn("12");
                    when(payment.getId()).thenReturn(777L);
                    when(payment.getStatus()).thenReturn("approved");
                    @SuppressWarnings("unchecked")
                    MPResultsResourcesPage<Payment> page = mock(MPResultsResourcesPage.class);
                    when(page.getResults()).thenReturn(List.of(payment));
                    when(paymentClientMock.search(any(), any())).thenReturn(page);
                })) {
            mockMvc.perform(post("/api/payments/verificar")
                            .param("externalReference", "12"))
                    .andExpect(status().isOk());
        }

        verify(pagoWebhookHandler).procesarAprobacionTurno(12L, "777");
    }

    @Test
    void verificarPago_turnoNoEncontrado_devuelve404() throws Exception {
        when(turnoRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/payments/verificar").param("externalReference", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void verificarPago_externalReferenceInvalida_devuelve400() throws Exception {
        mockMvc.perform(post("/api/payments/verificar").param("externalReference", "no-es-un-numero"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verificarPago_pagoNoCorrespondeAlTurno_devuelveConflict() throws Exception {
        Usuario medico = Usuario.builder().id(3L).build();
        Turno turno = Turno.builder().id(13L).medico(medico).estado(EstadoTurno.PENDIENTE_PAGO).build();
        when(turnoRepository.findById(13L)).thenReturn(Optional.of(turno));
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn("token-z");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class,
                (paymentClientMock, context) -> {
                    Payment payment = mock(Payment.class);
                    when(payment.getExternalReference()).thenReturn("OTRO-TURNO-DISTINTO");
                    when(paymentClientMock.get(anyLong(), any())).thenReturn(payment);
                })) {
            mockMvc.perform(post("/api/payments/verificar")
                            .param("externalReference", "13")
                            .param("paymentId", "1"))
                    .andExpect(status().isConflict());
        }

        verifyNoInteractions(pagoWebhookHandler);
    }

    @Test
    void verificarPago_sinAccessTokenValido_devuelveConflict() throws Exception {
        Usuario medico = Usuario.builder().id(4L).build();
        Turno turno = Turno.builder().id(14L).medico(medico).estado(EstadoTurno.PENDIENTE_PAGO).build();
        when(turnoRepository.findById(14L)).thenReturn(Optional.of(turno));
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn(null);

        mockMvc.perform(post("/api/payments/verificar").param("externalReference", "14"))
                .andExpect(status().isConflict());

        verifyNoInteractions(pagoWebhookHandler);
    }
}
