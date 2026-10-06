package com.tranqui.app.controller;

import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.service.TurnoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<Void> abandonarReservaPendiente(@PathVariable Long turnoId,
                                                          @RequestParam(required = false) String token) {
        try {
            turnoService.abandonarReservaPendiente(turnoId, token);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
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

    /**
     * Público (sin login), llegado desde el botón "Confirmar asistencia" del mail de recordatorio
     * enviado 2 días antes del turno (ver TurnoService#enviarRecordatoriosConfirmacionAsistencia).
     * El token prueba que el click vino de ese mail en vez de alguien adivinando el turnoId.
     */
    @GetMapping("/turnos/{turnoId}/confirmar-asistencia")
    public ResponseEntity<String> confirmarAsistencia(@PathVariable Long turnoId, @RequestParam String token) {
        try {
            turnoService.confirmarAsistenciaPaciente(turnoId, token);
            return paginaHtml(true, "¡Asistencia confirmada!", "Le avisamos al profesional que vas a estar en tu turno. ¡Te esperamos!");
        } catch (Exception e) {
            return paginaHtml(false, "No pudimos confirmar tu turno", e.getMessage());
        }
    }

    /**
     * Público (sin login), contraparte del botón "No podré asistir" del mismo mail — cancela el
     * turno con la misma lógica (y política de reembolso) que una cancelación normal del paciente.
     */
    @GetMapping("/turnos/{turnoId}/no-asistira")
    public ResponseEntity<String> noAsistira(@PathVariable Long turnoId, @RequestParam String token) {
        try {
            turnoService.marcarNoAsistiraPorToken(turnoId, token);
            return paginaHtml(true, "Turno cancelado", "Avisamos al profesional que no vas a poder asistir. Gracias por avisarnos con anticipación.");
        } catch (Exception e) {
            return paginaHtml(false, "No pudimos procesar tu solicitud", e.getMessage());
        }
    }

    // Página HTML mínima y con la misma identidad visual que los mails de Tranqui App — es lo que
    // ve el paciente al tocar un botón del correo, no una API pensada para ser consumida por el
    // frontend.
    private ResponseEntity<String> paginaHtml(boolean exito, String titulo, String mensaje) {
        String color = exito ? "#16a34a" : "#dc2626";
        // Ícono SVG inline (check o alerta): esta página se abre en el navegador, así que SVG se
        // ve bien — a diferencia de los mails, donde los clientes de correo lo bloquean.
        String icono = exito
                ? "<svg width=\"44\" height=\"44\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><polyline points=\"16 9 10.5 15 8 12.5\"/></svg>"
                : "<svg width=\"44\" height=\"44\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"/><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/></svg>";
        String html = "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>" + titulo + "</title></head>" +
                "<body style=\"margin:0;background:#eef2f7;font-family:'Segoe UI',Helvetica,Arial,sans-serif;\">" +
                "<div style=\"max-width:420px;margin:64px auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:36px 32px;text-align:center;\">" +
                "<div style=\"font-size:20px;font-weight:700;color:#0f172a;margin-bottom:18px;\">Tranqui<span style=\"color:#009ee3;\">App</span></div>" +
                "<div style=\"margin-bottom:10px;\">" + icono + "</div>" +
                "<h1 style=\"font-size:19px;color:#0f172a;margin:0 0 10px;\">" + titulo + "</h1>" +
                "<p style=\"font-size:14px;color:#334155;line-height:1.6;margin:0;\">" + (mensaje != null ? org.springframework.web.util.HtmlUtils.htmlEscape(mensaje) : "") + "</p>" +
                "<p style=\"font-size:12px;color:#94a3b8;margin-top:24px;\">Ya podés cerrar esta ventana.</p>" +
                "</div></body></html>";
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_HTML).body(html);
    }
}
