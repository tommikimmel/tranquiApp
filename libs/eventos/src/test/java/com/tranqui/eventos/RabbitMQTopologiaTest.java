package com.tranqui.eventos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQTopologiaTest {

    @Test
    void constantesExchanges_coincidenConSeccion5_1() {
        assertThat(RabbitMQConstantes.EXCHANGE_EVENTOS).isEqualTo("tranqui.eventos");
        assertThat(RabbitMQConstantes.EXCHANGE_REINTENTOS).isEqualTo("tranqui.reintentos");
        assertThat(RabbitMQConstantes.EXCHANGE_DLX).isEqualTo("tranqui.dlx");
    }

    @Test
    void generadoresDeNombresDeColas_formanSufijosCorrectos() {
        String servicio = "notificaciones";

        assertThat(RabbitMQConstantes.colaEventos(servicio)).isEqualTo("notificaciones.eventos");
        assertThat(RabbitMQConstantes.colaReintento10s(servicio)).isEqualTo("notificaciones.reintento.10s");
        assertThat(RabbitMQConstantes.colaReintento1m(servicio)).isEqualTo("notificaciones.reintento.1m");
        assertThat(RabbitMQConstantes.colaReintento5m(servicio)).isEqualTo("notificaciones.reintento.5m");
        assertThat(RabbitMQConstantes.colaDlq(servicio)).isEqualTo("notificaciones.dlq");
    }
}
