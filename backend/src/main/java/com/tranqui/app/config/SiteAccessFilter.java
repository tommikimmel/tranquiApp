package com.tranqui.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Temporary site-wide gate ("por el momento... para que clientes externos no accedan a la
 * página") sitting in front of the JWT auth filter — separate from and earlier than the normal
 * per-user login, so anonymous/public endpoints (browsing médicos, booking a turno) are gated
 * too, not just the authenticated dashboard. The expected password lives only in the
 * SITE_ACCESS_PASSWORD env var (.env); leaving it unset disables this filter entirely so local
 * dev isn't affected.
 */
@Component
public class SiteAccessFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "SITE-ACCESS";

    @Value("${site.access.password:}")
    private String sitePassword;

    // Server-to-server callbacks that never carry the visitor's browser cookies (Mercado Pago's
    // and Google's servers hit these directly), plus infra/health endpoints and the gate's own
    // verification endpoint, which obviously can't require the cookie it's meant to grant.
    private static final List<String> BYPASS_PREFIXES = List.of(
            "/api/site-access/",
            "/api/health",
            "/api/payments/webhook",
            "/api/medicos/google-calendar/webhook",
            "/error"
    );

    public boolean isEnabled() {
        return sitePassword != null && !sitePassword.isBlank();
    }

    public boolean matches(String candidatePassword) {
        return isEnabled() && sitePassword.equals(candidatePassword);
    }

    public String expectedCookieValue() {
        return sha256Hex(sitePassword);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod()) || isBypassed(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (hasValidCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Acceso restringido. Ingresá la contraseña del sitio.\"}");
    }

    private boolean isBypassed(String uri) {
        return BYPASS_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private boolean hasValidCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        String expected = expectedCookieValue();
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && expected.equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
