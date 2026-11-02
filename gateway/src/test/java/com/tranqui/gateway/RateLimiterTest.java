package com.tranqui.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tranqui.gateway.ratelimit.TranquiRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RateLimiterTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TranquiRateLimiter rateLimiter;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private ReactiveStringRedisTemplate mockRedisTemplate;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tranqui.services.monolito.uri", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void testFailOpenSinRedis() {
        // Simular que Redis falla con una excepción de conexión
        when(mockRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RedisConnectionFailureException("Redis caído")));

        wireMockServer.stubFor(get(urlEqualTo("/api/medicos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        double initialErrors = meterRegistry.counter("gateway.ratelimit.redis.errors").count();

        // Debe fallar abierto y dejar pasar el pedido con 200 OK
        webTestClient.get().uri("/api/medicos")
                .exchange()
                .expectStatus().isOk();

        double finalErrors = meterRegistry.counter("gateway.ratelimit.redis.errors").count();
        assertThat(finalErrors).isGreaterThan(initialErrors);
    }

    @Test
    void testRateLimitSuperadoRetorna429ConRetryAfter() {
        double rechazosIniciales = meterRegistry.counter("gateway.requests.429").count();

        // Simular respuesta de Redis indicando límite superado: allowed=0, tokens=0, retryAfter=45
        List<Long> redisResult = Arrays.asList(0L, 0L, 45L);
        when(mockRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(redisResult));

        webTestClient.get().uri("/api/auth/login")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "45")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");

        double rechazosFinales = meterRegistry.counter("gateway.requests.429").count();
        assertThat(rechazosFinales).isGreaterThan(rechazosIniciales);
    }

    @Test
    void testRateLimitPermitidoRetornaRemainingYHeaders() {
        // Simular respuesta de Redis permitiendo: allowed=1, tokens=8, retryAfter=0
        List<Long> redisResult = Arrays.asList(1L, 8L, 0L);
        when(mockRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(redisResult));

        wireMockServer.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        webTestClient.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "8");
    }

    @Test
    void testResolucionDeLimitesPorGrupo() {
        // Auth: 5/min (5/60 = 0.0833), burst 10
        TranquiRateLimiter.Config authConfig = rateLimiter.resolveConfigForRoute("auth-ratelimited");
        assertThat(authConfig.getBurstCapacity()).isEqualTo(10);
        assertThat(authConfig.getReplenishRate()).isCloseTo(5.0 / 60.0, org.assertj.core.data.Offset.offset(0.001));

        // Checkout / Reservas: 10/min, burst 10
        TranquiRateLimiter.Config checkoutConfig = rateLimiter.resolveConfigForRoute("turnos-reservar");
        assertThat(checkoutConfig.getBurstCapacity()).isEqualTo(10);
        assertThat(checkoutConfig.getReplenishRate()).isCloseTo(10.0 / 60.0, org.assertj.core.data.Offset.offset(0.001));

        // Webhooks: 60/min, burst 60
        TranquiRateLimiter.Config webhookConfig = rateLimiter.resolveConfigForRoute("pagos-webhooks");
        assertThat(webhookConfig.getBurstCapacity()).isEqualTo(60);
        assertThat(webhookConfig.getReplenishRate()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));

        // Internal: 600/min, burst 600
        TranquiRateLimiter.Config internalConfig = rateLimiter.resolveConfigForRoute("internal");
        assertThat(internalConfig.getBurstCapacity()).isEqualTo(600);
        assertThat(internalConfig.getReplenishRate()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.001));

        // Default API: 120/min, burst 60
        TranquiRateLimiter.Config defaultConfig = rateLimiter.resolveConfigForRoute("monolito-fallback");
        assertThat(defaultConfig.getBurstCapacity()).isEqualTo(60);
        assertThat(defaultConfig.getReplenishRate()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.001));
    }
}
