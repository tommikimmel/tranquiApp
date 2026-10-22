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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 3 (G1): con SITE_ACCESS_PASSWORD, las rutas que van a un servicio exigen la cookie SITE-ACCESS
 * (la misma que emite el monolito); los webhooks y las rutas internas no.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tranqui.portones.sitio-password=clave-del-sitio")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PortonSitioServiciosTest {

    private static WireMockServer monolito;
    private static WireMockServer servicio;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() throws Exception {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        servicio = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        servicio.start();
        for (WireMockServer s : new WireMockServer[]{monolito, servicio}) {
            String quien = s == monolito ? "monolito" : "servicio";
            s.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        servicio.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.chat.uri", () -> "http://localhost:" + servicio.port());
        registry.add("tranqui.services.calendario.uri", () -> "http://localhost:" + servicio.port());
    }

    private static String cookieValida() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest("clave-del-sitio".getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void sinCookie_403ConElMensajeDelMonolito() {
        webTestClient.get().uri("/api/chat/canales").exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("Acceso restringido. Ingresá la contraseña del sitio.");
        webTestClient.get().uri("/api/chat/canales").cookie("SITE-ACCESS", "otra-cosa").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void conCookie_pasa() throws Exception {
        webTestClient.get().uri("/api/chat/canales").cookie("SITE-ACCESS", cookieValida()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("servicio");
    }

    @Test
    void webhookDeGoogleYRutasInternas_sinCookie() {
        webTestClient.post().uri("/api/medicos/google-calendar/webhook").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("servicio");
        webTestClient.get().uri("/internal/chat/profesionales/2/resumen")
                .header("Authorization", "Bearer " + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("monolito"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void rutasDelMonolito_lasFiltraElMonolito() {
        webTestClient.get().uri("/api/medicos").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
    }
}
