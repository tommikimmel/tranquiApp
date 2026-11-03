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
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Fase 6: con TRANQUI_SERVICES_TURNOS_URI configurada, las rutas de turnos (horarios libres, conteo, reserva,
 * "Mis turnos" del profesional y del paciente, cancelar, asistencia, documentos, reprogramar y los links de los
 * mails) y /internal/turnos van al servicio; la historia clínica, las estadísticas y el perfil siguen en sus
 * dueños, y /internal/notificaciones va a notificaciones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoTurnosTest {

    private static WireMockServer monolito;
    private static WireMockServer turnos;
    private static WireMockServer notificaciones;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        turnos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        notificaciones = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        for (WireMockServer servidor : new WireMockServer[]{monolito, turnos, notificaciones}) {
            servidor.start();
            String quien = servidor == monolito ? "monolito" : servidor == turnos ? "turnos" : "notificaciones";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        turnos.stop();
        notificaciones.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.turnos.uri", () -> "http://localhost:" + turnos.port());
        registry.add("tranqui.services.notificaciones.uri", () -> "http://localhost:" + notificaciones.port());
    }

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void lasRutasDeTurnos_vanAlServicio() {
        va("GET", "/api/medicos/16/turnos-disponibles?fecha=2026-10-30&modalidad=ONLINE", "turnos");
        va("GET", "/api/medicos/turnos-disponibles-conteo?medicoIds=16&fecha=2026-10-30", "turnos");
        va("POST", "/api/turnos/reservar", "turnos");
        va("GET", "/api/medicos/turnos", "turnos");
        va("GET", "/api/medicos/turnos/hoy", "turnos");
        va("GET", "/api/pacientes/me/turnos", "turnos");
        va("POST", "/api/turnos/7/cancelar", "turnos");
        va("PUT", "/api/turnos/7/reprogramar?fecha=2026-10-30&hora=10:00", "turnos");
        va("GET", "/api/turnos/7/confirmar-asistencia?token=x", "turnos");
    }

    @Test
    void historiaClinicaEstadisticasYPerfil_siguenEnSusDuenios() {
        va("GET", "/api/pacientes/me/seguimientos", "monolito");
        va("GET", "/api/pacientes/7/informes", "monolito");
        va("GET", "/api/medicos/stats", "turnos");
    }

    @Test
    void rutasInternasDeTurnosYNotificaciones() {
        String token = "Bearer " + new ServiceJwt("test-service-jwt-secret-key-32chars").emitirToken("monolito");
        webTestClient.get().uri("/internal/turnos/turnos").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("turnos");
        webTestClient.post().uri("/internal/notificaciones/documentos").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("notificaciones");
        webTestClient.get().uri("/internal/monolito/turnos").header("Authorization", token).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("monolito");
        webTestClient.get().uri("/internal/turnos/turnos").exchange().expectStatus().isUnauthorized();
    }
}
