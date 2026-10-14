package com.tranqui.app.eventos;

import com.tranqui.eventos.RabbitMQConstantes;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Componente Relay para la tabla outbox (sección 6.1 de docs/arquitectura/microservicios.md).
 *
 * Escucha notificaciones en tiempo real vía PostgreSQL LISTEN 'outbox', ejecuta barridos
 * de respaldo periódicos cada 30 segundos, publica eventos a RabbitMQ con publisher confirms
 * y mandatory routing, y marca published_at únicamente ante confirmación exitosa del broker.
 */
@Component
@ConditionalOnProperty(prefix = "eventos.relay", name = "enabled", havingValue = "true")
public class OutboxRelay implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final DataSource dataSource;
    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final BlockingQueue<Boolean> triggerQueue = new LinkedBlockingQueue<>(10);

    private Thread listenerThread;
    private Thread workerThread;

    public OutboxRelay(DataSource dataSource,
                       OutboxRepository outboxRepository,
                       RabbitTemplate rabbitTemplate,
                       PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Iniciando OutboxRelay de mensajería...");

            workerThread = new Thread(this::runWorkerLoop, "outbox-relay-worker");
            workerThread.setDaemon(true);
            workerThread.start();

            listenerThread = new Thread(this::runListenLoop, "outbox-relay-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            trigger();
        }
    }

    public void trigger() {
        triggerQueue.offer(Boolean.TRUE);
    }

    /**
     * Barrido de respaldo cada 30 segundos según sección 6.1.
     */
    @Scheduled(fixedDelayString = "${eventos.relay.backup-poll-ms:30000}", initialDelay = 10000)
    public void barridoDeRespaldo() {
        if (!running.get()) {
            return;
        }
        log.debug("Ejecutando barrido de respaldo cada 30s de la outbox...");
        trigger();
    }

    private void runWorkerLoop() {
        while (running.get()) {
            try {
                triggerQueue.poll(5, TimeUnit.SECONDS);
                procesarPendientes();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error en bucle de procesamiento de OutboxRelay: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Procesa lotes de hasta 100 eventos pendientes en orden de id con SELECT ... FOR UPDATE SKIP LOCKED.
     */
    public void procesarPendientes() {
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        }
        try {
            boolean hayMas = true;
            long backoffMs = 1000;
            while (hayMas && running.get()) {
                ProcesamientoLoteResultado resultado = transactionTemplate.execute(status -> procesarLote());
                if (resultado == null || resultado.procesados() == 0) {
                    hayMas = false;
                } else if (resultado.fallo()) {
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    hayMas = false;
                } else {
                    backoffMs = 1000;
                    hayMas = resultado.procesados() == BATCH_SIZE;
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }

    private record ProcesamientoLoteResultado(int procesados, boolean fallo) {}

    private ProcesamientoLoteResultado procesarLote() {
        List<OutboxRecord> batch = outboxRepository.findPendientesConLock(BATCH_SIZE);
        if (batch.isEmpty()) {
            return new ProcesamientoLoteResultado(0, false);
        }

        boolean fallo = false;
        int count = 0;
        for (OutboxRecord record : batch) {
            count++;
            try {
                publicarAMQP(record);
                record.setPublishedAt(OffsetDateTime.now());
                record.setLastError(null);
                outboxRepository.save(record);
                log.debug("Evento {} publicado a RabbitMQ en tópico {}", record.getEventId(), record.getTopic());
            } catch (Exception e) {
                fallo = true;
                record.setAttempts(record.getAttempts() + 1);
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                record.setLastError(errMsg);
                outboxRepository.save(record);
                log.warn("Fallo al publicar evento {} a RabbitMQ (intento {}): {}", record.getEventId(), record.getAttempts(), errMsg);
                break;
            }
        }
        return new ProcesamientoLoteResultado(count, fallo);
    }

    private void publicarAMQP(OutboxRecord record) throws Exception {
        CorrelationData correlationData = new CorrelationData(record.getEventId().toString());

        rabbitTemplate.convertAndSend(
            RabbitMQConstantes.EXCHANGE_EVENTOS,
            record.getTopic(),
            record.getPayload(),
            message -> {
                message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                message.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
                message.getMessageProperties().setMessageId(record.getEventId().toString());
                return message;
            },
            correlationData
        );

        CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
        if (confirm == null || !confirm.isAck()) {
            String reason = (confirm != null && confirm.getReason() != null) ? confirm.getReason() : "Broker no confirmó recepción";
            throw new IllegalStateException("Confirmación de broker negativa para " + record.getEventId() + ": " + reason);
        }
    }

    private void runListenLoop() {
        long backoffSegundos = 1;
        while (running.get()) {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isWrapperFor(PGConnection.class)) {
                    PGConnection pgConn = conn.unwrap(PGConnection.class);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("LISTEN outbox");
                    }
                    log.info("Relay conectado exitosamente a PostgreSQL LISTEN 'outbox'");
                    backoffSegundos = 1;
                    trigger();

                    while (running.get() && !conn.isClosed()) {
                        PGNotification[] notifications = pgConn.getNotifications(5000);
                        if (notifications != null && notifications.length > 0) {
                            trigger();
                        }
                    }
                } else {
                    log.info("Conexión de base de datos no soporta PGConnection. Relay operará por sondeo periódico.");
                    while (running.get()) {
                        Thread.sleep(5000);
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.warn("Error en conexión LISTEN de outbox (reconectando en {}s): {}", backoffSegundos, e.getMessage());
                    try {
                        Thread.sleep(backoffSegundos * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoffSegundos = Math.min(backoffSegundos * 2, 30);
                }
            }
        }
    }

    @Override
    public void destroy() {
        log.info("Deteniendo OutboxRelay...");
        running.set(false);
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
