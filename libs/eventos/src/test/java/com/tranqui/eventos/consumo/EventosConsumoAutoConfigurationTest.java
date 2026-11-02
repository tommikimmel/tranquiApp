package com.tranqui.eventos.consumo;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EventosConsumoAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventosConsumoAutoConfiguration.class))
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:x")))
            .withBean(PlatformTransactionManager.class, () -> mock(DataSourceTransactionManager.class))
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class));

    @Test
    void sinNombreDeServicio_soloRegistraElInbox() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(InboxHelper.class);
            assertThat(ctx).doesNotHaveBean(ConsumidorEventos.class);
            assertThat(ctx).doesNotHaveBean(TopologiaConsumo.class);
        });
    }

    @Test
    void conNombreDeServicio_registraConsumidorSinArrancarloTodavia() {
        runner.withPropertyValues("eventos.consumo.servicio=notificaciones").run(ctx -> {
            assertThat(ctx).hasSingleBean(ConsumidorEventos.class);
            assertThat(ctx).hasSingleBean(TopologiaConsumo.class);
            assertThat(ctx.getBean(org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer.class)
                    .isRunning()).isFalse();
        });
    }
}
