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

@SpringBootTest
class AgendaServiceTest {

    @Autowired
    private AgendaService agendaService;

    @Test
    void shouldGenerateCorrectBlocksOf45Minutes() {
        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(10, 30)) // Debería dar exactamente 2 bloques (09:00 y 09:45)
                .diaSemana(LocalDate.now().getDayOfWeek().getValue())
                .build();

        List<Disponibilidad> disponibilidades = List.of(disp);
        List<Turno> turnosExistentes = new ArrayList<>();

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, LocalDate.now());
        
        assertEquals(2, bloques.size());
        assertEquals(LocalTime.of(9, 0), bloques.get(0));
        assertEquals(LocalTime.of(9, 45), bloques.get(1));
    }

    @Test
    void whenTurnoExists_shouldNotGenerateThatBlock() {
        Disponibilidad disp = Disponibilidad.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(10, 30))
                .diaSemana(LocalDate.now().getDayOfWeek().getValue())
                .build();

        // Existing booking from 09:00 to 09:45
        Turno turno = Turno.builder()
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(9, 45))
                .build();

        List<Disponibilidad> disponibilidades = List.of(disp);
        List<Turno> turnosExistentes = List.of(turno);

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, LocalDate.now());
        
        // 09:00 block should be filtered out, leaving only 09:45 block
        assertEquals(1, bloques.size());
        assertEquals(LocalTime.of(9, 45), bloques.get(0));
    }
}
