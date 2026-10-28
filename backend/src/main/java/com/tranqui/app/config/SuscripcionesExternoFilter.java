package com.tranqui.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Fase 8: con {@code suscripciones.externo=true} las rutas de suscripciones son del servicio de pagos
 * (el gateway se las manda a él). Si alguna llega igual al monolito, responde 409 en vez de procesarla.
 * Cubre /api/subscriptions/** y /api/admin/subscriptions/**.
 */
@Component
public class SuscripcionesExternoFilter extends OncePerRequestFilter {

    private static final Pattern RUTAS_SUSCRIPCIONES = Pattern.compile(
            "^/api/(subscriptions|admin/subscriptions)(/.*)?$");

    private final boolean suscripcionesExterno;

    public SuscripcionesExternoFilter(@Value("${suscripciones.externo:false}") boolean suscripcionesExterno) {
        this.suscripcionesExterno = suscripcionesExterno;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !suscripcionesExterno || !RUTAS_SUSCRIPCIONES.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Las suscripciones las administra el servicio de pagos.");
    }
}
