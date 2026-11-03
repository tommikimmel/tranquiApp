package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 3 (G1): el paywall de SubscriptionAccessFilter aplicado en el gateway a las rutas que van a un
 * servicio (acá, el chat). Las que siguen en el monolito no se chequean en el gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PaywallServiciosTest {

    private static final String CLAVE_JWT = "test-jwt-secret-key-must-be-at-least-32-chars-long!";
    private static final String ACCESO = "/internal/monolito/suscripciones/acceso";

    private static WireMockServer monolito;
    private static WireMockServer chat;
    private static WireMockServer suscripciones;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        chat = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        suscripciones = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        chat.start();
        suscripciones.start();
    }

    @BeforeEach
    void stubs() {
        monolito.resetAll();
        chat.resetAll();
        suscripciones.resetAll();
        chat.stubFor(any(urlPathMatching("/.*")).willReturn(json("{\"desde\":\"chat\"}")));
        suscripciones.stubFor(any(urlPathMatching("/.*")).willReturn(json("{\"desde\":\"suscripciones\"}")));
        monolito.stubFor(any(urlPathMatching("/api/.*")).willReturn(json("{\"desde\":\"monolito\"}")));
        monolito.stubFor(get(urlPathEqualTo(ACCESO)).withQueryParam("email", equalTo("sin@mail.com"))
                .willReturn(json("{\"permitido\":false}")));
        monolito.stubFor(get(urlPathEqualTo(ACCESO)).withQueryParam("email", equalTo("con@mail.com"))
                .willReturn(json("{\"permitido\":true}")));
        monolito.stubFor(get(urlPathEqualTo(ACCESO)).withQueryParam("email", equalTo("caido@mail.com"))
                .willReturn(aResponse().withStatus(500)));
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        chat.stop();
        suscripciones.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.chat.uri", () -> "http://localhost:" + chat.port());
        registry.add("tranqui.services.suscripciones.uri", () -> "http://localhost:" + suscripciones.port());
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String cuerpo) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(cuerpo);
    }

    private static String sesion(String email, String rol) {
        return Jwts.builder().setClaims(Map.of("rol", rol)).setSubject(email)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE_JWT.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private WebTestClient.ResponseSpec pedir(String ruta, String token) {
        WebTestClient.RequestHeadersSpec<?> pedido = webTestClient.get().uri(ruta);
        if (token != null) {
            pedido = pedido.cookie("SESSION-TOKEN", token);
        }
        return pedido.exchange();
    }

    @Test
    void psiquiatraSinSuscripcion_403EnElServicio() {
        pedir("/api/chat/canales", sesion("sin@mail.com", "PSIQUIATRA"))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("SUBSCRIPTION_REQUIRED");
        chat.verify(0, getRequestedFor(urlPathEqualTo("/api/chat/canales")));
    }

    @Test
    void psiquiatraConSuscripcion_pasaYLaRespuestaQuedaEnCache() {
        String token = sesion("con@mail.com", "PSIQUIATRA");
        pedir("/api/chat/canales", token).expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("chat");
        pedir("/api/chat/canales", token).expectStatus().isOk();
        monolito.verify(1, getRequestedFor(urlPathEqualTo(ACCESO)));
    }

    @Test
    void pacienteYSinSesion_noSeConsultaLaSuscripcion() {
        pedir("/api/chat/canales", sesion("pac@mail.com", "PACIENTE")).expectStatus().isOk();
        pedir("/api/chat/canales", null).expectStatus().isOk();
        monolito.verify(0, getRequestedFor(urlPathEqualTo(ACCESO)));
    }

    @Test
    void monolitoSinResponder_dejaPasar() {
        pedir("/api/chat/canales", sesion("caido@mail.com", "PSIQUIATRA")).expectStatus().isOk();
    }

    @Test
    void psiquiatraSinAcceso_checkoutPasaAlServicioYOtraRutaDa403() {
        String tokenSinAcceso = sesion("sin@mail.com", "PSIQUIATRA");

        // Otra ruta de servicio da 403 SUBSCRIPTION_REQUIRED
        pedir("/api/chat/canales", tokenSinAcceso)
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("SUBSCRIPTION_REQUIRED");

        // /api/subscriptions/checkout pasa al servicio porque está en SIN_PAYWALL
        webTestClient.post().uri("/api/subscriptions/checkout")
                .cookie("SESSION-TOKEN", tokenSinAcceso)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("suscripciones");
    }

    @Test
    void rutaQueSigueEnElMonolito_noSeChequeaEnElGateway() {
        pedir("/api/medicos/perfil", sesion("sin@mail.com", "PSIQUIATRA"))
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        monolito.verify(0, getRequestedFor(urlPathEqualTo(ACCESO)));
    }
}
