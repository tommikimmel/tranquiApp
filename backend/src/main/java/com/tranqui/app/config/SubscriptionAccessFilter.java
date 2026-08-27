package com.tranqui.app.config;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Paywall de suscripciones (Etapa 1): corta el acceso a la API de cualquier PSIQUIATRA sin
 * suscripción activa vigente (ver SubscriptionService.isAccessAllowed), en tiempo real en cada
 * request — no depende del cron diario de reconciliación. Corre DESPUÉS de JwtAuthenticationFilter
 * (necesita el usuario ya resuelto en el SecurityContext) y ANTES de que la request llegue al
 * controller. Mismo patrón que SiteAccessFilter: deny-by-default con una allowlist chica de rutas
 * que siempre deben quedar accesibles (login/registro/logout, ver el propio estado de
 * suscripción/planes, webhooks, health).
 *
 * Deliberadamente conservador: solo actúa sobre requests autenticadas cuyo rol sea PSIQUIATRA.
 * Pacientes, admin, visitadores y requests anónimas nunca son tocadas por este filtro.
 */
@Component
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Rutas que un profesional bloqueado por falta de pago igual debe poder usar: autenticarse,
    // cerrar sesión, completar su perfil (Google signup usa /api/auth/complete-profile), ver su
    // propio estado de suscripción/catálogo de planes y facturas, y todo lo que no pasa por el
    // navegador del profesional (webhooks, health check, el propio gate de sitio).
    private static final List<String> BYPASS_PREFIXES = List.of(
            "/api/auth/",
            "/api/subscriptions/",
            "/api/site-access/",
            "/api/health",
            "/api/payments/webhook",
            "/api/medicos/google-calendar/webhook",
            "/api/medicos/google-calendar/callback",
            "/api/medicos/mercadopago/callback",
            "/error"
    );

    // Match exacto (no prefijo): GET /api/medicos es el buscador público de profesionales que
    // usa la landing page — sin @PreAuthorize en el controller a propósito, porque lo consulta
    // cualquier visitante anónimo. Si un psiquiatra bloqueado por el paywall navega esa misma
    // página estando logueado (con su propio JWT), este filtro no debe cortarle un endpoint que
    // es público igual — MedicoService.obtenerMedicosActivos() ya excluye ahí a los profesionales
    // sin pago al día, así que dejar pasar la request no expone nada de más. No puede sumarse a
    // BYPASS_PREFIXES tal cual porque matchea por prefijo: "/api/medicos" ahí adentro dejaría
    // pasar también /api/medicos/perfil y el resto de rutas del panel, que sí deben seguir
    // bloqueadas para un profesional sin suscripción activa.
    private static final List<String> BYPASS_EXACT = List.of(
            "/api/medicos"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isBypassed(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            // No autenticado — dejá que el resto de la cadena (o el controller) decida (401/permitAll).
            filterChain.doFilter(request, response);
            return;
        }

        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (usuario == null || usuario.getRol() != Rol.PSIQUIATRA) {
            // El paywall solo aplica a profesionales — todo lo demás (paciente, admin, visitador) pasa.
            filterChain.doFilter(request, response);
            return;
        }

        if (subscriptionService.isAccessAllowed(usuario.getId())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"SUBSCRIPTION_REQUIRED\",\"message\":\"Necesitás una suscripción activa para acceder a esta funcionalidad.\"}"
        );
    }

    private boolean isBypassed(String uri) {
        return BYPASS_PREFIXES.stream().anyMatch(uri::startsWith) || BYPASS_EXACT.contains(uri);
    }
}
