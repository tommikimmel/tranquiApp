package com.tranqui.app.service.novedades;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente mínimo de la API de marketing de Resend (contactos, segmentos y broadcasts), usado solo
 * por las novedades. Los mails transaccionales siguen en ResendEmailService.
 *
 * La API key necesita permiso "Full access" (una key de solo envío no puede gestionar contactos).
 * Respeta el límite de pedidos por segundo de Resend espaciando las llamadas.
 */
@Component
@Slf4j
public class ResendAudienciaClient {

    private static final String BASE = "https://api.resend.com";
    // Resend limita a ~2 pedidos por segundo por equipo.
    private static final long ESPERA_ENTRE_LLAMADAS_MS = 550;

    @Value("${resend.api-key:}")
    private String apiKey;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private long ultimaLlamada = 0;

    public record Contacto(String id, String email, boolean unsubscribed) {}

    public boolean configurado() {
        return apiKey != null && apiKey.trim().startsWith("re_");
    }

    /** Devuelve el id del segmento con ese nombre, creándolo si no existe. */
    public String asegurarSegmento(String nombre) {
        JsonNode lista = llamar("GET", "/segments", null);
        for (JsonNode s : lista.path("data")) {
            if (nombre.equals(s.path("name").asText())) return s.path("id").asText();
        }
        return llamar("POST", "/segments", Map.of("name", nombre)).path("id").asText();
    }

    public Optional<Contacto> buscarContacto(String email) {
        try {
            JsonNode c = llamar("GET", "/contacts/" + enc(email), null);
            return Optional.of(new Contacto(c.path("id").asText(), c.path("email").asText(), c.path("unsubscribed").asBoolean(false)));
        } catch (NoEncontradoException e) {
            return Optional.empty();
        }
    }

    /** Crea el contacto en el segmento indicado. Nunca toca la suscripción de uno existente. */
    public void crearContacto(String email, String nombre, String apellido, String segmentoId) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        if (nombre != null) body.put("first_name", nombre);
        if (apellido != null) body.put("last_name", apellido);
        body.put("segments", List.of(Map.of("id", segmentoId)));
        llamar("POST", "/contacts", body);
    }

    public void actualizarNombre(String email, String nombre, String apellido) {
        Map<String, Object> body = new HashMap<>();
        body.put("first_name", nombre);
        body.put("last_name", apellido);
        llamar("PATCH", "/contacts/" + enc(email), body);
    }

    public void actualizarSuscripcion(String email, boolean suscripto) {
        llamar("PATCH", "/contacts/" + enc(email), Map.of("unsubscribed", !suscripto));
    }

    public java.util.Set<String> segmentosDe(String email) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode s : llamar("GET", "/contacts/" + enc(email) + "/segments", null).path("data")) {
            ids.add(s.path("id").asText());
        }
        return ids;
    }

    public void agregarASegmento(String email, String segmentoId) {
        llamar("POST", "/contacts/" + enc(email) + "/segments/" + segmentoId, null);
    }

    public void quitarDeSegmento(String email, String segmentoId) {
        try {
            llamar("DELETE", "/contacts/" + enc(email) + "/segments/" + segmentoId, null);
        } catch (NoEncontradoException ignored) {
            // ya no estaba en el segmento
        }
    }

    public void borrarContacto(String email) {
        try {
            llamar("DELETE", "/contacts/" + enc(email), null);
        } catch (NoEncontradoException ignored) {
            // no existía
        }
    }

    /** Crea y envía un broadcast al segmento. Devuelve el id del broadcast. */
    public String enviarBroadcast(String segmentoId, String from, String asunto, String html, String nombreInterno) {
        Map<String, Object> body = new HashMap<>();
        body.put("segment_id", segmentoId);
        body.put("from", from);
        body.put("subject", asunto);
        body.put("html", html);
        body.put("name", nombreInterno);
        body.put("send", true);
        return llamar("POST", "/broadcasts", body).path("id").asText();
    }

    // ================================================================================

    static class NoEncontradoException extends RuntimeException {
        NoEncontradoException(String msg) { super(msg); }
    }

    private synchronized JsonNode llamar(String metodo, String ruta, Object cuerpo) {
        if (!configurado()) throw new IllegalStateException("RESEND_API_KEY no configurada");
        esperarTurno();
        try {
            HttpRequest.BodyPublisher pub = cuerpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo));
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + ruta))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .method(metodo, pub)
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) throw new NoEncontradoException(metodo + " " + ruta);
            if (res.statusCode() == 429) {
                // Límite de Resend: una espera y un reintento.
                Thread.sleep(1500);
                ultimaLlamada = System.currentTimeMillis();
                res = http.send(req, HttpResponse.BodyHandlers.ofString());
            }
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IllegalStateException("Resend respondió " + res.statusCode() + " a " + metodo + " " + ruta + ": " + res.body());
            }
            return res.body() == null || res.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(res.body());
        } catch (NoEncontradoException | IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido llamando a Resend", e);
        } catch (Exception e) {
            throw new IllegalStateException("Error llamando a Resend: " + metodo + " " + ruta, e);
        }
    }

    private void esperarTurno() {
        long espera = ultimaLlamada + ESPERA_ENTRE_LLAMADAS_MS - System.currentTimeMillis();
        if (espera > 0) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ultimaLlamada = System.currentTimeMillis();
    }

    private static String enc(String email) {
        return URLEncoder.encode(email, StandardCharsets.UTF_8);
    }
}
