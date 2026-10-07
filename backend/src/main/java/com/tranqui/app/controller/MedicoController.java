package com.tranqui.app.controller;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.MedicoDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.MedicoService;
import com.tranqui.app.service.MercadoPagoOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private static final Logger log = LoggerFactory.getLogger(MedicoController.class);

    @Autowired
    private MedicoService medicoService;

    @Autowired
    private com.tranqui.app.service.DisponibilidadService disponibilidadService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MercadoPagoOAuthService mercadoPagoOAuthService;

    @Autowired
    private com.tranqui.app.service.GoogleCalendarOAuthService googleCalendarOAuthService;

    @Autowired
    private com.tranqui.app.service.GoogleCalendarSyncService googleCalendarSyncService;

    @Autowired
    private com.tranqui.app.service.GoogleCalendarWatchService googleCalendarWatchService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${mercadopago.enabled:false}")
    private boolean mercadoPagoEnabled;

    @Value("${google.calendar.enabled:false}")
    private boolean googleCalendarEnabled;

    // Solo true en el perfil "local" (application-local.yml): habilita simular integraciones que
    // en producción requieren OAuth real. Default false, así nunca se activa en producción.
    @Value("${app.local-simulations:false}")
    private boolean localSimulations;

    @GetMapping
    public ResponseEntity<List<MedicoDto>> obtenerMedicos() {
        return ResponseEntity.ok(medicoService.obtenerMedicosActivos());
    }

    @GetMapping("/perfil")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<MedicoDto> obtenerPerfil(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(medicoService.obtenerPerfil(userDetails.getUsername()));
    }

    @PutMapping("/perfil")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<MedicoDto> actualizarPerfil(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody MedicoDto dto) {
        return ResponseEntity.ok(medicoService.actualizarPerfil(userDetails.getUsername(), dto));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<com.tranqui.app.model.dto.DashboardStatsDto> obtenerStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "MENSUAL") String periodo) {
        return ResponseEntity.ok(medicoService.obtenerStats(userDetails.getUsername(), periodo));
    }

    /**
     * Updates only the médico's agenda settings (turno duration + gap between bookable
     * slots, "Duración de turno" / "Intervalo entre turnos" in the Agenda page). Kept
     * separate from PUT /perfil since that endpoint replaces the whole profile and the
     * Agenda page never has the rest of the profile fields loaded.
     */
    @PutMapping("/disponibilidad-config")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<MedicoDto> actualizarConfigAgenda(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody MedicoDto dto) {
        return ResponseEntity.ok(medicoService.actualizarConfigAgenda(
                userDetails.getUsername(), dto.getDuracionTurnoMinutos(), dto.getIntervaloEntreTurnosMinutos()));
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.DisponibilidadDto>> obtenerDisponibilidad(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam com.tranqui.app.model.Modalidad modalidad) {
        return ResponseEntity.ok(disponibilidadService.obtenerDisponibilidades(userDetails.getUsername(), modalidad));
    }

    @PutMapping("/disponibilidad")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.DisponibilidadDto>> actualizarDisponibilidad(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam com.tranqui.app.model.Modalidad modalidad,
            @RequestBody List<com.tranqui.app.model.dto.DisponibilidadDto> dtos) {
        return ResponseEntity.ok(disponibilidadService.guardarDisponibilidades(userDetails.getUsername(), modalidad, dtos));
    }

    private Usuario obtenerMedicoAutenticado(UserDetails userDetails) {
        return usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Médico no encontrado"));
    }

    @GetMapping("/mercadopago/status")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Map<String, Object>> obtenerEstadoMercadoPago(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        boolean connected = medico.getMpAccessTokenEncrypted() != null;
        return ResponseEntity.ok(Map.of(
                "connected", connected,
                "mpUserId", medico.getMpUserId() != null ? medico.getMpUserId() : "",
                "mercadopagoEnabled", mercadoPagoEnabled
        ));
    }

    @GetMapping("/mercadopago/connect")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Map<String, String>> obtenerUrlConexionMercadoPago(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        String url = mercadoPagoOAuthService.buildAuthorizationUrl(medico);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Dev-only: fakes a successful Mercado Pago link without contacting the real API,
     * so the connect/status/disconnect UI can be tested locally without real credentials
     * or a public callback URL. Disabled whenever mercadopago.enabled=true so it can never
     * be used to fake a link in a real deployment.
     */
    @PostMapping("/mercadopago/connect-simulado")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<?> simularConexionMercadoPago(@AuthenticationPrincipal UserDetails userDetails) throws Exception {
        if (mercadoPagoEnabled) {
            return ResponseEntity.status(403).body("La simulación solo está disponible en modo desarrollo (mercadopago.enabled=false).");
        }
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        mercadoPagoOAuthService.simularConexionDev(medico);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mercadopago/disconnect")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Void> desvincularMercadoPago(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        mercadoPagoOAuthService.desvincular(medico);
        return ResponseEntity.noContent().build();
    }

    /**
     * Public redirect target Mercado Pago sends the browser back to after the
     * professional accepts (or rejects) linking their account. Not authenticated
     * via JWT since it's a top-level browser navigation coming from MP's domain.
     */
    @GetMapping("/mercadopago/callback")
    public void callbackMercadoPago(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response) throws IOException {

        if (error != null) {
            response.sendRedirect(frontendUrl + "/?mp=error&reason=" + error);
            return;
        }

        Long medicoId = mercadoPagoOAuthService.verificarState(state);
        if (medicoId == null || code == null) {
            response.sendRedirect(frontendUrl + "/?mp=error&reason=invalid_state");
            return;
        }

        try {
            mercadoPagoOAuthService.procesarCallback(medicoId, code);
            response.sendRedirect(frontendUrl + "/?mp=success");
        } catch (Exception e) {
            log.error("Error al procesar el callback de OAuth de Mercado Pago", e);
            response.sendRedirect(frontendUrl + "/?mp=error&reason=exchange_failed");
        }
    }

    @GetMapping("/google-calendar/status")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Map<String, Object>> obtenerEstadoGoogleCalendar(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        return ResponseEntity.ok(Map.of(
                "connected", medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected(),
                "simulated", googleCalendarSimulado()
        ));
    }

    /**
     * Solo entorno local: marca Google Calendar como vinculado sin pasar por el OAuth de Google,
     * igual que /mercadopago/connect-simulado. Exige google.calendar.enabled=false y
     * app.local-simulations=true (solo perfil "local"), así nunca está disponible en producción.
     */
    @PostMapping("/google-calendar/connect-simulado")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<?> simularConexionGoogleCalendar(@AuthenticationPrincipal UserDetails userDetails) {
        if (!googleCalendarSimulado()) {
            return ResponseEntity.status(403).body("La simulación de Google Calendar solo está disponible en el entorno local.");
        }
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        medico.setGoogleCalendarConnected(true);
        usuarioRepository.save(medico);
        return ResponseEntity.ok().build();
    }

    private boolean googleCalendarSimulado() {
        return localSimulations && !googleCalendarEnabled;
    }

    @GetMapping("/google-calendar/connect")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Map<String, String>> obtenerUrlConexionGoogleCalendar(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        String url = googleCalendarOAuthService.buildAuthorizationUrl(medico);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/google-calendar/disconnect")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<Void> desvincularGoogleCalendar(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        googleCalendarWatchService.detenerCanal(medico);
        // Must run before desvincular() below: it still needs the médico's (still valid) Google
        // access token to delete the turno events TranquiApp created in their real calendar —
        // once desvincular() clears the tokens there's no way left to call the Calendar API.
        googleCalendarSyncService.eliminarEventosCreadosPorLaApp(medico);
        googleCalendarOAuthService.desvincular(medico);
        return ResponseEntity.noContent().build();
    }

    // Google's push notification for a change on the médico's calendar — no body, just headers.
    // Never returns an error status for a stale/unrecognized channel: Google retries with backoff
    // on non-2xx responses and eventually kills a channel it can't deliver to, so "not our
    // problem anymore" is a 200 no-op here, not a 4xx/5xx.
    @PostMapping("/google-calendar/webhook")
    public ResponseEntity<Void> recibirNotificacionGoogleCalendar(
            @RequestHeader(value = "X-Goog-Channel-ID", required = false) String channelId,
            @RequestHeader(value = "X-Goog-Resource-ID", required = false) String resourceId,
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String resourceState,
            @RequestHeader(value = "X-Goog-Channel-Token", required = false) String channelToken) {

        if ("sync".equals(resourceState)) {
            // The initial confirmation ping Google sends right after the channel is created —
            // no calendar change happened, nothing to sync.
            return ResponseEntity.ok().build();
        }

        Long medicoId = googleCalendarWatchService.verificarChannelToken(channelToken);
        if (medicoId == null) {
            return ResponseEntity.ok().build();
        }

        usuarioRepository.findById(medicoId).ifPresent(medico -> {
            boolean channelMatches = channelId != null && channelId.equals(medico.getGoogleWatchChannelId())
                    && resourceId != null && resourceId.equals(medico.getGoogleWatchResourceId());
            if (!channelMatches) {
                // Stale notification from a channel we already renewed/stopped — ignore it.
                return;
            }
            try {
                googleCalendarSyncService.sincronizarIncremental(medico);
            } catch (Exception e) {
                log.error("Fallo al procesar el webhook de Google Calendar para médico ID {}", medicoId, e);
            }
        });

        return ResponseEntity.ok().build();
    }

    @GetMapping("/google-calendar/eventos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.EventoExternoDto>> obtenerEventosExternosGoogleCalendar(
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario medico = obtenerMedicoAutenticado(userDetails);
        return ResponseEntity.ok(googleCalendarSyncService.obtenerEventosExternosCacheados(medico));
    }

    @GetMapping("/google-calendar/callback")
    public void callbackGoogleCalendar(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response) throws IOException {

        if (error != null) {
            response.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=" + error);
            return;
        }

        Long medicoId = googleCalendarOAuthService.verificarState(state);
        if (medicoId == null || code == null) {
            response.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=invalid_state");
            return;
        }

        try {
            Usuario medico = googleCalendarOAuthService.procesarCallback(medicoId, code);
            googleCalendarWatchService.registrarCanal(medico); // best-effort — see registrarCanal's own try/catch
            // Seed the eventos externos cache right away instead of waiting for the next
            // polling cycle (up to 5 min) — otherwise "Próximos Eventos" looks empty right
            // after connecting even though the link succeeded.
            try {
                googleCalendarSyncService.sincronizacionCompleta(medico);
            } catch (Exception syncEx) {
                log.error("Fallo al sincronizar eventos de Google Calendar tras vincular el médico ID {}", medicoId, syncEx);
            }
            response.sendRedirect(frontendUrl + "/?googleCalendar=success");
        } catch (Exception e) {
            log.error("Error al procesar el callback de OAuth de Google Calendar", e);
            response.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=exchange_failed");
        }
    }
}
