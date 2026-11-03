package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
 * Fase 9 (I4): /api/medicos/stats se enruta al servicio de turnos (regla turnos-stats),
 * mientras que otras rutas de /api/medicos/** sin regla propia siguen en el monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RuteoTurnosStatsTest {

    private static WireMockServer monolito;
    private static WireMockServer turnos;
    private static WireMockServer profesionales;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        turnos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        profesionales = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        for (WireMockServer servidor : new WireMockServer[]{monolito, turnos, profesionales}) {
            servidor.start();
            String quien = servidor == monolito ? "monolito"
                    : servidor == turnos ? "turnos"
                    : "profesionales";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        turnos.stop();
        profesionales.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.turnos.uri", () -> "http://localhost:" + turnos.port());
        registry.add("tranqui.services.profesionales.uri", () -> "http://localhost:" + profesionales.port());
    }

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void medicosStats_vaATurnos() {
        va("GET", "/api/medicos/stats", "turnos");
    }

    @Test
    void otroMedicosSinReglaPropia_sigueEnElMonolito() {
        va("GET", "/api/medicos/999/resumen", "monolito");
        va("GET", "/api/medicos/especialidades", "monolito");
    }

    @Test
    void buscadorYPerfil_vanAProfesionales() {
        va("GET", "/api/medicos", "profesionales");
        va("GET", "/api/medicos/perfil", "profesionales");
    }
}
