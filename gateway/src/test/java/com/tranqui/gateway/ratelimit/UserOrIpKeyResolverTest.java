package com.tranqui.gateway.ratelimit;

import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.gateway.security.RealIpResolver;
import com.tranqui.gateway.security.UserJwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;

class UserOrIpKeyResolverTest {

    private KeyPair keyPair;
    private EmisorJwtUsuario emisor;
    private UserOrIpKeyResolver keyResolver;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        emisor = new EmisorJwtUsuario((RSAPrivateKey) keyPair.getPrivate(), Duration.ofHours(1));
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        UserJwtVerifier verifier = new UserJwtVerifier(publicKeyBase64, null, false);
        RealIpResolver ipResolver = new RealIpResolver();
        keyResolver = new UserOrIpKeyResolver(verifier, ipResolver);
    }

    @Test
    void testResolvesUserFromCookie() {
        String token = emisor.emitir("paciente@tranqui.com", "PACIENTE", "Paciente", 123L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .cookie(new HttpCookie("SESSION-TOKEN", token))
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .build()
        );

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("user:paciente@tranqui.com")
                .verifyComplete();
    }

    @Test
    void testResolvesUserFromAuthorizationHeader() {
        String token = emisor.emitir("profesional@tranqui.com", "PSIQUIATRA", "Doctor", 456L);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .header("Authorization", "Bearer " + token)
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .build()
        );

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("user:profesional@tranqui.com")
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
