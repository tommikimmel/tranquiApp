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

@Service
public class GoogleCalendarOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarOAuthService.class);
    private static final String AUTH_HOST = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 300;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private com.tranqui.app.repository.GoogleCalendarEventoExternoRepository eventoExternoRepository;

    @Value("${google.client-id:dummy-client-id}")
    private String clientId;

    @Value("${google.client-secret:}")
    private String clientSecret;

    @Value("${app.public-url:http://localhost:8081}")
    private String appPublicUrl;

    @Value("${security.encryption.key:masterdecryptionkey32charspart12}")
    private String stateSigningKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String redirectUri() {
        return appPublicUrl + "/api/medicos/google-calendar/callback";
    }

    public String buildAuthorizationUrl(Usuario medico) {
        String state = signState(medico.getId());
        return AUTH_HOST
                + "?client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri())
                + "&response_type=code"
                + "&scope=" + urlEncode("https://www.googleapis.com/auth/calendar.events")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + urlEncode(state);
    }

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
        medico.setGoogleCalendarConnected(true);
        return usuarioRepository.save(medico);
    }

    public String obtenerAccessToken(Usuario medico) {
        if (medico.getGoogleRefreshTokenEncrypted() == null) {
            return null;
        }

        try {
            // Check if existing access token is still valid
            if (medico.getGoogleAccessTokenEncrypted() != null && medico.getGoogleTokenExpiresAt() != null) {
                if (medico.getGoogleTokenExpiresAt().isAfter(LocalDateTime.now().plusSeconds(EXPIRY_SAFETY_MARGIN_SECONDS))) {
                    return encryptionUtil.decrypt(medico.getGoogleAccessTokenEncrypted());
                }
            }

            // Expirado o a punto de expirar -> Refresh token
            log.info("Google Access Token expirado o ausente para el médico ID {}. Refrescando...", medico.getId());
            String refreshToken = encryptionUtil.decrypt(medico.getGoogleRefreshTokenEncrypted());
            JsonNode tokenResponse = requestToken(
                    "grant_type=refresh_token"
                            + "&client_id=" + urlEncode(clientId)
                            + "&client_secret=" + urlEncode(clientSecret)
                            + "&refresh_token=" + urlEncode(refreshToken)
            );

            aplicarTokenResponse(medico, tokenResponse);
            usuarioRepository.save(medico);
            return encryptionUtil.decrypt(medico.getGoogleAccessTokenEncrypted());

        } catch (Exception e) {
            log.error("Fallo al renovar Google Access Token para médico ID {}", medico.getId(), e);
            return null;
        }
    }

    private JsonNode requestToken(String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error en respuesta de Google Token API. Status: " + response.statusCode() + " Body: " + response.body());
        }

        return objectMapper.readTree(response.body());
    }

    private void aplicarTokenResponse(Usuario medico, JsonNode tokenResponse) throws Exception {
        String accessToken = tokenResponse.get("access_token").asText();
        medico.setGoogleAccessTokenEncrypted(encryptionUtil.encrypt(accessToken));

        if (tokenResponse.has("refresh_token")) {
            String refreshToken = tokenResponse.get("refresh_token").asText();
            medico.setGoogleRefreshTokenEncrypted(encryptionUtil.encrypt(refreshToken));
        }

        int expiresInSeconds = tokenResponse.get("expires_in").asInt();
        medico.setGoogleTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
    }

    private String signState(Long medicoId) {
        String payload = String.valueOf(medicoId);
        return payload + "." + hmac(payload);
    }

    private String hmac(String payload) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(stateSigningKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Fallo al firmar el estado de Google OAuth", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public void desvincular(Usuario medico) {
        medico.setGoogleAccessTokenEncrypted(null);
        medico.setGoogleRefreshTokenEncrypted(null);
        medico.setGoogleTokenExpiresAt(null);
        medico.setGoogleCalendarConnected(false);
        medico.setGoogleSyncToken(null);
        usuarioRepository.save(medico);
        // Drop cached external events too — they'd otherwise keep showing stale personal
        // events in the calendar after the médico revokes access.
        eventoExternoRepository.deleteByMedicoId(medico.getId());
    }

    private String urlEncode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}