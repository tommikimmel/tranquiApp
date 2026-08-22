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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam com.tranqui.app.model.Modalidad modalidad) {
        return ResponseEntity.ok(turnoService.obtenerHorariosDisponibles(medicoId, fecha, modalidad));
    }

    /**
     * Batched counterpart used by the public homepage's date filter: instead of one request per
     * visible professional, the frontend sends every médicoId once and gets back a count per id.
     * modalidad is optional here — the homepage shows this badge before the patient has picked
     * a modalidad, so it counts the union of whatever the médico offers (see TurnoService).
     */
    @GetMapping("/medicos/turnos-disponibles-conteo")
    public ResponseEntity<java.util.Map<Long, Integer>> obtenerConteosDisponibilidad(
            @RequestParam List<Long> medicoIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) com.tranqui.app.model.Modalidad modalidad) {
        return ResponseEntity.ok(turnoService.obtenerConteosDisponibilidad(medicoIds, fecha, modalidad));
    }

    @PostMapping("/turnos/reservar")
    public ResponseEntity<TurnoResponseDto> reservarTurno(@RequestBody ReservaTurnoDto dto) {
        return ResponseEntity.ok(turnoService.reservarTurno(dto));
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
    public ResponseEntity<Void> cancelarTurno(
            @PathVariable Long turnoId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        turnoService.cancelarTurno(turnoId, userDetails != null ? userDetails.getUsername() : null);
        return ResponseEntity.ok().build();
    }

    /**
     * Public counterpart of /cancelar, reachable without login since booking itself doesn't
     * require an account. Only releases turnos still in PENDIENTE_PAGO (see
     * TurnoService#abandonarReservaPendiente) so it can't be used to cancel a real, paid
     * appointment by guessing an id — lets a patient who backs out of payment immediately
     * free their slot instead of blocking themselves for 5 minutes until the cleanup job runs.
     */
    @PostMapping("/turnos/{turnoId}/abandonar-pago")
    public ResponseEntity<Void> abandonarReservaPendiente(@PathVariable Long turnoId) {
        turnoService.abandonarReservaPendiente(turnoId);
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

    @PostMapping("/turnos/{turnoId}/documento-enviado")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Void> marcarDocumentoEnviado(
            @PathVariable Long turnoId,
            @RequestBody(required = false) com.tranqui.app.model.dto.MarcarDocumentoEnviadoRequest body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        turnoService.marcarDocumentoEnviado(
                turnoId,
                userDetails.getUsername(),
                body != null ? body.getArchivoData() : null,
                body != null ? body.getArchivoNombre() : null);
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
