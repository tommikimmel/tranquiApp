package com.tranqui.gateway;

import com.tranqui.eventos.contratos.Contratos;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tranqui.eventos.jwt.EmisorJwtUsuario;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 9 (I4): Paywall consultando al servicio de pagos (tranqui.portones.acceso.fuente=pagos).
 * Consulta /internal/suscripciones/acceso?email= en el servicio de pagos.
 * Si pagos responde {"permitido": false}, da 403 SUBSCRIPTION_REQUIRED.
 * Si pagos responde {"permitido": true}, deja pasar.
 * Si pagos falla (HTTP 500, timeout) o devuelve un JSON no válido (sin campo booleano 'permitido'),
 * deja pasar (fail-open por ser regla comercial).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tranqui.portones.acceso.fuente=pagos")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class PaywallFuentePagosTest {

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

    private static final String ACCESO_PAGOS = "/internal/suscripciones/acceso";

    private static WireMockServer monolito;
    private static WireMockServer chat;
    private static WireMockServer pagos;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        chat = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        pagos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        chat.start();
        pagos.start();
    }

    @BeforeEach
    void stubs() {
        monolito.resetAll();
        chat.resetAll();
        pagos.resetAll();
        chat.stubFor(any(urlPathMatching("/.*")).willReturn(json("{\"desde\":\"chat\"}")));
        monolito.stubFor(any(urlPathMatching("/.*")).willReturn(json("{\"desde\":\"monolito\"}")));

        pagos.stubFor(get(urlPathEqualTo(ACCESO_PAGOS)).withQueryParam("email", equalTo("sin@mail.com"))
                .willReturn(json(Contratos.fixture("internas/suscripciones/acceso.json").toString())));
        pagos.stubFor(get(urlPathEqualTo(ACCESO_PAGOS)).withQueryParam("email", equalTo("con@mail.com"))
                .willReturn(json("{\"permitido\":true}")));
        pagos.stubFor(get(urlPathEqualTo(ACCESO_PAGOS)).withQueryParam("email", equalTo("caido@mail.com"))
                .willReturn(aResponse().withStatus(500)));
        pagos.stubFor(get(urlPathEqualTo(ACCESO_PAGOS)).withQueryParam("email", equalTo("malformado@mail.com"))
                .willReturn(json("{\"otro_campo\":123}")));
        pagos.stubFor(get(urlPathEqualTo(ACCESO_PAGOS)).withQueryParam("email", equalTo("no_booleano@mail.com"))
                .willReturn(json("{\"permitido\":\"verdadero\"}")));
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        chat.stop();
        pagos.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.chat.uri", () -> "http://localhost:" + chat.port());
        registry.add("tranqui.services.pagos.uri", () -> "http://localhost:" + pagos.port());
        registry.add("jwt.public-key", () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String cuerpo) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(cuerpo);
    }

    private static String sesion(String email, String rol) {
        return EMISOR.emitir(email, rol, "Usuario Test", 1L);
    }

    private WebTestClient.ResponseSpec pedir(String ruta, String token) {
        WebTestClient.RequestHeadersSpec<?> pedido = webTestClient.get().uri(ruta);
        if (token != null) {
            pedido = pedido.cookie("SESSION-TOKEN", token);
        }
        return pedido.exchange();
    }

    @Test
    void psiquiatraSinSuscripcion_consultaAPagosYResponde403() {
        pedir("/api/chat/canales", sesion("sin@mail.com", "PSIQUIATRA"))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("SUBSCRIPTION_REQUIRED");

        pagos.verify(1, getRequestedFor(urlPathEqualTo(ACCESO_PAGOS))
                .withQueryParam("email", equalTo("sin@mail.com"))
                .withHeader("Authorization", matching("Bearer .+")));
        monolito.verify(0, getRequestedFor(urlPathMatching("/internal/.*")));
    }

    @Test
    void psiquiatraConSuscripcion_consultaAPagosYPasa() {
        pedir("/api/chat/canales", sesion("con@mail.com", "PSIQUIATRA"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("chat");

        pagos.verify(1, getRequestedFor(urlPathEqualTo(ACCESO_PAGOS))
                .withQueryParam("email", equalTo("con@mail.com")));
    }

    @Test
    void pagosCaido_dejaPasar() {
        pedir("/api/chat/canales", sesion("caido@mail.com", "PSIQUIATRA"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("chat");
    }

    @Test
    void pagosRespuestaMalformadaOSinBooleano_trataComoFallaYDejaPasar() {
        // Sin campo 'permitido'
        pedir("/api/chat/canales", sesion("malformado@mail.com", "PSIQUIATRA"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("chat");

        // Con campo 'permitido' que no es booleano
        pedir("/api/chat/canales", sesion("no_booleano@mail.com", "PSIQUIATRA"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("chat");
    }
}
