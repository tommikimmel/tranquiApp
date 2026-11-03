package com.tranqui.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador de fallback de Circuit Breaker (Resilience4j) según sección 4.6 del plan.
 * Retorna HTTP 503 con formato JSON {"error":"servicio_no_disponible","servicio":"<nombre>"}.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{servicio}")
    public Mono<ResponseEntity<Map<String, String>>> fallback(@PathVariable("servicio") String servicio) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "servicio_no_disponible");
        body.put("servicio", servicio);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
