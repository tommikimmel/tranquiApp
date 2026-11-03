package com.tranqui.calendario.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.calendario.datos.CuentasGoogle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * OAuth de Google Calendar (antes GoogleCalendarOAuthService del monolito): la URL para conectar, el
 * canje del código del callback y la renovación del access token. Mismo cliente, mismo redirect
 * ({@code <app.public-url>/api/medicos/google-calendar/callback}, que el gateway deriva acá) y el
 * mismo state firmado, así nada cambia en la consola de Google.
 */
@Component
public class TokensGoogle {

    private static final Logger log = LoggerFactory.getLogger(TokensGoogle.class);
    private static final String AUTORIZACION = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final long MARGEN_SEGUNDOS = 300;

    private final CuentasGoogle cuentas;
    private final Cifrado cifrado;
    private final String clientId;
    private final String clientSecret;
    private final String publicUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public TokensGoogle(CuentasGoogle cuentas, Cifrado cifrado,
                        @Value("${google.client-id}") String clientId,
                        @Value("${google.client-secret:}") String clientSecret,
                        @Value("${app.public-url}") String publicUrl) {
        this.cuentas = cuentas;
        this.cifrado = cifrado;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.publicUrl = publicUrl;
    }

    private String redirectUri() {
        return publicUrl + "/api/medicos/google-calendar/callback";
    }

    public String urlDeConexion(Long profesionalId) {
        return AUTORIZACION
                + "?client_id=" + codificar(clientId)
                + "&redirect_uri=" + codificar(redirectUri())
                + "&response_type=code"
                + "&scope=" + codificar("https://www.googleapis.com/auth/calendar.events")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + codificar(cifrado.firmar(profesionalId));
    }

    /** Canjea el código del callback y guarda la cuenta conectada. */
    public void conectar(Long profesionalId, String codigo) throws IOException {
        JsonNode respuesta = pedirToken("grant_type=authorization_code"
                + "&client_id=" + codificar(clientId)
                + "&client_secret=" + codificar(clientSecret)
                + "&code=" + codificar(codigo)
                + "&redirect_uri=" + codificar(redirectUri()));
        guardar(profesionalId, respuesta);
    }

    /** Un access token vigente (lo renueva si vence en menos de 5 minutos), o {@code null} si no se puede. */
    public String accessToken(CuentasGoogle.Cuenta cuenta) {
        if (cuenta == null || cuenta.refreshTokenCifrado() == null) {
            return null;
        }
        try {
            if (cuenta.accessTokenCifrado() != null && cuenta.tokenVenceEn() != null
                    && cuenta.tokenVenceEn().isAfter(LocalDateTime.now().plusSeconds(MARGEN_SEGUNDOS))) {
                return cifrado.descifrar(cuenta.accessTokenCifrado());
            }
            log.info("Access token de Google vencido o ausente para el profesional {}; se renueva", cuenta.profesionalId());
            JsonNode respuesta = pedirToken("grant_type=refresh_token"
                    + "&client_id=" + codificar(clientId)
                    + "&client_secret=" + codificar(clientSecret)
                    + "&refresh_token=" + codificar(cifrado.descifrar(cuenta.refreshTokenCifrado())));
            guardar(cuenta.profesionalId(), respuesta);
            return respuesta.get("access_token").asText();
        } catch (Exception e) {
            log.error("No se pudo renovar el access token de Google del profesional {}", cuenta.profesionalId(), e);
            return null;
        }
    }

    private void guardar(Long profesionalId, JsonNode respuesta) {
        String refresh = respuesta.has("refresh_token") ? cifrado.cifrar(respuesta.get("refresh_token").asText()) : null;
        cuentas.guardarTokens(profesionalId, cifrado.cifrar(respuesta.get("access_token").asText()), refresh,
                LocalDateTime.now().plusSeconds(respuesta.get("expires_in").asInt()));
    }

    private JsonNode pedirToken(String formulario) throws IOException {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(TOKEN))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                .build();
        try {
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                // Sin el cuerpo: puede traer datos de la cuenta.
                throw new IOException("Google respondió " + respuesta.statusCode() + " al pedir el token");
            }
            return mapper.readTree(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido pidiendo el token de Google", e);
        }
    }

    private static String codificar(String valor) {
        return valor == null ? "" : URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
