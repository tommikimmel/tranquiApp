package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.Suscripciones;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Publica suscripciones.aviso.programado.v1 por outbox para que el servicio de notificaciones
 * envíe los correos correspondientes a los profesionales.
 */
@Component
public class AvisoSuscripcionPublisher {

    private final OutboxPublisher outboxPublisher;

    public AvisoSuscripcionPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public void publicar(Suscripciones.Suscripcion sub, String nombrePlan, String tipo, BigDecimal monto, LocalDateTime fecha,
                         LocalDateTime fechaHasta, String ciclo, String comprobante, Integer diasRestantes) {
        outboxPublisher.publicar(
                Topicos.SUSCRIPCIONES_AVISO_PROGRAMADO_V1,
                "suscripcion",
                String.valueOf(sub.id()),
                sub.aggregateVersion(),
                new SuscripcionAvisoData(
                        sub.id(),
                        sub.professionalId(),
                        tipo,
                        nombrePlan != null ? nombrePlan : "Profesional",
                        monto,
                        fecha,
                        fechaHasta,
                        ciclo,
                        comprobante,
                        diasRestantes
                )
        );
    }
}
