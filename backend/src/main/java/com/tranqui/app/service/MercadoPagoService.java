package com.tranqui.app.service;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.resources.preapproval.Preapproval;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MercadoPagoService {

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private MercadoPagoOAuthService oauthService;

    @Value("${mercadopago.enabled:false}")
    private boolean isEnabled;

    @Value("${mercadopago.sandbox:true}")
    private boolean isSandbox;

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
                .startDate(OffsetDateTime.now())
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

    public String crearPreferenciaPago(Turno turno, Usuario medico) throws Exception {
        if (!isEnabled) {
            // Simulated return for local/offline dev environments where Mercado Pago isn't
            // configured at all. Once mercadopago.enabled=true (any real deployment), a
            // professional without a linked account must fail loudly below instead of
            // silently falling back to this dev-only simulator in front of real patients.
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
        if (!isEnabled) {
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
