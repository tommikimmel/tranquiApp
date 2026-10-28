package com.tranqui.identidad.novedades;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Envío de novedades, invocado por `npm run mantenimiento -- off` desde el propio VPS (ver scripts/mantenimiento.mjs).
 * Mismo contrato que {@code /api/internal/novedades} del monolito, que pasa a esta ruta en la Fase 9. No es una API
 * pública ni la llama otro servicio (por eso no pide el JWT de servicio):
 * - exige el encabezado X-Novedades-Token igual a NOVEDADES_TOKEN (solo vive en el .env del VPS);
 * - rechaza todo pedido que haya pasado por Traefik o por el gateway (trae encabezados X-Forwarded-*).
 * Ante cualquier rechazo responde 404, para no revelar que la ruta existe.
 */
@RestController
@RequestMapping("/internal/identidad/novedades")
public class NovedadesController {

    private final NovedadesService novedadesService;

    @Value("${novedades.token:}")
    private String token;

    public NovedadesController(NovedadesService novedadesService) {
        this.novedadesService = novedadesService;
    }

    @PostMapping
    public ResponseEntity<?> enviar(@RequestBody NovedadesContenido contenido,
                                    @RequestParam(name = "prueba", defaultValue = "false") boolean prueba,
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
    public ResponseEntity<?> estado(@PathVariable("id") String id, HttpServletRequest request) {
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
