package com.tranqui.pagos.mp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Implementación de {@link MercadoPagoAdminApi} contra la API real de Mercado Pago usando el SDK 2.1.0
 * y {@code MP_ADMIN_ACCESS_TOKEN}.
 */
@Component
@ConditionalOnProperty(name = "payment.simulation.enabled", havingValue = "false", matchIfMissing = true)
public class MercadoPagoAdminApiSdk implements MercadoPagoAdminApi {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoAdminApiSdk.class);

    private final String adminAccessToken;
    private final String adminPublicKey;
    private final String frontendUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public MercadoPagoAdminApiSdk(
            @Value("${mercadopago.admin.access-token:}") String adminAccessToken,
            @Value("${mercadopago.admin.public-key:}") String adminPublicKey,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.adminAccessToken = adminAccessToken;
        this.adminPublicKey = adminPublicKey;
        this.frontendUrl = frontendUrl;
    }

    private MPRequestOptions opcionesAdmin() {
        if (adminAccessToken == null || adminAccessToken.isBlank()) {
            throw new IllegalStateException("El cobro de suscripciones todavía no está configurado (falta MP_ADMIN_ACCESS_TOKEN).");
        }
        return MPRequestOptions.builder().accessToken(adminAccessToken).build();
    }

    @Override
    public PreapprovalMp crearPreapproval(String email, String planName, BigDecimal monto, boolean esAnual, Long subscriptionId) throws Exception {
        MPRequestOptions options = opcionesAdmin();

        PreApprovalAutoRecurringCreateRequest autoRecurring = PreApprovalAutoRecurringCreateRequest.builder()
                .frequency(esAnual ? 12 : 1)
                .frequencyType("months")
                .transactionAmount(monto)
                .currencyId("ARS")
                .startDate(OffsetDateTime.now().plusMinutes(10))
                .build();

        PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                .payerEmail(email)
                .backUrl((frontendUrl != null ? frontendUrl : "http://localhost:5173") + "/panel")
                .reason("Suscripción Tranqui App - " + planName + (esAnual ? " (anual)" : ""))
                .externalReference("sub-" + subscriptionId)
                .status("pending")
                .autoRecurring(autoRecurring)
                .build();

        PreapprovalClient client = new PreapprovalClient();
        Preapproval preapproval = client.create(request, options);
        return new PreapprovalMp(preapproval.getId(), preapproval.getInitPoint(), preapproval.getStatus());
    }

    @Override
    public void cancelarPreapproval(String preapprovalId) throws Exception {
        MPRequestOptions options = opcionesAdmin();
        PreapprovalUpdateRequest request = PreapprovalUpdateRequest.builder()
                .status("cancelled")
                .build();
        new PreapprovalClient().update(preapprovalId, request, options);
    }

    @Override
    public PreapprovalMp obtenerPreapproval(String preapprovalId) {
        if (adminAccessToken == null || adminAccessToken.isBlank() || preapprovalId == null || preapprovalId.isBlank()) {
            return null;
        }
        try {
            MPRequestOptions options = MPRequestOptions.builder().accessToken(adminAccessToken).build();
            Preapproval p = new PreapprovalClient().get(preapprovalId, options);
            return p != null ? new PreapprovalMp(p.getId(), p.getInitPoint(), p.getStatus()) : null;
        } catch (Exception e) {
            log.warn("Error al consultar preapproval {} en Mercado Pago: {}", preapprovalId, e.getMessage());
            return null;
        }
    }

    @Override
    public PagoAdminMp obtenerPagoAdmin(Long paymentId) throws Exception {
        MPRequestOptions options = opcionesAdmin();
        Payment payment = new PaymentClient().get(paymentId, options);
        return payment != null
                ? new PagoAdminMp(payment.getId(), payment.getStatus(), payment.getExternalReference(), payment.getTransactionAmount())
                : null;
    }

    @Override
    public JsonNode obtenerDetalleAuthorizedPayment(String authorizedPaymentId) {
        if (adminAccessToken == null || adminAccessToken.isBlank() || authorizedPaymentId == null || authorizedPaymentId.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mercadopago.com/authorized_payments/" + authorizedPaymentId.trim()))
                    .header("Authorization", "Bearer " + adminAccessToken.trim())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readTree(response.body());
            }
            log.warn("No se pudo obtener detalle de authorized_payment {}. Status: {}, Body: {}",
                    authorizedPaymentId, response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("Excepción al consultar authorized_payment {} en Mercado Pago: {}", authorizedPaymentId, e.getMessage());
            return null;
        }
    }
}
