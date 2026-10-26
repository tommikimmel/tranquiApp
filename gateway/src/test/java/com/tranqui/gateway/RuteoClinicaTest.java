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
 * Fase 7 (H3): con TRANQUI_SERVICES_CLINICA_URI configurada, la historia clínica (/api/pacientes/** menos
 * /api/pacientes/me/turnos), las recetas (/api/recetas/**) y los documentos de conceptos (/api/conceptos/**)
 * van al servicio de profesionales; /api/pacientes/me/turnos va a turnos; las estadísticas siguen en el
 * monolito.
 *
 * Sin la variable de clínica, o con solo TRANQUI_SERVICES_PROFESIONALES_URI (situación en producción antes
 * del corte), todo eso sigue yendo al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RuteoClinicaTest {

    private static WireMockServer monolito;
    private static WireMockServer clinica;
    private static WireMockServer turnos;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        clinica = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        turnos = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        for (WireMockServer servidor : new WireMockServer[]{monolito, clinica, turnos}) {
            servidor.start();
            String quien = servidor == monolito ? "monolito" : servidor == clinica ? "clinica" : "turnos";
            servidor.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse()
                    .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        clinica.stop();
        turnos.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.clinica.uri", () -> "http://localhost:" + clinica.port());
        registry.add("tranqui.services.turnos.uri", () -> "http://localhost:" + turnos.port());
    }

    private void va(String metodo, String ruta, String destino) {
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void conVariableClinica_rutasDeClinica_vanAlServicio() {
        va("GET", "/api/pacientes/atendidos", "clinica");
        va("GET", "/api/pacientes/5/informes/3", "clinica");
        va("GET", "/api/pacientes/me/seguimientos", "clinica");
        va("POST", "/api/recetas/enviar", "clinica");
        va("GET", "/api/recetas/financiadores", "clinica");
        va("POST", "/api/conceptos/solicitar", "clinica");
    }

    @Test
    void conVariableClinica_turnosYEstadisticas_noVanAClinica() {
        va("GET", "/api/pacientes/me/turnos", "turnos");
        va("GET", "/api/medicos/stats", "monolito");
    }

    @Test
    void recetasFinanciadoresSinSesion_llegaAlDestino() {
        webTestClient.get().uri("/api/recetas/financiadores").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo("clinica");
    }
}

/**
 * Sin TRANQUI_SERVICES_CLINICA_URI (por defecto o vuelta atrás):
 * la historia clínica, las recetas y los conceptos van al monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoClinicaSinVariableTest {

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
    void sinVariableClinica_todoVaAlMonolito() {
        va("GET", "/api/pacientes/atendidos", "monolito");
        va("GET", "/api/pacientes/5/informes/3", "monolito");
        va("GET", "/api/pacientes/me/seguimientos", "monolito");
        va("POST", "/api/recetas/enviar", "monolito");
        va("GET", "/api/recetas/financiadores", "monolito");
        va("POST", "/api/conceptos/solicitar", "monolito");
        va("GET", "/api/medicos/stats", "monolito");
    }
}

/**
 * Con SOLO TRANQUI_SERVICES_PROFESIONALES_URI (la situación en producción antes del corte):
 * el buscador y perfil van a profesionales, pero historia clínica, recetas y conceptos siguen en el monolito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuteoClinicaSoloProfesionalesTest {

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
        webTestClient.method(HttpMethod.valueOf(metodo)).uri(ruta).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.desde").isEqualTo(destino);
    }

    @Test
    void conSoloProfesionales_historiaClinicaRecetasYConceptos_siguenEnElMonolito() {
        va("GET", "/api/pacientes/atendidos", "monolito");
        va("GET", "/api/pacientes/5/informes/3", "monolito");
        va("GET", "/api/pacientes/me/seguimientos", "monolito");
        va("POST", "/api/recetas/enviar", "monolito");
        va("GET", "/api/recetas/financiadores", "monolito");
        va("POST", "/api/conceptos/solicitar", "monolito");
        va("GET", "/api/medicos/stats", "monolito");
        // Profesionales sigue funcionando hacia su servicio
        va("GET", "/api/medicos", "profesionales");
        va("GET", "/api/medicos/perfil", "profesionales");
    }
}
