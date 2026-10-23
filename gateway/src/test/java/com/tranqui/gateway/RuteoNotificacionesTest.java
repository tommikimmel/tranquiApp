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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Fase 1: con TRANQUI_SERVICES_NOTIFICACIONES_URI configurada, /api/notificaciones/** y
 * /ws-notificaciones/** van al servicio de notificaciones; el chat (/ws-tranqui) y el resto de
 * /api siguen yendo al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoNotificacionesTest {

    private static WireMockServer monolito;
    private static WireMockServer notificaciones;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        notificaciones = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        notificaciones.start();
        for (WireMockServer servidor : new WireMockServer[]{monolito, notificaciones}) {
            String quien = servidor == monolito ? "monolito" : "notificaciones";
            servidor.stubFor(get(urlPathEqualTo("/api/notificaciones")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
            servidor.stubFor(get(urlPathEqualTo("/ws-notificaciones/info")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
            servidor.stubFor(get(urlPathEqualTo("/ws-tranqui/info")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
            servidor.stubFor(get(urlPathEqualTo("/api/medicos")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        notificaciones.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.notificaciones.uri", () -> "http://localhost:" + notificaciones.port());
    }

    private void esperarDesde(String ruta, String destino) {
        webTestClient.get().uri(ruta).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void apiDeNotificaciones_vaAlServicio() {
        esperarDesde("/api/notificaciones", "notificaciones");
    }

    @Test
    void webSocketDeNotificaciones_vaAlServicio() {
        esperarDesde("/ws-notificaciones/info", "notificaciones");
    }

    @Test
    void chatYRestoDeLaApi_siguenEnElMonolito() {
        esperarDesde("/ws-tranqui/info", "monolito");
        esperarDesde("/api/medicos", "monolito");
    }
}
