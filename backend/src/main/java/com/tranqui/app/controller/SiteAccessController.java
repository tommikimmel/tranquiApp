package com.tranqui.app.controller;

import com.tranqui.app.config.SiteAccessFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/site-access")
public class SiteAccessController {

    @Autowired
    private SiteAccessFilter siteAccessFilter;

    @Autowired
    private Environment env;

    @Value("${google.client-id:dummy-client-id}")
    private String googleClientId;

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Boolean>> estado(HttpServletRequest request) {
        // When the gate isn't configured (SITE_ACCESS_PASSWORD unset, e.g. local dev) there's
        // nothing to unlock — report authorized so the app just loads normally.
        if (!siteAccessFilter.isEnabled()) {
            return ResponseEntity.ok(Map.of("autorizado", true));
        }
        boolean autorizado = false;
        if (request.getCookies() != null) {
            String expected = siteAccessFilter.expectedCookieValue();
            autorizado = Arrays.stream(request.getCookies())
                    .anyMatch(c -> SiteAccessFilter.COOKIE_NAME.equals(c.getName()) && expected.equals(c.getValue()));
        }
        return ResponseEntity.ok(Map.of("autorizado", autorizado));
    }

    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletResponse response) {
        if (!siteAccessFilter.isEnabled()) {
            return ResponseEntity.ok(Map.of("autorizado", true));
        }
        String password = body.get("password");
        if (!siteAccessFilter.matches(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Contraseña incorrecta."));
        }

        // Same secure/sameSite decision as AuthController's SESSION-TOKEN cookie — this
        // deployment always runs with SPRING_PROFILES_ACTIVE=dev, so http-only Lax is what
        // actually applies in production today too.
        boolean secureCookie = true;
        String sameSiteVal = "Strict";
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        if (isDev || "dummy-client-id".equals(googleClientId)) {
            secureCookie = false;
            sameSiteVal = "Lax";
        }

        ResponseCookie cookie = ResponseCookie.from(SiteAccessFilter.COOKIE_NAME, siteAccessFilter.expectedCookieValue())
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(30L * 24 * 60 * 60) // 30 days
                .sameSite(sameSiteVal)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of("autorizado", true));
    }
}
