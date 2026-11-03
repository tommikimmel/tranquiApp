package com.tranqui.calendario.interno;

import com.tranqui.calendario.datos.EventosExternos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Consultas internas al calendario (sección 4.2), protegidas por {@link FiltroServicioInterno}. La
 * disponibilidad de turnos (hoy en el monolito) pregunta qué horarios del día tiene ocupados el
 * profesional en su Google Calendar, y se responde desde la caché sincronizada, sin llamar a Google.
 */
@RestController
@RequestMapping("/internal/calendario")
public class InternoCalendarioController {

    /** Mismo formato que HorariosOcupadosCliente.Intervalo del monolito. */
    public record Intervalo(LocalTime inicio, LocalTime fin, boolean todoElDia) {}

    private final EventosExternos externos;

    public InternoCalendarioController(EventosExternos externos) {
        this.externos = externos;
    }

    @GetMapping("/profesionales/{id}/ocupado")
    public List<Intervalo> ocupado(@PathVariable("id") Long profesionalId, @RequestParam(name = "fecha") LocalDate fecha) {
        return externos.delDia(profesionalId, fecha).stream()
                // Los eventos marcados "disponible" en Google no bloquean la agenda.
                .filter(e -> !e.transparente())
                .map(e -> e.todoElDia()
                        ? new Intervalo(LocalTime.MIN, LocalTime.MAX, true)
                        // Un evento que empieza el día anterior o termina el siguiente ocupa el borde del día.
                        : new Intervalo(e.inicio().toLocalDate().isBefore(fecha) ? LocalTime.MIN : e.inicio().toLocalTime(),
                                e.fin().toLocalDate().isAfter(fecha) ? LocalTime.MAX : e.fin().toLocalTime(), false))
                .toList();
    }
}
