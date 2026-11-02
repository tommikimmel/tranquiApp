package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RoutingAndCorrelationIdTest {

    private static WireMockServer wireMockServer;

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
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void testHealthEndpointPublico() {
        webTestClient.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testRuteoAlMonolito() {
        wireMockServer.stubFor(get(urlEqualTo("/api/medicos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":1,\"nombre\":\"Dr. Test\"}]")));

        webTestClient.get().uri("/api/medicos")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].nombre").isEqualTo("Dr. Test");
    }

    @Test
    void testCorrelationIdPropagadoSiViene() {
        wireMockServer.stubFor(get(urlEqualTo("/api/medicos/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));

        String customCorrId = "custom-correlation-id-xyz-123";

        webTestClient.get().uri("/api/medicos/1")
                .header("X-Correlation-Id", customCorrId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", customCorrId);

        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/medicos/1"))
                .withHeader("X-Correlation-Id", equalTo(customCorrId)));
    }

    @Test
    void testCorrelationIdGeneradoSiNoViene() {
        wireMockServer.stubFor(get(urlEqualTo("/api/conceptos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webTestClient.get().uri("/api/conceptos")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", corrId -> {
                    assertThat(corrId).isNotBlank();
                    assertThat(corrId).matches("^[0-9a-fA-F\\-]{36}$");
                });
    }
}
