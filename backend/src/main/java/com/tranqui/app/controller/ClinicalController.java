package com.tranqui.app.controller;

import com.tranqui.app.model.InformeClinico;
import com.tranqui.app.model.Notificacion;
import com.tranqui.app.model.SeguimientoDiario;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.service.ClinicalService;
import com.tranqui.app.service.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ClinicalController {

    @Autowired
    private ClinicalService clinicalService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private com.tranqui.app.service.TurnoService turnoService;

    // --- Notificaciones ---

    @GetMapping("/notificaciones")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<List<Notificacion>> obtenerNotificaciones(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(notificacionService.obtenerNotificaciones(userDetails.getUsername()));
    }

    @PostMapping("/notificaciones/marcar-leidas")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<Void> marcarLeidas(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        notificacionService.marcarTodasComoLeidas(userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    // --- Pacientes ---

    @GetMapping("/pacientes/atendidos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<PacienteDto>> obtenerPacientesAtendidos(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.obtenerPacientesAtendidos(userDetails.getUsername()));
    }

    // --- Seguimiento Diario ---

    @GetMapping("/pacientes/{pacienteId}/seguimientos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<SeguimientoDiario>> obtenerSeguimientos(
            @PathVariable Long pacienteId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.obtenerSeguimientos(pacienteId, userDetails.getUsername()));
    }

    @PostMapping("/pacientes/{pacienteId}/seguimientos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<SeguimientoDiario> crearSeguimiento(
            @PathVariable Long pacienteId,
            @RequestBody SeguimientoDiario entry,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.guardarSeguimiento(pacienteId, userDetails.getUsername(), entry));
    }

    // --- Informes Clínicos ---

    @GetMapping("/pacientes/{pacienteId}/informes")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<InformeClinico>> obtenerInformes(
            @PathVariable Long pacienteId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.obtenerInformes(pacienteId, userDetails.getUsername()));
    }

    @PostMapping("/pacientes/{pacienteId}/informes")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<InformeClinico> crearInforme(
            @PathVariable Long pacienteId,
            @RequestParam String tipoInforme,
            @RequestParam(required = false) String planTrabajo,
            @RequestParam(required = false) String contenido,
            @RequestParam(required = false) String nombreArchivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.guardarInforme(
                userDetails.getUsername(),
                pacienteId,
                tipoInforme,
                planTrabajo,
                contenido,
                nombreArchivo
        ));
    }

    // --- Patient Portal Endpoints ---

    @GetMapping("/pacientes/me/turnos")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<List<com.tranqui.app.model.dto.TurnoMedicoDto>> obtenerMisTurnos(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(turnoService.obtenerTurnosPaciente(userDetails.getUsername()));
    }

    @GetMapping("/pacientes/me/seguimientos")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<List<SeguimientoDiario>> obtenerMisSeguimientos(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.obtenerSeguimientosPaciente(userDetails.getUsername()));
    }

    @PostMapping("/pacientes/me/seguimientos")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<SeguimientoDiario> crearMiSeguimiento(
            @RequestBody SeguimientoDiario entry,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.guardarSeguimientoPaciente(userDetails.getUsername(), entry));
    }

    @GetMapping("/pacientes/me/informes")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<List<InformeClinico>> obtenerMisInformes(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.obtenerInformesPaciente(userDetails.getUsername()));
    }

    @PutMapping("/pacientes/{pacienteId}")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<PacienteDto> actualizarPaciente(
            @PathVariable Long pacienteId,
            @RequestBody PacienteDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(clinicalService.actualizarPaciente(pacienteId, userDetails.getUsername(), dto));
    }
}
