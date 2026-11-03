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
 * Fase 5: con TRANQUI_SERVICES_PROFESIONALES_URI configurada, el buscador ({@code GET /api/medicos}), "Mi perfil",
 * la disponibilidad y /internal/profesionales van al servicio; las estadísticas del panel, los turnos, los
 * conceptos, las recetas, la historia clínica y las rutas internas del monolito siguen en el monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoProfesionalesTest {

    private static WireMockServer monolito;
    private static WireMockServer profesionales;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        profesionales = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        profesionales.start();
        for (WireMockServer servidor : new WireMockServer[]{monolito, profesionales}) {
            String quien = servidor == monolito ? "monolito" : "profesionales";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        profesionales.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.profesionales.uri", () -> "http://localhost:" + profesionales.port());
    }

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(org.springframework.http.HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void buscadorPerfilYAgenda_vanAlServicio() {
        va("GET", "/api/medicos", "profesionales");
        va("GET", "/api/medicos/perfil", "profesionales");
        va("PUT", "/api/medicos/perfil", "profesionales");
        va("GET", "/api/medicos/disponibilidad?modalidad=ONLINE", "profesionales");
        va("PUT", "/api/medicos/disponibilidad?modalidad=ONLINE", "profesionales");
        va("PUT", "/api/medicos/disponibilidad-config", "profesionales");
    }

    @Test
    void estadisticasTurnosConceptosRecetasEHistoria_siguenEnElMonolito() {
        va("GET", "/api/medicos/stats", "monolito");
        va("GET", "/api/medicos/turnos", "monolito");
        va("GET", "/api/medicos/7/turnos-disponibles?fecha=2026-10-23", "monolito");
        va("POST", "/api/conceptos/solicitar", "monolito");
        va("GET", "/api/recetas", "monolito");
        va("GET", "/api/pacientes/7/historia", "monolito");
    }

    @Test
    void rutasInternas_laDelServicioYLasDelMonolito() {
        String token = "Bearer " + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("monolito");
        webTestClient.get().uri("/internal/profesionales/profesionales").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("profesionales");
        webTestClient.get().uri("/internal/monolito/profesionales/estado").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.get().uri("/internal/profesionales/profesionales").exchange()
                .expectStatus().isUnauthorized();
    }
}
