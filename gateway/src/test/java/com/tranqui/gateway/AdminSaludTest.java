package com.tranqui.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AdminSaludTest {

    @Autowired
    private WebTestClient webTestClient;

    private static final String SECRET = "defaultsecretkeymustbe32byteslongforhs256algorithm!";

    private String crearToken(String rol) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("test@tranqui.com")
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
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
