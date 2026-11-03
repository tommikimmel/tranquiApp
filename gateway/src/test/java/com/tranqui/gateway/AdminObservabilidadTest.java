package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tranqui.eventos.jwt.EmisorJwtUsuario;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AdminObservabilidadTest {

    private static WireMockServer wireMockServer;
    private static final KeyPair KEY_PAIR;
    private static final EmisorJwtUsuario EMISOR;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KEY_PAIR = kpg.generateKeyPair();
            EMISOR = new EmisorJwtUsuario((RSAPrivateKey) KEY_PAIR.getPrivate(), Duration.ofHours(1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

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
        registry.add("jwt.public-key", () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
    }

    private String crearToken(String rol) {
        return EMISOR.emitir("admin@tranqui.com", rol, "Admin", 1L);
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
