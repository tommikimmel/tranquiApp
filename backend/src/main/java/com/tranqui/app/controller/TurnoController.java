package com.tranqui.app.controller;

import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.service.TurnoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TurnoController {

    @Autowired
    private TurnoService turnoService;

    @GetMapping("/medicos/{medicoId}/turnos-disponibles")
    public ResponseEntity<List<LocalTime>> obtenerTurnosDisponibles(
            @PathVariable Long medicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(turnoService.obtenerHorariosDisponibles(medicoId, fecha));
    }

    @PostMapping("/turnos/reservar")
    public ResponseEntity<TurnoResponseDto> reservarTurno(@RequestBody ReservaTurnoDto dto) {
        return ResponseEntity.ok(turnoService.reservarTurno(dto));
    }

    @GetMapping("/turnos/check-first-consultation")
    public ResponseEntity<Boolean> isFirstConsultation(@RequestParam String email) {
        return ResponseEntity.ok(turnoService.esPrimeraConsulta(email));
    }

    @GetMapping("/medicos/turnos/hoy")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.TurnoMedicoDto>> obtenerTurnosDeHoy(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        return ResponseEntity.ok(turnoService.obtenerTurnosDeHoy(userDetails.getUsername()));
    }

    @GetMapping("/medicos/turnos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.TurnoMedicoDto>> obtenerTodosTurnos(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        return ResponseEntity.ok(turnoService.obtenerTodosTurnos(userDetails.getUsername()));
    }

    @PostMapping("/turnos/{turnoId}/cancelar")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<Void> cancelarTurno(@PathVariable Long turnoId) {
        turnoService.cancelarTurno(turnoId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/turnos/{turnoId}/asistencia")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Void> actualizarAsistencia(
            @PathVariable Long turnoId,
            @RequestParam String asistencia) {
        turnoService.actualizarAsistencia(turnoId, asistencia);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/turnos/{turnoId}/reprogramar")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Void> reprogramarTurno(
            @PathVariable Long turnoId,
            @RequestParam String fecha,
            @RequestParam String hora) {
        turnoService.reprogramarTurno(turnoId, fecha, hora);
        return ResponseEntity.ok().build();
    }
}
