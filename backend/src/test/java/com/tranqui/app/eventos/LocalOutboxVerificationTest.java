package com.tranqui.app.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.repository.*;
import com.tranqui.app.service.PagoWebhookHandler;
import com.tranqui.app.service.TurnoService;
import com.tranqui.eventos.Topicos;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Disabled("Verificación manual contra entorno Docker Compose local (docker-compose.local.yml)")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/tranqui_local",
        "spring.datasource.username=tranqui",
        "spring.datasource.password=tranqui-local",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=tranqui",
        "spring.rabbitmq.password=tranqui-local",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true",
        "spring.rabbitmq.template.mandatory=true",
        "eventos.relay.enabled=true",
        "eventos.relay.backup-poll-ms=1000",
        "payment.simulation.enabled=true"
})
class LocalOutboxVerificationTest {

    private static final String COLA_TEMPORAL = "cola-evidencia-local";

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private PagoWebhookHandler pagoWebhookHandler;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private PagoRepository pagoRepository;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @Test
    void verificarPublicacionEnEntornoLocal() throws Exception {
        System.out.println("=== INICIO PRUEBA EN LOCAL ===");

        // 1. Declarar cola temporal enlazada a tranqui.eventos con '#'
        Queue cola = new Queue(COLA_TEMPORAL, false, false, false);
        rabbitAdmin.declareQueue(cola);
        TopicExchange exchange = new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false);
        rabbitAdmin.declareExchange(exchange);
        Binding binding = BindingBuilder.bind(cola).to(exchange).with("#");
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(COLA_TEMPORAL, true);

        System.out.println("1. Cola temporal declarada y enlazada a 'tranqui.eventos' con '#': " + COLA_TEMPORAL);

        // 2. Insertar fila en outbox dentro de transacción de negocio
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] eventIdHolder = new UUID[1];

        tx.executeWithoutResult(status -> {
            EventEnvelope<String> env = outboxPublisher.publicar(
                    "turnos.turno.confirmado.v1",
                    "turno",
                    "turno-demo-local-4521",
                    1,
                    "{\"pacienteId\":88,\"profesionalId\":7,\"precio\":45000}"
            );
            eventIdHolder[0] = env.eventId();
        });

        UUID eventId = eventIdHolder[0];
        System.out.println("2. Evento insertado en outbox dentro de transacción. eventId: " + eventId);

        // 3. Esperar confirmación del relay
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OutboxRecord> record = outboxRepository.findByEventId(eventId);
            assertThat(record).isPresent();
            assertThat(record.get().getPublishedAt()).isNotNull();
        });

        OutboxRecord publicado = outboxRepository.findByEventId(eventId).orElseThrow();
        System.out.println("3. OutboxRecord confirmado por Relay:");
        System.out.println("   - ID: " + publicado.getId());
        System.out.println("   - Topic: " + publicado.getTopic());
        System.out.println("   - CreatedAt: " + publicado.getCreatedAt());
        System.out.println("   - PublishedAt: " + publicado.getPublishedAt());
        System.out.println("   - Attempts: " + publicado.getAttempts());

        // 4. Leer mensaje desde RabbitMQ en la cola temporal
        Object mensaje = rabbitTemplate.receiveAndConvert(COLA_TEMPORAL, 5000);
        assertThat(mensaje).isNotNull();

        String payload = mensaje instanceof byte[] b ? new String(b) : mensaje.toString();
        System.out.println("4. Mensaje recibido desde RabbitMQ en cola '" + COLA_TEMPORAL + "':");
        System.out.println("   Payload JSON: " + payload);

        JsonNode root = mapper.readTree(payload);
        assertThat(root.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(root.get("eventType").asText()).isEqualTo("turnos.turno.confirmado");
        assertThat(root.get("aggregateId").asText()).isEqualTo("turno-demo-local-4521");

        System.out.println("=== FIN PRUEBA EN LOCAL: EXCELENTE ===");
    }

    @Test
    void verificarFlujoCompletoReservaYPago() throws Exception {
        System.out.println("=== INICIO PRUEBA SIMULADOR: RESERVA -> PAGO -> CONFIRMACION ===");

        // 1. Declarar cola temporal enlazada a tranqui.eventos con '#'
        Queue cola = new Queue(COLA_TEMPORAL, false, false, false);
        rabbitAdmin.declareQueue(cola);
        TopicExchange exchange = new TopicExchange(RabbitMQConstantes.EXCHANGE_EVENTOS, true, false);
        rabbitAdmin.declareExchange(exchange);
        Binding binding = BindingBuilder.bind(cola).to(exchange).with("#");
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(COLA_TEMPORAL, true);

        // 2. Preparar médico y paciente
        Usuario medico = usuarioRepository.save(Usuario.builder()
                .email("dr.simulador." + System.currentTimeMillis() + "@test.com")
                .nombre("Dr Simulador")
                .rol(Rol.PSIQUIATRA)
                .googleCalendarConnected(false)
                .build());

        Usuario paciente = usuarioRepository.save(Usuario.builder()
                .email("paciente.simulador." + System.currentTimeMillis() + "@test.com")
                .nombre("Paciente Simulador")
                .rol(Rol.PACIENTE)
                .build());

        LocalDate fecha = LocalDate.now().plusDays(3);
        Disponibilidad disp = disponibilidadRepository.save(Disponibilidad.builder()
                .medico(medico)
                .diaSemana(fecha.getDayOfWeek().getValue())
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(18, 0))
                .build());

        // 3. Reservar turno usando TurnoService (produce TURNOS_TURNO_RESERVADO_V1)
        ReservaTurnoDto reservaDto = ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(fecha)
                .hora(LocalTime.of(10, 0))
                .tipo(TipoTurno.PARTICULAR)
                .modalidad(Modalidad.ONLINE)
                .nombrePaciente("Paciente Simulador")
                .emailPaciente(paciente.getEmail())
                .build();

        TurnoResponseDto respuestaReserva = turnoService.reservarTurno(reservaDto);
        Long turnoId = respuestaReserva.getTurnoId();
        System.out.println("1. Turno reservado exitosamente. Turno ID: " + turnoId + ", Estado: " + respuestaReserva.getEstado());

        // 4. Procesar pago aprobado vía PagoWebhookHandler (produce PAGOS_PAGO_APROBADO_V1 y TURNOS_TURNO_CONFIRMADO_V1)
        pagoWebhookHandler.procesarAprobacionTurno(turnoId, "PAY-SIMULADO-" + System.currentTimeMillis());
        System.out.println("2. Pago aprobado procesado para Turno ID: " + turnoId);

        // 5. Verificar que OutboxRelay publica los eventos a RabbitMQ y recibirlos
        List<String> topicosRecibidos = new ArrayList<>();
        List<String> payloadsRecibidos = new ArrayList<>();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            while (topicosRecibidos.size() < 3) {
                org.springframework.amqp.core.Message msg = rabbitTemplate.receive(COLA_TEMPORAL, 2000);
                if (msg == null) break;
                String rk = msg.getMessageProperties().getReceivedRoutingKey();
                String body = new String(msg.getBody());
                topicosRecibidos.add(rk);
                payloadsRecibidos.add(body);
                System.out.println("   [RabbitMQ Evento Recibido] Topico: " + rk);
                System.out.println("   Payload: " + body);
            }
            assertThat(topicosRecibidos).contains(
                    Topicos.TURNOS_TURNO_RESERVADO_V1,
                    Topicos.PAGOS_PAGO_APROBADO_V1,
                    Topicos.TURNOS_TURNO_CONFIRMADO_V1
            );
        });

        System.out.println("=== FIN PRUEBA SIMULADOR: 3 EVENTOS VERIFICADOS EXITOSAMENTE (reservado -> aprobado -> confirmado) ===");
    }
}
