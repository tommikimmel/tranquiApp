package com.tranqui.turnos.turnos;

import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PagoRechazadoData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Lo que turnos hace con los eventos de pagos y del calendario: confirma el turno pagado (lo que hacía
 * {@code PagoWebhookHandler} en el monolito), registra el pago rechazado y el reembolso, guarda el link del
 * checkout y el Meet. Todos son idempotentes.
 */
@Service
public class Cobros {

    private static final Logger log = LoggerFactory.getLogger(Cobros.class);

    private final Turnos turnos;
    private final Publicador publicador;
    private final Gestion gestion;

    public Cobros(Turnos turnos, Publicador publicador, Gestion gestion) {
        this.turnos = turnos;
        this.publicador = publicador;
        this.gestion = gestion;
    }

    private Optional<Turno> turno(Long id, String evento) {
        if (id == null) {
            return Optional.empty(); // un documento de la API de conceptos: no es un turno
        }
        Optional<Turno> t = turnos.porIdParaActualizar(id);
        if (t.isEmpty()) {
            log.warn("{} de un turno que no existe: {}", evento, id);
        }
        return t;
    }

    /**
     * El pago aprobado confirma el turno. Si el turno ya se había cancelado (venció el bloqueo de 5 minutos o el
     * paciente abandonó el pago) y mientras tanto otro paciente tomó el horario, no se confirma: queda cancelado
     * y se avisa con reembolso, para que pagos le devuelva la plata con la lógica de siempre (decisión 4). Si el
     * horario sigue libre, se confirma como hasta ahora.
     */
    @Transactional
    public void pagoAprobado(PagoAprobadoData d) {
        turno(d.turnoId(), "pagos.pago.aprobado").ifPresent(t -> {
            if (Turno.CONFIRMADO.equals(t.estado)) {
                return;
            }
            t.pagoTransactionId = d.mpPaymentId();
            t.pagoEstado = Turno.PAGO_APROBADO;
            t.pagoMonto = t.precio;
            t.pagoFecha = LocalDateTime.now();
            if ((Turno.CANCELADO.equals(t.estado) || Turno.EXPIRADO.equals(t.estado)) && horarioTomado(t)) {
                t.estado = Turno.CANCELADO;
                turnos.guardar(t);
                log.warn("Pago aprobado del turno {} que ya estaba cancelado y con el horario tomado: se reembolsa", t.id);
                publicador.cancelado(t, "SISTEMA", "El horario ya no estaba disponible cuando se acreditó el pago", "EN_PROCESO");
                publicador.actualizado(t);
                return;
            }
            t.estado = Turno.CONFIRMADO;
            turnos.guardar(t);
            String direccion = "ONLINE".equals(t.modalidad) ? null : gestion.direccion(t);
            publicador.confirmado(t, t.ocupaAgenda ? Etiquetas.tipoTurno(t) : null, t.ocupaAgenda ? null : Etiquetas.tipoDocumento(t),
                    direccion);
            if (!t.ocupaAgenda) {
                publicador.documentoSolicitado(t, Etiquetas.tipoDocumento(t), "DOCUMENTO");
            }
            publicador.actualizado(t);
        });
    }

    /** ¿Otro turno de agenda, vigente, ocupa ese horario del profesional? */
    private boolean horarioTomado(Turno pagado) {
        if (!pagado.ocupaAgenda) {
            return false;
        }
        LocalDateTime limiteCincoMin = LocalDateTime.now().minusMinutes(5);
        return turnos.delDia(pagado.medicoId, pagado.fecha).stream()
                .filter(o -> !o.id.equals(pagado.id) && o.ocupaAgenda && !Turno.EXPIRADO.equals(o.estado))
                .filter(o -> !Turno.PENDIENTE_PAGO.equals(o.estado) || (o.fechaCreacion != null && o.fechaCreacion.isAfter(limiteCincoMin)))
                .anyMatch(o -> pagado.horaInicio.isBefore(o.horaFin) && pagado.horaFin.isAfter(o.horaInicio));
    }

    /** Un pago por turno: un rechazo no pisa otro intento ni el aprobado. El horario lo libera el vencimiento. */
    @Transactional
    public void pagoRechazado(PagoRechazadoData d) {
        turno(d.turnoId(), "pagos.pago.rechazado").ifPresent(t -> {
            if (t.pagoEstado != null) {
                return;
            }
            t.pagoTransactionId = d.mpPaymentId();
            t.pagoEstado = Turno.PAGO_RECHAZADO;
            t.pagoMonto = t.precio;
            t.pagoFecha = LocalDateTime.now();
            turnos.guardar(t);
            publicador.actualizado(t);
        });
    }

    @Transactional
    public void reembolsado(ReembolsoRealizadoData d) {
        turno(d.turnoId(), "pagos.reembolso.realizado").ifPresent(t -> {
            if (t.pagoEstado == null || Turno.PAGO_REEMBOLSADO.equals(t.pagoEstado)) {
                return;
            }
            t.pagoEstado = Turno.PAGO_REEMBOLSADO;
            turnos.guardar(t);
            publicador.actualizado(t);
        });
    }

    /** El link del checkout, para "Mis turnos" del paciente (solo mientras espera el pago). */
    @Transactional
    public void checkout(CheckoutCreadoData d) {
        turno(d.turnoId(), "pagos.checkout.creado").ifPresent(t -> {
            if (!Turno.PENDIENTE_PAGO.equals(t.estado) || d.checkoutUrl().equals(t.checkoutUrl)) {
                return;
            }
            t.checkoutUrl = d.checkoutUrl();
            turnos.guardar(t);
            publicador.actualizado(t);
        });
    }

    /** El Meet y el evento de Google que creó el calendario para el turno confirmado. */
    @Transactional
    public void meet(MeetCreadoData d) {
        turno(d.turnoId(), "calendario.meet.creado").ifPresent(t -> {
            boolean cambia = false;
            if (d.meetUrl() != null && !d.meetUrl().isBlank() && !d.meetUrl().equals(t.telemedicinaUrl)) {
                t.telemedicinaUrl = d.meetUrl();
                cambia = true;
            }
            if (d.googleEventId() != null && !d.googleEventId().equals(t.googleEventId)) {
                t.googleEventId = d.googleEventId();
                cambia = true;
            }
            if (cambia) {
                turnos.guardar(t);
                publicador.actualizado(t);
            }
        });
    }
}
