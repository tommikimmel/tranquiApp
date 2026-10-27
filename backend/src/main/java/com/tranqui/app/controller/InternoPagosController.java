package com.tranqui.app.controller;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Pago;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.MercadoPagoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fase 4: lo que el monolito le responde al servicio de pagos. Las consultas son para su carga inicial;
 * los tokens de Mercado Pago viajan cifrados tal cual (misma ENCRYPTION_KEY), nunca se descifran para
 * moverlos. Protegidas por {@link com.tranqui.app.config.FiltroServicioInterno}, como el resto de
 * {@code /internal/monolito}.
 */
@RestController
@RequestMapping("/internal/monolito/pagos")
public class InternoPagosController {

    public record CuentaMpDto(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                              LocalDateTime tokenVenceEn, String mpUserId) {}

    public record PagoDto(Long id, Long turnoId, Long solicitudId, Long profesionalId, Long pacienteId,
                          String transactionId, String estado, BigDecimal monto, LocalDateTime fechaPago) {}

    public record SolicitudDto(Long solicitudId, Long profesionalId, Long pacienteId, String tipoConcepto,
                               BigDecimal precio, String estado, String transactionId, String titulo) {}

    public record CobroPendienteDto(Long turnoId, Long profesionalId, Long pacienteId, BigDecimal monto,
                                    String titulo, String checkoutUrl, LocalDateTime creadoEn) {}

    private static final int TAMANIO_MAXIMO = 1000;

    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final TurnoRepository turnoRepository;
    private final SolicitudDocumentoRepository solicitudRepository;
    private final WebhookController webhookController;
    private final boolean suscripcionesExterno;

    public InternoPagosController(UsuarioRepository usuarioRepository, PagoRepository pagoRepository,
                                  TurnoRepository turnoRepository, SolicitudDocumentoRepository solicitudRepository,
                                  WebhookController webhookController,
                                  @org.springframework.beans.factory.annotation.Value("${suscripciones.externo:false}") boolean suscripcionesExterno) {
        this.suscripcionesExterno = suscripcionesExterno;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
        this.turnoRepository = turnoRepository;
        this.solicitudRepository = solicitudRepository;
        this.webhookController = webhookController;
    }

    /** Cuentas de Mercado Pago vinculadas, con los tokens cifrados tal cual están guardados. */
    @GetMapping("/cuentas")
    @Transactional(readOnly = true)
    public List<CuentaMpDto> cuentas() {
        return usuarioRepository.findByMpAccessTokenEncryptedIsNotNull().stream()
                .map(u -> new CuentaMpDto(u.getId(), u.getMpAccessTokenEncrypted(), u.getMpRefreshTokenEncrypted(),
                        u.getMpTokenExpiresAt(), u.getMpUserId()))
                .toList();
    }

    /** Pagos de turnos registrados (aprobados, rechazados y reembolsados), paginados por id. */
    @GetMapping("/pagos")
    @Transactional(readOnly = true)
    public List<PagoDto> pagos(@RequestParam(defaultValue = "0") int pagina,
                               @RequestParam(defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return pagoRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoPagosController::pago)
                .toList();
    }

    /** Solicitudes de documentos de la API de conceptos (pendientes y pagadas). */
    @GetMapping("/solicitudes")
    @Transactional(readOnly = true)
    public List<SolicitudDto> solicitudes() {
        return solicitudRepository.findAll(Sort.by("id")).stream()
                .map(InternoPagosController::solicitud)
                .toList();
    }

    /** Turnos esperando el pago (bloqueo de 5 minutos), para que el servicio conozca sus cobros en curso. */
    @GetMapping("/cobros-pendientes")
    @Transactional(readOnly = true)
    public List<CobroPendienteDto> cobrosPendientes() {
        return turnoRepository.findByEstadoAndFechaCreacionBefore(EstadoTurno.PENDIENTE_PAGO,
                        LocalDateTime.now().plusMinutes(1)).stream()
                .map(t -> new CobroPendienteDto(t.getId(), t.getMedico().getId(), t.getPaciente().getId(), t.getPrecio(),
                        MercadoPagoService.tituloCobroTurno(t.getMedico()), t.getCheckoutUrl(), t.getFechaCreacion()))
                .toList();
    }

    /**
     * La única ruta interna que no es una consulta: es el webhook de Mercado Pago mismo. Las
     * suscripciones siguen en el monolito hasta la Fase 8, pero Mercado Pago avisa todo a la misma URL,
     * que con la Fase 4 atiende el servicio de pagos; el servicio valida la firma y reenvía acá lo que
     * es de suscripciones (decisión 2 de la spec).
     */
    @PostMapping("/notificacion-suscripcion")
    public ResponseEntity<Void> notificacionSuscripcion(@RequestParam(name = "dataId", required = false) String dataId,
                                                        @RequestBody String body) {
        if (suscripcionesExterno) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        }
        webhookController.procesarNotificacion(body, dataId, true);
        return ResponseEntity.ok().build();
    }

    private static PagoDto pago(Pago p) {
        Turno t = p.getTurno();
        return new PagoDto(p.getId(), t != null ? t.getId() : null, p.getSolicitudDocumentoId(),
                t != null ? t.getMedico().getId() : null, t != null ? t.getPaciente().getId() : null,
                p.getTransactionId(), p.getEstado() != null ? p.getEstado().name() : null, p.getMonto(), p.getFechaPago());
    }

    private static SolicitudDto solicitud(SolicitudDocumento s) {
        return new SolicitudDto(s.getId(), s.getMedico().getId(), s.getPaciente().getId(),
                s.getTipoConcepto() != null ? s.getTipoConcepto().toString() : null, s.getPrecio(),
                s.getEstado() != null ? s.getEstado().name() : null, s.getTransactionId(),
                MercadoPagoService.tituloCobroConcepto(s));
    }
}
