package com.tranqui.eventos.outbox;

import com.tranqui.eventos.RabbitMQConstantes;
import io.micrometer.core.instrument.MeterRegistry;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relay de la outbox de un servicio (sección 6.1). Mismo comportamiento que el {@code OutboxRelay}
 * del monolito:
 * <ul>
 *   <li>Escucha {@code LISTEN outbox} con una conexión propia fuera del pool y publica en el momento.</li>
 *   <li>Barrido de respaldo periódico (por si se perdió un aviso al reconectar).</li>
 *   <li>Lotes de 100 en orden de id con {@code FOR UPDATE SKIP LOCKED}; publisher confirms y
 *       {@code mandatory}; marca {@code published_at} solo con la confirmación del broker.</li>
 *   <li>Un mensaje sin cola enlazada se marca igual (no bloquea la outbox) y se cuenta en
 *       {@code outbox.eventos.sin_ruta}.</li>
 * </ul>
 * Requiere {@code spring.rabbitmq.publisher-confirm-type=correlated} y {@code publisher-returns=true}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int LOTE = 100;

    record Pendiente(long id, UUID eventId, String topico, String payload) {}

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaccion;
    private final MeterRegistry metricas;
    private final String jdbcUrl;
    private final String usuario;
    private final String clave;

    private final AtomicBoolean corriendo = new AtomicBoolean(true);
    private final AtomicBoolean iniciado = new AtomicBoolean(false);
    private final AtomicBoolean procesando = new AtomicBoolean(false);
    private final BlockingQueue<Boolean> avisos = new LinkedBlockingQueue<>(10);
    private Thread hiloEscucha;
    private Thread hiloTrabajo;
    private volatile Connection conexionEscucha;

    public OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, PlatformTransactionManager transacciones,
                       MeterRegistry metricas, String jdbcUrl, String usuario, String clave) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.metricas = metricas;
        this.jdbcUrl = jdbcUrl;
        this.usuario = usuario;
        this.clave = clave;
    }

    /** Arranca los hilos de escucha y de publicación. Lo llama la autoconfiguración con la app lista. */
    public synchronized void iniciar() {
        if (!iniciado.compareAndSet(false, true)) {
            return;
        }
        hiloTrabajo = new Thread(this::bucleTrabajo, "outbox-relay-worker");
        hiloTrabajo.setDaemon(true);
        hiloTrabajo.start();
        hiloEscucha = new Thread(this::bucleEscucha, "outbox-relay-listener");
        hiloEscucha.setDaemon(true);
        hiloEscucha.start();
        avisar();
    }

    /** Pide una vuelta de publicación (aviso de LISTEN o barrido de respaldo). */
    public void avisar() {
        if (iniciado.get()) {
            avisos.offer(Boolean.TRUE);
        }
    }

    private void bucleTrabajo() {
        while (corriendo.get()) {
            try {
                avisos.poll(5, TimeUnit.SECONDS);
                procesarPendientes();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Error en el relay de la outbox: {}", e.getMessage(), e);
            }
        }
    }

    /** Publica lotes hasta vaciar la outbox o hasta el primer fallo (que espera y se reintenta). */
    public void procesarPendientes() {
        if (!procesando.compareAndSet(false, true)) {
            return;
        }
        try {
            long esperaMs = 1_000;
            while (corriendo.get()) {
                int[] resultado = transaccion.execute(estado -> procesarLote());
                if (resultado == null || resultado[0] == 0) {
                    return;
                }
                if (resultado[1] == 1) {
                    try {
                        Thread.sleep(esperaMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                if (resultado[0] < LOTE) {
                    return;
                }
                esperaMs = 1_000;
            }
        } finally {
            procesando.set(false);
        }
    }

    /** @return {cantidad procesada, 1 si hubo un fallo} */
    private int[] procesarLote() {
        List<Pendiente> lote = jdbc.query(
                "SELECT id, event_id, topic, payload::text AS payload FROM outbox WHERE published_at IS NULL "
                        + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, i) -> new Pendiente(rs.getLong("id"), (UUID) rs.getObject("event_id"), rs.getString("topic"),
                        rs.getString("payload")),
                LOTE);
        int procesados = 0;
        for (Pendiente p : lote) {
            procesados++;
            try {
                publicar(p);
                jdbc.update("UPDATE outbox SET published_at = now(), last_error = NULL WHERE id = ?", p.id());
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                jdbc.update("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?", error, p.id());
                log.warn("No se pudo publicar el evento {} ({}): {}", p.eventId(), p.topico(), error);
                return new int[]{procesados, 1};
            }
        }
        return new int[]{procesados, 0};
    }

    private void publicar(Pendiente p) throws Exception {
        CorrelationData correlacion = new CorrelationData(p.eventId().toString());
        rabbit.convertAndSend(RabbitMQConstantes.EXCHANGE_EVENTOS, p.topico(), p.payload(), mensaje -> {
            mensaje.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            mensaje.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
            mensaje.getMessageProperties().setMessageId(p.eventId().toString());
            return mensaje;
        }, correlacion);
        CorrelationData.Confirm confirmacion = correlacion.getFuture().get(5, TimeUnit.SECONDS);
        if (confirmacion == null || !confirmacion.isAck()) {
            throw new IllegalStateException("El broker no confirmó el evento " + p.eventId()
                    + (confirmacion != null && confirmacion.getReason() != null ? ": " + confirmacion.getReason() : ""));
        }
        if (correlacion.getReturned() != null) {
            log.warn("Evento {} sin cola enlazada para '{}' (replyCode {}): se marca publicado igual",
                    p.eventId(), p.topico(), correlacion.getReturned().getReplyCode());
            if (metricas != null) {
                metricas.counter("outbox.eventos.sin_ruta", "topico", p.topico()).increment();
            }
        }
    }

    private Connection abrirConexionEscucha() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(jdbcUrl);
        if (usuario != null) {
            ds.setUser(usuario);
        }
        if (clave != null) {
            ds.setPassword(clave);
        }
        return ds.getConnection();
    }

    private void bucleEscucha() {
        long esperaSegundos = 1;
        while (corriendo.get()) {
            try (Connection conexion = abrirConexionEscucha()) {
                conexionEscucha = conexion;
                PGConnection pg = conexion.unwrap(PGConnection.class);
                try (Statement st = conexion.createStatement()) {
                    st.execute("LISTEN outbox");
                }
                log.info("Relay de la outbox escuchando LISTEN outbox con conexión propia");
                esperaSegundos = 1;
                avisar();
                while (corriendo.get() && !conexion.isClosed()) {
                    PGNotification[] notificaciones = pg.getNotifications(5_000);
                    if (notificaciones != null && notificaciones.length > 0) {
                        avisar();
                    }
                }
            } catch (Exception e) {
                if (!corriendo.get()) {
                    return;
                }
                log.warn("Se cortó el LISTEN de la outbox; se reconecta en {} s: {}", esperaSegundos, e.getMessage());
                try {
                    Thread.sleep(esperaSegundos * 1_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                esperaSegundos = Math.min(esperaSegundos * 2, 30);
            } finally {
                conexionEscucha = null;
            }
        }
    }

    public void detener() {
        corriendo.set(false);
        Connection conexion = conexionEscucha;
        if (conexion != null) {
            try {
                conexion.close();
            } catch (Exception ignorada) {
                // se está apagando
            }
        }
        if (hiloEscucha != null) {
            hiloEscucha.interrupt();
        }
        if (hiloTrabajo != null) {
            hiloTrabajo.interrupt();
        }
    }
}
