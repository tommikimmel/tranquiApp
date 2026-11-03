package com.tranqui.calendario.web;

import com.tranqui.calendario.agenda.AgendaTurnos;
import com.tranqui.calendario.agenda.Canales;
import com.tranqui.calendario.agenda.Sincronizacion;
import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.calendario.google.Cifrado;
import com.tranqui.calendario.google.TokensGoogle;
import com.tranqui.calendario.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.calendario.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Las rutas de Google Calendar del panel del profesional, con las mismas URLs, roles, JSON y
 * redirecciones que tenían en MedicoController del monolito. El frontend no cambia.
 */
@RestController
@RequestMapping("/api/medicos/google-calendar")
public class GoogleCalendarController {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarController.class);

    /** Mismo formato que EventoExternoDto del monolito. */
    public record EventoExternoDto(String id, String title, String fecha, String hour, String endHour, boolean allDay) {}

    private final Participantes participantes;
    private final CuentasGoogle cuentas;
    private final EventosExternos externos;
    private final TokensGoogle tokens;
    private final Cifrado cifrado;
    private final Canales canales;
    private final Sincronizacion sincronizacion;
    private final AgendaTurnos agenda;
    private final boolean simulado;
    private final String frontendUrl;

    public GoogleCalendarController(Participantes participantes, CuentasGoogle cuentas, EventosExternos externos,
                                    TokensGoogle tokens, Cifrado cifrado, Canales canales, Sincronizacion sincronizacion,
                                    AgendaTurnos agenda,
                                    @Value("${app.local-simulations:false}") boolean simulacionesLocales,
                                    @Value("${google.calendar.enabled:false}") boolean googleHabilitado,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.participantes = participantes;
        this.cuentas = cuentas;
        this.externos = externos;
        this.tokens = tokens;
        this.cifrado = cifrado;
        this.canales = canales;
        this.sincronizacion = sincronizacion;
        this.agenda = agenda;
        // Solo en local (perfil "local") y sin Google real, como en el monolito.
        this.simulado = simulacionesLocales && !googleHabilitado;
        this.frontendUrl = frontendUrl;
    }

    /** El profesional autenticado (rol PSIQUIATRA, como el @PreAuthorize del monolito); si no, 403. */
    private Long profesional(HttpServletRequest pedido) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) pedido.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (usuario == null || !"PSIQUIATRA".equals(usuario.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return participantes.porEmail(usuario.email()).map(Participantes.Participante::id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    @GetMapping("/status")
    public Map<String, Object> estado(HttpServletRequest pedido) {
        return Map.of("connected", cuentas.buscar(profesional(pedido)).isPresent(), "simulated", simulado);
    }

    /** Solo entorno local: marca Google Calendar como vinculado sin pasar por el OAuth de Google. */
    @PostMapping("/connect-simulado")
    public ResponseEntity<?> conectarSimulado(HttpServletRequest pedido) {
        Long profesionalId = profesional(pedido);
        if (!simulado) {
            return ResponseEntity.status(403).body("La simulación de Google Calendar solo está disponible en el entorno local.");
        }
        if (cuentas.buscar(profesionalId).isEmpty()) {
            cuentas.guardar(new CuentasGoogle.Cuenta(profesionalId, null, null, null, null, null, null, null, null, false));
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/connect")
    public Map<String, String> conectar(HttpServletRequest pedido) {
        return Map.of("url", tokens.urlDeConexion(profesional(pedido)));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> desvincular(HttpServletRequest pedido) {
        Long profesionalId = profesional(pedido);
        canales.detener(profesionalId);
        // Antes de borrar la cuenta: necesita su access token para borrar los eventos de la app.
        agenda.eliminarEventosDeLaApp(profesionalId);
        cuentas.borrar(profesionalId);
        // Sin la caché, el calendario no sigue mostrando eventos de una cuenta desvinculada.
        externos.borrarDeProfesional(profesionalId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/eventos")
    public List<EventoExternoDto> eventos(HttpServletRequest pedido) {
        return externos.deProfesional(profesional(pedido)).stream()
                .map(e -> new EventoExternoDto(e.googleEventId(), e.titulo(), e.inicio().toLocalDate().toString(),
                        String.format("%02d:%02d", e.inicio().getHour(), e.inicio().getMinute()),
                        String.format("%02d:%02d", e.fin().getHour(), e.fin().getMinute()), e.todoElDia()))
                .toList();
    }

    /**
     * Aviso de Google de un cambio en el calendario: sin cuerpo, solo encabezados. Siempre 200: Google
     * reintenta los errores y termina dando de baja el canal.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Goog-Channel-ID", required = false) String channelId,
            @RequestHeader(value = "X-Goog-Resource-ID", required = false) String resourceId,
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String estado,
            @RequestHeader(value = "X-Goog-Channel-Token", required = false) String token) {
        if ("sync".equals(estado)) {
            return ResponseEntity.ok().build();
        }
        Long profesionalId = canales.profesionalDelToken(token);
        if (profesionalId == null) {
            return ResponseEntity.ok().build();
        }
        cuentas.buscar(profesionalId).ifPresent(cuenta -> {
            if (channelId == null || !channelId.equals(cuenta.watchChannelId())
                    || resourceId == null || !resourceId.equals(cuenta.watchResourceId())) {
                return; // canal viejo, ya renovado o detenido
            }
            try {
                sincronizacion.incremental(profesionalId);
            } catch (Exception e) {
                log.error("Fallo al procesar el webhook de Google del profesional {}", profesionalId, e);
            }
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(name = "code", required = false) String codigo,
                         @RequestParam(name = "state", required = false) String state,
                         @RequestParam(name = "error", required = false) String error,
                         HttpServletResponse respuesta) throws IOException {
        if (error != null) {
            respuesta.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=" + error);
            return;
        }
        Long profesionalId = cifrado.verificar(state);
        if (profesionalId == null || codigo == null) {
            respuesta.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=invalid_state");
            return;
        }
        try {
            tokens.conectar(profesionalId, codigo);
            canales.registrar(profesionalId);
            // La caché de eventos se llena enseguida, no en la próxima pasada (hasta 5 minutos).
            try {
                sincronizacion.completa(profesionalId);
            } catch (Exception e) {
                log.error("Fallo al sincronizar Google Calendar tras vincular al profesional {}", profesionalId, e);
            }
            respuesta.sendRedirect(frontendUrl + "/?googleCalendar=success");
        } catch (Exception e) {
            log.error("Error al procesar el callback de OAuth de Google Calendar: {}", e.getMessage());
            respuesta.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=exchange_failed");
        }
    }
}
