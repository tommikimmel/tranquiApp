package com.tranqui.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

/**
 * Healthcheck público mínimo (/health) según sección 4.4 del plan de microservicios.
 * Utilizado por Traefik y monitoreo externo para verificar que el gateway está arriba.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Collections.singletonMap("status", "UP")));
    }
}
