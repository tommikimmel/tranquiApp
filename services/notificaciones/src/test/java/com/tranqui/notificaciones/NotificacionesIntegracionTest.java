package com.tranqui.notificaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TicketCreadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.notificaciones.contacto.ContactoRepository;
import com.tranqui.notificaciones.contacto.ContactoService;
import com.tranqui.notificaciones.mail.EnvioMailException;
import com.tranqui.notificaciones.mail.ResendEmailService;
import com.tranqui.notificaciones.notificacion.NotificacionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.tranqui.notificaciones.seguridad.JwtTestHelper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El servicio completo contra PostgreSQL y RabbitMQ reales: eventos que llegan por
 * {@code tranqui.eventos}, mails (el transporte se reemplaza por un mock), notificaciones en la
 * base, idempotencia, reintentos y la API para el frontend.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "notificaciones.carga-inicial.habilitada=false",
        "jwt.secret=clave-de-test-de-al-menos-32-bytes-ok",
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "tranqui.gateway.url=http://localhost:1"
})
class NotificacionesIntegracionTest {

    static final String CLAVE_JWT = JwtTestHelper.CLAVE_JWT;

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

    @MockitoBean
    ResendEmailService mails;

    @Autowired RabbitTemplate rabbit;
    @Autowired ContactoService contactos;
    @Autowired ContactoRepository contactoRepository;
    @Autowired NotificacionRepository notificaciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        // El consumo arranca en segundo plano con la app lista: se espera a que la cola tenga consumidor.
        org.springframework.amqp.rabbit.core.RabbitAdmin admin =
                new org.springframework.amqp.rabbit.core.RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("notificaciones"));
            return info != null && info.getConsumerCount() > 0;
        });
        reset(mails);
        jdbc.update("DELETE FROM notificacion");
        jdbc.update("DELETE FROM eventos_procesados");
        jdbc.update("DELETE FROM versiones_aplicadas");
        jdbc.update("DELETE FROM contacto");
        contactos.guardar(1L, "paciente@mail.com", "Ana", "Paz", "PACIENTE", true);
        contactos.guardar(2L, "dra@mail.com", "Dra. Ruiz", "Ruiz", "PSIQUIATRA", true);
        contactos.guardar(3L, "admin@mail.com", "Admin", null, "ADMIN", true);
    }

    private <T> UUID publicar(String topico, String aggregateId, int version, T data) throws Exception {
        UUID id = UUID.randomUUID();
        publicar(id, topico, aggregateId, version, data);
        return id;
    }

    private <T> void publicar(UUID id, String topico, String aggregateId, int version, T data) throws Exception {
        EventEnvelope<T> sobre = EventEnvelope.<T>builder()
                .eventId(id).eventType(Topicos.extraerTipoEvento(topico)).version(Topicos.extraerVersion(topico))
                .producer("monolito").aggregateType(topico.split("\\.")[1]).aggregateId(aggregateId)
                .aggregateVersion(version).data(data).build();
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, topico,
                MessageBuilder.withBody(mapper.writeValueAsBytes(sobre)).setContentType("application/json").build());
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se cumplió la condición a tiempo");
            }
            Thread.sleep(100);
        }
    }

    private TurnoConfirmadoData turnoConfirmado() {
        return new TurnoConfirmadoData(50L, 1L, 2L, LocalDate.of(2026, 10, 30), "10:00", "ONLINE",
                new BigDecimal("45000"), true, "Consulta particular", null, null, "https://meet.google.com/x",
                TurnoConfirmadoData.ORIGEN_PAGO);
    }

    @Test
    void turnoConfirmado_creaAvisosYMailsUnaSolaVezAunqueElEventoLlegueDosVeces() throws Exception {
        UUID id = UUID.randomUUID();
        publicar(id, Topicos.TURNOS_TURNO_CONFIRMADO_V1, "50", 3, turnoConfirmado());
        publicar(id, Topicos.TURNOS_TURNO_CONFIRMADO_V1, "50", 3, turnoConfirmado());

        esperar(() -> notificaciones.count() == 2);
        verify(mails, timeout(10_000)).enviarNuevoTurnoProfesional(eq("dra@mail.com"), eq("Dra. Ruiz"), eq("Ana"),
                eq(LocalDate.of(2026, 10, 30)), any(), eq("ONLINE"), eq("Consulta particular"));
        verify(mails, timeout(10_000)).enviarTurnoConfirmadoPaciente(eq("paciente@mail.com"), eq("Ana"), eq("Dra. Ruiz"),
                eq(LocalDate.of(2026, 10, 30)), any(), eq(true), eq("Consulta particular"), any(),
                eq("https://meet.google.com/x"), eq(new BigDecimal("45000")));
        Thread.sleep(1_000);

        assertThat(notificaciones.count()).isEqualTo(2);
        verify(mails, times(1)).enviarNuevoTurnoProfesional(anyString(), anyString(), anyString(), any(), any(), any(), any());
        verify(mails, times(1)).enviarTurnoConfirmadoPaciente(anyString(), anyString(), anyString(), any(), any(),
                anyBoolean(), any(), any(), any(), any());
        assertThat(notificaciones.findAll()).extracting("titulo")
                .containsExactlyInAnyOrder("Nuevo Turno Reservado", "Turno confirmado");
    }

    @Test
    void pacienteSinMails_recibeLaNotificacionPeroNoElMail() throws Exception {
        contactos.guardar(1L, null, null, null, null, false);

        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "50", 4, new TurnoCanceladoData(50L, 1L, 2L, "PACIENTE",
                "Cancelado por usuario", false, LocalDate.of(2026, 10, 30), "10:00", true, "SIN_PAGO"));

        esperar(() -> notificaciones.count() == 2);
        verify(mails, timeout(10_000)).enviarTurnoCanceladoProfesional(eq("dra@mail.com"), any(), eq("Ana"), any(), any());
        Thread.sleep(1_000);
        verify(mails, never()).enviarTurnoCanceladoPaciente(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void mailQueFallaUnaVez_seReintentaYSaleUnaSolaVez() throws Exception {
        doThrow(new EnvioMailException("Resend caído")).doNothing()
                .when(mails).enviarNuevoTurnoProfesional(anyString(), anyString(), anyString(), any(), any(), any(), any());

        publicar(Topicos.TURNOS_TURNO_CONFIRMADO_V1, "50", 3, turnoConfirmado());

        // Los demás avisos no esperan al mail que falló: salen en el primer intento.
        verify(mails, timeout(5_000)).enviarTurnoConfirmadoPaciente(anyString(), anyString(), anyString(), any(), any(),
                anyBoolean(), any(), any(), any(), any());
        esperar(() -> notificaciones.count() == 2);
        // El mail que falló vuelve por la cola de 10 s y sale.
        verify(mails, timeout(25_000).times(2))
                .enviarNuevoTurnoProfesional(anyString(), anyString(), anyString(), any(), any(), any(), any());
        // Los que ya habían salido no se repiten en el reintento.
        Thread.sleep(1_000);
        verify(mails, times(1)).enviarTurnoConfirmadoPaciente(anyString(), anyString(), anyString(), any(), any(),
                anyBoolean(), any(), any(), any(), any());
        assertThat(notificaciones.count()).isEqualTo(2);
    }

    @Test
    void contactos_seMantienenConLosEventosYDescartanVersionesViejas() throws Exception {
        publicar(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, "9", 1,
                new UsuarioRegistradoData(9L, "nuevo@mail.com", "Leo", "Sosa", "PACIENTE"));
        esperar(() -> contactoRepository.existsById(9L));

        publicar(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, "9", 3,
                new UsuarioActualizadoData(9L, "leo@mail.com", "Leo", "Sosa", null, "PACIENTE", false));
        esperar(() -> contactoRepository.findById(9L).map(c -> c.getEmail().equals("leo@mail.com")).orElse(false));
        publicar(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, "9", 2,
                new UsuarioActualizadoData(9L, "viejo@mail.com", "Leo", "Sosa", null, "PACIENTE", true));
        Thread.sleep(1_500);

        assertThat(contactoRepository.findById(9L).orElseThrow().getEmail()).isEqualTo("leo@mail.com");
        assertThat(contactoRepository.findById(9L).orElseThrow().isEmailHabilitado()).isFalse();
    }

    @Test
    void ticketNuevo_avisaATodosLosAdministradores() throws Exception {
        publicar(Topicos.IDENTIDAD_TICKET_CREADO_V1, "77", 1, new TicketCreadoData(77L, 1L, "Ana Paz", "No puedo pagar"));

        esperar(() -> notificaciones.count() == 1);
        assertThat(notificaciones.findAll().get(0).getUsuarioId()).isEqualTo(3L);
        assertThat(notificaciones.findAll().get(0).getMensaje()).isEqualTo("Ana Paz abrió un ticket: No puedo pagar");
    }

    // ---------------------------------------------------------------- API para el frontend

    private static String token(String email, String rol) {
        return JwtTestHelper.tokenRs256(email, rol);
    }

    private ResponseEntity<String> get(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        return http.exchange("/api/notificaciones", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void api_listaLasNotificacionesDelUsuarioYMarcaLeidas() throws Exception {
        publicar(Topicos.TURNOS_TURNO_CONFIRMADO_V1, "50", 3, turnoConfirmado());
        esperar(() -> notificaciones.count() == 2);

        ResponseEntity<String> respuesta = get(token("paciente@mail.com", "PACIENTE"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"titulo\":\"Turno confirmado\"").contains("\"leido\":false")
                .doesNotContain("Nuevo Turno Reservado");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token("paciente@mail.com", "PACIENTE"));
        ResponseEntity<Void> marcar = http.exchange("/api/notificaciones/marcar-leidas", HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
        assertThat(marcar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(token("paciente@mail.com", "PACIENTE")).getBody()).contains("\"leido\":true");
    }

    @Test
    void transicion_tokenHs256_esAceptado() {
        String tokenHs = JwtTestHelper.tokenHs256("paciente@mail.com", "PACIENTE");
        assertThat(get(tokenHs).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rutasDeNotificaciones_conTokenRs256FirmadoConOtraClave_devuelve401() {
        String tokenOtra = JwtTestHelper.tokenRs256OtraClave("paciente@mail.com", "PACIENTE");
        assertThat(get(tokenOtra).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- en vivo (/ws-notificaciones)

    @org.springframework.boot.test.web.server.LocalServerPort
    int puerto;

    private org.springframework.messaging.simp.stomp.StompSession conectar(String token,
            java.util.concurrent.CompletableFuture<Throwable> errores) throws Exception {
        org.springframework.web.socket.messaging.WebSocketStompClient cliente =
                new org.springframework.web.socket.messaging.WebSocketStompClient(
                        new org.springframework.web.socket.client.standard.StandardWebSocketClient());
        cliente.setMessageConverter(new org.springframework.messaging.converter.MappingJackson2MessageConverter());
        org.springframework.web.socket.WebSocketHttpHeaders headers = new org.springframework.web.socket.WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        return cliente.connectAsync("ws://localhost:" + puerto + "/ws-notificaciones", headers,
                new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(org.springframework.messaging.simp.stomp.StompSession s,
                                                org.springframework.messaging.simp.stomp.StompCommand c,
                                                org.springframework.messaging.simp.stomp.StompHeaders h, byte[] p, Throwable ex) {
                        errores.complete(ex);
                    }

                    @Override
                    public void handleTransportError(org.springframework.messaging.simp.stomp.StompSession s, Throwable ex) {
                        errores.complete(ex);
                    }
                }).get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void enVivo_elDuenioRecibeSuNotificacion() throws Exception {
        java.util.concurrent.CompletableFuture<Map<String, Object>> recibido = new java.util.concurrent.CompletableFuture<>();
        org.springframework.messaging.simp.stomp.StompSession sesion =
                conectar(token("admin@mail.com", "ADMIN"), new java.util.concurrent.CompletableFuture<>());
        sesion.subscribe("/topic/notificaciones/3", new org.springframework.messaging.simp.stomp.StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders h) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders h, Object payload) {
                recibido.complete((Map<String, Object>) payload);
            }
        });
        Thread.sleep(500);

        publicar(Topicos.IDENTIDAD_TICKET_CREADO_V1, "78", 1, new TicketCreadoData(78L, 1L, "Ana Paz", "Ayuda"));

        Map<String, Object> payload = recibido.get(15, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(payload.get("titulo")).isEqualTo("Nuevo ticket de soporte");
        assertThat(payload.get("tipo")).isEqualTo("TICKET_NUEVO");
        sesion.disconnect();
    }

    @Test
    void enVivo_noSePuedeSuscribirAlCanalDeOtroUsuario() throws Exception {
        java.util.concurrent.CompletableFuture<Throwable> errores = new java.util.concurrent.CompletableFuture<>();
        org.springframework.messaging.simp.stomp.StompSession sesion = conectar(token("paciente@mail.com", "PACIENTE"), errores);

        sesion.subscribe("/topic/notificaciones/2", new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {});

        assertThat(errores.get(10, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void api_sinSesion401_conTokenInvalido401_yAdmin403() {
        assertThat(get(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("no-es-un-jwt").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(token("admin@mail.com", "ADMIN")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------ documento adjunto (Fase 6)

    private ResponseEntity<String> documento(String cuerpo, boolean conToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (conToken) {
            headers.setBearerAuth(new com.tranqui.eventos.jwt.ServiceJwt("clave-servicio-test-de-32-bytes-ok").emitirToken("turnos"));
        }
        return http.exchange("/internal/notificaciones/documentos", HttpMethod.POST, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private static final String DOCUMENTO = "{\"email\":\"pac@mail.com\",\"pacienteNombre\":\"Ana\",\"medicoNombre\":\"Dra. Paz\","
            + "\"tipoDocumento\":\"Certificado\",\"archivoDataUri\":\"data:application/pdf;base64,JVBERi0=\","
            + "\"archivoNombre\":\"certificado.pdf\"}";

    @Test
    void documentoAdjunto_soloConTokenDeServicio_yLoMandaConElArchivo() {
        assertThat(documento(DOCUMENTO, false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> r = documento(DOCUMENTO, true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"enviado\":true");
        verify(mails).enviarDocumentoAdjunto("pac@mail.com", "Ana", "Dra. Paz", "Certificado",
                "data:application/pdf;base64,JVBERi0=", "certificado.pdf");
    }

    @Test
    void documentoAdjunto_siElMailNoSale_502() {
        doThrow(new EnvioMailException("Resend caído")).when(mails).enviarDocumentoAdjunto(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());

        ResponseEntity<String> r = documento(DOCUMENTO, true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody()).contains("\"enviado\":false");
    }

    // ------------------------------------------------------------------ receta emitida (Fase 7)

    private ResponseEntity<String> receta(String cuerpo, boolean conToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (conToken) {
            headers.setBearerAuth(new com.tranqui.eventos.jwt.ServiceJwt("clave-servicio-test-de-32-bytes-ok").emitirToken("profesionales"));
        }
        return http.exchange("/internal/notificaciones/recetas", HttpMethod.POST, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private static final String RECETA = "{\"email\":\"pac@mail.com\",\"pacienteNombre\":\"Ana\",\"medicoNombre\":\"Dra. Paz\","
            + "\"matricula\":\"MN 12345\",\"pdfUrl\":\"https://s3.tranqui/r.pdf\",\"nroRecetario\":\"REC-1\"}";

    @Test
    void recetaEmitida_soloConTokenDeServicio_yLaManda() {
        assertThat(receta(RECETA, false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> sinEmail = receta("{\"email\":\"\",\"pacienteNombre\":\"Ana\"}", true);
        assertThat(sinEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> r = receta(RECETA, true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"enviado\":true");
        verify(mails).enviarRecetaEmitidaPaciente("pac@mail.com", "Ana", "Dra. Paz", "MN 12345",
                "https://s3.tranqui/r.pdf", "REC-1");
    }

    @Test
    void recetaEmitida_siElMailNoSale_502() {
        doThrow(new EnvioMailException("Resend caído")).when(mails).enviarRecetaEmitidaPaciente(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());

        ResponseEntity<String> r = receta(RECETA, true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody()).contains("\"enviado\":false");
    }

    @Test
    void rutasInternas_validanElCuerpoAunqueVengaConTokenDeServicio() {
        assertThat(documento(DOCUMENTO.replace("pac@mail.com", "no-es-un-mail"), true).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(documento(DOCUMENTO.replace("data:application/pdf;base64,JVBERi0=", "https://otro.sitio/x.pdf"), true)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(receta(RECETA.replace("https://s3.tranqui/r.pdf", "http://s3.tranqui/r.pdf"), true).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        org.mockito.Mockito.verifyNoInteractions(mails);
    }
}
