package com.tranqui.pagos.mp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mercado Pago de verdad, con el SDK 2.1.0 como el monolito (MercadoPagoService, WebhookController,
 * ReembolsoService y MercadoPagoOAuthService): mismas preferencias, mismas búsquedas y el mismo OAuth.
 */
@Component
public class MercadoPagoApiSdk implements MercadoPagoApi {

    private static final String TOKEN_URL = "https://api.mercadopago.com/oauth/token";

    private final String notificationUrl;
    private final String frontendUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public MercadoPagoApiSdk(@Value("${app.public-url}") String publicUrl,
                             @Value("${app.frontend-url}") String frontendUrl) {
        // Con la Fase 4 el webhook lo atiende este servicio, en la misma URL pública de siempre (gateway).
        this.notificationUrl = publicUrl + "/api/payments/webhook";
        this.frontendUrl = frontendUrl;
    }

    private static MPRequestOptions con(String accessToken) {
        return MPRequestOptions.builder().accessToken(accessToken).build();
    }

    @Override
    public String crearPreferencia(String accessToken, String titulo, BigDecimal monto, String referencia) throws Exception {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(titulo)
                .categoryId("health")
                .quantity(1)
                .currencyId("ARS")
                .unitPrice(monto)
                .build();
        PreferenceBackUrlsRequest vuelta = PreferenceBackUrlsRequest.builder()
                .success(frontendUrl)
                .failure(frontendUrl)
                .pending(frontendUrl)
                .build();
        PreferenceRequest pedido = PreferenceRequest.builder()
                .items(List.of(item))
                .externalReference(referencia)
                .notificationUrl(notificationUrl)
                .backUrls(vuelta)
                .autoReturn("approved")
                .build();
        return new PreferenceClient().create(pedido, con(accessToken)).getInitPoint();
    }

    @Override
    public PagoMp obtenerPago(String accessToken, long paymentId) throws Exception {
        return pago(new PaymentClient().get(paymentId, con(accessToken)));
    }

    @Override
    public PagoMp buscarPorReferencia(String accessToken, String referencia) throws Exception {
        Map<String, Object> filtros = new HashMap<>();
        filtros.put("external_reference", referencia);
        filtros.put("sort", "date_created");
        filtros.put("criteria", "desc");
        MPResultsResourcesPage<Payment> pagina = new PaymentClient()
                .search(MPSearchRequest.builder().filters(filtros).build(), con(accessToken));
        List<Payment> resultados = pagina != null ? pagina.getResults() : null;
        if (resultados == null || resultados.isEmpty()) {
            return null;
        }
        return pago(resultados.stream().filter(p -> "approved".equals(p.getStatus())).findFirst().orElse(resultados.get(0)));
    }

    @Override
    public void reembolsar(String accessToken, long paymentId) throws Exception {
        new PaymentRefundClient().refund(paymentId, con(accessToken));
    }

    @Override
    public TokensOAuth pedirTokens(String formulario) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                .build();
        HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        JsonNode cuerpo = mapper.readTree(respuesta.body());
        if (respuesta.statusCode() >= 400) {
            String mensaje = cuerpo.has("message") ? cuerpo.get("message").asText() : respuesta.body();
            throw new IllegalStateException("Mercado Pago rechazó la solicitud OAuth: " + mensaje);
        }
        return new TokensOAuth(cuerpo.get("access_token").asText(),
                cuerpo.has("refresh_token") ? cuerpo.get("refresh_token").asText() : null,
                cuerpo.has("expires_in") ? cuerpo.get("expires_in").asLong() : 0,
                cuerpo.has("user_id") ? cuerpo.get("user_id").asText() : null);
    }

    private static PagoMp pago(Payment p) {
        return p == null ? null : new PagoMp(p.getId(), p.getStatus(), p.getExternalReference(), p.getTransactionAmount());
    }
}
