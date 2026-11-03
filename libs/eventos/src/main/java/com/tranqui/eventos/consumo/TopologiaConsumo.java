package com.tranqui.eventos.consumo;

import com.tranqui.eventos.RabbitMQConstantes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Declara la topología de consumo de un servicio (sección 5.1). Es idempotente: se puede correr en
 * cada arranque.
 *
 * <ul>
 *   <li>{@code <servicio>.eventos}: cola del servicio, enlazada a {@code tranqui.eventos} con los
 *       tópicos de sus handlers.</li>
 *   <li>{@code <servicio>.reintento.{10s,1m,5m}}: enlazadas a {@code tranqui.reintentos}; al vencer
 *       el TTL el mensaje vuelve a la cola del servicio por el exchange por defecto, así un
 *       reintento no les llega a los demás servicios suscritos al mismo tópico.</li>
 *   <li>{@code <servicio>.dlq}: enlazada a {@code tranqui.dlx}, para revisar a mano.</li>
 * </ul>
 */
public class TopologiaConsumo {

    private static final Logger log = LoggerFactory.getLogger(TopologiaConsumo.class);

    /** Niveles de reintento en orden: después del último, el evento va a la DLQ. */
    static final List<Nivel> NIVELES = List.of(
            new Nivel(RabbitMQConstantes.SUFIJO_COLA_REINTENTO_10S, RabbitMQConstantes.TTL_REINTENTO_10S_MS),
            new Nivel(RabbitMQConstantes.SUFIJO_COLA_REINTENTO_1M, RabbitMQConstantes.TTL_REINTENTO_1M_MS),
            new Nivel(RabbitMQConstantes.SUFIJO_COLA_REINTENTO_5M, RabbitMQConstantes.TTL_REINTENTO_5M_MS));

    record Nivel(String sufijo, int ttlMs) {}

    private final AmqpAdmin admin;
    private final String servicio;

    public TopologiaConsumo(AmqpAdmin admin, String servicio) {
        this.admin = admin;
        this.servicio = servicio;
    }

    public void declarar(Collection<String> topicos) {
        TopicExchange eventos = new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false);
        DirectExchange reintentos = new DirectExchange(RabbitMQConstantes.EXCHANGE_REINTENTOS, true, false);
        DirectExchange dlx = new DirectExchange(RabbitMQConstantes.EXCHANGE_DLX, true, false);
        admin.declareExchange(eventos);
        admin.declareExchange(reintentos);
        admin.declareExchange(dlx);

        String colaEventos = RabbitMQConstantes.colaEventos(servicio);
        Queue principal = QueueBuilder.durable(colaEventos).build();
        admin.declareQueue(principal);
        for (String topico : new TreeSet<>(topicos)) {
            admin.declareBinding(BindingBuilder.bind(principal).to(eventos).with(topico));
        }

        for (Nivel nivel : NIVELES) {
            String nombre = servicio + nivel.sufijo();
            Queue espera = QueueBuilder.durable(nombre)
                    .ttl(nivel.ttlMs())
                    .deadLetterExchange("")
                    .deadLetterRoutingKey(colaEventos)
                    .build();
            admin.declareQueue(espera);
            admin.declareBinding(new Binding(nombre, Binding.DestinationType.QUEUE,
                    RabbitMQConstantes.EXCHANGE_REINTENTOS, nombre, null));
        }

        String colaDlq = RabbitMQConstantes.colaDlq(servicio);
        admin.declareQueue(QueueBuilder.durable(colaDlq).build());
        admin.declareBinding(new Binding(colaDlq, Binding.DestinationType.QUEUE,
                RabbitMQConstantes.EXCHANGE_DLX, colaDlq, null));

        log.info("Topología de consumo de '{}' declarada: {} tópicos", servicio, topicos.size());
    }
}
