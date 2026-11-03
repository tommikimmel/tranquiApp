package com.tranqui.pagos.interno;

import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

/** Fase 10, T2, lado consumidor: pagos lee cada contrato de lo que le responden sin perder campos. */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/monolito/contactos.json", ClienteInterno.Contacto.class);
        Contratos.leer("internas/monolito/pagos-cuentas.json", ClienteInterno.Cuenta.class);
        Contratos.leer("internas/monolito/pagos-pagos.json", ClienteInterno.Pago.class);
        Contratos.leer("internas/monolito/pagos-solicitudes.json", ClienteInterno.Solicitud.class);
        Contratos.leer("internas/monolito/pagos-cobros-pendientes.json", ClienteInterno.CobroPendiente.class);
        Contratos.leer("internas/monolito/suscripciones-planes.json", ClienteInterno.PlanMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-features.json", ClienteInterno.FeatureMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-plan-features.json", ClienteInterno.PlanFeatureMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-suscripciones.json", ClienteInterno.SubscriptionMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-eventos.json", ClienteInterno.SubscriptionEventMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-pagos.json", ClienteInterno.SubscriptionPaymentMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-facturas.json", ClienteInterno.InvoiceMonolito.class);
        Contratos.leer("internas/monolito/suscripciones-secuencias.json", ClienteInterno.InvoiceSequenceMonolito.class);
        Contratos.leer("internas/fuente-identidad/suscriptores.json", ClienteInterno.SuscriptorMonolito.class);
        Contratos.leer("internas/fuente-identidad/suscriptor.json", ClienteInterno.SuscriptorMonolito.class);
    }
}
