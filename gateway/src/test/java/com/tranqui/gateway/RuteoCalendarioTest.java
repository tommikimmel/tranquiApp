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
 * Fase 3: con TRANQUI_SERVICES_CALENDARIO_URI configurada, /api/medicos/google-calendar/** (incluidos el
 * callback del OAuth y el webhook de Google) y /internal/calendario/** van al servicio de calendario; el
 * resto de /api/medicos sigue en el monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoCalendarioTest {

    private static WireMockServer monolito;
    private static WireMockServer calendario;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        calendario = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        calendario.start();
        for (WireMockServer servidor : new WireMockServer[]{monolito, calendario}) {
            String quien = servidor == monolito ? "monolito" : "calendario";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        calendario.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.calendario.uri", () -> "http://localhost:" + calendario.port());
    }

    private static String tokenServicio() {
        return "Bearer " + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("monolito");
    }

    @Test
    void panelCallbackYWebhook_vanAlServicio() {
        webTestClient.get().uri("/api/medicos/google-calendar/status").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("calendario");
        webTestClient.get().uri("/api/medicos/google-calendar/callback?code=x&state=y").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("calendario");
        webTestClient.post().uri("/api/medicos/google-calendar/webhook").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("calendario");
    }

    @Test
    void restoDeMedicos_sigueEnElMonolito() {
        webTestClient.get().uri("/api/medicos/perfil").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.get().uri("/api/medicos/16/turnos-disponibles").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
    }

    @Test
    void rutaInternaDelCalendario_conTokenDeServicio() {
        webTestClient.get().uri("/internal/calendario/profesionales/16/ocupado?fecha=2026-11-04")
                .header("Authorization", tokenServicio()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("calendario");
        webTestClient.get().uri("/internal/monolito/calendario/cuentas")
                .header("Authorization", tokenServicio()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.get().uri("/internal/calendario/profesionales/16/ocupado?fecha=2026-11-04").exchange()
                .expectStatus().isUnauthorized();
    }
}
