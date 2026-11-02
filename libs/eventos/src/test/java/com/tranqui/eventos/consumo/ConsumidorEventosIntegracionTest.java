package com.tranqui.eventos.consumo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumo de punta a punta contra PostgreSQL y RabbitMQ reales (sección 12 del plan): inbox,
 * reintentos, DLQ y descarte por versión.
 */
@Testcontainers
class ConsumidorEventosIntegracionTest {

    private static final String SERVICIO = "prueba";
    private static final String TOPICO = "turnos.turno.confirmado.v1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    record DatosPrueba(Long turnoId, String nombre) {}

    private final ObjectMapper mapper = EventObjectMapperFactory.create();
    private final List<String> procesados = new CopyOnWriteArrayList<>();
    private JdbcTemplate jdbc;
    private CachingConnectionFactory conexion;
    private RabbitAdmin admin;
    private RabbitTemplate rabbit;
    private SimpleMessageListenerContainer contenedor;

    @BeforeEach
    void preparar() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/eventos/consumo.sql"));
        }
        jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM eventos_procesados");
        jdbc.update("DELETE FROM versiones_aplicadas");
        procesados.clear();

        conexion = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexion.setUsername(RABBIT.getAdminUsername());
        conexion.setPassword(RABBIT.getAdminPassword());
        admin = new RabbitAdmin(conexion);
        rabbit = new RabbitTemplate(conexion);
        for (String cola : List.of(".eventos", ".reintento.10s", ".reintento.1m", ".reintento.5m", ".dlq")) {
            admin.deleteQueue(SERVICIO + cola);
        }
    }

    @AfterEach
    void cerrar() {
        if (contenedor != null) {
            contenedor.stop();
        }
        conexion.destroy();
    }

    private void arrancar(ManejadorEvento<?>... manejadores) {
        InboxHelper inbox = new InboxHelper(jdbc);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        ConsumidorEventos consumidor = new ConsumidorEventos(SERVICIO, List.of(manejadores), inbox, tx, mapper, null);
        new TopologiaConsumo(admin, SERVICIO).declarar(consumidor.topicos());
        contenedor = new SimpleMessageListenerContainer(conexion);
        contenedor.setQueueNames(RabbitMQConstantes.colaEventos(SERVICIO));
        contenedor.setMessageListener(consumidor);
        contenedor.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        contenedor.setPrefetchCount(10);
        contenedor.start();
    }

    private ManejadorEvento<DatosPrueba> manejador(String nombre, Runnable antes, boolean porVersion) {
        return new ManejadorEvento<>() {
            public String consumidor() { return nombre; }
            public Set<String> topicos() { return Set.of(TOPICO); }
            public Class<DatosPrueba> tipoData() { return DatosPrueba.class; }
            public boolean descartarVersionesViejas() { return porVersion; }
            public void manejar(EventEnvelope<DatosPrueba> evento) {
                antes.run();
                procesados.add(nombre + ":" + evento.data().nombre() + ":" + evento.aggregateVersion());
            }
        };
    }

    private EventEnvelope<DatosPrueba> evento(UUID id, int version, String nombre) {
        return EventEnvelope.<DatosPrueba>builder()
                .eventId(id).eventType("turnos.turno.confirmado").version(1).producer("turnos")
                .aggregateType("turno").aggregateId("10").aggregateVersion(version)
                .data(new DatosPrueba(10L, nombre)).build();
    }

    private void publicar(Object cuerpo) throws Exception {
        byte[] bytes = cuerpo instanceof String s ? s.getBytes(StandardCharsets.UTF_8) : mapper.writeValueAsBytes(cuerpo);
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, TOPICO,
                MessageBuilder.withBody(bytes).setContentType("application/json").build());
    }

    private int mensajesEn(String sufijo) {
        var info = admin.getQueueInfo(SERVICIO + sufijo);
        return info == null ? 0 : info.getMessageCount();
    }

    private static void esperar(BooleanSupplier condicion, Duration maximo) throws InterruptedException {
        long limite = System.nanoTime() + maximo.toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se cumplió la condición en " + maximo);
            }
            Thread.sleep(100);
        }
    }

    @Test
    void eventoDuplicado_seProcesaUnaSolaVez() throws Exception {
        arrancar(manejador("mail", () -> {}, false));
        UUID id = UUID.randomUUID();

        publicar(evento(id, 1, "Ana"));
        publicar(evento(id, 1, "Ana"));

        esperar(() -> mensajesEn(".eventos") == 0 && !procesados.isEmpty(), Duration.ofSeconds(10));
        Thread.sleep(500);
        assertThat(procesados).containsExactly("mail:Ana:1");
    }

    @Test
    void eventoMalFormado_vaDirectoALaDlqSinReintentos() throws Exception {
        arrancar(manejador("mail", () -> {}, false));

        publicar("esto no es json");

        esperar(() -> mensajesEn(".dlq") == 1, Duration.ofSeconds(10));
        assertThat(mensajesEn(".reintento.10s")).isZero();
        Message enDlq = rabbit.receive(SERVICIO + ".dlq", 2_000);
        assertThat(enDlq.getMessageProperties().<Integer>getHeader(ConsumidorEventos.HEADER_INTENTOS)).isZero();
        assertThat(procesados).isEmpty();
    }

    @Test
    void dataQueNoRespetaElContrato_vaALaDlq() throws Exception {
        arrancar(manejador("mail", () -> {}, false));

        publicar("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"turnos.turno.confirmado\","
                + "\"version\":1,\"data\":{\"turnoId\":\"no-es-un-numero\"}}");

        esperar(() -> mensajesEn(".dlq") == 1, Duration.ofSeconds(10));
        assertThat(procesados).isEmpty();
    }

    @Test
    void handlerQueFallaUnaVez_seReintentaA10sYSeProcesa() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();
        arrancar(manejador("mail", () -> {
            if (llamadas.incrementAndGet() == 1) {
                throw new IllegalStateException("Resend caído");
            }
        }, false));

        publicar(evento(UUID.randomUUID(), 1, "Ana"));

        esperar(() -> mensajesEn(".reintento.10s") == 1, Duration.ofSeconds(5));
        assertThat(procesados).isEmpty();
        esperar(() -> procesados.size() == 1, Duration.ofSeconds(20));
        assertThat(mensajesEn(".reintento.10s")).isZero();
        assertThat(mensajesEn(".dlq")).isZero();
    }

    @Test
    void ultimoReintentoFallido_vaALaDlqConElError() throws Exception {
        arrancar(manejador("mail", () -> { throw new IllegalStateException("sigue caído"); }, false));

        // Simula un evento que ya pasó por los 3 niveles de espera.
        byte[] cuerpo = mapper.writeValueAsBytes(evento(UUID.randomUUID(), 1, "Ana"));
        rabbit.send("", SERVICIO + ".eventos", MessageBuilder.withBody(cuerpo)
                .setContentType("application/json").setHeader(ConsumidorEventos.HEADER_INTENTOS, 3).build());

        esperar(() -> mensajesEn(".dlq") == 1, Duration.ofSeconds(10));
        Message enDlq = rabbit.receive(SERVICIO + ".dlq", 2_000);
        assertThat(enDlq.getMessageProperties().<String>getHeader(ConsumidorEventos.HEADER_ERROR)).contains("sigue caído");
        assertThat(enDlq.getMessageProperties().<String>getHeader(ConsumidorEventos.HEADER_TOPICO)).isEqualTo(TOPICO);
    }

    @Test
    void dosHandlers_siFallaUnoElOtroNoSeRepite() throws Exception {
        AtomicInteger llamadasNotificacion = new AtomicInteger();
        arrancar(
                manejador("mail", () -> {}, false),
                manejador("notificacion", () -> {
                    if (llamadasNotificacion.incrementAndGet() == 1) {
                        throw new IllegalStateException("falla una vez");
                    }
                }, false));

        publicar(evento(UUID.randomUUID(), 1, "Ana"));

        esperar(() -> procesados.size() == 2, Duration.ofSeconds(20));
        Thread.sleep(500);
        assertThat(procesados).containsExactlyInAnyOrder("mail:Ana:1", "notificacion:Ana:1");
    }

    @Test
    void versionVieja_seDescartaSiElHandlerLoPide() throws Exception {
        arrancar(manejador("copia", () -> {}, true));

        publicar(evento(UUID.randomUUID(), 3, "Nueva"));
        esperar(() -> procesados.size() == 1, Duration.ofSeconds(10));
        publicar(evento(UUID.randomUUID(), 2, "Vieja"));
        esperar(() -> mensajesEn(".eventos") == 0, Duration.ofSeconds(10));
        Thread.sleep(500);

        assertThat(procesados).containsExactly("copia:Nueva:3");
        assertThat(jdbc.queryForObject("SELECT version FROM versiones_aplicadas WHERE consumidor = 'copia'",
                Integer.class)).isEqualTo(3);
    }
}
