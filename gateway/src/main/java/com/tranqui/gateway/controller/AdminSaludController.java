package com.tranqui.gateway.controller;

import com.tranqui.gateway.admin.AdminAuthValidator;
import com.tranqui.gateway.admin.ConsulSaludService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoint de estado de salud de servicios (/api/admin/salud) según sección 4.4 del plan de microservicios.
 * Restringido exclusivamente a usuarios con rol ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSaludController {

    private final AdminAuthValidator authValidator;
    private final ConsulSaludService consulSaludService;

    public AdminSaludController(AdminAuthValidator authValidator, ConsulSaludService consulSaludService) {
        this.authValidator = authValidator;
        this.consulSaludService = consulSaludService;
    }

    @GetMapping("/salud")
    public Mono<ResponseEntity<Map<String, Object>>> obtenerSalud(ServerWebExchange exchange) {
        authValidator.requireAdmin(exchange);
        Map<String, Object> estado = consulSaludService.obtenerEstadoServicios();
        return Mono.just(ResponseEntity.ok(estado));
    }
}
