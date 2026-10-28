package com.tranqui.gateway.security;

import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.eventos.jwt.UsuarioToken;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserJwtVerifierTest {

    private static final String SECRET_HS256 = "local-dev-encryption-key-32chars";
    private KeyPair keyPair;
    private EmisorJwtUsuario emisor;
    private UserJwtVerifier verifier;
    private String publicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        emisor = new EmisorJwtUsuario((RSAPrivateKey) keyPair.getPrivate(), Duration.ofHours(1));
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        verifier = new UserJwtVerifier(publicKeyBase64, SECRET_HS256, true);
    }

    private String crearTokenHs256(String subject, String rol, long ttlMs) {
        Key key = Keys.hmacShaKeyFor(SECRET_HS256.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(subject)
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void testFailsStartupWhenNoKeysConfigured() {
        assertThatThrownBy(() -> new UserJwtVerifier("", null, false))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new UserJwtVerifier(null, "short-key", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testVerifyFromCookie() {
        String token = emisor.emitir("paciente@tranqui.com", "PACIENTE", "Paciente", 123L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .build()
        );

        Optional<UsuarioToken> usuarioOpt = verifier.verify(exchange);
        assertThat(usuarioOpt).isPresent();
        assertThat(usuarioOpt.get().email()).isEqualTo("paciente@tranqui.com");
        assertThat(usuarioOpt.get().rol()).isEqualTo("PACIENTE");
        assertThat(usuarioOpt.get().uid()).isEqualTo(123L);
    }

    @Test
    void testVerifyFromAuthorizationBearer() {
        String token = emisor.emitir("doctor@tranqui.com", "PSIQUIATRA", "Doctor", 456L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        Optional<UsuarioToken> usuarioOpt = verifier.verify(exchange);
        assertThat(usuarioOpt).isPresent();
        assertThat(usuarioOpt.get().email()).isEqualTo("doctor@tranqui.com");
        assertThat(usuarioOpt.get().rol()).isEqualTo("PSIQUIATRA");
        assertThat(usuarioOpt.get().uid()).isEqualTo(456L);
    }

    @Test
    void testExpiredTokenReturnsEmpty() {
        String token = emisor.emitir("paciente@tranqui.com", "PACIENTE", "Paciente", 123L, Instant.now().minus(Duration.ofHours(2)));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .build()
        );

        Optional<UsuarioToken> usuarioOpt = verifier.verify(exchange);
        assertThat(usuarioOpt).isEmpty();
    }

    @Test
    void testInvalidSignatureReturnsEmpty() {
        String forgedToken = "header.payload.signatureInvalida";

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .header("Authorization", "Bearer " + forgedToken)
                        .build()
        );

        Optional<UsuarioToken> usuarioOpt = verifier.verify(exchange);
        assertThat(usuarioOpt).isEmpty();
    }

    @Test
    void testNoTokenReturnsEmpty() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos").build()
        );

        Optional<UsuarioToken> usuarioOpt = verifier.verify(exchange);
        assertThat(usuarioOpt).isEmpty();
    }

    @Test
    void testTransicionHs256_aceptadoConTrue_rechazadoConFalse() {
        String tokenHs256 = crearTokenHs256("usuario@tranqui.com", "PACIENTE", 3600_000L);

        // 1. Con jwt.hs256-aceptado=true: aceptado
        UserJwtVerifier verifierConHs256 = new UserJwtVerifier(publicKeyBase64, SECRET_HS256, true);
        Optional<UsuarioToken> usuarioOpt = verifierConHs256.verifyToken(tokenHs256);
        assertThat(usuarioOpt).isPresent();
        assertThat(usuarioOpt.get().email()).isEqualTo("usuario@tranqui.com");
        assertThat(usuarioOpt.get().rol()).isEqualTo("PACIENTE");

        // 2. Con jwt.hs256-aceptado=false: rechazado (empty)
        UserJwtVerifier verifierSinHs256 = new UserJwtVerifier(publicKeyBase64, SECRET_HS256, false);
        Optional<UsuarioToken> rechazado = verifierSinHs256.verifyToken(tokenHs256);
        assertThat(rechazado).isEmpty();
    }
}
