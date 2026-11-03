package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
 * Fase 9 (I4): Portón del sitio con identidad en su propio servicio (fuera del monolito).
 * /api/site-access/verificar pasa sin la cookie SITE-ACCESS para poder desbloquear el sitio.
 * /api/auth/login sin cookie da 403 con el mensaje del monolito.
 * Con la cookie SITE-ACCESS válida, /api/auth/login pasa al servicio de identidad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tranqui.portones.sitio-password=clave-del-sitio")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class PortonSitioIdentidadTest {

    private static WireMockServer monolito;
    private static WireMockServer identidad;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void arrancar() {
        monolito = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        identidad = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        monolito.start();
        identidad.start();

        for (WireMockServer s : new WireMockServer[]{monolito, identidad}) {
            String quien = s == monolito ? "monolito" : "identidad";
            s.stubFor(any(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json").withBody("{\"desde\":\"" + quien + "\"}")));
        }
    }

    @AfterAll
    static void parar() {
        monolito.stop();
        identidad.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + monolito.port());
        registry.add("tranqui.services.identidad.uri", () -> "http://localhost:" + identidad.port());
    }

    private static String cookieValida() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest("clave-del-sitio".getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void siteAccessVerificar_pasaSinCookie() {
        webTestClient.post().uri("/api/site-access/verificar")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("identidad");
    }

    @Test
    void authLogin_sinCookie_da403MismoQueMonolito() {
        webTestClient.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("Acceso restringido. Ingresá la contraseña del sitio.");
    }

    @Test
    void authLogin_conCookieValida_pasaAIdentidad() throws Exception {
        webTestClient.post().uri("/api/auth/login")
                .cookie("SITE-ACCESS", cookieValida())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.desde").isEqualTo("identidad");
    }
}
