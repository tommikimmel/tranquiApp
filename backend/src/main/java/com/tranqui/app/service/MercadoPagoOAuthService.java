package com.tranqui.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Handles the Mercado Pago "Connect" OAuth flow so each professional links
 * their own Mercado Pago account. Preferences are later created using the
 * professional's own access token (see MercadoPagoService), so payments are
 * settled 100% into their account with no intermediary/commission.
 */
@Service
public class MercadoPagoOAuthService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoOAuthService.class);
    private static final String AUTH_HOST = "https://auth.mercadopago.com.ar";
    private static final String TOKEN_URL = "https://api.mercadopago.com/oauth/token";
    // Refresh a bit before real expiry to avoid using a token that dies mid-request
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 300;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Value("${mercadopago.client-id:}")
    private String clientId;

    @Value("${mercadopago.client-secret:}")
    private String clientSecret;

    @Value("${app.public-url:http://localhost:8081}")
    private String appPublicUrl;

    @Value("${security.encryption.key:masterdecryptionkey32charspart12}")
    private String stateSigningKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String redirectUri() {
        return appPublicUrl + "/api/medicos/mercadopago/callback";
    }

    public String buildAuthorizationUrl(Usuario medico) {
        String state = signState(medico.getId());
        return AUTH_HOST + "/authorization"
                + "?client_id=" + urlEncode(clientId)
                + "&response_type=code"
                + "&platform_id=mp"
                + "&redirect_uri=" + urlEncode(redirectUri())
                + "&state=" + urlEncode(state);
    }

    /**
     * Validates the OAuth "state" and returns the medico id it was issued for,
     * or null if the state is missing/invalid/tampered with.
     */
    public Long verificarState(String state) {
        if (state == null || !state.contains(".")) {
            return null;
        }
        int sep = state.lastIndexOf('.');
        String payload = state.substring(0, sep);
        String signature = state.substring(sep + 1);
        String expectedSignature = hmac(payload);
        if (!constantTimeEquals(expectedSignature, signature)) {
            return null;
        }
        try {
            return Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Exchanges the authorization code for an access/refresh token pair and
     * persists it (encrypted) on the given medico.
     */
    public Usuario procesarCallback(Long medicoId, String code) throws Exception {
        Usuario medico = usuarioRepository.findById(medicoId)
                .orElseThrow(() -> new IllegalArgumentException("Médico no encontrado: " + medicoId));

        JsonNode tokenResponse = requestToken(
                "grant_type=authorization_code"
                        + "&client_id=" + urlEncode(clientId)
                        + "&client_secret=" + urlEncode(clientSecret)
                        + "&code=" + urlEncode(code)
                        + "&redirect_uri=" + urlEncode(redirectUri())
        );

        aplicarTokenResponse(medico, tokenResponse);
        return usuarioRepository.save(medico);
    }

    private JsonNode requestToken(String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = objectMapper.readTree(response.body());

        if (response.statusCode() >= 400) {
            String message = body.has("message") ? body.get("message").asText() : response.body();
            throw new IllegalStateException("Mercado Pago rechazó la solicitud OAuth: " + message);
        }
        return body;
    }

    private void aplicarTokenResponse(Usuario medico, JsonNode tokenResponse) throws Exception {
        String accessToken = tokenResponse.get("access_token").asText();
        String refreshToken = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null;
        long expiresIn = tokenResponse.has("expires_in") ? tokenResponse.get("expires_in").asLong() : 0;
        String userId = tokenResponse.has("user_id") ? tokenResponse.get("user_id").asText() : null;

        medico.setMpAccessTokenEncrypted(encryptionUtil.encrypt(accessToken));
        if (refreshToken != null) {
            medico.setMpRefreshTokenEncrypted(encryptionUtil.encrypt(refreshToken));
        }
        medico.setMpTokenExpiresAt(expiresIn > 0 ? LocalDateTime.now().plusSeconds(expiresIn) : null);
        if (userId != null) {
            medico.setMpUserId(userId);
        }
    }

    /**
     * Returns a valid (decrypted) access token for the given medico, refreshing
     * it first if it's expired or about to expire. Returns null if the
     * professional never linked their Mercado Pago account.
     */
    public String obtenerAccessTokenValido(Usuario medico) throws Exception {
        if (medico.getMpAccessTokenEncrypted() == null) {
            return null;
        }

        boolean expiraProximamente = medico.getMpTokenExpiresAt() != null
                && LocalDateTime.now().plusSeconds(EXPIRY_SAFETY_MARGIN_SECONDS).isAfter(medico.getMpTokenExpiresAt());

        if (expiraProximamente && medico.getMpRefreshTokenEncrypted() != null) {
            refrescarToken(medico);
        }

        return encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
    }

    private void refrescarToken(Usuario medico) throws Exception {
        String refreshToken = encryptionUtil.decrypt(medico.getMpRefreshTokenEncrypted());
        try {
            JsonNode tokenResponse = requestToken(
                    "grant_type=refresh_token"
                            + "&client_id=" + urlEncode(clientId)
                            + "&client_secret=" + urlEncode(clientSecret)
                            + "&refresh_token=" + urlEncode(refreshToken)
            );
            aplicarTokenResponse(medico, tokenResponse);
            usuarioRepository.save(medico);
        } catch (Exception e) {
            log.error("No se pudo refrescar el token de Mercado Pago del médico {}", medico.getId(), e);
            // Keep the (possibly expired) token; the actual payment call will surface the real error.
        }
    }

    public void desvincular(Usuario medico) {
        medico.setMpAccessTokenEncrypted(null);
        medico.setMpRefreshTokenEncrypted(null);
        medico.setMpTokenExpiresAt(null);
        medico.setMpUserId(null);
        usuarioRepository.save(medico);
    }

    /**
     * Fakes a successful OAuth link without contacting Mercado Pago at all, so the
     * connect/status/disconnect flow can be exercised end-to-end in local development
     * without real credentials. Only meant to be called while mercadopago.enabled=false;
     * callers are responsible for that guard (see MedicoController).
     */
    public void simularConexionDev(Usuario medico) throws Exception {
        medico.setMpAccessTokenEncrypted(encryptionUtil.encrypt("TEST-dev-mock-access-token-" + medico.getId()));
        medico.setMpRefreshTokenEncrypted(encryptionUtil.encrypt("TEST-dev-mock-refresh-token-" + medico.getId()));
        medico.setMpTokenExpiresAt(LocalDateTime.now().plusMonths(6));
        medico.setMpUserId("999999999");
        usuarioRepository.save(medico);
    }

    private String signState(Long medicoId) {
        String payload = String.valueOf(medicoId);
        return payload + "." + hmac(payload);
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSigningKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el estado de OAuth", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
