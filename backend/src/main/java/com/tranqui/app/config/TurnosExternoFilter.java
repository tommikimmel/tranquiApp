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
 * Fase 6: con {@code turnos.externo=true} las rutas de turnos son del servicio de turnos (el gateway se las
 * manda a él). Si alguna llega igual al monolito, responde 409 en vez de escribir en su copia de
 * {@code turno}. Son las mismas rutas que las reglas de turnos del gateway, más "Mis turnos" del paciente.
 */
@Component
public class TurnosExternoFilter extends OncePerRequestFilter {

    private static final Pattern RUTAS_DE_TURNOS = Pattern.compile(
            "^/api/(turnos(/.*)?|medicos/turnos(/.*)?|medicos/turnos-disponibles-conteo|medicos/[^/]+/turnos-disponibles|pacientes/me/turnos)$");

    private final boolean turnosExterno;

    public TurnosExternoFilter(@Value("${turnos.externo:false}") boolean turnosExterno) {
        this.turnosExterno = turnosExterno;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !turnosExterno || !RUTAS_DE_TURNOS.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Los turnos los administra el servicio de turnos.");
    }
}
