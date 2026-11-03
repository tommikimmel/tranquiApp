package com.tranqui.identidad.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

/**
 * Portón del sitio (Fase 9, portado de SiteAccessController del monolito): mientras {@code SITE_ACCESS_PASSWORD}
 * esté configurada, el sitio pide esa contraseña antes de mostrarse. Acá se emite la cookie {@code SITE-ACCESS}
 * (SHA-256 en hexadecimal de la contraseña, 30 días); la exige el gateway en el resto de las rutas.
 */
@RestController
@RequestMapping("/api/site-access")
public class SiteAccessController {

    public static final String COOKIE = "SITE-ACCESS";

    private final Environment env;
    private final String password;
    private final String googleClientId;

    public SiteAccessController(Environment env,
                                @Value("${site.access.password:}") String password,
                                @Value("${google.client-id:dummy-client-id}") String googleClientId) {
        this.env = env;
        this.password = password != null ? password : "";
        this.googleClientId = googleClientId;
    }

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Boolean>> estado(HttpServletRequest request) {
        // Sin contraseña configurada (por ejemplo en local) no hay nada que desbloquear.
        if (!habilitado()) {
            return ResponseEntity.ok(Map.of("autorizado", true));
        }
        boolean autorizado = false;
        if (request.getCookies() != null) {
            String esperado = valorCookie();
            autorizado = Arrays.stream(request.getCookies())
                    .anyMatch(c -> COOKIE.equals(c.getName()) && esperado.equals(c.getValue()));
        }
        return ResponseEntity.ok(Map.of("autorizado", autorizado));
    }

    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestBody(required = false) Map<String, String> body, HttpServletResponse response) {
        if (!habilitado()) {
            return ResponseEntity.ok(Map.of("autorizado", true));
        }
        String candidata = body != null ? body.get("password") : null;
        if (candidata == null || !MessageDigest.isEqual(candidata.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Contraseña incorrecta."));
        }

        // La misma decisión de Secure y SameSite que la cookie de sesión (ver AuthController).
        boolean lax = Arrays.asList(env.getActiveProfiles()).contains("dev") || "dummy-client-id".equals(googleClientId);
        ResponseCookie cookie = ResponseCookie.from(COOKIE, valorCookie())
                .httpOnly(true)
                .secure(!lax)
                .path("/")
                .maxAge(30L * 24 * 60 * 60)
                .sameSite(lax ? "Lax" : "Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("autorizado", true));
    }

    private boolean habilitado() {
        return !password.isBlank();
    }

    private String valorCookie() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
