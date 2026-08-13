package com.tranqui.app.service;

import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.core.MPRequestOptions;
import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class ReembolsoService {

    private static final Logger log = LoggerFactory.getLogger(ReembolsoService.class);

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Value("${mercadopago.enabled:false}")
    private boolean isEnabled;

    // Legacy 2-arg entry point: always enforces the 48h policy (saltarPolitica48h = false), same
    // as this method's original/only behavior — kept so existing callers/tests that don't care
    // about who's cancelling keep working unchanged.
    public boolean procesarReembolso(Turno turno, Usuario medico) throws Exception {
        return procesarReembolso(turno, medico, false);
    }

    // saltarPolitica48h = true when the médico themselves is the one cancelling (see
    // TurnoService#cancelarTurno) — the 48h window exists to protect the médico's booked time
    // from a patient bailing last-minute, not to penalize the patient's refund when it's the
    // médico who decided not to hold the appointment. A patient-initiated cancellation still
    // goes through the false path below and keeps the existing policy.
    public boolean procesarReembolso(Turno turno, Usuario medico, boolean saltarPolitica48h) throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaTurno = LocalDateTime.of(turno.getFecha(), turno.getHoraInicio());

        // Validate 48 hours cancellation policy
        if (!saltarPolitica48h && ahora.plusHours(48).isAfter(fechaTurno)) {
            log.warn("Intento de cancelación tardía para turno ID: {}. Menos de 48 horas.", turno.getId());
            return false; // Block automatic refund
        }

        String rawToken = "dummy-token";
        if (medico.getMpAccessTokenEncrypted() != null) {
            rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        }

        if (isEnabled && !rawToken.startsWith("dummy") && !rawToken.equals("test-token")) {
            // Per-request access token (not the global/static MercadoPagoConfig.setAccessToken,
            // which is a JVM-wide singleton) — same reasoning as MercadoPagoService: this refund
            // and a concurrent payment/refund for a *different* médico must never be able to
            // stomp on each other's access token via that shared global field. Using a
            // MercadoPagoConfig.setAccessToken here previously meant that after this call ran
            // once, the last-refunded médico's token silently became the JVM-wide default —
            // exactly the kind of stale/cross-account state that made payments break for other
            // médicos (most visibly right after a médico disconnected/reconnected their account).
            MPRequestOptions options = MPRequestOptions.builder().accessToken(rawToken).build();
            PaymentRefundClient refundClient = new PaymentRefundClient();
            refundClient.refund(Long.parseLong(turno.getPago().getTransactionId()), options);
        } else {
            log.info("Sincronización de reembolso con Mercado Pago omitida (simulación/offline).");
        }

        turno.setEstado(EstadoTurno.CANCELADO);
        if (turno.getPago() != null) {
            turno.getPago().setEstado(EstadoPago.REEMBOLSADO);
        }
        return true;
    }
}
