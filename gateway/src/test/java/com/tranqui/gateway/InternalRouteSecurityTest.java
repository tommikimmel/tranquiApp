package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tranqui.eventos.jwt.ServiceJwt;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class InternalRouteSecurityTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ServiceJwt serviceJwt;

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
    void testInternalRechazaCon404SiVieneConXForwardedFor() {
        webTestClient.get().uri("/internal/turnos/resumen")
                .header("X-Forwarded-For", "192.168.1.50")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testInternalRechazaCon404SiVieneConXForwardedProto() {
        webTestClient.get().uri("/internal/turnos/resumen")
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testInternalRechazaCon401SinToken() {
        webTestClient.get().uri("/internal/turnos/resumen")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testInternalRechazaCon401ConTokenInvalido() {
        webTestClient.get().uri("/internal/turnos/resumen")
                .header("Authorization", "Bearer invalid-jwt-token-xyz")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testInternalAceptaConTokenDeServicioValidoYPropagaNombreDeServicio() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/profesionales/medicos/7/resumen"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"profesionalId\":7,\"nombre\":\"Dr. Lucia\"}")));

        String serviceToken = serviceJwt.emitirToken("turnos");

        webTestClient.get().uri("/internal/profesionales/medicos/7/resumen")
                .header("Authorization", "Bearer " + serviceToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.profesionalId").isEqualTo(7);

        wireMockServer.verify(getRequestedFor(urlEqualTo("/internal/profesionales/medicos/7/resumen"))
                .withHeader("X-Service-Name", equalTo("turnos")));
    }
}
