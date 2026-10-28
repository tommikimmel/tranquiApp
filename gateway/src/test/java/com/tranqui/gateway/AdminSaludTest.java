package com.tranqui.gateway;

import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;

import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AdminSaludTest {

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

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
    }

    private String crearToken(String rol) {
        return EMISOR.emitir("test@tranqui.com", rol, "Test Admin", 1L);
    }

    @Test
    void testSaludSinTokenRetorna401() {
        webTestClient.get().uri("/api/admin/salud")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testSaludConTokenPacienteRetorna403() {
        String tokenPaciente = crearToken("PACIENTE");

        webTestClient.get().uri("/api/admin/salud")
                .header("Authorization", "Bearer " + tokenPaciente)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSaludConTokenPsiquiatraRetorna403() {
        String tokenPsiquiatra = crearToken("PSIQUIATRA");

        webTestClient.get().uri("/api/admin/salud")
                .header("Authorization", "Bearer " + tokenPsiquiatra)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSaludConTokenAdminRetorna200YListaDeServicios() {
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/salud")
                .header("Authorization", "Bearer " + tokenAdmin)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.servicios").isArray()
                .jsonPath("$.servicios[?(@.nombre == 'gateway')]").exists()
                .jsonPath("$.servicios[?(@.nombre == 'suscripciones')]").doesNotExist()
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void testSaludConCookieSessionTokenAdminRetorna200() {
        String tokenAdmin = crearToken("ADMIN");

        webTestClient.get().uri("/api/admin/salud")
                .cookie("SESSION-TOKEN", tokenAdmin)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.servicios").isArray();
    }
}
