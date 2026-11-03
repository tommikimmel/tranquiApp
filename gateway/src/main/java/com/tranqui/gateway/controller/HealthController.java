package com.tranqui.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Healthcheck público mínimo (/health y /api/health) según sección 4.4 del plan de microservicios.
 * /health es utilizado por Traefik y monitoreo externo.
 * /api/health responde directamente desde el gateway (mismo formato que el monolito: {"status":"UP"}),
 * sin depender del monolito.
 */
@RestController
public class HealthController {

    @GetMapping({"/health", "/api/health"})
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP")));
    }
}

