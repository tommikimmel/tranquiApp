package com.tranqui.app.eventos;

import com.tranqui.eventos.consumo.InboxHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests de integración end-to-end con Testcontainers (PostgreSQL 15 y RabbitMQ 3.13)
 * para validar la outbox, inbox, LISTEN/NOTIFY y el Relay de mensajería.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OutboxRelayIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");

        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
        registry.add("spring.rabbitmq.template.mandatory", () -> "true");

        registry.add("eventos.relay.enabled", () -> "true");
        registry.add("eventos.relay.backup-poll-ms", () -> "1000");
    }

    private static final String TEST_QUEUE = "test.integracion.eventos";

    @TestConfiguration
    static class TestConfig {
        @Bean
        public Queue testQueue() {
            return new Queue(TEST_QUEUE, true, false, false);
        }

        @Bean
        public Binding testBinding(Queue testQueue, TopicExchange exchangeEventos) {
            return BindingBuilder.bind(testQueue).to(exchangeEventos).with("turnos.#");
        }
    }

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private InboxHelper inboxHelper;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired
    private javax.sql.DataSource dataSource;

    private final ObjectMapper objectMapper = EventObjectMapperFactory.create();

    @BeforeEach
    void asegurarColaYPurgar() {
        try {
            rabbitAdmin.declareQueue(new Queue(TEST_QUEUE, true, false, false));
            rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(TEST_QUEUE, true, false, false))
                    .to(new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false))
                    .with("turnos.#"));
            rabbitAdmin.purgeQueue(TEST_QUEUE, true);
        } catch (Exception ignored) {
        }
    }

    @Test
    @Order(1)
    void transaccionConfirmada_insertEnOutbox_sePublicaUnaVezEnRabbitMQ() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] eventIdHolder = new UUID[1];

        tx.executeWithoutResult(status -> {
            EventEnvelope<String> envelope = outboxPublisher.publicar(
                    "turnos.turno.confirmado.v1",
                    "turno",
                    "7001",
                    1,
                    "datos-confirmacion"
            );
            eventIdHolder[0] = envelope.eventId();
        });

        UUID eventId = eventIdHolder[0];
        assertThat(eventId).isNotNull();

        // Esperar que el relay procese vía LISTEN outbox o sondeo
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OutboxRecord> record = outboxRepository.findByEventId(eventId);
            assertThat(record).isPresent();
            assertThat(record.get().getPublishedAt()).isNotNull();
            assertThat(record.get().getLastError()).isNull();
        });

        // Verificar mensaje recibido en RabbitMQ
        Object recibido = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);
        assertThat(recibido).isNotNull();

        String json = recibido instanceof byte[] b ? new String(b) : recibido.toString();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(node.get("eventType").asText()).isEqualTo("turnos.turno.confirmado");
        assertThat(node.get("aggregateId").asText()).isEqualTo("7001");
        assertThat(node.get("data").asText()).isEqualTo("datos-confirmacion");

        // Verificar que no se duplicó
        Object segundo = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 1000);
        assertThat(segundo).isNull();
    }

    @Test
    @Order(2)
    void rollback_noHayFilaNiMensaje() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            outboxPublisher.publicar(
                    "pagos.pago.aprobado.v1",
                    "pago",
                    "8001",
                    1,
                    "pago-revertido"
            );
            throw new RuntimeException("Rollback intencional");
        })).isInstanceOf(RuntimeException.class);

        assertThat(outboxRepository.findAll().stream()
                .anyMatch(r -> "8001".equals(r.getAggregateId()))).isFalse();

        Object recibido = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 1500);
        assertThat(recibido).isNull();
    }

    @Test
    @Order(3)
    void conRabbitMQCaido_filaQuedaPendienteYSePublicaAlVolver() throws Exception {
        // 1. Detener el contenedor de RabbitMQ para simular caída real del broker
        rabbitmq.stop();
        connectionFactory.resetConnection();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] eventIdHolder = new UUID[1];

        tx.executeWithoutResult(status -> {
            EventEnvelope<String> envelope = outboxPublisher.publicar(
                    "turnos.turno.cancelado.v1",
                    "turno",
                    "9001",
                    2,
                    "datos-cancelacion"
            );
            eventIdHolder[0] = envelope.eventId();
        });

        UUID eventId = eventIdHolder[0];

        // Disparar relay mientras RabbitMQ está caído
        outboxRelay.trigger();

        // Fila debe quedar pendiente con attempts >= 1 y error registrado
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OutboxRecord> record = outboxRepository.findByEventId(eventId);
            assertThat(record).isPresent();
            assertThat(record.get().getPublishedAt()).isNull();
            assertThat(record.get().getAttempts()).isGreaterThanOrEqualTo(1);
            assertThat(record.get().getLastError()).isNotNull();
        });

        // 2. Levantar el broker nuevamente
        rabbitmq.start();
        int newPort = rabbitmq.getAmqpPort();
        connectionFactory.setAddresses("localhost:" + newPort);
        connectionFactory.setPort(newPort);
        connectionFactory.resetConnection();

        // Re-declarar queue y binding en el nuevo contenedor RabbitMQ
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        Queue q = new Queue(TEST_QUEUE, true, false, false);
        admin.declareQueue(q);
        TopicExchange ex = new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false);
        admin.declareExchange(ex);
        admin.declareBinding(BindingBuilder.bind(q).to(ex).with("turnos.#"));

        // Reintentar relay
        outboxRelay.trigger();

        // Debe publicarse con éxito
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OutboxRecord> record = outboxRepository.findByEventId(eventId);
            assertThat(record).isPresent();
            assertThat(record.get().getPublishedAt()).isNotNull();
        });

        Object recibido = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);
        assertThat(recibido).isNotNull();
        String json = recibido instanceof byte[] b ? new String(b) : recibido.toString();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(node.get("aggregateId").asText()).isEqualTo("9001");
    }

    @Test
    @Order(4)
    void inbox_ignoraDuplicado() {
        UUID eventId = UUID.randomUUID();
        String consumidor = "notificaciones-handler";
        AtomicBoolean ejecutado = new AtomicBoolean(false);

        boolean primeraVez = inboxHelper.procesar(eventId, consumidor, () -> ejecutado.set(true));
        assertThat(primeraVez).isTrue();
        assertThat(ejecutado.get()).isTrue();

        AtomicBoolean segundaEjecucion = new AtomicBoolean(false);
        boolean segundaVez = inboxHelper.procesar(eventId, consumidor, () -> segundaEjecucion.set(true));
        assertThat(segundaVez).isFalse();
        assertThat(segundaEjecucion.get()).isFalse();
    }

    @Test
    @Order(5)
    void eventoSinColaEnlazada_seMarcaPublicado_registraMetricaSinRuta() throws Exception {
        String topicoSinCola = "identidad.usuario.registrado.v1";
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] eventIdHolder = new UUID[1];

        tx.executeWithoutResult(status -> {
            EventEnvelope<String> envelope = outboxPublisher.publicar(
                    topicoSinCola,
                    "usuario",
                    "usr-100",
                    1,
                    "datos-registro"
            );
            eventIdHolder[0] = envelope.eventId();
        });

        UUID eventId = eventIdHolder[0];
        assertThat(eventId).isNotNull();

        // Esperar que el relay procese el evento y verifique published_at no nulo
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OutboxRecord> record = outboxRepository.findByEventId(eventId);
            assertThat(record).isPresent();
            assertThat(record.get().getPublishedAt()).isNotNull();
            assertThat(record.get().getLastError()).isNull();
        });

        // Verificar métrica Micrometer outbox.eventos.sin_ruta = 1 con tag topico
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var counter = meterRegistry.find("outbox.eventos.sin_ruta")
                    .tag("topico", topicoSinCola)
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        });

        // Verificar que ninguna cola recibió el mensaje (era unrouted)
        Object recibido = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 1000);
        assertThat(recibido).isNull();

        // Verificar que la outbox no quedó bloqueada publicando un evento con ruta posterior
        UUID[] segundoEventIdHolder = new UUID[1];
        tx.executeWithoutResult(status -> {
            EventEnvelope<String> envelope = outboxPublisher.publicar(
                    "turnos.turno.confirmado.v1",
                    "turno",
                    "7002",
                    1,
                    "datos-posterior"
            );
            segundoEventIdHolder[0] = envelope.eventId();
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OutboxRecord> record = outboxRepository.findByEventId(segundoEventIdHolder[0]);
            assertThat(record).isPresent();
            assertThat(record.get().getPublishedAt()).isNotNull();
        });

        Object segundoRecibido = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);
        assertThat(segundoRecibido).isNotNull();
    }

    @Test
    @Order(6)
    void conexionListenDedicada_noOcupaConexionDelPoolHikari() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDs) {
            // El loop LISTEN de OutboxRelay usa una conexión dedicada fuera del pool Hikari,
            // por lo que cuando no hay transacciones activas el pool Hikari tiene 0 conexiones activas.
            assertThat(hikariDs.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }
}
