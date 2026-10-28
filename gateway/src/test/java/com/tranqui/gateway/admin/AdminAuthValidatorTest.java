package com.tranqui.gateway.admin;

import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.gateway.security.UserJwtVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthValidatorTest {

    private static final String SECRET_HS256 = "local-dev-encryption-key-32chars";
    private KeyPair keyPair;
    private EmisorJwtUsuario emisor;
    private UserJwtVerifier verifier;
    private AdminAuthValidator validator;
    private String publicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        emisor = new EmisorJwtUsuario((RSAPrivateKey) keyPair.getPrivate(), Duration.ofHours(1));
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        verifier = new UserJwtVerifier(publicKeyBase64, SECRET_HS256, true);
        validator = new AdminAuthValidator(verifier);
    }

    private String crearTokenRs256(String email, String rol) {
        return emisor.emitir(email, rol, "Admin Test", 1L);
    }

    private String crearTokenHs256(String email, String rol) {
        return Jwts.builder()
                .setSubject(email)
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET_HS256.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void testRequireAdminSinTokenLanza401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud").build()
        );

        assertThatThrownBy(() -> validator.requireAdmin(exchange))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Token de autenticación requerido");
                });
    }

    @Test
    void testRequireAdminTokenInvalidoLanza401() {
        String forgedToken = "header.payload.signatureInvalida";

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .header("Authorization", "Bearer " + forgedToken)
                        .build()
        );

        assertThatThrownBy(() -> validator.requireAdmin(exchange))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Token de autenticación inválido o expirado");
                });
    }

    @Test
    void testRequireAdminTokenExpiradoLanza401() {
        String expiredToken = emisor.emitir("admin@tranqui.com", "ADMIN", "Admin", 1L, Instant.now().minus(Duration.ofHours(2)));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .cookie(new HttpCookie("SESSION-TOKEN", expiredToken))
                        .build()
        );

        assertThatThrownBy(() -> validator.requireAdmin(exchange))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Token de autenticación inválido o expirado");
                });
    }

    @Test
    void testRequireAdminTokenRolPacienteLanza403() {
        String token = crearTokenRs256("paciente@tranqui.com", "PACIENTE");

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        assertThatThrownBy(() -> validator.requireAdmin(exchange))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Acceso restringido a administradores");
                });
    }

    @Test
    void testRequireAdminTokenAdminValidoRetornaUsuarioToken() {
        String token = crearTokenRs256("admin@tranqui.com", "ADMIN");

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        UsuarioToken usuario = validator.requireAdmin(exchange);
        assertThat(usuario).isNotNull();
        assertThat(usuario.email()).isEqualTo("admin@tranqui.com");
        assertThat(usuario.rol()).isEqualTo("ADMIN");
        assertThat(usuario.uid()).isEqualTo(1L);
    }

    @Test
    void testRequireAdminTokenAdminEnCookieRetornaUsuarioToken() {
        String token = crearTokenRs256("admin@tranqui.com", "ADMIN");

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .build()
        );

        UsuarioToken usuario = validator.requireAdmin(exchange);
        assertThat(usuario).isNotNull();
        assertThat(usuario.email()).isEqualTo("admin@tranqui.com");
    }

    @Test
    void testExtractToken() {
        String token = crearTokenRs256("admin@tranqui.com", "ADMIN");

        ServerWebExchange exchangeBearer = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );
        assertThat(validator.extractToken(exchangeBearer)).isEqualTo(token);

        ServerWebExchange exchangeVacio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud").build()
        );
        assertThat(validator.extractToken(exchangeVacio)).isNull();
    }

    @Test
    void testTransicionHs256_aceptadoConTrue_rechazadoConFalse() {
        String tokenHs256 = crearTokenHs256("admin@tranqui.com", "ADMIN");

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .header("Authorization", "Bearer " + tokenHs256)
                        .build()
        );

        // 1. Con jwt.hs256-aceptado=true: es aceptado
        UserJwtVerifier verifierConHs256 = new UserJwtVerifier(publicKeyBase64, SECRET_HS256, true);
        AdminAuthValidator validatorConHs256 = new AdminAuthValidator(verifierConHs256);
        UsuarioToken usuario = validatorConHs256.requireAdmin(exchange);
        assertThat(usuario).isNotNull();
        assertThat(usuario.email()).isEqualTo("admin@tranqui.com");

        // 2. Con jwt.hs256-aceptado=false: 401 Unauthorized
        UserJwtVerifier verifierSinHs256 = new UserJwtVerifier(publicKeyBase64, SECRET_HS256, false);
        AdminAuthValidator validatorSinHs256 = new AdminAuthValidator(verifierSinHs256);
        assertThatThrownBy(() -> validatorSinHs256.requireAdmin(exchange))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
