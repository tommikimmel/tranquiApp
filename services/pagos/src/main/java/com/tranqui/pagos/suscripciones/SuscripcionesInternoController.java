package com.tranqui.pagos.suscripciones;

import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Endpoint interno para la resincronización de suscripciones desde el monolito
 * vía gateway con JWT de servicio (Fase 8, S1 / S2b; Fase 9, I3c).
 */
@RestController
@RequestMapping("/internal/suscripciones")
public class SuscripcionesInternoController {

    private static final Pattern PATRON_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Suscripciones suscripciones;
    private final Planes planes;
    private final SuscripcionService suscripcionService;
    private final Suscriptores suscriptores;

    public SuscripcionesInternoController(
            Suscripciones suscripciones,
            Planes planes,
            SuscripcionService suscripcionService,
            Suscriptores suscriptores
    ) {
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.suscripcionService = suscripcionService;
        this.suscriptores = suscriptores;
    }

    @GetMapping("/estado")
    public ResponseEntity<List<Dtos.EstadoSuscripcionDto>> estado(
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "tamanio", defaultValue = "200") int tamanio
    ) {
        List<Suscripciones.Suscripcion> lote = suscripciones.paginadasOrdenadasPorProfesional(pagina, tamanio);
        List<Dtos.EstadoSuscripcionDto> resultado = lote.stream().map(sub -> {
            boolean acceso = suscripcionService.isAccessAllowed(sub.professionalId());
            boolean puedeRecetar = suscripcionService.hasFeature(sub.professionalId(), "recetas_electronicas");
            String planCode = planes.porId(sub.planId()).map(Planes.Plan::code).orElse(null);
            // Usamos aggregateVersion() (version + 1) para que coincida exactamente con la versión
            // publicada en los eventos outbox (suscripciones.suscripcion.cambio-estado.v2). De lo contrario,
            // la resincronización en el monolito (suscripcion_acceso) descartaría la actualización
            // por ser <= a la versión guardada tras el último evento.
            Long version = (long) sub.aggregateVersion();

            return new Dtos.EstadoSuscripcionDto(
                    sub.professionalId(),
                    sub.status(),
                    planCode,
                    acceso,
                    puedeRecetar,
                    sub.currentPeriodEnd(),
                    sub.graceUntil(),
                    version
            );
        }).toList();

        return ResponseEntity.ok(resultado);
    }

    /**
     * Consulta de acceso de suscripción por email (Fase 9, I3c), portado de AccesoSuscripciones e
     * InternoMonolitoController del monolito. Email desconocido o sin suscripción: permitido=true.
     * Email inválido o vacío: 400.
     */
    @GetMapping("/acceso")
    public ResponseEntity<Dtos.AccesoDto> acceso(@RequestParam(value = "email", required = false) String email) {
        if (email == null || email.isBlank() || !PATRON_EMAIL.matcher(email.trim()).matches()) {
            return ResponseEntity.badRequest().build();
        }

        String emailNormalizado = email.trim();
        Optional<Suscriptores.Suscriptor> suscriptorOpt = suscriptores.porEmail(emailNormalizado);
        if (suscriptorOpt.isEmpty()) {
            return ResponseEntity.ok(new Dtos.AccesoDto(true));
        }

        Suscriptores.Suscriptor suscriptor = suscriptorOpt.get();
        if (suscriptor.rol() != null && !"PSIQUIATRA".equalsIgnoreCase(suscriptor.rol())) {
            return ResponseEntity.ok(new Dtos.AccesoDto(true));
        }

        Optional<Suscripciones.Suscripcion> subOpt = suscripciones.porProfesionalId(suscriptor.id());
        if (subOpt.isEmpty()) {
            return ResponseEntity.ok(new Dtos.AccesoDto(true));
        }

        Suscripciones.Suscripcion sub = subOpt.get();
        boolean permitido = suscripcionService.isAccessAllowed(suscriptor.id())
                || ("ACTIVE".equals(sub.status()) && sub.graceUntil() != null && sub.graceUntil().isAfter(LocalDateTime.now()));

        return ResponseEntity.ok(new Dtos.AccesoDto(permitido));
    }
}
