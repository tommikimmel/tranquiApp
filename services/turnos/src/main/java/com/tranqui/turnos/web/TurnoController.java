package com.tranqui.turnos.web;

import com.tranqui.turnos.agenda.Horarios;
import com.tranqui.turnos.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.turnos.seguridad.UsuarioJwt;
import com.tranqui.turnos.turnos.Estadisticas;
import com.tranqui.turnos.turnos.Gestion;
import com.tranqui.turnos.turnos.Listados;
import com.tranqui.turnos.turnos.Reservas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Las rutas de {@code TurnoController} del monolito, más "Mis turnos" del paciente ({@code ClinicalController}),
 * con las mismas respuestas y permisos.
 */
@RestController
@RequestMapping("/api")
public class TurnoController {

    private static final Set<String> MODALIDADES = Set.of("PRESENCIAL", "ONLINE");

    private final Horarios horarios;
    private final Reservas reservas;
    private final Gestion gestion;
    private final Listados listados;
    private final Estadisticas estadisticas;

    public TurnoController(Horarios horarios, Reservas reservas, Gestion gestion, Listados listados, Estadisticas estadisticas) {
        this.horarios = horarios;
        this.reservas = reservas;
        this.gestion = gestion;
        this.listados = listados;
        this.estadisticas = estadisticas;
    }

    @GetMapping("/medicos/stats")
    public ResponseEntity<Dtos.DashboardStatsDto> stats(
            HttpServletRequest request,
            @RequestParam(name = "periodo", defaultValue = "MENSUAL") String periodo) {
        String email = conRol(request, "PSIQUIATRA");
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(estadisticas.obtenerStats(email, periodo));
    }

    @GetMapping("/medicos/{medicoId}/turnos-disponibles")
    public List<LocalTime> turnosDisponibles(@PathVariable("medicoId") Long medicoId, @RequestParam("fecha") LocalDate fecha,
                                             @RequestParam("modalidad") String modalidad) {
        return horarios.disponibles(medicoId, fecha, modalidad(modalidad));
    }

    /** El conteo de la home: sin modalidad, la unión de las que ofrece cada profesional. */
    @GetMapping("/medicos/turnos-disponibles-conteo")
    public Map<Long, Integer> conteos(@RequestParam("medicoIds") List<Long> medicoIds, @RequestParam("fecha") LocalDate fecha,
                                      @RequestParam(name = "modalidad", required = false) String modalidad) {
        return horarios.conteos(medicoIds, fecha, modalidad != null ? modalidad(modalidad) : null);
    }

    @PostMapping("/turnos/reservar")
    public Dtos.TurnoResponseDto reservar(@RequestBody Dtos.ReservaTurnoDto dto) {
        return reservas.reservar(dto);
    }

    @GetMapping("/medicos/turnos/hoy")
    public ResponseEntity<List<Dtos.TurnoMedicoDto>> hoy(HttpServletRequest request) {
        String email = conRol(request, "PSIQUIATRA");
        return email == null ? prohibido() : ResponseEntity.ok(listados.deHoy(email));
    }

    @GetMapping("/medicos/turnos")
    public ResponseEntity<List<Dtos.TurnoMedicoDto>> todos(HttpServletRequest request) {
        String email = conRol(request, "PSIQUIATRA");
        return email == null ? prohibido() : ResponseEntity.ok(listados.todos(email));
    }

    @GetMapping("/pacientes/me/turnos")
    public ResponseEntity<List<Dtos.TurnoMedicoDto>> misTurnos(HttpServletRequest request) {
        String email = conRol(request, "PACIENTE");
        return email == null ? prohibido() : ResponseEntity.ok(listados.dePaciente(email));
    }

    @PostMapping("/turnos/{turnoId}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable("turnoId") Long turnoId, HttpServletRequest request) {
        String email = conRol(request, "PACIENTE", "PSIQUIATRA");
        if (email == null) {
            return prohibido();
        }
        gestion.cancelar(turnoId, email);
        return ResponseEntity.ok().build();
    }

    /** Sin sesión, con el token de la reserva: solo libera una reserva todavía sin pagar. */
    @PostMapping("/turnos/{turnoId}/abandonar-pago")
    public ResponseEntity<Void> abandonarPago(@PathVariable("turnoId") Long turnoId,
                                              @RequestParam(name = "token", required = false) String token) {
        try {
            gestion.abandonarPago(turnoId, token);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/turnos/{turnoId}/asistencia")
    public ResponseEntity<Void> asistencia(@PathVariable("turnoId") Long turnoId, @RequestParam("asistencia") String asistencia,
                                           HttpServletRequest request) {
        String email = conRol(request, "PSIQUIATRA");
        if (email == null) {
            return prohibido();
        }
        gestion.asistencia(turnoId, asistencia, email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/turnos/{turnoId}/documento-enviado")
    public ResponseEntity<Void> documentoEnviado(@PathVariable("turnoId") Long turnoId,
                                                 @RequestBody(required = false) Dtos.MarcarDocumentoEnviadoRequest body,
                                                 HttpServletRequest request) {
        String email = conRol(request, "PSIQUIATRA");
        if (email == null) {
            return prohibido();
        }
        gestion.documentoEnviado(turnoId, email, body != null ? body.archivoData() : null, body != null ? body.archivoNombre() : null);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/turnos/{turnoId}/reprogramar")
    public ResponseEntity<Void> reprogramar(@PathVariable("turnoId") Long turnoId, @RequestParam("fecha") String fecha,
                                            @RequestParam("hora") String hora, HttpServletRequest request) {
        String email = conRol(request, "PSIQUIATRA");
        if (email == null) {
            return prohibido();
        }
        gestion.reprogramar(turnoId, fecha, hora, email);
        return ResponseEntity.ok().build();
    }

    /** El botón "Confirmar asistencia" del mail de recordatorio (sin sesión, con el token del mail). */
    @GetMapping("/turnos/{turnoId}/confirmar-asistencia")
    public ResponseEntity<String> confirmarAsistencia(@PathVariable("turnoId") Long turnoId, @RequestParam("token") String token) {
        try {
            gestion.confirmarAsistencia(turnoId, token);
            return pagina(true, "¡Asistencia confirmada!", "Le avisamos al profesional que vas a estar en tu turno. ¡Te esperamos!");
        } catch (Exception e) {
            return pagina(false, "No pudimos confirmar tu turno", e.getMessage());
        }
    }

    /** El botón "No podré asistir" del mismo mail: cancela con la misma política de reembolso. */
    @GetMapping("/turnos/{turnoId}/no-asistira")
    public ResponseEntity<String> noAsistira(@PathVariable("turnoId") Long turnoId, @RequestParam("token") String token) {
        try {
            gestion.noAsistira(turnoId, token);
            return pagina(true, "Turno cancelado", "Avisamos al profesional que no vas a poder asistir. Gracias por avisarnos con anticipación.");
        } catch (Exception e) {
            return pagina(false, "No pudimos procesar tu solicitud", e.getMessage());
        }
    }

    /** El mail del usuario logueado si tiene alguno de esos roles; null si no (el monolito respondía 403). */
    private static String conRol(HttpServletRequest request, String... roles) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (usuario == null) {
            return null;
        }
        for (String rol : roles) {
            if (rol.equals(usuario.rol())) {
                return usuario.email();
            }
        }
        return null;
    }

    private static String modalidad(String valor) {
        if (!MODALIDADES.contains(valor)) {
            throw new IllegalArgumentException("Modalidad inválida: " + valor);
        }
        return valor;
    }

    private static <T> ResponseEntity<T> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /** La página que ve el paciente al tocar un botón del mail, igual que la del monolito. */
    private static ResponseEntity<String> pagina(boolean exito, String titulo, String mensaje) {
        String color = exito ? "#16a34a" : "#dc2626";
        String icono = exito
                ? "<svg width=\"44\" height=\"44\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><polyline points=\"16 9 10.5 15 8 12.5\"/></svg>"
                : "<svg width=\"44\" height=\"44\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"/><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/></svg>";
        String html = "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + titulo + "</title></head>"
                + "<body style=\"margin:0;background:#eef2f7;font-family:'Segoe UI',Helvetica,Arial,sans-serif;\">"
                + "<div style=\"max-width:420px;margin:64px auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:36px 32px;text-align:center;\">"
                + "<div style=\"font-size:20px;font-weight:700;color:#0f172a;margin-bottom:18px;\">Tranqui<span style=\"color:#009ee3;\">App</span></div>"
                + "<div style=\"margin-bottom:10px;\">" + icono + "</div>"
                + "<h1 style=\"font-size:19px;color:#0f172a;margin:0 0 10px;\">" + titulo + "</h1>"
                + "<p style=\"font-size:14px;color:#334155;line-height:1.6;margin:0;\">" + (mensaje != null ? HtmlUtils.htmlEscape(mensaje) : "") + "</p>"
                + "<p style=\"font-size:12px;color:#94a3b8;margin-top:24px;\">Ya podés cerrar esta ventana.</p>"
                + "</div></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
