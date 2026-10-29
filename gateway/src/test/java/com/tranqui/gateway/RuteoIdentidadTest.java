package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tranqui.eventos.jwt.ServiceJwt;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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
 * Fase 9 (I4): con TRANQUI_SERVICES_IDENTIDAD_URI configurada, todas las rutas de identidad
 * (/api/auth/**, /api/tickets/**, /api/soporte/**, /api/admin/users/**, /api/admin/tickets/**,
 * /api/site-access/**) van al servicio de identidad; /api/admin/subscriptions/** sigue en suscripciones.
 *
 * Además, las rutas internas /internal/identidad/** y /internal/pagos/** se enrutan a sus respectivos
 * servicios con JWT de servicio, y responden 401 sin JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RuteoIdentidadTest {

    private static WireMockServer monolito;
    private static WireMockServer identidad;
    private static WireMockServer identidadInterno;
    private static WireMockServer suscripciones;
    private static WireMockServer pagos;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        identidad = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        identidadInterno = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        suscripciones = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        pagos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        for (WireMockServer servidor : new WireMockServer[]{monolito, identidad, identidadInterno, suscripciones, pagos}) {
            servidor.start();
            String quien = servidor == monolito ? "monolito"
                    : servidor == identidad ? "identidad"
                    : servidor == identidadInterno ? "identidad-interno"
                    : servidor == suscripciones ? "suscripciones"
                    : "pagos";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        identidad.stop();
        identidadInterno.stop();
        suscripciones.stop();
        pagos.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.identidad.uri", () -> "http://localhost:" + identidad.port());
        registry.add("TRANQUI_SERVICES_IDENTIDAD_INTERNO_URI", () -> "http://localhost:" + identidadInterno.port());
        registry.add("tranqui.services.suscripciones.uri", () -> "http://localhost:" + suscripciones.port());
        registry.add("tranqui.services.pagos.uri", () -> "http://localhost:" + pagos.port());
    }

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void conVariableIdentidad_rutasDeIdentidadVanAlServicio() {
        // auth-ratelimited
        va("POST", "/api/auth/login", "identidad");
        va("POST", "/api/auth/register", "identidad");
        va("POST", "/api/auth/verify-email", "identidad");
        va("POST", "/api/auth/resend-code", "identidad");
        va("POST", "/api/auth/forgot-password", "identidad");
        va("POST", "/api/auth/reset-password", "identidad");

        // identidad general
        va("GET", "/api/auth/me", "identidad");
        va("POST", "/api/auth/logout", "identidad");
        va("GET", "/api/tickets", "identidad");
        va("POST", "/api/tickets/1/mensajes", "identidad");
        va("POST", "/api/soporte/datos", "identidad");

        // admin users y admin tickets
        va("GET", "/api/admin/users", "identidad");
        va("GET", "/api/admin/tickets", "identidad");

        // site-access
        va("GET", "/api/site-access/estado", "identidad");
        va("POST", "/api/site-access/verificar", "identidad");

        // admin subscriptions sigue yendo a suscripciones
        va("GET", "/api/admin/subscriptions", "suscripciones");
        va("GET", "/api/admin/subscriptions/stats", "suscripciones");
    }

    @Test
    void circuitoIdentidad_abreYResponde503ConFallback() {
        CircuitBreaker cbIdentidad = circuitBreakerRegistry.circuitBreaker("identidad");
        try {
            cbIdentidad.transitionToOpenState();

            webTestClient.get().uri("/api/auth/me").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().contentType("application/json")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("servicio_no_disponible")
                    .jsonPath("$.servicio").isEqualTo("identidad");
        } finally {
            cbIdentidad.transitionToClosedState();
        }
    }

    @Test
    void rutasInternas_identidadYPagos_conJwtVanASuDestino_sinJwt401() {
        String token = "Bearer " + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("gateway");

        // /internal/identidad/** -> servicio de identidad interno
        webTestClient.get().uri("/internal/identidad/carga/usuarios")
                .header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("identidad-interno");

        webTestClient.get().uri("/internal/identidad/carga/usuarios")
                .exchange().expectStatus().isUnauthorized();

        // /internal/pagos/** -> servicio de pagos
        webTestClient.get().uri("/internal/pagos/transacciones")
                .header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("pagos");

        webTestClient.get().uri("/internal/pagos/transacciones")
                .exchange().expectStatus().isUnauthorized();
    }
}

/**
 * Sin TRANQUI_SERVICES_IDENTIDAD_URI (por defecto o vuelta atrás):
 * todas las rutas de identidad van al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoIdentidadSinVariableTest {

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
    void sinVariableIdentidad_todoVaAlMonolito() {
        va("POST", "/api/auth/login", "monolito");
        va("POST", "/api/auth/register", "monolito");
        va("GET", "/api/auth/me", "monolito");
        va("GET", "/api/tickets", "monolito");
        va("POST", "/api/soporte/datos", "monolito");
        va("GET", "/api/admin/users", "monolito");
        va("GET", "/api/admin/tickets", "monolito");
        va("GET", "/api/site-access/estado", "monolito");
    }
}
