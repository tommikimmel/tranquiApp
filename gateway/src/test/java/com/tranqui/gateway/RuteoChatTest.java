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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 2: con TRANQUI_SERVICES_CHAT_URI configurada, /api/chat/**, /ws-tranqui/** y /internal/chat/**
 * van al servicio de chat; el resto de /api y de /internal sigue yendo al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoChatTest {

    private static WireMockServer monolito;
    private static WireMockServer chat;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        chat = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        chat.start();
        for (WireMockServer servidor : new WireMockServer[]{monolito, chat}) {
            String quien = servidor == monolito ? "monolito" : "chat";
            servidor.stubFor(get(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        chat.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.chat.uri", () -> "http://localhost:" + chat.port());
    }

    private void esperarDesde(String ruta, String destino, boolean conTokenDeServicio) {
        WebTestClient.RequestHeadersSpec<?> pedido = webTestClient.get().uri(ruta);
        if (conTokenDeServicio) {
            pedido = pedido.header("Authorization", "Bearer "
                    + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("monolito"));
        }
        pedido.exchange().expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void apiYWebSocketDelChat_vanAlServicio() {
        esperarDesde("/api/chat/canales", "chat", false);
        esperarDesde("/ws-tranqui/info", "chat", false);
    }

    @Test
    void rutaInternaDelChat_vaAlServicioYElRestoAlMonolito() {
        esperarDesde("/internal/chat/profesionales/2/resumen", "chat", true);
        esperarDesde("/internal/monolito/contactos", "monolito", true);
        esperarDesde("/api/medicos", "monolito", false);
    }

    @Test
    void rutaInternaDelChat_sinTokenDeServicio_401() {
        webTestClient.get().uri("/internal/chat/profesionales/2/resumen").exchange().expectStatus().isUnauthorized();
    }
}
