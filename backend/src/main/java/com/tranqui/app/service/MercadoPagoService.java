package com.tranqui.app.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
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

    @Value("${mercadopago.enabled:false}")
    private boolean isEnabled;

    public String crearPreferenciaPago(Turno turno, Usuario medico) throws Exception {
        String rawToken = "dummy-token";
        if (medico.getMpAccessTokenEncrypted() != null) {
            rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        }

        if (!isEnabled || rawToken.startsWith("dummy") || rawToken.equals("test-token")) {
            // Simulated return for test and offline environments
            return "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id";
        }

        // Initialize SDK with physician's decrypted token
        MercadoPagoConfig.setAccessToken(rawToken);

        PreferenceClient client = new PreferenceClient();

        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .title("Consulta Psiquiátrica - " + medico.getNombre())
                .quantity(1)
                .unitPrice(turno.getPrecio())
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(itemRequest))
                .externalReference(turno.getId().toString())
                .notificationUrl("https://tranquiapp.com/api/payments/webhook")
                .build();

        Preference preference = client.create(request);
        return preference.getInitPoint();
    }
}
