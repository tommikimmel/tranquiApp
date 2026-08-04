package com.tranqui.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.dto.Qbi2AnularDtos;
import com.tranqui.app.model.dto.Qbi2CatalogoDtos;
import com.tranqui.app.model.dto.Qbi2RecetaDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Real HTTP implementation of {@link Qbi2RecipeClient}, active only when
 * qbi2.recipe.enabled=true. NONE OF THIS HAS BEEN TESTED AGAINST A REAL QBI2
 * SERVER — we have no credentials yet, and QBI2's own authentication
 * mechanism is undocumented (see the Javadoc on Qbi2RecipeClient). This code
 * prioritizes clarity and correctness of the request shape over defensive
 * edge-case handling; treat it as a first draft to be reviewed and corrected
 * once real credentials exist, not as finished/battle-tested code.
 */
@Service
@ConditionalOnProperty(prefix = "qbi2.recipe", name = "enabled", havingValue = "true")
public class Qbi2RecipeClientHttp implements Qbi2RecipeClient {

    private static final Logger log = LoggerFactory.getLogger(Qbi2RecipeClientHttp.class);

    @Value("${qbi2.recipe.environment:hml}")
    private String environment;

    @Value("${qbi2.recipe.base-url-hml}")
    private String baseUrlHml;

    @Value("${qbi2.recipe.base-url-prod}")
    private String baseUrlProd;

    @Value("${qbi2.recipe.cliente-app-id:}")
    private String clienteAppId;

    @Value("${qbi2.recipe.token:}")
    private String token;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String baseUrl() {
        return "prod".equalsIgnoreCase(environment) ? baseUrlProd : baseUrlHml;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("QBI2 Recipe respondió " + response.statusCode()
                        + " para " + request.method() + " " + request.uri()
                        + " — body: " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error llamando a QBI2 Recipe: " + request.method() + " " + request.uri(), e);
        }
    }

    @Override
    public Qbi2CatalogoDtos.DiagnosticoResponse buscarDiagnosticos(String texto) {
        String path = "/apirecipe/GetDiagnostico?text=" + urlEncode(texto);
        if (clienteAppId != null && !clienteAppId.isBlank()) {
            path += "&clienteAppId=" + urlEncode(clienteAppId);
        }
        HttpRequest request = requestBuilder(path)
                .GET()
                .build();
        String body = send(request);
        try {
            return objectMapper.readValue(body, Qbi2CatalogoDtos.DiagnosticoResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo parsear la respuesta de GetDiagnostico: " + body, e);
        }
    }

    @Override
    public Qbi2CatalogoDtos.MedicamentoResponse buscarMedicamentos(String texto, int numeroPagina) {
        HttpRequest request = requestBuilder("/apirecipe/GetMedicamento/" + urlEncode(texto)
                        + "?numeroPagina=" + numeroPagina
                        + "&clienteAppId=" + urlEncode(clienteAppId))
                .GET()
                .build();
        String body = send(request);
        try {
            return objectMapper.readValue(body, Qbi2CatalogoDtos.MedicamentoResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo parsear la respuesta de GetMedicamento: " + body, e);
        }
    }

    @Override
    public Qbi2CatalogoDtos.FinanciadorResponse buscarFinanciadores() {
        String path = "/apirecipe/GetFinanciadores";
        if (clienteAppId != null && !clienteAppId.isBlank()) {
            path += "?clienteAppId=" + urlEncode(clienteAppId);
        }
        HttpRequest request = requestBuilder(path)
                .GET()
                .build();
        String body = send(request);
        try {
            return objectMapper.readValue(body, Qbi2CatalogoDtos.FinanciadorResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo parsear la respuesta de GetFinanciadores: " + body, e);
        }
    }

    @Override
    public Qbi2RecetaDtos.RecetaResponse generarReceta(Qbi2RecetaDtos.RecetaRequest recetaRequest) {
        try {
            if (recetaRequest.getClienteAppId() == null && clienteAppId != null && !clienteAppId.isBlank()) {
                try {
                    recetaRequest.setClienteAppId(Integer.parseInt(clienteAppId));
                } catch (NumberFormatException ignored) {}
            }
            String jsonBody = objectMapper.writeValueAsString(recetaRequest);
            HttpRequest request = requestBuilder("/apirecipe/Receta")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            String body = send(request);
            return objectMapper.readValue(body, Qbi2RecetaDtos.RecetaResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar la receta en QBI2 Recipe", e);
        }
    }

    @Override
    public Qbi2AnularDtos.FarmalinkResult anularReceta(String hash) {
        try {
            Integer appId = clienteAppId == null || clienteAppId.isBlank() ? null : Integer.valueOf(clienteAppId);
            Qbi2AnularDtos.AnularRequest anularRequest = Qbi2AnularDtos.AnularRequest.builder()
                    .clienteAppId(appId)
                    .build();
            String jsonBody = objectMapper.writeValueAsString(anularRequest);
            HttpRequest request = requestBuilder("/apirecipe/Receta/" + urlEncode(hash))
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            String body = send(request);
            return objectMapper.readValue(body, Qbi2AnularDtos.FarmalinkResult.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo anular la receta en QBI2 Recipe", e);
        }
    }
}
