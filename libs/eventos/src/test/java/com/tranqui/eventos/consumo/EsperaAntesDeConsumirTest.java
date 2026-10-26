package com.tranqui.eventos.consumo;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** El consumo espera a los EsperaAntesDeConsumir (por ejemplo, la carga inicial) antes de arrancar. */
class EsperaAntesDeConsumirTest {

    @Test
    void declaraLaTopologiaPeroNoConsumeHastaQueTodoEsteListo() {
        TopologiaConsumo topologia = mock(TopologiaConsumo.class);
        ConsumidorEventos consumidor = mock(ConsumidorEventos.class);
        SimpleMessageListenerContainer contenedor = mock(SimpleMessageListenerContainer.class);
        AtomicBoolean cargaTerminada = new AtomicBoolean(false);
        List<EsperaAntesDeConsumir> esperas = List.of(() -> true, cargaTerminada::get);

        Thread hilo = new Thread(() -> EventosConsumoAutoConfiguration.Consumo.iniciarConReintentos(
                topologia, consumidor, contenedor, esperas, 10));
        hilo.setDaemon(true);
        hilo.start();

        verify(topologia, timeout(2_000)).declarar(any());
        verify(contenedor, after(300).never()).start();

        cargaTerminada.set(true);
        verify(contenedor, timeout(2_000)).start();
    }

    @Test
    void sinEsperas_arrancaEnseguida() {
        TopologiaConsumo topologia = mock(TopologiaConsumo.class);
        SimpleMessageListenerContainer contenedor = mock(SimpleMessageListenerContainer.class);

        EventosConsumoAutoConfiguration.Consumo.iniciarConReintentos(topologia, mock(ConsumidorEventos.class),
                contenedor, List.of(), 10);

        verify(contenedor).start();
    }
}
