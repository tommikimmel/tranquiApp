package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 10, T3: la suite de punta a punta encontró que con {@code lb://pagos} (como en el compose) la consulta del
 * paywall fallaba con "Invalid scheme [lb]" y, como falla abierta, dejaba pasar a todos. Acá pagos se resuelve por
 * discovery, igual que en el stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tranqui.portones.acceso.fuente=pagos")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PaywallPagosPorDiscoveryTest {

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

    private static WireMockServer chat;
    private static WireMockServer pagos;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        chat = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        pagos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        chat.start();
        pagos.start();
        chat.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"desde\":\"chat\"}")));
        pagos.stubFor(get(urlPathEqualTo("/internal/suscripciones/acceso")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"permitido\":false}")));
    }

    @AfterAll
    static void parar() {
        chat.stop();
        pagos.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.chat.uri", () -> "http://localhost:" + chat.port());
        registry.add("tranqui.services.pagos.uri", () -> "lb://pagos");
        registry.add("spring.cloud.discovery.client.simple.instances.pagos[0].uri", () -> "http://localhost:" + pagos.port());
        registry.add("jwt.public-key", () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
    }

    @Test
    void pagosPorDiscovery_sinSuscripcionDa403() {
        webTestClient.get().uri("/api/chat/canales")
                .cookie("SESSION-TOKEN", EMISOR.emitir("sin@mail.com", "PSIQUIATRA", "Usuario Test", 1L))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("SUBSCRIPTION_REQUIRED");
    }
}
