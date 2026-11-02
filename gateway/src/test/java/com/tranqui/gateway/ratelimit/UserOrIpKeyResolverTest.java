package com.tranqui.gateway.ratelimit;

import com.tranqui.gateway.security.RealIpResolver;
import com.tranqui.gateway.security.UserJwtVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

class UserOrIpKeyResolverTest {

    private static final String SECRET = "local-dev-encryption-key-32chars";
    private UserOrIpKeyResolver keyResolver;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        UserJwtVerifier verifier = new UserJwtVerifier(SECRET);
        RealIpResolver ipResolver = new RealIpResolver();
        keyResolver = new UserOrIpKeyResolver(verifier, ipResolver);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String createToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void testResolvesUserFromCookie() {
        String token = createToken("paciente@tranqui.com", Map.of("userId", "user-123"));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .build()
        );

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("user:user-123")
                .verifyComplete();
    }

    @Test
    void testResolvesUserFromAuthorizationHeader() {
        String token = createToken("profesional@tranqui.com", Map.of("id", "doc-456"));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .header("Authorization", "Bearer " + token)
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .build()
        );

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("user:doc-456")
                .verifyComplete();
    }

    @Test
    void testResolvesIpWhenNoTokenProvidedBehindTrustedProxy() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .header("X-Forwarded-For", "203.0.113.195")
                        .build()
        );

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("ip:203.0.113.195")
                .verifyComplete();
    }

    @Test
    void testResolvesRemoteIpWhenUntrustedRemoteSendsSpoofedXff() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("198.51.100.20", 54321))
                        .header("X-Forwarded-For", "1.1.1.1")
                        .build()
        );

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("ip:198.51.100.20")
                .verifyComplete();
    }
}
