package com.tranqui.pagos.cobros;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PagoRechazadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.Cobros;
import com.tranqui.pagos.datos.Pagos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Lo que pasa cuando Mercado Pago informa un pago (por webhook o por "verificar"): se registra una sola vez
 * por {@code transaction_id} y se avisa a turnos ({@code pagos.pago.aprobado/rechazado}) o, si es un
 * documento de la API de conceptos, con {@code pagos.concepto.aprobado}. Mismos casos que el webhook del
 * monolito: los rechazos de documentos no se informan.
 */
@Service
public class Aprobaciones {

    private static final Logger log = LoggerFactory.getLogger(Aprobaciones.class);

    private final Cobros cobros;
    private final Pagos pagos;
    private final OutboxPublisher outbox;

    public Aprobaciones(Cobros cobros, Pagos pagos, OutboxPublisher outbox) {
        this.cobros = cobros;
        this.pagos = pagos;
        this.outbox = outbox;
    }

    /**
     * @param estadoMp el {@code status} de Mercado Pago: {@code approved}, {@code rejected} y {@code cancelled}
     *                 se registran; los demás (pendiente, en proceso) todavía no.
     */
    @Transactional
    public void aplicar(String referencia, String transactionId, String estadoMp, BigDecimal montoMp) {
        boolean aprobado = "approved".equals(estadoMp);
        boolean rechazado = "rejected".equals(estadoMp) || "cancelled".equals(estadoMp);
        if (referencia == null || transactionId == null || (!aprobado && !rechazado)) {
            return;
        }
        boolean documento = referencia.startsWith("doc-");
        Long turnoId;
        Long solicitudId;
        try {
            turnoId = documento ? null : Long.parseLong(referencia);
            solicitudId = documento ? Long.parseLong(referencia.substring(4)) : null;
        } catch (NumberFormatException e) {
            log.error("Referencia externa de Mercado Pago no válida: {}", referencia);
            return;
        }
        if (documento && rechazado) {
            return;
        }
        Optional<Cobros.Cobro> cobro = cobros.buscar(referencia);
        Long profesionalId = cobro.map(Cobros.Cobro::profesionalId).orElse(null);
        Long pacienteId = cobro.map(Cobros.Cobro::pacienteId).orElse(null);
        BigDecimal monto = montoMp != null ? montoMp : cobro.map(Cobros.Cobro::monto).orElse(BigDecimal.ZERO);

        Optional<Long> pagoId = pagos.registrar(new Pagos.Pago(null, referencia, turnoId, solicitudId, profesionalId, pacienteId,
                transactionId, aprobado ? Pagos.APROBADO : Pagos.RECHAZADO, monto, LocalDateTime.now()), null);
        if (pagoId.isEmpty()) {
            log.info("Pago {} de {} ya estaba registrado; se ignora el aviso repetido.", transactionId, referencia);
            return;
        }
        if (rechazado) {
            outbox.publicar(Topicos.PAGOS_PAGO_RECHAZADO_V1, "pago", String.valueOf(pagoId.get()), 1,
                    new PagoRechazadoData(pagoId.get(), turnoId, transactionId, estadoMp));
            return;
        }
        cobros.cambiarEstado(referencia, Cobros.PAGADO);
        if (documento) {
            outbox.publicar(Topicos.PAGOS_CONCEPTO_APROBADO_V1, "solicitud", String.valueOf(solicitudId), 1,
                    new ConceptoAprobadoData(solicitudId, profesionalId, pacienteId,
                            cobro.map(Cobros.Cobro::tipoConcepto).orElse(null), transactionId));
        } else {
            outbox.publicar(Topicos.PAGOS_PAGO_APROBADO_V1, "pago", String.valueOf(pagoId.get()), 1,
                    new PagoAprobadoData(pagoId.get(), turnoId, transactionId, monto, "mercadopago"));
        }
        log.info("Pago {} aprobado para {}.", transactionId, referencia);
    }
}
