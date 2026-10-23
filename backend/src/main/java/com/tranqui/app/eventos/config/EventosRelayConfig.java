package com.tranqui.app.eventos.config;

import com.tranqui.eventos.RabbitMQConstantes;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Spring AMQP y RabbitMQ para la plataforma de eventos.
 */
@Configuration
public class EventosRelayConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    /**
     * Declara el topic exchange principal tranqui.eventos de forma durable.
     */
    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false);
    }

    /**
     * Configura el RabbitTemplate con mandatory=true para publisher returns.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }
}
