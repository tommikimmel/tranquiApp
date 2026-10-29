package com.tranqui.pagos.web;

import com.tranqui.pagos.cobros.Aprobaciones;
import com.tranqui.pagos.cobros.NotificacionesMercadoPago;
import com.tranqui.pagos.datos.Cobros;
import com.tranqui.pagos.datos.Pagos;
import com.tranqui.pagos.mp.MercadoPagoApi;
import com.tranqui.pagos.mp.ValidadorFirma;
import com.tranqui.pagos.mp.Vinculacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /api/payments}: el webhook de Mercado Pago y "verificar", con las mismas URLs y respuestas que el
 * monolito, y el link del checkout que ahora el frontend pide aparte (decisión 1). Sin sesión, como antes:
 * el webhook lo llama Mercado Pago y la reserva no exige estar logueado.
 */
@RestController
@RequestMapping("/api/payments")
public class PagosController {

    private static final Logger log = LoggerFactory.getLogger(PagosController.class);

    private final ValidadorFirma validador;
    private final NotificacionesMercadoPago notificaciones;
    private final Aprobaciones aprobaciones;
    private final Cobros cobros;
    private final Pagos pagos;
    private final Vinculacion vinculacion;
    private final MercadoPagoApi mercadoPago;

    public PagosController(ValidadorFirma validador, NotificacionesMercadoPago notificaciones, Aprobaciones aprobaciones,
                           Cobros cobros, Pagos pagos, Vinculacion vinculacion, MercadoPagoApi mercadoPago) {
        this.validador = validador;
        this.notificaciones = notificaciones;
        this.aprobaciones = aprobaciones;
        this.cobros = cobros;
        this.pagos = pagos;
        this.vinculacion = vinculacion;
        this.mercadoPago = mercadoPago;
    }

    /**
     * A diferencia del monolito (que siempre respondía 200), si el aviso no se pudo procesar responde 500:
     * Mercado Pago lo reintenta, y así un pago no se pierde si la base o el monolito están caídos un momento.
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestHeader(value = "x-signature", required = false) String firma,
                                     @RequestHeader(value = "x-request-id", required = false) String requestId,
                                     @RequestParam(value = "data.id", required = false) String dataId,
                                     @RequestBody String cuerpo) {
        if (!validador.esValida(firma, requestId, dataId)) {
            log.warn("Firma de webhook de Mercado Pago no válida; se rechaza.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Firma inválida");
        }
        try {
            notificaciones.procesar(cuerpo, dataId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error al procesar el webhook de Mercado Pago; Mercado Pago lo va a reintentar", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Respaldo del webhook, como en el monolito: el frontend lo llama al volver del checkout (con el
     * {@code paymentId}) o al abrir "Mis Turnos" con un turno pendiente (sin él, busca por referencia). El pago
     * se vuelve a consultar en Mercado Pago con el token del profesional y tiene que ser de ese turno.
     */
    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestParam("externalReference") String externalReference,
                                       @RequestParam(value = "paymentId", required = false) Long paymentId) {
        if (!externalReference.matches("\\d+")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referencia externa inválida.");
        }
        try {
            if (pagos.hayAprobadoPara(externalReference)) {
                return ResponseEntity.ok().build();
            }
            Optional<Cobros.Cobro> cobro = cobros.buscar(externalReference);
            if (cobro.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Optional<String> token = vinculacion.accessTokenValido(cobro.get().profesionalId());
            if (token.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("No se pudo verificar el pago.");
            }
            MercadoPagoApi.PagoMp pago = paymentId != null
                    ? mercadoPago.obtenerPago(token.get(), paymentId)
                    : mercadoPago.buscarPorReferencia(token.get(), externalReference);
            if (pago == null || !externalReference.equals(pago.referencia())) {
                log.warn("Verificación de pago: no hay un pago de Mercado Pago que corresponda al turno {}.", externalReference);
                return ResponseEntity.status(HttpStatus.CONFLICT).body("El pago no corresponde a este turno.");
            }
            if ("approved".equals(pago.estado())) {
                aprobaciones.aplicar(externalReference, String.valueOf(pago.id()), pago.estado(), pago.monto());
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error al verificar el pago (externalReference={}, paymentId={})", externalReference, paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * El link del checkout de una reserva ({@code id} del turno) o de un documento ({@code doc-<id>}). El
     * frontend lo pide después de reservar hasta que esté {@code LISTO}; si todavía no llegó la reserva al
     * servicio, responde {@code PENDIENTE}.
     */
    @GetMapping("/checkout/{referencia}")
    public Map<String, Object> checkout(@PathVariable("referencia") String referencia) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        Optional<Cobros.Cobro> cobro = cobros.buscar(referencia);
        respuesta.put("estado", cobro.map(Cobros.Cobro::estado).orElse(Cobros.PENDIENTE));
        cobro.map(Cobros.Cobro::checkoutUrl).ifPresent(url -> respuesta.put("checkoutUrl", url));
        cobro.map(Cobros.Cobro::error).ifPresent(error -> respuesta.put("error", error));
        return respuesta;
    }
}
