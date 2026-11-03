package com.tranqui.pagos.cobros;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.ReembolsoFallidoData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.Pagos;
import com.tranqui.pagos.datos.Reembolsos;
import com.tranqui.pagos.mp.MercadoPagoApi;
import com.tranqui.pagos.mp.Vinculacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * El reembolso de un turno cancelado (decisión 3 de la spec). Turnos decide si corresponde con la política
 * de siempre (48 hs, o cancela el profesional) y lo informa en {@code turnos.turno.cancelado}; acá se pide a
 * Mercado Pago con el token del profesional y se avisa con {@code pagos.reembolso.realizado} (el segundo mail
 * al paciente) o {@code pagos.reembolso.fallido} (se revisa a mano). Un reembolso por turno como máximo.
 */
@Service
public class ReembolsosTurnos {

    private static final Logger log = LoggerFactory.getLogger(ReembolsosTurnos.class);
    /** El resultado que manda turnos cuando el reembolso lo hace este servicio (en la vuelta atrás, lo hace él). */
    static final String EN_PROCESO = "EN_PROCESO";

    private final Pagos pagos;
    private final Reembolsos reembolsos;
    private final Vinculacion vinculacion;
    private final MercadoPagoApi mercadoPago;
    private final OutboxPublisher outbox;
    private final boolean habilitado;

    public ReembolsosTurnos(Pagos pagos, Reembolsos reembolsos, Vinculacion vinculacion, MercadoPagoApi mercadoPago,
                            OutboxPublisher outbox, @Value("${mercadopago.enabled:false}") boolean habilitado) {
        this.pagos = pagos;
        this.reembolsos = reembolsos;
        this.vinculacion = vinculacion;
        this.mercadoPago = mercadoPago;
        this.outbox = outbox;
        this.habilitado = habilitado;
    }

    @Transactional
    public void alCancelar(TurnoCanceladoData d) {
        if (!d.correspondeReembolso() || !EN_PROCESO.equals(d.resultadoReembolso()) || reembolsos.existe(d.turnoId())) {
            return;
        }
        Optional<Pagos.Pago> aprobado = pagos.aprobadoDeTurno(d.turnoId());
        if (aprobado.isEmpty()) {
            log.error("Turno {} cancelado con reembolso pero sin un pago aprobado registrado", d.turnoId());
            fallido(d, "No hay un pago aprobado registrado para el turno.");
            return;
        }
        Pagos.Pago pago = aprobado.get();
        String motivo = "PROFESIONAL".equals(d.canceladoPor()) ? "Cancelado por profesional" : "Cancelación con anticipación > 48hs";
        try {
            pedirAMercadoPago(pago);
        } catch (Exception e) {
            log.error("Mercado Pago no aceptó el reembolso del turno {} (pago {})", d.turnoId(), pago.transactionId(), e);
            reembolsos.registrar(pago.id(), d.turnoId(), Reembolsos.FALLIDO, pago.monto(), motivo, e.getMessage());
            fallido(d, e.getMessage());
            return;
        }
        Long reembolsoId = reembolsos.registrar(pago.id(), d.turnoId(), Reembolsos.REALIZADO, pago.monto(), motivo, null)
                .orElseThrow();
        pagos.cambiarEstado(pago.id(), Pagos.REEMBOLSADO);
        outbox.publicar(Topicos.PAGOS_REEMBOLSO_REALIZADO_V1, "pago", String.valueOf(pago.id()), 2,
                new ReembolsoRealizadoData(reembolsoId, pago.id(), d.turnoId(), pago.monto(), motivo, d.pacienteId(),
                        d.profesionalId(), d.fecha(), d.horaInicio(), true));
    }

    private void pedirAMercadoPago(Pagos.Pago pago) throws Exception {
        if (!habilitado) {
            log.info("Reembolso del pago {} sin pedirlo a Mercado Pago (simulación o local).", pago.transactionId());
            return;
        }
        String token = vinculacion.accessTokenValido(pago.profesionalId())
                .orElseThrow(() -> new IllegalStateException("El profesional desvinculó su cuenta de Mercado Pago."));
        if (Vinculacion.esDePrueba(token)) {
            log.info("Reembolso del pago {} sin pedirlo a Mercado Pago (cuenta de prueba).", pago.transactionId());
            return;
        }
        mercadoPago.reembolsar(token, Long.parseLong(pago.transactionId()));
    }

    private void fallido(TurnoCanceladoData d, String motivo) {
        outbox.publicar(Topicos.PAGOS_REEMBOLSO_FALLIDO_V1, "turno", String.valueOf(d.turnoId()), 1,
                new ReembolsoFallidoData(d.turnoId(), d.pacienteId(), d.profesionalId(), d.fecha(), d.horaInicio(), motivo));
    }
}
