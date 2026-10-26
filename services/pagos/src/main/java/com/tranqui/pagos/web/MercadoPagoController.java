package com.tranqui.pagos.web;

import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.datos.Participantes;
import com.tranqui.pagos.mp.Vinculacion;
import com.tranqui.pagos.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.pagos.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

/**
 * La vinculación de Mercado Pago del panel del profesional, con las mismas URLs, roles, JSON y
 * redirecciones que tenía MedicoController en el monolito. El callback no lleva sesión (vuelve el navegador
 * desde Mercado Pago) y se valida con la firma del {@code state}.
 */
@RestController
@RequestMapping("/api/medicos/mercadopago")
public class MercadoPagoController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoController.class);

    private final Participantes participantes;
    private final CuentasMp cuentas;
    private final Vinculacion vinculacion;
    private final boolean habilitado;
    private final String frontendUrl;

    public MercadoPagoController(Participantes participantes, CuentasMp cuentas, Vinculacion vinculacion,
                                 @Value("${mercadopago.enabled:false}") boolean habilitado,
                                 @Value("${app.frontend-url}") String frontendUrl) {
        this.participantes = participantes;
        this.cuentas = cuentas;
        this.vinculacion = vinculacion;
        this.habilitado = habilitado;
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
        var cuenta = cuentas.buscar(profesional(pedido));
        return Map.of(
                "connected", cuenta.isPresent(),
                "mpUserId", cuenta.map(CuentasMp.Cuenta::mpUserId).orElse(""),
                "mercadopagoEnabled", habilitado);
    }

    @GetMapping("/connect")
    public Map<String, String> conectar(HttpServletRequest pedido) {
        return Map.of("url", vinculacion.urlAutorizacion(profesional(pedido)));
    }

    /** Solo en local: vincula sin Mercado Pago real. Con {@code mercadopago.enabled=true} nunca se puede. */
    @PostMapping("/connect-simulado")
    public ResponseEntity<?> conectarSimulado(HttpServletRequest pedido) {
        if (habilitado) {
            return ResponseEntity.status(403).body("La simulación solo está disponible en modo desarrollo (mercadopago.enabled=false).");
        }
        vinculacion.simularConexion(profesional(pedido));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> desvincular(HttpServletRequest pedido) {
        vinculacion.desvincular(profesional(pedido));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(value = "code", required = false) String code,
                         @RequestParam(value = "state", required = false) String state,
                         @RequestParam(value = "error", required = false) String error,
                         HttpServletResponse respuesta) throws IOException {
        if (error != null) {
            respuesta.sendRedirect(frontendUrl + "/?mp=error&reason=" + error);
            return;
        }
        Long profesionalId = vinculacion.verificarState(state);
        if (profesionalId == null || code == null) {
            respuesta.sendRedirect(frontendUrl + "/?mp=error&reason=invalid_state");
            return;
        }
        try {
            vinculacion.procesarCallback(profesionalId, code);
            respuesta.sendRedirect(frontendUrl + "/?mp=success");
        } catch (Exception e) {
            log.error("Error al procesar el callback de OAuth de Mercado Pago", e);
            respuesta.sendRedirect(frontendUrl + "/?mp=error&reason=exchange_failed");
        }
    }
}
