package com.tranqui.app.service;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.preference.Preference;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MercadoPagoService {

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private MercadoPagoOAuthService oauthService;

    @Value("${mercadopago.enabled:false}")
    private boolean isEnabled;

    @Value("${app.public-url:http://localhost:8081}")
    private String appPublicUrl;

    private String notificationUrl() {
        return appPublicUrl + "/api/payments/webhook";
    }

    // Extracted so tests can substitute a mock client instead of hitting the real Mercado Pago API.
    protected PreferenceClient buildPreferenceClient() {
        return new PreferenceClient();
    }

    public String crearPreferenciaPago(Turno turno, Usuario medico) throws Exception {
        String rawToken = "dummy-token";
        if (medico.getMpAccessTokenEncrypted() != null) {
            rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        }

        if (!isEnabled || rawToken.startsWith("dummy") || rawToken.equals("test-token")) {
            // Simulated return for test and offline environments
            return "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id";
        }

        String accessToken = oauthService.obtenerAccessTokenValido(medico);
        if (accessToken == null) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        PreferenceClient client = buildPreferenceClient();

        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .title("Consulta Psiquiátrica - " + medico.getNombre())
                .quantity(1)
                .unitPrice(turno.getPrecio())
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(itemRequest))
                .externalReference(turno.getId().toString())
                .notificationUrl(notificationUrl())
                .build();

        // Per-request access token (instead of the global/static MercadoPagoConfig)
        // so concurrent requests for different professionals never cross wires.
        MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
        Preference preference = client.create(request, options);
        return preference.getInitPoint();
    }

    public String crearPreferenciaDocumento(com.tranqui.app.model.SolicitudDocumento solicitud) throws Exception {
        Usuario medico = solicitud.getMedico();
        String rawToken = "dummy-token";
        if (medico.getMpAccessTokenEncrypted() != null) {
            rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        }

        if (!isEnabled || rawToken.startsWith("dummy") || rawToken.equals("test-token")) {
            return "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-doc-preference-id";
        }

        String accessToken = oauthService.obtenerAccessTokenValido(medico);
        if (accessToken == null) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        PreferenceClient client = buildPreferenceClient();

        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .title(solicitud.getTipoConcepto().toString() + " - " + medico.getNombre())
                .quantity(1)
                .unitPrice(solicitud.getPrecio())
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(itemRequest))
                .externalReference("doc-" + solicitud.getId().toString())
                .notificationUrl(notificationUrl())
                .build();

        MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
        Preference preference = client.create(request, options);
        return preference.getInitPoint();
    }
}
