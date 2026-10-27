package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 8 (S3): con TRANQUI_SERVICES_SUSCRIPCIONES_URI configurada, las suscripciones
 * (/api/subscriptions/** y /api/admin/subscriptions/**) van al servicio de pagos (lb://pagos);
 * los webhooks de pagos siguen yendo al servicio de pagos.
 *
 * Sin la variable de suscripciones, o con solo TRANQUI_SERVICES_PAGOS_URI (situación en producción antes
 * del corte), las suscripciones siguen yendo al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RuteoSuscripcionesTest {

    private static WireMockServer monolito;
    private static WireMockServer suscripciones;
    private static WireMockServer pagos;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        suscripciones = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        pagos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        for (WireMockServer servidor : new WireMockServer[]{monolito, suscripciones, pagos}) {
            servidor.start();
            String quien = servidor == monolito ? "monolito" : servidor == suscripciones ? "suscripciones" : "pagos";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        suscripciones.stop();
        pagos.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.suscripciones.uri", () -> "http://localhost:" + suscripciones.port());
        registry.add("tranqui.services.pagos.uri", () -> "http://localhost:" + pagos.port());
    }

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void conVariableSuscripciones_lasDosRutasVanAlServicio() {
        // Regla subscriptions-checkout
        va("POST", "/api/subscriptions/checkout", "suscripciones");
        // Regla suscripciones (/api/subscriptions/** y /api/admin/subscriptions/**)
        va("GET", "/api/subscriptions/plans", "suscripciones");
        va("GET", "/api/subscriptions/me", "suscripciones");
        va("POST", "/api/subscriptions/cancel", "suscripciones");
        va("GET", "/api/admin/subscriptions", "suscripciones");
        va("GET", "/api/admin/subscriptions/stats", "suscripciones");
        va("POST", "/api/admin/subscriptions/1/pago-manual", "suscripciones");
    }

    @Test
    void suscripcionesPlansSinSesion_llegaAlDestino() {
        webTestClient.get().uri("/api/subscriptions/plans").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("suscripciones");
    }

    @Test
    void circuitoSuscripciones_esIndependienteDePagos() {
        var cbSuscripciones = circuitBreakerRegistry.circuitBreaker("suscripciones");
        var cbPagos = circuitBreakerRegistry.circuitBreaker("pagos");
        try {
            // Abrir el circuito de suscripciones no afecta a pagos
            cbSuscripciones.transitionToOpenState();

            webTestClient.get().uri("/api/subscriptions/plans").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().contentType("application/json")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("servicio_no_disponible")
                    .jsonPath("$.servicio").isEqualTo("suscripciones");

            // Pagos sigue respondiendo normalmente
            va("GET", "/api/payments/checkout/63", "pagos");

            // Restaurar suscripciones y abrir pagos: suscripciones responde, pagos va a fallback
            cbSuscripciones.transitionToClosedState();
            cbPagos.transitionToOpenState();

            va("GET", "/api/subscriptions/plans", "suscripciones");

            webTestClient.get().uri("/api/payments/checkout/63").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().contentType("application/json")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("servicio_no_disponible")
                    .jsonPath("$.servicio").isEqualTo("pagos");
        } finally {
            cbSuscripciones.transitionToClosedState();
            cbPagos.transitionToClosedState();
        }
    }
}

/**
 * Sin TRANQUI_SERVICES_SUSCRIPCIONES_URI (por defecto o vuelta atrás):
 * las suscripciones van al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoSuscripcionesSinVariableTest {

    private static WireMockServer monolito;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        monolito.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
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

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void sinVariableSuscripciones_todoVaAlMonolito() {
        va("POST", "/api/subscriptions/checkout", "monolito");
        va("GET", "/api/subscriptions/plans", "monolito");
        va("GET", "/api/subscriptions/me", "monolito");
        va("POST", "/api/subscriptions/cancel", "monolito");
        va("GET", "/api/admin/subscriptions", "monolito");
        va("GET", "/api/admin/subscriptions/stats", "monolito");
        va("POST", "/api/admin/subscriptions/1/pago-manual", "monolito");
    }
}

/**
 * Con SOLO TRANQUI_SERVICES_PAGOS_URI (la situación en producción antes del corte):
 * los webhooks y pagos van al servicio de pagos, pero las suscripciones siguen en el monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoSuscripcionesSoloPagosTest {

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

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void conSoloPagos_suscripcionesSiguenEnMonolitoYWebhooksVanAPagos() {
        // Suscripciones siguen en el monolito
        va("POST", "/api/subscriptions/checkout", "monolito");
        va("GET", "/api/subscriptions/plans", "monolito");
        va("GET", "/api/subscriptions/me", "monolito");
        va("POST", "/api/subscriptions/cancel", "monolito");
        va("GET", "/api/admin/subscriptions", "monolito");

        // Webhooks y rutas de pagos van al servicio de pagos
        va("POST", "/api/payments/webhook?data.id=1", "pagos");
        va("POST", "/api/webhooks/mercadopago", "pagos");
        va("GET", "/api/payments/checkout/63", "pagos");
        va("POST", "/api/payments/verificar?externalReference=63", "pagos");
        va("GET", "/api/medicos/mercadopago/status", "pagos");
    }
}
