package com.tranqui.gateway.controller;

import com.tranqui.gateway.admin.AdminAuthValidator;
import com.tranqui.gateway.admin.VictoriaMetricsObservabilidadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoint de métricas de observabilidad (/api/admin/observabilidad/{categoria}?rango=1h|24h|7d)
 * según sección 3 y 11.2 del plan de microservicios.
 * Solo ADMIN. Ejecuta consultas PromQL FIJAS contra VictoriaMetrics (nunca acepta consultas libres).
 */
@RestController
@RequestMapping("/api/admin/observabilidad")
public class AdminObservabilidadController {

    private final AdminAuthValidator authValidator;
    private final VictoriaMetricsObservabilidadService observabilidadService;

    public AdminObservabilidadController(
            AdminAuthValidator authValidator,
            VictoriaMetricsObservabilidadService observabilidadService) {
        this.authValidator = authValidator;
        this.observabilidadService = observabilidadService;
    }

    @GetMapping("/{categoria}")
    public Mono<ResponseEntity<Map<String, Object>>> obtenerMetricas(
            @PathVariable("categoria") String categoria,
            @RequestParam(name = "rango", defaultValue = "24h") String rango,
            ServerWebExchange exchange) {

        authValidator.requireAdmin(exchange);

        return observabilidadService.obtenerMetricas(categoria, rango)
                .map(ResponseEntity::ok);
    }
}
