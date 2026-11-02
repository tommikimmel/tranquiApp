package com.tranqui.gateway.admin;

import com.tranqui.gateway.security.UserJwtVerifier;
import io.jsonwebtoken.Claims;
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
import java.security.Key;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthValidatorTest {

    private static final String SECRET = "local-dev-encryption-key-32chars";
    private UserJwtVerifier verifier;
    private AdminAuthValidator validator;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        verifier = new UserJwtVerifier(SECRET);
        validator = new AdminAuthValidator(verifier);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String createToken(String subject, Map<String, Object> claims, long ttlMs) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
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
        Key otherKey = Keys.hmacShaKeyFor("different-secret-key-at-least-32-chars-long!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .setSubject("attacker@evil.com")
                .claim("rol", "ADMIN")
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

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
        String expiredToken = createToken("admin@tranqui.com", Map.of("rol", "ADMIN"), -10_000L);

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
        String token = createToken("paciente@tranqui.com", Map.of("rol", "PACIENTE"), 3600_000L);

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
    void testRequireAdminTokenSinRolLanza403() {
        String token = createToken("desconocido@tranqui.com", Map.of(), 3600_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
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
    void testRequireAdminTokenAdminValidoRetornaClaims() {
        String token = createToken("admin@tranqui.com", Map.of("rol", "ADMIN"), 3600_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        Claims claims = validator.requireAdmin(exchange);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("admin@tranqui.com");
        assertThat(claims.get("rol")).isEqualTo("ADMIN");
    }

    @Test
    void testRequireAdminTokenAdminEnCookieRetornaClaims() {
        String token = createToken("admin@tranqui.com", Map.of("rol", "ADMIN"), 3600_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/salud")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .build()
        );

        Claims claims = validator.requireAdmin(exchange);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("admin@tranqui.com");
    }

    @Test
    void testExtractToken() {
        String token = createToken("admin@tranqui.com", Map.of("rol", "ADMIN"), 3600_000L);

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
}
