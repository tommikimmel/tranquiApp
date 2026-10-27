package com.tranqui.pagos.suscripciones;

import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint interno para la resincronización de suscripciones desde el monolito
 * vía gateway con JWT de servicio (Fase 8, S1 / S2b).
 */
@RestController
@RequestMapping("/internal/suscripciones")
public class SuscripcionesInternoController {

    private final Suscripciones suscripciones;
    private final Planes planes;
    private final SuscripcionService suscripcionService;

    public SuscripcionesInternoController(
            Suscripciones suscripciones,
            Planes planes,
            SuscripcionService suscripcionService
    ) {
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.suscripcionService = suscripcionService;
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
            Long version = sub.version() != null ? sub.version().longValue() : 0L;

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
}
