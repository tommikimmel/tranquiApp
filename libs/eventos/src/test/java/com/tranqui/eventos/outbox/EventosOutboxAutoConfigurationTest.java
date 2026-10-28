package com.tranqui.eventos.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventosOutboxAutoConfigurationTest {

    private ApplicationContextRunner runner(boolean confirms) {
        ConnectionFactory conexion = mock(ConnectionFactory.class);
        when(conexion.isPublisherConfirms()).thenReturn(confirms);
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EventosOutboxAutoConfiguration.class))
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://x/y")))
                .withBean(PlatformTransactionManager.class, () -> mock(DataSourceTransactionManager.class))
                .withBean(DataSourceProperties.class, () -> {
                    DataSourceProperties p = new DataSourceProperties();
                    p.setUrl("jdbc:postgresql://x/y");
                    return p;
                })
                .withBean(ConnectionFactory.class, () -> conexion);
    }

    @Test
    void apagadaPorDefecto_comoEnElMonolitoYElGateway() {
        runner(true).run(ctx -> {
            assertThat(ctx).doesNotHaveBean(OutboxPublisher.class);
            assertThat(ctx).doesNotHaveBean(OutboxRelay.class);
        });
    }

    @Test
    void habilitada_registraPublisherYRelay() {
        runner(true).withPropertyValues("eventos.outbox.habilitada=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(OutboxPublisher.class);
            assertThat(ctx).hasSingleBean(OutboxRelay.class);
            assertThat(ctx).hasSingleBean(OutboxMantenimiento.class);
        });
    }

    @Test
    void sinRelay_soloElPublisher() {
        runner(true).withPropertyValues("eventos.outbox.habilitada=true", "eventos.relay.enabled=false").run(ctx -> {
            assertThat(ctx).hasSingleBean(OutboxPublisher.class);
            assertThat(ctx).doesNotHaveBean(OutboxRelay.class);
        });
    }

    @Test
    void sinPublisherConfirms_noArranca() {
        runner(false).withPropertyValues("eventos.outbox.habilitada=true").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("publisher-confirm-type=correlated");
        });
    }
}
