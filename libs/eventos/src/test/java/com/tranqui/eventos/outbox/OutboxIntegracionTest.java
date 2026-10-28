package com.tranqui.eventos.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.data.MensajeEnviadoData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Outbox de la librería contra PostgreSQL y RabbitMQ reales (sección 6.1). */
@Testcontainers
class OutboxIntegracionTest {

    private static final String COLA = "prueba.outbox";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private final ObjectMapper mapper = EventObjectMapperFactory.create();
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private CachingConnectionFactory conexion;
    private RabbitTemplate rabbit;
    private SimpleMeterRegistry metricas;
    private OutboxPublisher publisher;
    private OutboxRelay relay;

    @BeforeEach
    void preparar() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        // El script tiene una función con $$...$$: va entero, como lo corre Flyway (ScriptUtils lo partiría en cada ;).
        try (Connection c = ds.getConnection(); java.sql.Statement st = c.createStatement()) {
            st.execute(new String(new ClassPathResource("db/eventos/outbox.sql").getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM outbox");
        DataSourceTransactionManager transacciones = new DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(transacciones);

        conexion = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexion.setUsername(RABBIT.getAdminUsername());
        conexion.setPassword(RABBIT.getAdminPassword());
        conexion.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        conexion.setPublisherReturns(true);
        RabbitAdmin admin = new RabbitAdmin(conexion);
        TopicExchange eventos = new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false);
        admin.declareExchange(eventos);
        admin.declareQueue(new Queue(COLA, false));
        admin.purgeQueue(COLA);
        admin.declareBinding(BindingBuilder.bind(new Queue(COLA)).to(eventos).with("chat.mensaje.*.v1"));

        rabbit = new RabbitTemplate(conexion);
        rabbit.setMandatory(true);
        metricas = new SimpleMeterRegistry();
        publisher = new OutboxPublisher(jdbc);
        relay = new OutboxRelay(jdbc, rabbit, transacciones, metricas, POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    @AfterEach
    void cerrar() {
        relay.detener();
        conexion.destroy();
    }

    private static void esperar(BooleanSupplier condicion, Duration maximo) throws InterruptedException {
        long limite = System.nanoTime() + maximo.toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se cumplió la condición en " + maximo);
            }
            Thread.sleep(50);
        }
    }

    private MensajeEnviadoData mensaje(long id) {
        return new MensajeEnviadoData(id, 99L, 1L, 2L, OffsetDateTime.now());
    }

    @Test
    void eventoPublicadoEnUnaTransaccion_llegaARabbitYQuedaMarcado() throws Exception {
        relay.iniciar();
        Thread.sleep(500);
        long inicio = System.nanoTime();

        tx.executeWithoutResult(s -> publisher.publicar("chat.mensaje.enviado.v1", "mensaje", "7", 1, mensaje(7)));

        Message recibido = null;
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (recibido == null && System.nanoTime() < limite) {
            recibido = rabbit.receive(COLA, 200);
        }
        assertThat(recibido).isNotNull();
        // LISTEN avisa en el momento: no espera los 5 s del barrido de respaldo.
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
        JsonNode sobre = mapper.readTree(recibido.getBody());
        assertThat(sobre.get("eventType").asText()).isEqualTo("chat.mensaje.enviado");
        assertThat(sobre.get("producer").asText()).isEqualTo("chat");
        assertThat(sobre.get("data").get("mensajeId").asLong()).isEqualTo(7L);
        assertThat(recibido.getMessageProperties().getMessageId()).isEqualTo(sobre.get("eventId").asText());
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Long.class) == 1,
                Duration.ofSeconds(5));
    }

    @Test
    void siLaTransaccionSeDeshace_noHayEvento() throws Exception {
        relay.iniciar();

        try {
            tx.executeWithoutResult(s -> {
                publisher.publicar("chat.mensaje.enviado.v1", "mensaje", "8", 1, mensaje(8));
                throw new IllegalStateException("falla el negocio");
            });
        } catch (IllegalStateException esperado) {
            // la transacción se deshizo
        }
        Thread.sleep(1_500);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Long.class)).isZero();
        assertThat(rabbit.receive(COLA, 500)).isNull();
    }

    @Test
    void eventoSinColaEnlazada_seMarcaYNoTrabaLosSiguientes() throws Exception {
        relay.iniciar();

        tx.executeWithoutResult(s -> {
            publisher.publicar("chat.conversacion.archivada.v1", "mensaje", "9", 1, mensaje(9));
            publisher.publicar("chat.mensaje.enviado.v1", "mensaje", "10", 1, mensaje(10));
        });

        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class) == 0,
                Duration.ofSeconds(10));
        Message siguiente = rabbit.receive(COLA, 5_000);
        assertThat(siguiente).isNotNull();
        assertThat(metricas.counter("outbox.eventos.sin_ruta", "topico", "chat.conversacion.archivada.v1").count()).isEqualTo(1.0);
    }

    @Test
    void conRabbitSinConfirmar_elEventoQuedaPendienteConElError() throws Exception {
        // Sin publisher confirms el relay no puede saber si el broker guardó el evento: no lo marca.
        CachingConnectionFactory sinConfirms = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        sinConfirms.setUsername(RABBIT.getAdminUsername());
        sinConfirms.setPassword(RABBIT.getAdminPassword());
        OutboxRelay relaySinConfirms = new OutboxRelay(jdbc, new RabbitTemplate(sinConfirms),
                new DataSourceTransactionManager(jdbc.getDataSource()), metricas, POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        try {
            tx.executeWithoutResult(s -> publisher.publicar("chat.mensaje.enviado.v1", "mensaje", "11", 1, mensaje(11)));
            relaySinConfirms.iniciar();
            esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE attempts > 0", Long.class) == 1,
                    Duration.ofSeconds(15));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT last_error FROM outbox", String.class)).isNotBlank();
        } finally {
            relaySinConfirms.detener();
            sinConfirms.destroy();
        }
    }
}
