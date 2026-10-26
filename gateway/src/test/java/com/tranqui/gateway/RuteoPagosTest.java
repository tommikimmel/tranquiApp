package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 4: con TRANQUI_SERVICES_PAGOS_URI configurada, /api/payments/** (el webhook de Mercado Pago, verificar y
 * el link del checkout) y /api/medicos/mercadopago/** (incluido el callback del OAuth) van al servicio de
 * pagos; las suscripciones, el resto de /api/medicos y las rutas internas del monolito siguen en el monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoPagosTest {

    private static WireMockServer monolito;
    private static WireMockServer pagos;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        pagos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        pagos.start();
        for (WireMockServer servidor : new WireMockServer[]{monolito, pagos}) {
            String quien = servidor == monolito ? "monolito" : "pagos";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        pagos.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.pagos.uri", () -> "http://localhost:" + pagos.port());
    }

    @Test
    void webhookVerificarYCheckout_vanAlServicio() {
        webTestClient.post().uri("/api/payments/webhook?data.id=1").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("pagos");
        webTestClient.post().uri("/api/payments/verificar?externalReference=63").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("pagos");
        webTestClient.get().uri("/api/payments/checkout/63").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("pagos");
    }

    @Test
    void panelYCallbackDeMercadoPago_vanAlServicio() {
        webTestClient.get().uri("/api/medicos/mercadopago/status").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("pagos");
        webTestClient.get().uri("/api/medicos/mercadopago/callback?code=x&state=y").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("pagos");
    }

    @Test
    void suscripcionesYElRestoDeMedicos_siguenEnElMonolito() {
        webTestClient.post().uri("/api/subscriptions/checkout").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.get().uri("/api/medicos/perfil").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.post().uri("/api/conceptos/solicitar").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
    }

    @Test
    void rutasInternasDePagosDelMonolito_conTokenDeServicio() {
        String token = "Bearer " + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("pagos");
        webTestClient.get().uri("/internal/monolito/pagos/cuentas").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.post().uri("/internal/monolito/pagos/notificacion-suscripcion").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.get().uri("/internal/monolito/pagos/cuentas").exchange()
                .expectStatus().isUnauthorized();
    }
}
