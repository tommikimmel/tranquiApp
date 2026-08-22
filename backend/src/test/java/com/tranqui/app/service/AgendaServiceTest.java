package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Turno;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AgendaServiceTest {

    // Same clock source AgendaService itself uses for "is this slot in the past" — computing
    // boundaries against plain LocalTime.now() (system default zone) here would be flaky
    // whenever the test host's default zone differs from Argentina.
    private static final java.time.ZoneId ZONE_CBA = java.time.ZoneId.of("America/Argentina/Cordoba");

    @Autowired
    private AgendaService agendaService;

    @Test
    void shouldGenerateCorrectHourlyBlocks() {
        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(11, 0)) // Debería dar exactamente 2 bloques (09:00 y 10:00)
                .diaSemana(LocalDate.now().plusDays(1).getDayOfWeek().getValue())
                .build();

        List<Disponibilidad> disponibilidades = List.of(disp);
        List<Turno> turnosExistentes = new ArrayList<>();

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, LocalDate.now().plusDays(1));
        
        assertEquals(2, bloques.size());
        assertEquals(LocalTime.of(9, 0), bloques.get(0));
        assertEquals(LocalTime.of(10, 0), bloques.get(1));
    }

    @Test
    void whenTurnoExists_shouldNotGenerateThatBlock() {
        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(11, 0))
                .diaSemana(LocalDate.now().plusDays(1).getDayOfWeek().getValue())
                .build();

        // Existing booking from 09:00 to 09:45
        Turno turno = Turno.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 45))
                .build();

        List<Disponibilidad> disponibilidades = List.of(disp);
        List<Turno> turnosExistentes = List.of(turno);

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, LocalDate.now().plusDays(1));
        
        // 09:00 block should be filtered out, leaving only 10:00 block
        assertEquals(1, bloques.size());
        assertEquals(LocalTime.of(10, 0), bloques.get(0));
    }

    // --- intervaloEntreTurnosMinutos boundary (fit-check uses only duracion, not paso) --------

    @Test
    void whenDisponibilidadWindowExactlyFitsOneSlot_thenIncludesIt() {
        // Window is exactly one turno-length long — no room for a trailing intervalo after it.
        // The fit-check only requires THIS candidate's own slot to fit (inicio + duracion <=
        // fin), not room for the next step, so a single slot must still come out.
        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 45))
                .diaSemana(LocalDate.now().plusDays(1).getDayOfWeek().getValue())
                .build();

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(
                List.of(disp), new ArrayList<>(), LocalDate.now().plusDays(1), 45, 15);

        assertEquals(1, bloques.size());
        assertEquals(LocalTime.of(9, 0), bloques.get(0));
    }

    @Test
    void whenDisponibilidadWindowOneMinuteShortOfASlot_thenExcludesIt() {
        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 44)) // one minute short of fitting a 45-minute turno
                .diaSemana(LocalDate.now().plusDays(1).getDayOfWeek().getValue())
                .build();

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(
                List.of(disp), new ArrayList<>(), LocalDate.now().plusDays(1), 45, 15);

        assertTrue(bloques.isEmpty());
    }

    // --- "hoy, horario ya pasado" filter -------------------------------------------------------

    @Test
    void whenFechaIsTodayAndWindowAlreadyEnded_thenAllItsSlotsAreExcluded() {
        LocalTime ahora = LocalTime.now(ZONE_CBA);
        LocalTime inicio = ahora.minusHours(3);
        LocalTime fin = ahora.minusHours(1);
        if (fin.isAfter(ahora) || inicio.isAfter(fin)) {
            // Wrapped past midnight (test ran very early in the morning) — fall back to a
            // window that's still guaranteed to be entirely in the past for "today".
            inicio = LocalTime.of(0, 1);
            fin = LocalTime.of(2, 1);
        }

        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(inicio)
                .horaFin(fin)
                .diaSemana(LocalDate.now(ZONE_CBA).getDayOfWeek().getValue())
                .build();

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(
                List.of(disp), new ArrayList<>(), LocalDate.now(ZONE_CBA), 45, 15);

        assertTrue(bloques.isEmpty());
    }

    // --- Filtering by día de semana across multiple Disponibilidad rows -----------------------

    @Test
    void whenMultipleDisponibilidadesAcrossDifferentDaysOfWeek_thenOnlyMatchingDayIsUsed() {
        LocalDate objetivo = LocalDate.now().plusDays(7);
        int diaObjetivo = objetivo.getDayOfWeek().getValue();
        int diaOtro = (diaObjetivo % 7) + 1; // guaranteed a different ISO day-of-week (1..7)

        Disponibilidad dispCorrecta = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(10, 0))
                .diaSemana(diaObjetivo)
                .build();
        Disponibilidad dispOtroDia = Disponibilidad.builder()
                .horaInicio(LocalTime.of(14, 0)).horaFin(LocalTime.of(15, 0))
                .diaSemana(diaOtro)
                .build();

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(
                List.of(dispCorrecta, dispOtroDia), new ArrayList<>(), objetivo);

        assertEquals(1, bloques.size());
        assertEquals(LocalTime.of(9, 0), bloques.get(0));
    }
}
