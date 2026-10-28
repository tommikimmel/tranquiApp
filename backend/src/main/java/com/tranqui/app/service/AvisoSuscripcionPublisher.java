package com.tranqui.app.service;

import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Subscription;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Publica suscripciones.aviso.programado.v1: cada mail al profesional sobre su suscripción sale
 * como evento para el servicio de notificaciones (Fase 1). Se llama desde el mismo punto donde el
 * monolito mandaba (o manda, con el envío directo prendido) el mail.
 *
 * <p>Es transaccional por su cuenta porque algunos llamadores (el scheduler de renovaciones, el
 * panel de admin) no tienen transacción y la outbox la exige; si el llamador ya tiene una, se suma.
 */
@Component
public class AvisoSuscripcionPublisher {

    private final OutboxPublisher outboxPublisher;

    public AvisoSuscripcionPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public void publicar(Subscription sub, String tipo, BigDecimal monto, LocalDateTime fecha,
                         LocalDateTime fechaHasta, String ciclo, String comprobante, Integer diasRestantes) {
        outboxPublisher.publicar(
                Topicos.SUSCRIPCIONES_AVISO_PROGRAMADO_V1,
                "suscripcion",
                String.valueOf(sub.getId()),
                sub.getAggregateVersion(),
                new SuscripcionAvisoData(
                        sub.getId(),
                        sub.getProfessional() != null ? sub.getProfessional().getId() : null,
                        tipo,
                        sub.getPlan() != null ? sub.getPlan().getName() : "Profesional",
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
