package com.tranqui.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

// Short-TTL cache for hot, repeatable reads that hit a slow external API — currently only
// GoogleCalendarService#obtenerEventosDelDia (used by TurnoService#obtenerConteosDisponibilidad
// on the public homepage, which loops per médico and would otherwise fire a blocking Google
// Calendar HTTP call per médico on every page load). A short TTL keeps availability reasonably
// fresh while collapsing the bursts of near-simultaneous requests visitors cause.
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
        return cacheManager -> cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(90, TimeUnit.SECONDS)
                        .maximumSize(2000)
        );
    }
}
