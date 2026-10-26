package com.tranqui.calendario.interno;

import com.tranqui.eventos.jwt.ServiceJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rutas {@code /internal/**} (sección 4.2): solo otros servicios, a través del gateway, con un JWT
 * de servicio válido. El gateway ya lo valida y bloquea lo que llega de afuera; este filtro es la
 * segunda barrera por si alguien llega al servicio sin pasar por el gateway. Sin clave configurada,
 * las rutas internas quedan cerradas (401).
 */
@Component
public class FiltroServicioInterno extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroServicioInterno.class);

    private final ServiceJwt serviceJwt;

    public FiltroServicioInterno(@Value("${tranqui.jwt.service-secret:}") String clave) {
        this.serviceJwt = clave == null || clave.isBlank() ? null : new ServiceJwt(clave);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String autorizacion = request.getHeader("Authorization");
        if (serviceJwt == null || autorizacion == null || !autorizacion.startsWith("Bearer ")
                || !serviceJwt.esValido(autorizacion.substring(7).trim())) {
            log.warn("Ruta interna {} rechazada: falta un JWT de servicio válido", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
