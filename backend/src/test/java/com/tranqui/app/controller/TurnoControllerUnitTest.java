package com.tranqui.app.controller;

import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.service.TurnoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoControllerUnitTest {

    @Mock private TurnoService turnoService;

    @InjectMocks
    private TurnoController controller;

    private final UserDetails medico = new User("dra@mail.com", "x", Collections.emptyList());

    @Test
    void obtenerTurnosDisponibles_shouldDelegateToService() {
        LocalDate fecha = LocalDate.now();
        when(turnoService.obtenerHorariosDisponibles(1L, fecha, Modalidad.ONLINE)).thenReturn(List.of(LocalTime.of(9, 0)));

        ResponseEntity<List<LocalTime>> response = controller.obtenerTurnosDisponibles(1L, fecha, Modalidad.ONLINE);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void reservarTurno_shouldDelegateToService() {
        ReservaTurnoDto dto = ReservaTurnoDto.builder().medicoId(1L).build();
        TurnoResponseDto expected = TurnoResponseDto.builder().turnoId(10L).build();
        when(turnoService.reservarTurno(dto)).thenReturn(expected);

        ResponseEntity<TurnoResponseDto> response = controller.reservarTurno(dto);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(expected, response.getBody());
    }

    @Test
    void obtenerTurnosDeHoy_shouldDelegateToService() {
        when(turnoService.obtenerTurnosDeHoy("dra@mail.com")).thenReturn(List.of());

        assertEquals(200, controller.obtenerTurnosDeHoy(medico).getStatusCodeValue());
        verify(turnoService).obtenerTurnosDeHoy("dra@mail.com");
    }

    @Test
    void obtenerTodosTurnos_shouldDelegateToService() {
        when(turnoService.obtenerTodosTurnos("dra@mail.com")).thenReturn(List.of());

        assertEquals(200, controller.obtenerTodosTurnos(medico).getStatusCodeValue());
        verify(turnoService).obtenerTodosTurnos("dra@mail.com");
    }

    @Test
    void cancelarTurno_shouldDelegateToService() {
        ResponseEntity<Void> response = controller.cancelarTurno(5L, medico);

        assertEquals(200, response.getStatusCodeValue());
        verify(turnoService).cancelarTurno(5L, "dra@mail.com");
    }

    @Test
    void actualizarAsistencia_shouldDelegateToService() {
        ResponseEntity<Void> response = controller.actualizarAsistencia(5L, "LLEGO");

        assertEquals(200, response.getStatusCodeValue());
        verify(turnoService).actualizarAsistencia(5L, "LLEGO");
    }

    @Test
    void reprogramarTurno_shouldDelegateToService() {
        ResponseEntity<Void> response = controller.reprogramarTurno(5L, "2026-01-01", "10:00");

        assertEquals(200, response.getStatusCodeValue());
        verify(turnoService).reprogramarTurno(5L, "2026-01-01", "10:00");
    }
}
