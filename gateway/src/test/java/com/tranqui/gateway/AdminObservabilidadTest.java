package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AdminObservabilidadTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    private static final String SECRET = "defaultsecretkeymustbe32byteslongforhs256algorithm!";

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tranqui.victoriametrics.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + wireMockServer.port());
    }

    private String crearToken(String rol) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("admin@tranqui.com")
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private void stubVictoriaMetricsResponses() {
        // Mock genérico para query_range de VictoriaMetrics
        String rangeResponse = "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{\"name\":\"gateway\",\"routeId\":\"turnos\"},\"values\":[[1774380000,\"12.5\"],[1774380030,\"14.2\"]]}]}}";
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/query_range"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(rangeResponse)));

        // Mock genérico para query instantánea de VictoriaMetrics
        String instantResponse = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{\"queue\":\"notificaciones.eventos\",\"routeId\":\"turnos\"},\"value\":[1774380000,\"5\"]}]}}";
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/query"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(instantResponse)));
    }

    @Test
    void testObservabilidadSinTokenRetorna401() {
        webTestClient.get().uri("/api/admin/observabilidad/recursos?rango=1h")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testObservabilidadConTokenPacienteRetorna403() {
        String tokenPaciente = crearToken("PACIENTE");

        webTestClient.get().uri("/api/admin/observabilidad/recursos?rango=1h")
                .header("Authorization", "Bearer " + tokenPaciente)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testObservabilidadConCategoriaInvalidaRetorna400() {
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/observabilidad/categoria-inexistente")
                .header("Authorization", "Bearer " + tokenAdmin)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testObservabilidadRecursosRetornaSeries() {
        stubVictoriaMetricsResponses();
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/observabilidad/recursos?rango=1h")
                .header("Authorization", "Bearer " + tokenAdmin)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.categoria").isEqualTo("recursos")
                .jsonPath("$.rango").isEqualTo("1h")
                .jsonPath("$.metricas.cpuVps").isArray()
                .jsonPath("$.metricas.ramVps").isArray()
                .jsonPath("$.metricas.discoLibreGb").exists();
    }

    @Test
    void testObservabilidadTraficoRetornaSeries() {
        stubVictoriaMetricsResponses();
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/observabilidad/trafico?rango=24h")
                .header("Authorization", "Bearer " + tokenAdmin)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.categoria").isEqualTo("trafico")
                .jsonPath("$.rango").isEqualTo("24h")
                .jsonPath("$.metricas.reqTotalPorMinuto").isArray()
                .jsonPath("$.metricas.errores5xx").isArray()
                .jsonPath("$.metricas.rechazos429").isArray();
    }

    @Test
    void testObservabilidadLatenciaRetornaSeries() {
        stubVictoriaMetricsResponses();
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/observabilidad/latencia?rango=7d")
                .header("Authorization", "Bearer " + tokenAdmin)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.categoria").isEqualTo("latencia")
                .jsonPath("$.rango").isEqualTo("7d")
                .jsonPath("$.metricas.p50").isArray()
                .jsonPath("$.metricas.p95").isArray()
                .jsonPath("$.metricas.p99").isArray()
                .jsonPath("$.metricas.rutasMasLentas").isArray();
    }

    @Test
    void testObservabilidadEventosRetornaSeries() {
        stubVictoriaMetricsResponses();
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/observabilidad/eventos?rango=24h")
                .header("Authorization", "Bearer " + tokenAdmin)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.categoria").isEqualTo("eventos")
                .jsonPath("$.rango").isEqualTo("24h")
                .jsonPath("$.metricas.outboxPendientes").isArray()
                .jsonPath("$.metricas.colasRabbitMQ").isArray()
                .jsonPath("$.metricas.dlqRabbitMQ").isArray();
    }
}
