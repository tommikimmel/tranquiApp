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
 * qbi2.recipe.enabled=true (currently the default in .env). generarReceta()
 * was verified against the real HML environment on 2026-08-05 (200 OK, valid
 * s3Link/verificador/idReceta returned for a particular/no-financiador case).
 * Every call is logged (request + response, token masked) via send() for
 * audit purposes — see the QBI2 Recipe → / ← log lines.
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
    // NON_NULL only affects serialization (outgoing requests), never deserialization — needed
    // because QBI2's schema has several non-nullable value-type fields (RecetaRequestDto.fechaEmision:
    // DateTime, PacienteRecetaDto.ocultarPaciente: bool, MedicamentoDto.tratamiento: int) that we never
    // populate. Without this, Jackson wrote them out as explicit JSON `null`, which .NET's model binder
    // rejects for non-nullable value types with error QBI34 "REVISE LOS TIPOS DE DATO DE LOS CAMPOS
    // INGRESADOS" — omitting the property entirely (this fix) makes it fall back to QBI2's own default
    // instead, which is exactly what their spec describes as the intended "not specified" behavior.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

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

    private String maskedToken() {
        if (token == null || token.isBlank()) return "(sin token)";
        return token.length() > 12 ? token.substring(0, 10) + "…(" + token.length() + " chars)" : "***";
    }

    private String send(HttpRequest request) {
        return send(request, null);
    }

    private String send(HttpRequest request, String requestBodyForLog) {
        log.info("QBI2 Recipe → {} {} | token={} | body={}", request.method(), request.uri(), maskedToken(),
                requestBodyForLog != null ? requestBodyForLog : "(sin body)");
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("QBI2 Recipe ← {} {} | status={} | body={}", request.method(), request.uri(),
                    response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                throw new Qbi2RecipeException(response.statusCode(), response.body(),
                        "QBI2 Recipe respondió " + response.statusCode()
                        + " para " + request.method() + " " + request.uri()
                        + " — body: " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("QBI2 Recipe: error de red/IO llamando a {} {}", request.method(), request.uri(), e);
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
            String body = send(request, jsonBody);
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
            String body = send(request, jsonBody);
            return objectMapper.readValue(body, Qbi2AnularDtos.FarmalinkResult.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo anular la receta en QBI2 Recipe", e);
        }
    }
}
