package com.tranqui.app.eventos;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private DataSource dataSource;
    private OutboxRepository outboxRepository;
    private RabbitTemplate rabbitTemplate;
    private PlatformTransactionManager transactionManager;
    private MeterRegistry meterRegistry;
    private DataSourceProperties dataSourceProperties;
    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        outboxRepository = mock(OutboxRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any(TransactionStatus.class));

        meterRegistry = new SimpleMeterRegistry();
        dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSourceProperties.setUsername("sa");
        dataSourceProperties.setPassword("");

        outboxRelay = new OutboxRelay(
                dataSource,
                outboxRepository,
                rabbitTemplate,
                transactionManager,
                meterRegistry,
                dataSourceProperties
        );
    }

    @Test
    void mensajeSinRuta_incrementaMetricaSinRutaYMarcaPublicado() {
        UUID eventId = UUID.randomUUID();
        String topico = "notificaciones.aviso.v1";
        OutboxRecord record = OutboxRecord.builder()
                .id(1L)
                .eventId(eventId)
                .topic(topico)
                .aggregateType("notificacion")
                .aggregateId("100")
                .payload("{\"test\":true}")
                .createdAt(OffsetDateTime.now())
                .attempts(0)
                .build();

        when(outboxRepository.findPendientesConLock(anyInt()))
                .thenReturn(List.of(record))
                .thenReturn(List.of());

        // Simular que RabbitTemplate envía y el broker responde con returned + ack
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            Message msg = new Message("{}".getBytes(), new MessageProperties());
            ReturnedMessage returned = new ReturnedMessage(msg, 312, "NO_ROUTE", "tranqui.eventos", topico);
            cd.setReturned(returned);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString(), any(MessagePostProcessor.class), any(CorrelationData.class));

        outboxRelay.procesarPendientes();

        // Verificar que no se bloqueó: record publicado y guardado
        ArgumentCaptor<OutboxRecord> captor = ArgumentCaptor.forClass(OutboxRecord.class);
        verify(outboxRepository).save(captor.capture());
        OutboxRecord guardado = captor.getValue();
        assertThat(guardado.getPublishedAt()).isNotNull();
        assertThat(guardado.getLastError()).isNull();

        // Verificar métrica outbox.eventos.sin_ruta con tag topico
        double metricValue = meterRegistry.get("outbox.eventos.sin_ruta")
                .tag("topico", topico)
                .counter()
                .count();
        assertThat(metricValue).isEqualTo(1.0);
    }

    @Test
    void confirmNegativo_fallaYRegistraIntentoSinMarcarPublicado() {
        UUID eventId = UUID.randomUUID();
        String topico = "turnos.turno.confirmado.v1";
        OutboxRecord record = OutboxRecord.builder()
                .id(2L)
                .eventId(eventId)
                .topic(topico)
                .aggregateType("turno")
                .aggregateId("200")
                .payload("{\"test\":true}")
                .createdAt(OffsetDateTime.now())
                .attempts(0)
                .build();

        when(outboxRepository.findPendientesConLock(anyInt()))
                .thenReturn(List.of(record));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "Broker NACK"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString(), any(MessagePostProcessor.class), any(CorrelationData.class));

        outboxRelay.procesarPendientes();

        ArgumentCaptor<OutboxRecord> captor = ArgumentCaptor.forClass(OutboxRecord.class);
        verify(outboxRepository).save(captor.capture());
        OutboxRecord guardado = captor.getValue();
        assertThat(guardado.getPublishedAt()).isNull();
        assertThat(guardado.getAttempts()).isEqualTo(1);
        assertThat(guardado.getLastError()).contains("Broker NACK");

        // No debe haber incrementado métrica sin_ruta
        assertThat(meterRegistry.find("outbox.eventos.sin_ruta").counter()).isNull();
    }

    @Test
    void conexionDedicada_noUtilizaPoolHikari() throws Exception {
        // Al tener URL configurada, abrirConexionDedicada() usa DriverManager y no llama a dataSource.getConnection()
        try (Connection conn = outboxRelay.abrirConexionDedicada()) {
            assertThat(conn).isNotNull();
        }
        verify(dataSource, never()).getConnection();
    }
}
