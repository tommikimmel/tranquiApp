package com.tranqui.profesionales.clinica;

import com.tranqui.profesionales.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.profesionales.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rutas de historia clínica, ficha del paciente y lista de pacientes (Fase 7, H2b).
 * Mismos paths, verbos HTTP y permisos que respondía {@code ClinicalController} del monolito
 * (menos /me/turnos que es de turnos, y /notificaciones que es de notificaciones).
 */
@RestController
@RequestMapping("/api")
public class HistoriaClinicaController {

    private static final String ROL_PROFESIONAL = "PSIQUIATRA";
    private static final String ROL_PACIENTE = "PACIENTE";

    private final HistoriaClinicaService clinicalService;

    public HistoriaClinicaController(HistoriaClinicaService clinicalService) {
        this.clinicalService = clinicalService;
    }

    // --- Pacientes ---

    @GetMapping("/pacientes/atendidos")
    public ResponseEntity<List<Dtos.PacienteDto>> obtenerPacientesAtendidos(HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.obtenerPacientesAtendidos(email));
    }

    // --- Seguimiento Diario ---

    @GetMapping("/pacientes/{pacienteId}/seguimientos")
    public ResponseEntity<List<Dtos.SeguimientoResponse>> obtenerSeguimientos(
            @PathVariable("pacienteId") Long pacienteId,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.obtenerSeguimientos(pacienteId, email));
    }

    @PostMapping("/pacientes/{pacienteId}/seguimientos")
    public ResponseEntity<Dtos.SeguimientoResponse> crearSeguimiento(
            @PathVariable("pacienteId") Long pacienteId,
            @RequestBody Dtos.SeguimientoRequest entry,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.guardarSeguimiento(pacienteId, email, entry));
    }

    // --- Informes Clínicos ---

    @GetMapping("/pacientes/{pacienteId}/informes")
    public ResponseEntity<List<Dtos.InformeClinicoResponse>> obtenerInformes(
            @PathVariable("pacienteId") Long pacienteId,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.obtenerInformes(pacienteId, email));
    }

    @PostMapping("/pacientes/{pacienteId}/informes")
    public ResponseEntity<Dtos.InformeClinicoResponse> crearInforme(
            @PathVariable("pacienteId") Long pacienteId,
            @RequestBody Dtos.InformeClinicoRequest dto,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.guardarInforme(email, pacienteId, dto));
    }

    // --- Portal del Paciente ---

    @GetMapping("/pacientes/me/seguimientos")
    public ResponseEntity<List<Dtos.SeguimientoResponse>> obtenerMisSeguimientos(HttpServletRequest request) {
        String email = paciente(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.obtenerSeguimientosPaciente(email));
    }

    @PostMapping("/pacientes/me/seguimientos")
    public ResponseEntity<Dtos.SeguimientoResponse> crearMiSeguimiento(
            @RequestBody Dtos.SeguimientoRequest entry,
            HttpServletRequest request) {
        String email = paciente(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.guardarSeguimientoPaciente(email, entry));
    }

    @GetMapping("/pacientes/me/informes")
    public ResponseEntity<List<Dtos.InformeClinicoResponse>> obtenerMisInformes(HttpServletRequest request) {
        String email = paciente(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.obtenerInformesPaciente(email));
    }

    // --- Ficha del Paciente ---

    @PutMapping("/pacientes/{pacienteId}")
    public ResponseEntity<Dtos.PacienteDto> actualizarPaciente(
            @PathVariable("pacienteId") Long pacienteId,
            @RequestBody Dtos.PacienteDto dto,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.actualizarPaciente(pacienteId, email, dto));
    }

    // --- Anulación y Corrección por Anexo ---

    @DeleteMapping("/pacientes/{pacienteId}/informes/{informeId}")
    public ResponseEntity<Void> eliminarInforme(
            @PathVariable("pacienteId") Long pacienteId,
            @PathVariable("informeId") Long informeId,
            @RequestParam(value = "motivo", required = false, defaultValue = "No especificado") String motivo,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        clinicalService.eliminarInforme(informeId, email, motivo);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pacientes/{pacienteId}/informes/{informeId}")
    public ResponseEntity<Dtos.InformeClinicoResponse> editarInforme(
            @PathVariable("pacienteId") Long pacienteId,
            @PathVariable("informeId") Long informeId,
            @RequestBody Dtos.InformeClinicoRequest dto,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(clinicalService.editarInforme(informeId, email, dto));
    }

    @DeleteMapping("/pacientes/{pacienteId}/seguimientos/{seguimientoId}")
    public ResponseEntity<Void> eliminarSeguimiento(
            @PathVariable("pacienteId") Long pacienteId,
            @PathVariable("seguimientoId") Long seguimientoId,
            @RequestParam(value = "motivo", required = false, defaultValue = "No especificado") String motivo,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        clinicalService.eliminarSeguimiento(seguimientoId, email, motivo);
        return ResponseEntity.ok().build();
    }

    private static String profesional(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        return usuario != null && ROL_PROFESIONAL.equals(usuario.rol()) ? usuario.email() : null;
    }

    private static String paciente(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        return usuario != null && ROL_PACIENTE.equals(usuario.rol()) ? usuario.email() : null;
    }

    private static <T> ResponseEntity<T> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
