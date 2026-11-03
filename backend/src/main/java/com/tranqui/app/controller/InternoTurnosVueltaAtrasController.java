package com.tranqui.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fase 6: las rutas internas de turnos que usan otros servicios, con la misma URL que atiende el servicio de
 * turnos. El gateway manda {@code /internal/turnos/**} a {@code TRANQUI_SERVICES_TURNOS_URI}, que sin el
 * servicio (antes del corte o en la vuelta atrás) es el monolito: así notificaciones pide siempre la misma
 * ruta.
 */
@RestController
@RequestMapping("/internal/turnos")
public class InternoTurnosVueltaAtrasController {

    private final InternoMonolitoController monolito;

    public InternoTurnosVueltaAtrasController(InternoMonolitoController monolito) {
        this.monolito = monolito;
    }

    @GetMapping("/turnos/{id}/enlaces-asistencia")
    public ResponseEntity<InternoMonolitoController.EnlacesAsistenciaDto> enlacesAsistencia(@PathVariable("id") Long id) {
        return monolito.enlacesAsistencia(id);
    }
}
