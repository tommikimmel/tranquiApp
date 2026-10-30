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
 * Fase 7: con {@code clinica.externo=true} las rutas de historia clínica, recetas y conceptos son del
 * servicio de profesionales (el gateway se las manda a él). Si alguna llega igual al monolito, responde
 * 409. Cubre /api/pacientes/** (salvo /api/pacientes/me/turnos que es de turnos), /api/recetas/** y
 * /api/conceptos/**.
 */
@Component
public class ClinicaExternoFilter extends OncePerRequestFilter {

    private static final Pattern RUTAS_CLINICA = Pattern.compile(
            "^/api/(pacientes(/.*)?|recetas(/.*)?|conceptos(/.*)?)$");
    private static final String EXCLUSION_TURNOS = "/api/pacientes/me/turnos";

    private final boolean clinicaExterno;

    public ClinicaExternoFilter(@Value("${clinica.externo:false}") boolean clinicaExterno) {
        this.clinicaExterno = clinicaExterno;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!clinicaExterno) {
            return true;
        }
        String uri = request.getRequestURI();
        if (EXCLUSION_TURNOS.equals(uri)) {
            return true;
        }
        return !RUTAS_CLINICA.matcher(uri).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("La historia clínica y las recetas las administra el servicio de profesionales.");
    }
}
