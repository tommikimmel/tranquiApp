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

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${mercadopago.enabled:false}")
    private boolean mercadoPagoEnabled;

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
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(medicoService.obtenerStats(userDetails.getUsername()));
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.DisponibilidadDto>> obtenerDisponibilidad(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(disponibilidadService.obtenerDisponibilidades(userDetails.getUsername()));
    }

    @PutMapping("/disponibilidad")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<com.tranqui.app.model.dto.DisponibilidadDto>> actualizarDisponibilidad(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody List<com.tranqui.app.model.dto.DisponibilidadDto> dtos) {
        return ResponseEntity.ok(disponibilidadService.guardarDisponibilidades(userDetails.getUsername(), dtos));
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
                "connected", medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected()
        ));
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
        googleCalendarOAuthService.desvincular(medico);
        return ResponseEntity.noContent().build();
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
            googleCalendarOAuthService.procesarCallback(medicoId, code);
            response.sendRedirect(frontendUrl + "/?googleCalendar=success");
        } catch (Exception e) {
            log.error("Error al procesar el callback de OAuth de Google Calendar", e);
            response.sendRedirect(frontendUrl + "/?googleCalendar=error&reason=exchange_failed");
        }
    }
}
