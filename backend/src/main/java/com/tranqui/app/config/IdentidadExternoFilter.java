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
 * Fase 9: con {@code identidad.externo=true} las rutas de identidad, tickets, soporte, administración
 * de usuarios, portón del sitio y novedades las administra el servicio de identidad. Si alguna llega al monolito,
 * responde 409 en vez de procesarla. También responde 409 ante escrituras internas de pacientes
 * (POST/PUT /internal/monolito/pacientes) y de identidad de profesionales (PUT /internal/monolito/profesionales/{id}/identidad).
 * Las lecturas de /internal/monolito/** siguen funcionando normalmente.
 */
@Component
public class IdentidadExternoFilter extends OncePerRequestFilter {

    private static final Pattern RUTAS_IDENTIDAD_API = Pattern.compile(
            "^/api/(auth|tickets|soporte|admin/users|admin/tickets|site-access|internal/novedades)(/.*)?$");

    private static final Pattern RUTA_INTERNA_PACIENTES = Pattern.compile(
            "^/internal/monolito/pacientes(/.*)?$");

    private static final Pattern RUTA_INTERNA_PROFESIONAL_IDENTIDAD = Pattern.compile(
            "^/internal/monolito/profesionales/[^/]+/identidad$");

    private final boolean identidadExterno;

    public IdentidadExternoFilter(@Value("${identidad.externo:false}") boolean identidadExterno) {
        this.identidadExterno = identidadExterno;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!identidadExterno) {
            return true;
        }
        String uri = request.getRequestURI();
        if (RUTAS_IDENTIDAD_API.matcher(uri).matches()) {
            return false;
        }
        String method = request.getMethod();
        if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))
                && RUTA_INTERNA_PACIENTES.matcher(uri).matches()) {
            return false;
        }
        if ("PUT".equalsIgnoreCase(method) && RUTA_INTERNA_PROFESIONAL_IDENTIDAD.matcher(uri).matches()) {
            return false;
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("La identidad la administra el servicio de identidad.");
    }
}
