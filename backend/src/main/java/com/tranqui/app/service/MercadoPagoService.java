package com.tranqui.app.service;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.resources.preapproval.Preapproval;
import com.mercadopago.resources.payment.Payment;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.Turno;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private MercadoPagoOAuthService oauthService;

    @Value("${mercadopago.enabled:false}")
    private boolean isEnabled;

    @Value("${mercadopago.sandbox:true}")
    private boolean isSandbox;

    @Value("${payment.simulation.enabled:false}")
    private boolean paymentSimulationEnabled;

    @Value("${app.public-url:http://localhost:8081}")
    private String appPublicUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Cuenta del ADMINISTRADOR/plataforma (Etapa 2 del paywall) — distinta del access token
    // por-profesional que usan crearPreferenciaPago/crearPreferenciaDocumento arriba. Con esto se
    // crea el Preapproval (suscripción recurrente) que le cobra al PROFESIONAL a favor del admin.
    @Value("${mercadopago.admin.access-token:}")
    private String adminAccessToken;

    private String notificationUrl() {
        return appPublicUrl + "/api/payments/webhook";
    }

    // Extracted so tests can substitute a mock client instead of hitting the real Mercado Pago API.
    protected PreferenceClient buildPreferenceClient() {
        return new PreferenceClient();
    }

    protected PreapprovalClient buildPreapprovalClient() {
        return new PreapprovalClient();
    }

    /**
     * Crea un Preapproval (suscripción recurrente mensual) de Mercado Pago cobrado a la cuenta
     * del administrador — el profesional autoriza el pago recurrente visitando la URL de checkout
     * que devuelve este método (ver checkoutUrlFor). No usa notificationUrl explícita porque el SDK
     * 2.1.0 no expone ese campo en PreapprovalCreateRequest: depende de que la aplicación de MP
     * (mismo App ID que MERCADOPAGO_CLIENT_ID — confirmado por el prefijo del access token) tenga
     * configurado en su panel de desarrollador un webhook de "Suscripciones" apuntando a
     * appPublicUrl + /api/payments/webhook, con el mismo secreto que MERCADOPAGO_WEBHOOK_SECRET.
     */
    public Preapproval crearSuscripcionPreapproval(Usuario profesional, Plan plan, Long subscriptionId) throws Exception {
        return crearSuscripcionPreapproval(profesional, plan, subscriptionId, "monthly");
    }

    /**
     * @param billingCycle "annual" cobra plan.getPriceArsAnual() con frequency=12 meses; cualquier
     *                      otro valor (incluido null) cobra plan.getPriceArs() mensual, como antes.
     */
    public Preapproval crearSuscripcionPreapproval(Usuario profesional, Plan plan, Long subscriptionId, String billingCycle) throws Exception {
        if (paymentSimulationEnabled) {
            log.info("Mercado Pago Preapproval en modo desarrollo/simulación (sin cobro real). Devolviendo Preapproval simulado para sub {}", subscriptionId);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String mockUrl = (frontendUrl != null ? frontendUrl : "http://localhost:3000") + "/panel?sub_mock=success&sub_id=" + subscriptionId;
            return mapper.readValue("{\"id\":\"MOCK-PRE-" + subscriptionId + "\",\"initPoint\":\"" + mockUrl + "\",\"sandboxInitPoint\":\"" + mockUrl + "\"}", Preapproval.class);
        }

        if (adminAccessToken == null || adminAccessToken.isBlank()) {
            throw new IllegalStateException("El cobro de suscripciones todavía no está configurado (falta MP_ADMIN_ACCESS_TOKEN).");
        }

        boolean esAnual = "annual".equalsIgnoreCase(billingCycle);
        if (esAnual && plan.getPriceArsAnual() == null) {
            throw new IllegalStateException("El plan " + plan.getName() + " todavía no tiene precio anual configurado.");
        }

        PreApprovalAutoRecurringCreateRequest autoRecurring = PreApprovalAutoRecurringCreateRequest.builder()
                .frequency(esAnual ? 12 : 1)
                .frequencyType("months")
                .transactionAmount(esAnual ? plan.getPriceArsAnual() : plan.getPriceArs())
                .currencyId("ARS")
                // OffsetDateTime.now() queda en el pasado para cuando la request llega a validarse
                // del lado de Mercado Pago (latencia de red + posible desfasaje de reloj entre este
                // servidor y el de MP), y la API rechaza el Preapproval entero con "Invalid value
                // for auto_recurring.start_date, cannot be a past date". Un margen de 10 minutos
                // no afecta al profesional — igual autoriza el pago de inmediato al entrar al
                // checkout, start_date solo define desde cuándo puede arrancar a cobrar.
                .startDate(OffsetDateTime.now().plusMinutes(10))
                .build();

        PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                .payerEmail(profesional.getEmail())
                .backUrl(frontendUrl + "/panel")
                .reason("Suscripción Tranqui App - " + plan.getName() + (esAnual ? " (anual)" : ""))
                .externalReference("sub-" + subscriptionId)
                .status("pending")
                .autoRecurring(autoRecurring)
                .build();

        PreapprovalClient client = buildPreapprovalClient();
        MPRequestOptions options = MPRequestOptions.builder().accessToken(adminAccessToken).build();
        return client.create(request, options);
    }

    public String checkoutUrlFor(Preapproval preapproval) {
        return isSandbox ? preapproval.getSandboxInitPoint() : preapproval.getInitPoint();
    }

    // Corta el cobro recurrente del lado de Mercado Pago (si no se llama esto, MP sigue
    // debitando todos los meses aunque nuestra base ya haya marcado la suscripción como
    // cancelada). El acceso local no se corta acá — sigue vigente hasta currentPeriodEnd, ver
    // SubscriptionService.cancelarSuscripcionPorProfesional.
    public void cancelarSuscripcionPreapproval(String preapprovalId) throws Exception {
        PreapprovalUpdateRequest request = PreapprovalUpdateRequest.builder()
                .status("cancelled")
                .build();
        MPRequestOptions options = MPRequestOptions.builder().accessToken(adminAccessToken).build();
        buildPreapprovalClient().update(preapprovalId, request, options);
    }

    // Usado por WebhookController para reconocer un webhook "payment" genérico como el cobro de
    // una suscripción: los pagos recurrentes de Preapproval los cobra la cuenta ADMIN (no el
    // OAuth por-profesional que usan los pagos de turno), así que hay que leerlo con este token
    // en vez de buscar a qué médico pertenece.
    public Payment obtenerPagoAdmin(Long paymentId) throws Exception {
        MPRequestOptions options = MPRequestOptions.builder().accessToken(adminAccessToken).build();
        return new PaymentClient().get(paymentId, options);
    }

    /**
     * Consulta el estado actual de un Preapproval en la API de Mercado Pago.
     */
    public Preapproval obtenerPreapproval(String mpPreapprovalId) {
        if (adminAccessToken == null || adminAccessToken.isBlank() || mpPreapprovalId == null || mpPreapprovalId.isBlank()) {
            return null;
        }
        try {
            PreapprovalClient client = buildPreapprovalClient();
            MPRequestOptions options = MPRequestOptions.builder().accessToken(adminAccessToken).build();
            return client.get(mpPreapprovalId, options);
        } catch (Exception e) {
            log.warn("Error al consultar preapproval {} en Mercado Pago: {}", mpPreapprovalId, e.getMessage());
            return null;
        }
    }

    /**
     * Consulta la API REST de Mercado Pago (/v1/authorized_payments/{id}) para obtener
     * el preapproval_id, monto y estado real de un cobro de suscripción recurrente.
     */
    public JsonNode obtenerDetalleAuthorizedPayment(String authorizedPaymentId) {
        if (adminAccessToken == null || adminAccessToken.isBlank() || authorizedPaymentId == null || authorizedPaymentId.isBlank()) {
            return null;
        }
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mercadopago.com/authorized_payments/" + authorizedPaymentId.trim()))
                    .header("Authorization", "Bearer " + adminAccessToken.trim())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ObjectMapper().readTree(response.body());
            }
            log.warn("No se pudo obtener detalle de authorized_payment {}. Status: {}, Body: {}", authorizedPaymentId, response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("Excepción al consultar authorized_payment {} en Mercado Pago: {}", authorizedPaymentId, e.getMessage());
            return null;
        }
    }

    public String crearPreferenciaPago(Turno turno, Usuario medico) throws Exception {
        if (!isEnabled || paymentSimulationEnabled) {
            // Simulated return for local/offline dev environments where Mercado Pago isn't
            // configured or payment simulation is enabled so no real money is ever charged.
            return "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id";
        }

        String rawToken = "dummy-token";
        if (medico.getMpAccessTokenEncrypted() != null) {
            rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        }
        if (rawToken.startsWith("dummy") || rawToken.equals("test-token")) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        String accessToken = oauthService.obtenerAccessTokenValido(medico);
        if (accessToken == null) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        PreferenceClient client = buildPreferenceClient();

        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .title("Consulta Psiquiátrica - " + medico.getNombre())
                .categoryId("health")
                .quantity(1)
                .currencyId("ARS")
                .unitPrice(turno.getPrecio())
                .build();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendUrl)
                .failure(frontendUrl)
                .pending(frontendUrl)
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(itemRequest))
                .externalReference(turno.getId().toString())
                .notificationUrl(notificationUrl())
                .backUrls(backUrls)
                .autoReturn("approved")
                .build();

        // Per-request access token (instead of the global/static MercadoPagoConfig)
        // so concurrent requests for different professionals never cross wires.
        MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
        Preference preference = client.create(request, options);
        return isSandbox ? preference.getSandboxInitPoint() : preference.getInitPoint();
    }

    public String crearPreferenciaDocumento(com.tranqui.app.model.SolicitudDocumento solicitud) throws Exception {
        Usuario medico = solicitud.getMedico();
        if (!isEnabled || paymentSimulationEnabled) {
            return "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-doc-preference-id";
        }

        String rawToken = "dummy-token";
        if (medico.getMpAccessTokenEncrypted() != null) {
            rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        }
        if (rawToken.startsWith("dummy") || rawToken.equals("test-token")) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        String accessToken = oauthService.obtenerAccessTokenValido(medico);
        if (accessToken == null) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        PreferenceClient client = buildPreferenceClient();

        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .title(solicitud.getTipoConcepto().toString() + " - " + medico.getNombre())
                .categoryId("health")
                .quantity(1)
                .currencyId("ARS")
                .unitPrice(solicitud.getPrecio())
                .build();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendUrl)
                .failure(frontendUrl)
                .pending(frontendUrl)
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(itemRequest))
                .externalReference("doc-" + solicitud.getId().toString())
                .notificationUrl(notificationUrl())
                .backUrls(backUrls)
                .autoReturn("approved")
                .build();

        MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
        Preference preference = client.create(request, options);
        return isSandbox ? preference.getSandboxInitPoint() : preference.getInitPoint();
    }
}
