package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El navegador manda un preflight OPTIONS antes de un POST con JSON desde otro origen (en local,
 * el frontend en :5173 contra el gateway en :8080). El gateway lo responde con la politica de
 * globalcors y los encabezados CORS no salen duplicados cuando el monolito tambien los agrega.
 * El cliente va contra el servidor real: el procesador CORS necesita host y puerto en la URI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CorsPreflightTest {

    private static final String ORIGEN = "http://localhost:5173";

    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int puerto;

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

    @BeforeEach
    void crearCliente() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void testPreflightDelLoginRespondeConCors() {
        webTestClient.options().uri("/api/auth/login")
                .header("Origin", ORIGEN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGEN)
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void testControladoresPropiosDelGatewayRespondenConCors() {
        webTestClient.options().uri("/api/admin/salud")
                .header("Origin", ORIGEN)
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGEN)
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");

        webTestClient.get().uri("/health")
                .header("Origin", ORIGEN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGEN);
    }

    @Test
    void testRespuestaDelMonolitoSinEncabezadosCorsDuplicados() {
        wireMockServer.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Access-Control-Allow-Origin", ORIGEN)
                        .withHeader("Access-Control-Allow-Credentials", "true")
                        .withBody("{\"id\":1}")));

        webTestClient.method(HttpMethod.POST).uri("/api/auth/login")
                .header("Origin", ORIGEN)
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"x\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("Access-Control-Allow-Origin", valor -> assertThat(valor).isEqualTo(ORIGEN))
                .expectHeader().values("Access-Control-Allow-Origin", valores -> assertThat(valores).hasSize(1))
                .expectHeader().values("Access-Control-Allow-Credentials", valores -> assertThat(valores).hasSize(1));
    }
}
