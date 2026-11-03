package com.tranqui.eventos.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Outbox de {@code libs/eventos} para los servicios que publican eventos. Se activa con
 * {@code eventos.outbox.habilitada=true}; el monolito no la usa (tiene la suya hasta la Fase 9).
 *
 * <ul>
 *   <li>{@link OutboxPublisher}: siempre que está habilitada.</li>
 *   <li>{@link OutboxRelay}: salvo {@code eventos.relay.enabled=false} (por ejemplo, en tests que
 *       solo miran la tabla). Arranca con la aplicación lista y se detiene al apagarse.</li>
 * </ul>
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, RabbitAutoConfiguration.class})
@ConditionalOnClass({JdbcTemplate.class, RabbitTemplate.class})
@ConditionalOnProperty(prefix = "eventos.outbox", name = "habilitada", havingValue = "true")
public class EventosOutboxAutoConfiguration {

    @Bean
    OutboxPublisher outboxPublisher(JdbcTemplate jdbc) {
        return new OutboxPublisher(jdbc);
    }

    @Bean
    OutboxMantenimiento outboxMantenimiento(JdbcTemplate jdbc, ObjectProvider<MeterRegistry> metricas) {
        return new OutboxMantenimiento(jdbc, metricas.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "eventos.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class Relay {

        @Bean(destroyMethod = "detener")
        OutboxRelay outboxRelay(JdbcTemplate jdbc, ConnectionFactory conexion, PlatformTransactionManager transacciones,
                                ObjectProvider<MeterRegistry> metricas, DataSourceProperties datos) {
            if (!conexion.isPublisherConfirms()) {
                throw new IllegalStateException("La outbox necesita spring.rabbitmq.publisher-confirm-type=correlated "
                        + "y publisher-returns=true");
            }
            // RabbitTemplate propio con mandatory, para detectar eventos sin cola enlazada.
            RabbitTemplate rabbit = new RabbitTemplate(conexion);
            rabbit.setMandatory(true);
            return new OutboxRelay(jdbc, rabbit, transacciones, metricas.getIfAvailable(),
                    datos.determineUrl(), datos.determineUsername(), datos.determinePassword());
        }

        @Bean
        ApplicationListener<ApplicationReadyEvent> iniciarOutboxRelay(OutboxRelay relay) {
            return evento -> relay.iniciar();
        }
    }
}
