package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Turno;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgendaService {

    // Historical fixed defaults, kept only to back the legacy 3-arg overload below so callers
    // (and tests) that don't pass a médico's configured values keep seeing the exact same
    // behavior as before per-médico agenda settings existed.
    private static final int DURACION_TURNO_MINUTOS = 45;
    private static final int BLOQUE_AGENDA_MINUTOS = 60;

    /**
     * @deprecated kept for backward compatibility with callers that haven't been threaded
     * through with a médico's configured duración/intervalo yet. Prefer the overload that
     * takes duracionTurnoMinutos and intervaloEntreTurnosMinutos explicitly.
     */
    @Deprecated
    public List<LocalTime> calcularBloquesDisponibles(List<Disponibilidad> disponibilidades, List<Turno> turnosExistentes, LocalDate fecha) {
        return calcularBloquesDisponibles(disponibilidades, turnosExistentes, fecha,
                DURACION_TURNO_MINUTOS, BLOQUE_AGENDA_MINUTOS - DURACION_TURNO_MINUTOS);
    }

    /**
     * @param duracionTurnoMinutos how long each bookable slot lasts — used both as the step
     *                             length checked for collisions against existing bookings.
     * @param intervaloEntreTurnosMinutos gap left between the end of one candidate slot and
     *                             the start of the next; the walk step through each
     *                             availability window is duracionTurnoMinutos + intervaloEntreTurnosMinutos.
     */
    public List<LocalTime> calcularBloquesDisponibles(List<Disponibilidad> disponibilidades, List<Turno> turnosExistentes, LocalDate fecha,
            int duracionTurnoMinutos, int intervaloEntreTurnosMinutos) {
        List<LocalTime> bloquesDisponibles = new ArrayList<>();
        int dayOfWeek = fecha.getDayOfWeek().getValue();
        int pasoMinutos = duracionTurnoMinutos + intervaloEntreTurnosMinutos;

        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Argentina/Cordoba"));
        LocalTime ahora = LocalTime.now(java.time.ZoneId.of("America/Argentina/Cordoba"));

        for (Disponibilidad disp : disponibilidades) {
            // Only calculate slots for availability that matches the target date's day of week
            if (disp.getDiaSemana() != dayOfWeek) {
                continue;
            }

            LocalTime inicio = disp.getHoraInicio();
            LocalTime fin = disp.getHoraFin();

            // Fit-check uses only duracionTurnoMinutos (does THIS candidate's own slot fit in
            // the window?), matching the frontend grid's computeCandidateStarts (`cursor + dur
            // <= limit`) — the grid never requires trailing room for an intervalo after the
            // last slot, so a franja exactly duracionTurnoMinutos long (a single selected slot,
            // common with narrow modalidad-specific agendas) must still produce that one slot.
            // Only the STEP between candidates uses duracion+intervalo.
            while (inicio.plusMinutes(duracionTurnoMinutos).isBefore(fin) || inicio.plusMinutes(duracionTurnoMinutos).equals(fin)) {
                // If the target date is today, ensure the slot starts in the future
                boolean enElPasado = fecha.equals(hoy) && inicio.isBefore(ahora);

                // Verify if it overlaps with an existing booking
                boolean ocupado = comprobarChoqueTurno(inicio, inicio.plusMinutes(duracionTurnoMinutos), turnosExistentes);
                if (!ocupado && !enElPasado) {
                    bloquesDisponibles.add(inicio);
                }
                inicio = inicio.plusMinutes(pasoMinutos);
            }
        }
        bloquesDisponibles.sort(java.util.Comparator.naturalOrder());
        return bloquesDisponibles;
    }

    private boolean comprobarChoqueTurno(LocalTime inicio, LocalTime fin, List<Turno> turnosExistentes) {
        return turnosExistentes.stream().anyMatch(turno -> {
            LocalTime tInicio = turno.getHoraInicio();
            LocalTime tFin = turno.getHoraFin();
            return (inicio.isBefore(tFin) && fin.isAfter(tInicio));
        });
    }
}
