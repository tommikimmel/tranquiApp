package com.tranqui.turnos.web;

import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.turnos.Gestion;
import com.tranqui.turnos.turnos.Publicador;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lo que turnos le responde a otros servicios (sección 4.2, solo con JWT de servicio): la resincronización de
 * la copia del monolito y los enlaces del mail de asistencia para notificaciones. Las mismas URLs que atiende
 * el monolito antes del corte y en la vuelta atrás.
 */
@RestController
@RequestMapping("/internal/turnos")
public class InternoController {

    /** {@code ResincronizacionTurnos.TurnoServicio} del monolito. */
    public record TurnoServicio(int version, TurnoActualizadoData turno) {}

    /** {@code InternoMonolitoController.EnlacesAsistenciaDto}. */
    public record EnlacesAsistencia(String confirmarUrl, String noAsistiraUrl) {}

    private final Turnos turnos;
    private final Gestion gestion;

    public InternoController(Turnos turnos, Gestion gestion) {
        this.turnos = turnos;
        this.gestion = gestion;
    }

    /** Todos, o los que cambiaron desde {@code desde} (sin los tokens de los mails). */
    @GetMapping("/turnos")
    public List<TurnoServicio> turnos(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde) {
        return turnos.cambiadosDesde(desde).stream()
                .map(t -> new TurnoServicio(t.version, Publicador.datos(t)))
                .toList();
    }

    @GetMapping("/turnos/{id}/enlaces-asistencia")
    public ResponseEntity<EnlacesAsistencia> enlacesAsistencia(@PathVariable("id") Long id) {
        return gestion.enlacesAsistencia(id)
                .map(e -> ResponseEntity.ok(new EnlacesAsistencia(e[0], e[1])))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
