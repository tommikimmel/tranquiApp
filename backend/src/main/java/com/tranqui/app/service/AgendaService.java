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

    private static final int DURACION_TURNO_MINUTOS = 45;

    public List<LocalTime> calcularBloquesDisponibles(List<Disponibilidad> disponibilidades, List<Turno> turnosExistentes, LocalDate fecha) {
        List<LocalTime> bloquesDisponibles = new ArrayList<>();
        int dayOfWeek = fecha.getDayOfWeek().getValue();

        for (Disponibilidad disp : disponibilidades) {
            // Only calculate slots for availability that matches the target date's day of week
            if (disp.getDiaSemana() != dayOfWeek) {
                continue;
            }

            LocalTime inicio = disp.getHoraInicio();
            LocalTime fin = disp.getHoraFin();

            while (inicio.plusMinutes(DURACION_TURNO_MINUTOS).isBefore(fin) || inicio.plusMinutes(DURACION_TURNO_MINUTOS).equals(fin)) {
                LocalTime finalBloque = inicio.plusMinutes(DURACION_TURNO_MINUTOS);
                
                // Verify if it overlaps with an existing booking
                boolean ocupado = comprobarChoqueTurno(inicio, finalBloque, turnosExistentes);
                if (!ocupado) {
                    bloquesDisponibles.add(inicio);
                }
                inicio = finalBloque;
            }
        }
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
