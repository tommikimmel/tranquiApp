package com.tranqui.app.controller;

import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.service.TurnoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
}
