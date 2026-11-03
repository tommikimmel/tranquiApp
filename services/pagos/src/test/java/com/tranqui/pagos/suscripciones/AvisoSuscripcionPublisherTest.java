package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.AlertaAdminData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AvisoSuscripcionPublisherTest {

    @Test
    void alertaAdministradores_unaPorMotivoYDia() {
        OutboxPublisher outbox = mock(OutboxPublisher.class);

        new AvisoSuscripcionPublisher(outbox).alertarAdministradores("monotributo", "Título", "Mensaje");

        verify(outbox).publicar(Topicos.PAGOS_ALERTA_ADMIN_V1, "alerta", "monotributo:" + LocalDate.now(), 1,
                new AlertaAdminData("Título", "Mensaje"));
    }
}
