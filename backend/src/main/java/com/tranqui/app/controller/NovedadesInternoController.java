package com.tranqui.app.controller;

import com.tranqui.app.service.novedades.NovedadesContenido;
import com.tranqui.app.service.novedades.NovedadesService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Envío de novedades, invocado por `npm run mantenimiento -- off` desde el propio VPS (curl a
 * 127.0.0.1:8081, ver scripts/mantenimiento.mjs). No es una API pública:
 * - exige el encabezado X-Novedades-Token igual a NOVEDADES_TOKEN (solo vive en el .env del VPS);
 * - rechaza todo pedido que haya pasado por Traefik (trae encabezados X-Forwarded-*).
 * Ante cualquier rechazo responde 404, para no revelar que la ruta existe.
 */
@RestController
@RequestMapping("/api/internal/novedades")
public class NovedadesInternoController {

    private final NovedadesService novedadesService;

    @Value("${novedades.token:}")
    private String token;

    public NovedadesInternoController(NovedadesService novedadesService) {
        this.novedadesService = novedadesService;
    }

    @PostMapping
    public ResponseEntity<?> enviar(@RequestBody NovedadesContenido contenido,
                                    @RequestParam(defaultValue = "false") boolean prueba,
                                    HttpServletRequest request) {
        if (!autorizado(request)) return ResponseEntity.notFound().build();
        try {
            if (prueba) {
                int enviados = novedadesService.enviarPrueba(contenido);
                return ResponseEntity.ok(Map.of("prueba", true, "mailsEnviados", enviados));
            }
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(novedadesService.enviar(contenido));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> estado(@PathVariable String id, HttpServletRequest request) {
        if (!autorizado(request)) return ResponseEntity.notFound().build();
        NovedadesService.Resultado r = novedadesService.estado(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    boolean autorizado(HttpServletRequest request) {
        if (token == null || token.isBlank()) return false;
        if (request.getHeader("X-Forwarded-For") != null || request.getHeader("X-Forwarded-Host") != null) return false;
        String recibido = request.getHeader("X-Novedades-Token");
        return recibido != null && MessageDigest.isEqual(
                recibido.getBytes(StandardCharsets.UTF_8), token.trim().getBytes(StandardCharsets.UTF_8));
    }
}
