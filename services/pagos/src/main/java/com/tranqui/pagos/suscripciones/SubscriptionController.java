package com.tranqui.pagos.suscripciones;

import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.pagos.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints públicos y del profesional para suscripciones, replicando exactamente las rutas
 * y contratos del monolito bajo /api/subscriptions.
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private static final Set<String> ROLES_MY_SUB = Set.of("PSIQUIATRA", "PROFESIONAL", "ADMIN", "PACIENTE");
    private static final Set<String> ROLES_PROFESIONAL = Set.of("PSIQUIATRA", "PROFESIONAL");

    private final Planes planes;
    private final Suscripciones suscripciones;
    private final Suscriptores suscriptores;
    private final SuscripcionService suscripcionService;

    public SubscriptionController(
            Planes planes,
            Suscripciones suscripciones,
            Suscriptores suscriptores,
            SuscripcionService suscripcionService
    ) {
        this.planes = planes;
        this.suscripciones = suscripciones;
        this.suscriptores = suscriptores;
        this.suscripcionService = suscripcionService;
    }

    private UsuarioJwt.Usuario usuario(HttpServletRequest request) {
        UsuarioJwt.Usuario u = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return u;
    }

    /**
     * Catálogo público de planes y precios activos.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<Dtos.PlanResponseDto>> getActivePlans() {
        List<Planes.Plan> plans = planes.activos();
        List<Dtos.PlanResponseDto> dtos = plans.stream().map(p -> new Dtos.PlanResponseDto(
                p.id(),
                p.code(),
                p.name(),
                p.description(),
                p.priceArs(),
                p.priceUsdRef(),
                p.priceArsAnual(),
                p.billingPeriod(),
                p.minSeats(),
                p.requiresPrescriber(),
                planes.featuresPorPlanId(p.id()).stream().map(Planes.PlanFeature::featureKey).toList(),
                p.isActive()
        )).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Suscripción del profesional autenticado.
     */
    @GetMapping("/my-subscription")
    public ResponseEntity<Dtos.SubscriptionResponseDto> getMySubscription(HttpServletRequest request) {
        UsuarioJwt.Usuario u = usuario(request);
        if (!ROLES_MY_SUB.contains(u.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Suscriptores.Suscriptor prof = suscriptores.porEmail(u.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(prof.id()).orElse(null);
        if (sub == null) {
            return ResponseEntity.ok(null);
        }

        Planes.Plan plan = planes.porId(sub.planId()).orElse(null);
        List<String> features = plan != null
                ? planes.featuresPorPlanId(plan.id()).stream().map(Planes.PlanFeature::featureKey).toList()
                : List.of();

        Dtos.PlanResponseDto planDto = plan != null ? new Dtos.PlanResponseDto(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.description(),
                plan.priceArs(),
                plan.priceUsdRef(),
                plan.priceArsAnual(),
                plan.billingPeriod(),
                plan.minSeats(),
                plan.requiresPrescriber(),
                features,
                plan.isActive()
        ) : null;

        Dtos.SubscriptionResponseDto response = new Dtos.SubscriptionResponseDto(
                sub.id(),
                prof.id(),
                prof.nombreLegalEfectivo(),
                prof.email(),
                planDto,
                sub.status(),
                sub.seats(),
                sub.billingSource(),
                sub.billingCycle(),
                sub.amountArs(),
                sub.currentPeriodStart(),
                sub.currentPeriodEnd(),
                sub.nextBillingDate(),
                sub.graceUntil(),
                sub.cancelAtPeriodEnd(),
                features
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Crea el checkout de Mercado Pago (preapproval) para el profesional autenticado.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> iniciarCheckout(HttpServletRequest request, @RequestBody Dtos.CheckoutRequestDto body) {
        UsuarioJwt.Usuario u = usuario(request);
        if (!ROLES_PROFESIONAL.contains(u.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (body == null || body.planId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "planId es obligatorio"));
        }

        Suscriptores.Suscriptor prof = suscriptores.porEmail(u.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        try {
            String checkoutUrl = suscripcionService.iniciarCheckout(prof.id(), body.planId(), body.billingCycle());
            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cancela la renovación automática de la suscripción del profesional autenticado.
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelarMiSuscripcion(HttpServletRequest request) {
        UsuarioJwt.Usuario u = usuario(request);
        if (!ROLES_PROFESIONAL.contains(u.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Suscriptores.Suscriptor prof = suscriptores.porEmail(u.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        try {
            Suscripciones.Suscripcion sub = suscripcionService.cancelarSuscripcionPorProfesional(prof.id());
            return ResponseEntity.ok(Map.of(
                    "message", "Suscripción cancelada exitosamente. Mantendrás acceso a todas las funcionalidades hasta el fin del período abonado.",
                    "status", sub.status(),
                    "cancelAtPeriodEnd", Boolean.TRUE.equals(sub.cancelAtPeriodEnd()),
                    "currentPeriodEnd", sub.currentPeriodEnd() != null ? sub.currentPeriodEnd().toString() : ""
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
