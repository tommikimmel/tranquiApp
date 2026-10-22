package com.tranqui.gateway.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Rate Limiter reactivo basado en Token Bucket sobre Redis (sección 4.3 del plan).
 * Aplica los límites exactos de la tabla 4.3 según la ruta o grupo:
 * - Login, registro, verificación, recuperación: 5 por minuto por IP (ráfaga 10)
 * - Crear pagos y reservas: 10 por minuto por usuario
 * - Webhooks de Mercado Pago: 60 por minuto por IP
 * - Resto de /api/**: 120 por minuto por usuario o IP (ráfaga 60)
 * - /internal/**: 600 por minuto por servicio
 *
 * Características:
 * - Retorna 429 con Retry-After cuando se supera el límite.
 * - Falla abierta si Redis no responde (log de advertencia + métrica Micrometer).
 */
@Component("tranquiRateLimiter")
@Primary
public class TranquiRateLimiter extends AbstractRateLimiter<TranquiRateLimiter.Config> implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(TranquiRateLimiter.class);

    public static final String RETRY_AFTER_HEADER = "Retry-After";
    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";
    public static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";

    private static final String LUA_SCRIPT =
            "local tokens_key = KEYS[1]\n" +
            "local timestamp_key = KEYS[2]\n" +
            "local rate_per_sec = tonumber(ARGV[1])\n" +
            "local capacity = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local requested = tonumber(ARGV[4]) or 1\n" +
            "local fill_time = capacity / rate_per_sec\n" +
            "local ttl = math.floor(fill_time * 2)\n" +
            "if ttl < 120 then ttl = 120 end\n" +
            "local last_tokens = tonumber(redis.call('get', tokens_key))\n" +
            "if last_tokens == nil then\n" +
            "    last_tokens = capacity\n" +
            "end\n" +
            "local last_refreshed = tonumber(redis.call('get', timestamp_key))\n" +
            "if last_refreshed == nil then\n" +
            "    last_refreshed = 0\n" +
            "end\n" +
            "local delta = math.max(0, now - last_refreshed)\n" +
            "local filled_tokens = math.min(capacity, last_tokens + (delta * rate_per_sec))\n" +
            "local allowed = filled_tokens >= requested\n" +
            "local new_tokens = filled_tokens\n" +
            "local retry_after = 0\n" +
            "if allowed then\n" +
            "    new_tokens = filled_tokens - requested\n" +
            "else\n" +
            "    retry_after = math.ceil((requested - filled_tokens) / rate_per_sec)\n" +
            "    if retry_after < 1 then retry_after = 1 end\n" +
            "end\n" +
            "redis.call('setex', tokens_key, ttl, new_tokens)\n" +
            "redis.call('setex', timestamp_key, ttl, now)\n" +
            "return { allowed and 1 or 0, math.floor(new_tokens), retry_after }";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> script;
    private final MeterRegistry meterRegistry;
    private Config defaultConfig = new Config(120.0 / 60.0, 60);

    public TranquiRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                              ConfigurationService configurationService,
                              MeterRegistry meterRegistry) {
        super(Config.class, "tranqui-rate-limiter", configurationService);
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(LUA_SCRIPT, List.class);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        // Inicialización de contexto si es requerida
    }

    @Override
    public Mono<RateLimiter.Response> isAllowed(String routeId, String id) {
        if (this.redisTemplate == null) {
            handleRedisFailure("Redis template no configurado");
            return Mono.just(new RateLimiter.Response(true, Collections.emptyMap()));
        }

        Config routeConfig = resolveConfigForRoute(routeId);
        double ratePerSec = routeConfig.getReplenishRate();
        int capacity = routeConfig.getBurstCapacity();

        List<String> keys = Arrays.asList(
                "rate_limit:{" + routeId + ":" + id + "}:tokens",
                "rate_limit:{" + routeId + ":" + id + "}:ts"
        );

        double nowInSeconds = System.currentTimeMillis() / 1000.0;
        List<String> args = Arrays.asList(
                String.valueOf(ratePerSec),
                String.valueOf(capacity),
                String.valueOf(nowInSeconds),
                "1"
        );

        return this.redisTemplate.execute(this.script, keys, args)
                .collectList()
                .map(results -> {
                    if (results == null || results.isEmpty() || !(results.get(0) instanceof List)) {
                        return new RateLimiter.Response(true, Collections.emptyMap());
                    }
                    List<?> resList = (List<?>) results.get(0);
                    if (resList.size() < 3) {
                        return new RateLimiter.Response(true, Collections.emptyMap());
                    }

                    Number allowedNum = (Number) resList.get(0);
                    Number tokensLeftNum = (Number) resList.get(1);
                    Number retryAfterNum = (Number) resList.get(2);

                    boolean allowed = allowedNum != null && allowedNum.intValue() == 1;
                    long tokensLeft = tokensLeftNum != null ? tokensLeftNum.longValue() : 0L;
                    long retryAfter = retryAfterNum != null ? retryAfterNum.longValue() : 60L;

                    Map<String, String> headers = new HashMap<>();
                    headers.put(BURST_CAPACITY_HEADER, String.valueOf(capacity));
                    headers.put(REPLENISH_RATE_HEADER, String.format(Locale.ROOT, "%.4f", ratePerSec));
                    headers.put(REMAINING_HEADER, String.valueOf(tokensLeft));

                    if (!allowed) {
                        if (retryAfter <= 0) retryAfter = 60;
                        headers.put(RETRY_AFTER_HEADER, String.valueOf(retryAfter));
                        if (meterRegistry != null) {
                            meterRegistry.counter("gateway.ratelimit.rejected", "route", routeId != null ? routeId : "unknown").increment();
                            meterRegistry.counter("gateway.requests.429", "route", routeId != null ? routeId : "unknown").increment();
                        }
                    }

                    return new RateLimiter.Response(allowed, headers);
                })
                .onErrorResume(ex -> {
                    handleRedisFailure(ex.getMessage());
                    return Mono.just(new RateLimiter.Response(true, Collections.emptyMap()));
                });
    }

    private void handleRedisFailure(String errorDetail) {
        log.warn("Redis no disponible para rate limiting; fallando abierto: {}", errorDetail);
        if (meterRegistry != null) {
            meterRegistry.counter("gateway.ratelimit.redis.errors").increment();
        }
    }

    public Config resolveConfigForRoute(String routeId) {
        if (routeId == null) {
            return defaultConfig;
        }

        String lower = routeId.toLowerCase();
        // 1. Login, registro, verificación, recuperación (5 por minuto, ráfaga 10)
        if (lower.contains("auth") || lower.contains("login") || lower.contains("register")
                || lower.contains("verify") || lower.contains("password")) {
            return new Config(5.0 / 60.0, 10);
        }

        // 2. Crear pagos y reservas (10 por minuto)
        if (lower.contains("reservar") || lower.contains("checkout")) {
            return new Config(10.0 / 60.0, 10);
        }

        // 3. Webhooks de Mercado Pago (60 por minuto)
        if (lower.contains("webhook") || lower.contains("mercadopago")) {
            return new Config(60.0 / 60.0, 60);
        }

        // 4. /internal/** (600 por minuto por servicio)
        if (lower.contains("internal")) {
            return new Config(600.0 / 60.0, 600);
        }

        // 5. Resto de /api/** (120 por minuto, ráfaga 60)
        Config customConfig = getConfig().get(routeId);
        if (customConfig != null) {
            return customConfig;
        }

        return defaultConfig;
    }

    public static class Config {
        private double replenishRate = 120.0 / 60.0; // tokens por segundo
        private int burstCapacity = 60;

        public Config() {}

        public Config(double replenishRate, int burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }

        public double getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(double replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }
    }
}
