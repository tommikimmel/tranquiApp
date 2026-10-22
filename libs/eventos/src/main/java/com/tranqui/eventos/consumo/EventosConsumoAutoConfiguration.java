package com.tranqui.eventos.consumo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Consumo de eventos de {@code libs/eventos}.
 *
 * <ul>
 *   <li>{@link InboxHelper}: en cualquier aplicación con {@link JdbcTemplate} (el monolito incluido).</li>
 *   <li>Consumidor y topología: solo si se configura {@code eventos.consumo.servicio} (el nombre del
 *       servicio, que da nombre a sus colas). El consumidor arranca recién con la aplicación lista.</li>
 * </ul>
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, RabbitAutoConfiguration.class,
        TransactionAutoConfiguration.class})
@ConditionalOnClass(JdbcTemplate.class)
public class EventosConsumoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(JdbcTemplate.class)
    public InboxHelper inboxHelper(JdbcTemplate jdbcTemplate) {
        return new InboxHelper(jdbcTemplate);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SimpleMessageListenerContainer.class)
    @ConditionalOnProperty(prefix = "eventos.consumo", name = "servicio")
    static class Consumo {

        @Bean
        TopologiaConsumo topologiaConsumo(ConnectionFactory connectionFactory,
                                          @Value("${eventos.consumo.servicio}") String servicio) {
            AmqpAdmin admin = new RabbitAdmin(connectionFactory);
            return new TopologiaConsumo(admin, servicio);
        }

        @Bean
        ConsumidorEventos consumidorEventos(@Value("${eventos.consumo.servicio}") String servicio,
                                            ObjectProvider<ManejadorEvento<?>> manejadores, InboxHelper inbox,
                                            PlatformTransactionManager transactionManager,
                                            ObjectProvider<MeterRegistry> metricas) {
            ObjectMapper mapper = EventObjectMapperFactory.create();
            List<ManejadorEvento<?>> lista = manejadores.orderedStream().toList();
            return new ConsumidorEventos(servicio, lista, inbox, new TransactionTemplate(transactionManager),
                    mapper, metricas.getIfAvailable());
        }

        @Bean
        SimpleMessageListenerContainer contenedorEventos(ConnectionFactory connectionFactory,
                                                         ConsumidorEventos consumidor,
                                                         @Value("${eventos.consumo.servicio}") String servicio,
                                                         @Value("${eventos.consumo.prefetch:10}") int prefetch,
                                                         @Value("${eventos.consumo.concurrencia:1}") int concurrencia) {
            SimpleMessageListenerContainer contenedor = new SimpleMessageListenerContainer(connectionFactory);
            contenedor.setQueueNames(RabbitMQConstantes.colaEventos(servicio));
            contenedor.setMessageListener(consumidor);
            contenedor.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            contenedor.setPrefetchCount(prefetch);
            contenedor.setConcurrentConsumers(concurrencia);
            contenedor.setDefaultRequeueRejected(true);
            // Sección 6.7: al apagarse termina lo que está procesando, hasta 20 s.
            contenedor.setShutdownTimeout(20_000);
            // Arranca recién con la aplicación lista (sección 7), ver iniciarConsumo.
            contenedor.setAutoStartup(false);
            contenedor.setMissingQueuesFatal(false);
            return contenedor;
        }

        @Bean
        ApplicationListener<ApplicationReadyEvent> iniciarConsumo(TopologiaConsumo topologia,
                                                                  ConsumidorEventos consumidor,
                                                                  SimpleMessageListenerContainer contenedor,
                                                                  org.springframework.beans.factory.ObjectProvider<EsperaAntesDeConsumir> esperas) {
            return evento -> {
                Thread hilo = new Thread(() -> iniciarConReintentos(topologia, consumidor, contenedor, esperas.orderedStream().toList(), 1_000),
                        "inicio-consumo-eventos");
                hilo.setDaemon(true);
                hilo.start();
            };
        }

        /**
         * Sección 7: espera a RabbitMQ con espera creciente (hasta 30 s), sin tirar la aplicación. Con la
         * topología declarada, espera además a los {@link EsperaAntesDeConsumir} (por ejemplo, la carga
         * inicial) antes de empezar a consumir.
         */
        static void iniciarConReintentos(TopologiaConsumo topologia, ConsumidorEventos consumidor,
                                         SimpleMessageListenerContainer contenedor,
                                         java.util.List<EsperaAntesDeConsumir> esperas, long sondeoMs) {
            long esperaMs = 1_000;
            boolean avisado = false;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    topologia.declarar(consumidor.topicos());
                    while (!esperas.stream().allMatch(EsperaAntesDeConsumir::listo)) {
                        if (!avisado) {
                            LoggerFactory.getLogger(EventosConsumoAutoConfiguration.class)
                                    .info("Consumo de eventos en espera hasta que termine la preparación del servicio");
                            avisado = true;
                        }
                        try {
                            Thread.sleep(sondeoMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    contenedor.start();
                    return;
                } catch (RuntimeException e) {
                    LoggerFactory.getLogger(EventosConsumoAutoConfiguration.class).warn(
                            "RabbitMQ no disponible para declarar la topología; se reintenta en {} ms: {}",
                            esperaMs, e.getMessage());
                    try {
                        Thread.sleep(esperaMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    esperaMs = Math.min(esperaMs * 2, 30_000);
                }
            }
        }
    }
}
