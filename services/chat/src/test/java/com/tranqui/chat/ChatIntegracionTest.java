package com.tranqui.chat;

import com.tranqui.eventos.contratos.Contratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.chat.copias.Copias;
import com.tranqui.chat.interno.CargaInicial;
import com.tranqui.chat.interno.ClienteInterno;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.jwt.ServiceJwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.tranqui.chat.seguridad.JwtTestHelper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** El servicio de chat completo contra PostgreSQL y RabbitMQ reales (Fase 2). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "chat.carga-inicial.habilitada=false",
        "jwt.secret=" + ChatIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + ChatIntegracionTest.CLAVE_SERVICIO,
        "tranqui.gateway.url=http://localhost:1"
})
class ChatIntegracionTest {

    static final String CLAVE_JWT = JwtTestHelper.CLAVE_JWT;
    static final String CLAVE_SERVICIO = "clave-servicio-test-de-32-bytes-ok";

    @DynamicPropertySource
    static void registrarJwt(DynamicPropertyRegistry registry) {
        JwtTestHelper.registrarPropiedades(registry);
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;

    @Autowired Copias copias;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate rabbit;
    @Autowired CargaInicial carga;
    @LocalServerPort int puerto;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("chat"));
            return info != null && info.getConsumerCount() > 0;
        });
        jdbc.update("DELETE FROM mensaje");
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM turno_confirmado");
        jdbc.update("DELETE FROM eventos_procesados");
        jdbc.update("DELETE FROM versiones_aplicadas");
        jdbc.update("DELETE FROM participante");
        copias.guardarParticipante(1L, "pac@mail.com", "Ana", "Paz", "PACIENTE");
        copias.guardarParticipante(2L, "dra@mail.com", "Dra. Ruiz", "Ruiz", "PSIQUIATRA");
        copias.guardarParticipante(3L, "pac2@mail.com", "Beto", "Sol", "PACIENTE");
        copias.guardarParticipante(4L, "visi@mail.com", "Vico", "Lab", "VISITADOR");
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se cumplió la condición a tiempo");
            }
            Thread.sleep(100);
        }
    }

    private static String token(String email, String rol) {
        return JwtTestHelper.tokenRs256(email, rol);
    }

    private ResponseEntity<String> get(String ruta, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void insertarMensaje(long remitente, long destinatario, String contenido, boolean leido) {
        jdbc.update("INSERT INTO mensaje (remitente_id, destinatario_id, contenido, leido, fecha_envio) VALUES (?, ?, ?, ?, ?)",
                remitente, destinatario, contenido, leido, java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ en vivo

    private StompSession conectar(String token) throws Exception {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new MappingJackson2MessageConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        return cliente.connectAsync("ws://localhost:" + puerto + "/ws-tranqui", headers, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private static CompletableFuture<Map<String, Object>> escuchar(StompSession sesion, String destino) {
        CompletableFuture<Map<String, Object>> recibido = new CompletableFuture<>();
        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders h, Object payload) {
                recibido.complete((Map<String, Object>) payload);
            }
        });
        return recibido;
    }

    @Test
    void chatEnVivo_elMensajeLlegaAlDestinatarioConAvisoYSePublicaElEvento() throws Exception {
        StompSession profesional = conectar(token("dra@mail.com", "PSIQUIATRA"));
        CompletableFuture<Map<String, Object>> mensaje = escuchar(profesional, "/user/queue/mensajes");
        CompletableFuture<Map<String, Object>> aviso = escuchar(profesional, "/topic/notificaciones/2");
        StompSession paciente = conectar(token("pac@mail.com", "PACIENTE"));
        CompletableFuture<Map<String, Object>> confirmacion = escuchar(paciente, "/user/queue/mensajes");
        Thread.sleep(500);

        paciente.send("/app/chat.enviar", Map.of("destinatarioId", 2, "contenido", "hola doctora"));

        Map<String, Object> recibido = mensaje.get(10, TimeUnit.SECONDS);
        assertThat(recibido.get("contenido")).isEqualTo("hola doctora");
        assertThat(((Number) recibido.get("remitenteId")).longValue()).isEqualTo(1L);
        assertThat(((Number) recibido.get("destinatarioId")).longValue()).isEqualTo(2L);
        assertThat(recibido.get("fechaEnvio")).isNotNull();
        assertThat(confirmacion.get(10, TimeUnit.SECONDS).get("id")).isEqualTo(recibido.get("id"));
        Map<String, Object> avisoRecibido = aviso.get(10, TimeUnit.SECONDS);
        assertThat(avisoRecibido.get("tipo")).isEqualTo("NUEVO_MENSAJE");
        assertThat(avisoRecibido.get("titulo")).isEqualTo("Nuevo mensaje de Ana");

        // El evento sale por la outbox y no lleva el contenido (sección 5.3).
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Long.class) == 1);
        JsonNode sobre = mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox", String.class));
        assertThat(sobre.get("eventType").asText()).isEqualTo("chat.mensaje.enviado");
        assertThat(sobre.toString()).doesNotContain("hola doctora");
        profesional.disconnect();
        paciente.disconnect();
    }

    // ------------------------------------------------------------------ REST

    @Test
    void historial_conElFormatoDelFrontendYMarcaLeidos() throws Exception {
        insertarMensaje(1, 2, "primero", false);
        insertarMensaje(2, 1, "respuesta", false);

        ResponseEntity<String> respuesta = get("/api/chat/historial/1", token("dra@mail.com", "PSIQUIATRA"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pagina = mapper.readTree(respuesta.getBody());
        assertThat(pagina.get("content")).hasSize(2);
        JsonNode masNuevo = pagina.get("content").get(0);
        assertThat(masNuevo.get("remitente").get("id").asLong()).isEqualTo(2L);
        assertThat(masNuevo.get("destinatario").get("id").asLong()).isEqualTo(1L);
        assertThat(masNuevo.has("contenido")).isTrue();
        assertThat(masNuevo.has("fechaEnvio")).isTrue();
        // Al abrir la conversación, los que le mandó la paciente quedan leídos.
        assertThat(get("/api/chat/tiene-no-leidos", token("dra@mail.com", "PSIQUIATRA")).getBody()).isEqualTo("false");
        assertThat(get("/api/chat/tiene-no-leidos", token("pac@mail.com", "PACIENTE")).getBody()).isEqualTo("true");
    }

    @Test
    void canales_prioridadAltaConTurnoConfirmadoEnLosProximosDias() throws Exception {
        insertarMensaje(1, 2, "de Ana", false);
        insertarMensaje(3, 2, "de Beto", false);
        insertarMensaje(3, 2, "otro de Beto", false);
        // El turno confirmado de Ana llega como evento (copia local de turnos).
        TurnoConfirmadoData turno = new TurnoConfirmadoData(70L, 1L, 2L, LocalDate.now().plusDays(1), "10:00", "ONLINE",
                new BigDecimal("1000"), true, "Consulta", null, null, null, TurnoConfirmadoData.ORIGEN_PAGO);
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.TURNOS_TURNO_CONFIRMADO_V1, MessageBuilder
                .withBody(mapper.writeValueAsBytes(EventEnvelope.<TurnoConfirmadoData>builder().eventId(UUID.randomUUID())
                        .eventType("turnos.turno.confirmado").producer("monolito").aggregateType("turno").aggregateId("70")
                        .aggregateVersion(2).data(turno).build()))
                .setContentType("application/json").build());
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM turno_confirmado", Long.class) == 1);

        JsonNode canales = mapper.readTree(get("/api/chat/canales", token("dra@mail.com", "PSIQUIATRA")).getBody());

        assertThat(canales).hasSize(2);
        assertThat(canales.get(0).get("nombre").asText()).isEqualTo("Ana");
        assertThat(canales.get(0).get("prioridadClinica").asText()).isEqualTo("PRIORIDAD_ALTA");
        assertThat(canales.get(1).get("nombre").asText()).isEqualTo("Beto");
        assertThat(canales.get(1).get("mensajesSinLeer").asInt()).isEqualTo(2);
        JsonNode delPaciente = mapper.readTree(get("/api/chat/canales", token("pac@mail.com", "PACIENTE")).getBody());
        assertThat(delPaciente).hasSize(1);
        assertThat(delPaciente.get(0).get("nombre").asText()).isEqualTo("Dra. Ruiz");
    }

    @Test
    void visitadores_soloParaElProfesional() throws Exception {
        insertarMensaje(4, 2, "le traigo muestras", false);

        JsonNode canales = mapper.readTree(get("/api/chat/canales/visitadores", token("dra@mail.com", "PSIQUIATRA")).getBody());

        assertThat(canales).hasSize(1);
        assertThat(canales.get(0).get("nombre").asText()).isEqualTo("Vico");
        assertThat(get("/api/chat/canales/visitadores", token("pac@mail.com", "PACIENTE")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/chat/historial/2", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/chat/canales", token("admin@mail.com", "ADMIN")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------ interno y carga inicial

    @Test
    void resumenInterno_conJwtDeServicio() throws Exception {
        insertarMensaje(1, 2, "a", false);
        insertarMensaje(1, 2, "b", false);
        insertarMensaje(3, 2, "c", true);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(new ServiceJwt(CLAVE_SERVICIO).emitirToken("monolito"));

        ResponseEntity<String> respuesta = http.exchange("/internal/chat/profesionales/2/resumen", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        Contratos.mismaForma("internas/chat/resumen.json", respuesta.getBody());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode resumen = mapper.readTree(respuesta.getBody());
        assertThat(resumen.get("pacientesConChat").toString()).contains("1").contains("3");
        assertThat(resumen.get("noLeidosPorPaciente").get("1").asInt()).isEqualTo(2);
        assertThat(resumen.get("noLeidosPorPaciente").has("3")).isFalse();
        assertThat(http.getForEntity("/internal/chat/profesionales/2/resumen", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cargaInicial_copiaLosMensajesConSusIdsYLosNuevosSiguenDespues() throws Exception {
        jdbc.update("DELETE FROM participante");
        when(interno.contactos(anyInt(), anyInt())).thenReturn(List.of(
                new ClienteInterno.Contacto(1L, "pac@mail.com", "Ana", "Paz", "PACIENTE", true),
                new ClienteInterno.Contacto(2L, "dra@mail.com", "Dra. Ruiz", "Ruiz", "PSIQUIATRA", true)));
        when(interno.turnosConfirmados()).thenReturn(List.of());
        when(interno.mensajes(0L, 500)).thenReturn(List.of(
                new ClienteInterno.MensajeMonolito(100L, 1L, 2L, "viejo 1", true, LocalDateTime.now().minusDays(2)),
                new ClienteInterno.MensajeMonolito(101L, 2L, 1L, "viejo 2", false, LocalDateTime.now().minusDays(1))));
        when(interno.mensajes(101L, 500)).thenReturn(List.of());

        java.lang.reflect.Method metodo = CargaInicial.class.getDeclaredMethod("cargarConReintentos");
        metodo.setAccessible(true);
        metodo.invoke(carga);

        assertThat(jdbc.queryForList("SELECT id FROM mensaje ORDER BY id", Long.class)).containsExactly(100L, 101L);
        assertThat(copias.cantidadParticipantes()).isEqualTo(2);
        StompSession paciente = conectar(token("pac@mail.com", "PACIENTE"));
        CompletableFuture<Map<String, Object>> confirmacion = escuchar(paciente, "/user/queue/mensajes");
        Thread.sleep(300);
        paciente.send("/app/chat.enviar", Map.of("destinatarioId", 2, "contenido", "nuevo"));
        assertThat(((Number) confirmacion.get(10, TimeUnit.SECONDS).get("id")).longValue()).isGreaterThan(101L);
        paciente.disconnect();
    }

    @Test
    void transicion_tokenHs256_esAceptado() {
        String tokenHs = JwtTestHelper.tokenHs256("dra@mail.com", "PSIQUIATRA");
        assertThat(get("/api/chat/tiene-no-leidos", tokenHs).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rutasDelChat_conTokenRs256FirmadoConOtraClave_devuelve401() {
        String tokenOtra = JwtTestHelper.tokenRs256OtraClave("dra@mail.com", "PSIQUIATRA");
        assertThat(get("/api/chat/tiene-no-leidos", tokenOtra).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
