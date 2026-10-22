package com.tranqui.gateway.security;

import io.jsonwebtoken.Claims;
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
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserJwtVerifierTest {

    private static final String SECRET = "local-dev-encryption-key-32chars";
    private UserJwtVerifier verifier;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        verifier = new UserJwtVerifier(SECRET);
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
    void testFailsStartupWhenSecretMissingOrTooShort() {
        assertThatThrownBy(() -> new UserJwtVerifier(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing JWT_SECRET");

        assertThatThrownBy(() -> new UserJwtVerifier(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing JWT_SECRET");

        assertThatThrownBy(() -> new UserJwtVerifier("short-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void testVerifyFromCookie() {
        String token = createToken("paciente@tranqui.com", Map.of("userId", "123", "rol", "PACIENTE"), 3600_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .build()
        );

        Optional<Claims> claimsOpt = verifier.verify(exchange);
        assertThat(claimsOpt).isPresent();
        assertThat(claimsOpt.get().getSubject()).isEqualTo("paciente@tranqui.com");

        Optional<String> userIdOpt = verifier.extractUserIdentifier(claimsOpt.get());
        assertThat(userIdOpt).contains("123");
    }

    @Test
    void testVerifyFromAuthorizationBearer() {
        String token = createToken("doctor@tranqui.com", Map.of("id", "456", "rol", "PROFESIONAL"), 3600_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        Optional<Claims> claimsOpt = verifier.verify(exchange);
        assertThat(claimsOpt).isPresent();
        assertThat(claimsOpt.get().getSubject()).isEqualTo("doctor@tranqui.com");

        Optional<String> userIdOpt = verifier.extractUserIdentifier(claimsOpt.get());
        assertThat(userIdOpt).contains("456");
    }

    @Test
    void testVerifySubjectFallbackWhenNoUserIdClaim() {
        String token = createToken("admin@tranqui.com", Map.of("rol", "ADMIN"), 3600_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        Optional<Claims> claimsOpt = verifier.verify(exchange);
        assertThat(claimsOpt).isPresent();

        Optional<String> userIdOpt = verifier.extractUserIdentifier(claimsOpt.get());
        assertThat(userIdOpt).contains("admin@tranqui.com");
    }

    @Test
    void testExpiredTokenReturnsEmpty() {
        String token = createToken("paciente@tranqui.com", Map.of(), -10_000L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .build()
        );

        Optional<Claims> claimsOpt = verifier.verify(exchange);
        assertThat(claimsOpt).isEmpty();
    }

    @Test
    void testInvalidSignatureReturnsEmpty() {
        Key otherKey = Keys.hmacShaKeyFor("different-secret-key-at-least-32-chars-long!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .setSubject("hacker@evil.com")
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .header("Authorization", "Bearer " + forgedToken)
                        .build()
        );

        Optional<Claims> claimsOpt = verifier.verify(exchange);
        assertThat(claimsOpt).isEmpty();
    }

    @Test
    void testNoTokenReturnsEmpty() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos").build()
        );

        Optional<Claims> claimsOpt = verifier.verify(exchange);
        assertThat(claimsOpt).isEmpty();
    }
}
