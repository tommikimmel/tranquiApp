package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 10 (T1): el circuito de cada ruta no corta una respuesta lenta. Sin configurar el TimeLimiter, el de
 * Resilience4j corta a 1 segundo y responde el 503 del fallback: un pedido de más de un segundo (emitir una receta,
 * el primer pedido en frío del CI) fallaba aunque el servicio respondiera bien. El límite lo pone el timeout de
 * respuesta del cliente HTTP de cada ruta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10s")
@ActiveProfiles("test")
class RespuestaLentaTest {

    private static WireMockServer monolito;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        monolito.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse().withFixedDelay(1_500)
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"monolito\"}")));
    }

    @AfterAll
    static void parar() {
        monolito.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
    }

    @Test
    void unaRespuestaDeMasDeUnSegundo_llegaEntera() {
        webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .get().uri("/api/recetas/financiadores").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
    }
}
