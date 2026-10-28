package com.tranqui.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoReservadoData;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.datos.Participantes;
import com.tranqui.pagos.interno.ClienteInterno;
import com.tranqui.pagos.mp.Cifrado;
import com.tranqui.pagos.mp.MercadoPagoApi;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.tranqui.pagos.seguridad.JwtTestHelper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El servicio de pagos completo contra PostgreSQL y RabbitMQ reales (Fase 4), con Mercado Pago simulado y
 * {@code mercadopago.enabled=true}: el camino real, con la firma HMAC del webhook.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "pagos.carga-inicial.habilitada=false",
        "mercadopago.enabled=true",
        "mercadopago.client-id=cliente",
        "mercadopago.client-secret=secreto-cliente",
        "mercadopago.webhook-secret=" + PagosIntegracionTest.SECRETO_WEBHOOK,
        "payment.simulation.enabled=false",
        "app.public-url=https://tranqui.test",
        "app.frontend-url=https://front.test",
        "jwt.secret=" + PagosIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "security.encryption.key=clave-de-cifrado-de-test-de-32-bytes",
        "tranqui.gateway.url=http://localhost:1"
})
class PagosIntegracionTest {

    static final String CLAVE_JWT = JwtTestHelper.CLAVE_JWT;
    static final String SECRETO_WEBHOOK = "secreto-del-webhook";

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
    @MockitoBean MercadoPagoApi mercadoPago;

    @Autowired Participantes participantes;
    @Autowired CuentasMp cuentas;
    @Autowired Cifrado cifrado;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate rabbit;
    @LocalServerPort int puerto;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("pagos"));
            return info != null && info.getConsumerCount() > 0;
        });
        for (String tabla : List.of("reembolso", "pago", "cobro", "cuenta_mp", "participante", "outbox",
                "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(mercadoPago, interno);
        participantes.guardar(1L, "pac@mail.com", "Ana", "Paz", "PACIENTE");
        participantes.guardar(2L, "dra@mail.com", "Dra. Ruiz", "Ruiz", "PSIQUIATRA");
        // Lucía (2) vinculada: el token viaja cifrado como en el monolito.
        cuentas.guardar(new CuentasMp.Cuenta(2L, cifrado.cifrar("APP_USR-lucia"), cifrado.cifrar("TG-refresh"),
                LocalDateTime.now().plusMonths(6), "777"));
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

    private <T> void publicar(String topico, String aggregateId, T data) throws Exception {
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, topico, MessageBuilder
                .withBody(mapper.writeValueAsBytes(EventEnvelope.<T>builder().eventId(UUID.randomUUID())
                        .eventType(Topicos.extraerTipoEvento(topico)).producer("monolito").aggregateType("turno")
                        .aggregateId(aggregateId).aggregateVersion(1).data(data).build()))
                .setContentType("application/json").build());
    }

    private long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n != null ? n : 0;
    }

    private JsonNode evento(String topico) throws Exception {
        return mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox WHERE topic = ? ORDER BY id DESC LIMIT 1",
                String.class, topico)).get("data");
    }

    private ResponseEntity<String> webhook(String cuerpo, String dataId, boolean firmado) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String firma = HexFormat.of().formatHex(mac.doFinal(("id:" + dataId + ";request-id:req-1;ts:" + ts + ";")
                .getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("x-signature", "ts=" + ts + ",v1=" + (firmado ? firma : "0".repeat(64)));
        headers.add("x-request-id", "req-1");
        return http.exchange("/api/payments/webhook?data.id=" + dataId, HttpMethod.POST, new HttpEntity<>(cuerpo, headers),
                String.class);
    }

    private static String token(String email, String rol) {
        return JwtTestHelper.tokenRs256(email, rol);
    }

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        return http.exchange(ruta, metodo, new HttpEntity<>(headers), String.class);
    }

    /** La redirección del callback, sin seguirla (TestRestTemplate la sigue hasta el frontend). */
    private String redireccion(String ruta) throws Exception {
        java.net.http.HttpResponse<Void> r = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + puerto + ruta)).build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());
        assertThat(r.statusCode()).isEqualTo(302);
        return r.headers().firstValue("Location").orElseThrow();
    }

    private TurnoReservadoData reserva(long turnoId, long profesionalId) {
        return new TurnoReservadoData(turnoId, 1L, profesionalId, LocalDate.now().plusDays(3), "10:00",
                new BigDecimal("60000"), OffsetDateTime.now().plusMinutes(5), "Consulta Psiquiátrica - Dra. Ruiz");
    }

    @Test
    void reserva_creaElCheckoutConElTokenDelProfesional() throws Exception {
        when(mercadoPago.crearPreferencia(anyString(), anyString(), any(), anyString())).thenReturn("https://mp.test/checkout/80");

        publicar(Topicos.TURNOS_TURNO_RESERVADO_V1, "80", reserva(80L, 2L));
        esperar(() -> contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.PAGOS_CHECKOUT_CREADO_V1) == 1);

        verify(mercadoPago).crearPreferencia("APP_USR-lucia", "Consulta Psiquiátrica - Dra. Ruiz", new BigDecimal("60000.00"), "80");
        assertThat(evento(Topicos.PAGOS_CHECKOUT_CREADO_V1).get("checkoutUrl").asText()).isEqualTo("https://mp.test/checkout/80");
        assertThat(pedir(HttpMethod.GET, "/api/payments/checkout/80", null).getBody())
                .contains("\"estado\":\"LISTO\"").contains("https://mp.test/checkout/80");
        assertThat(pedir(HttpMethod.GET, "/api/payments/checkout/999", null).getBody()).contains("\"estado\":\"PENDIENTE\"");
    }

    @Test
    void reserva_sinCuentaVinculada_quedaConError() throws Exception {
        publicar(Topicos.TURNOS_TURNO_RESERVADO_V1, "81", reserva(81L, 9L));
        esperar(() -> contar("SELECT count(*) FROM cobro WHERE referencia = '81' AND estado = 'ERROR'") == 1);

        assertThat(pedir(HttpMethod.GET, "/api/payments/checkout/81", null).getBody())
                .contains("\"estado\":\"ERROR\"").contains("no vinculó su cuenta");
        assertThat(contar("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void webhook_apruebaUnaSolaVezAunqueSeRepita() throws Exception {
        when(mercadoPago.crearPreferencia(anyString(), anyString(), any(), anyString())).thenReturn("https://mp.test/checkout/82");
        publicar(Topicos.TURNOS_TURNO_RESERVADO_V1, "82", reserva(82L, 2L));
        esperar(() -> contar("SELECT count(*) FROM cobro WHERE referencia = '82' AND estado = 'LISTO'") == 1);
        when(mercadoPago.obtenerPago("APP_USR-lucia", 5001L))
                .thenReturn(new MercadoPagoApi.PagoMp(5001L, "approved", "82", new BigDecimal("60000")));

        String cuerpo = "{\"type\":\"payment\",\"data\":{\"id\":\"5001\"},\"user_id\":\"777\"}";
        assertThat(webhook(cuerpo, "5001", true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(webhook(cuerpo, "5001", true).getStatusCode()).isEqualTo(HttpStatus.OK);
        // "Verificar" a la vez que el webhook tampoco duplica.
        assertThat(pedir(HttpMethod.POST, "/api/payments/verificar?externalReference=82&paymentId=5001", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(contar("SELECT count(*) FROM pago WHERE turno_id = 82")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.PAGOS_PAGO_APROBADO_V1)).isEqualTo(1);
        JsonNode aprobado = evento(Topicos.PAGOS_PAGO_APROBADO_V1);
        assertThat(aprobado.get("turnoId").asLong()).isEqualTo(82L);
        assertThat(aprobado.get("mpPaymentId").asText()).isEqualTo("5001");
        assertThat(contar("SELECT count(*) FROM cobro WHERE referencia = '82' AND estado = 'PAGADO'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT string_agg(payload::text, '') FROM outbox", String.class))
                .doesNotContain("APP_USR").doesNotContainIgnoringCase("token");
    }

    @Test
    void webhook_sinFirmaValida_400() throws Exception {
        assertThat(webhook("{\"type\":\"payment\",\"data\":{\"id\":\"1\"},\"user_id\":\"777\"}", "1", false)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mercadoPago, never()).obtenerPago(anyString(), anyLong());
    }

    @Test
    void webhook_deSuscripciones_seReenviaAlMonolito() throws Exception {
        String preapproval = "{\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"pre-1\"}}";
        String debito = "{\"type\":\"payment\",\"data\":{\"id\":\"9001\"},\"user_id\":\"plataforma\"}";

        assertThat(webhook(preapproval, "pre-1", true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(webhook(debito, "9001", true).getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(interno).reenviarSuscripcion(preapproval, "pre-1");
        verify(interno).reenviarSuscripcion(debito, "9001");
        verify(mercadoPago, never()).obtenerPago(anyString(), anyLong());

        // Si el monolito no responde, Mercado Pago tiene que reintentar.
        doThrow(new IllegalStateException("monolito caído")).when(interno).reenviarSuscripcion(anyString(), any());
        assertThat(webhook(preapproval, "pre-1", true).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void cancelacionConReembolso_reembolsaUnaSolaVezYAvisa() throws Exception {
        jdbc.update("INSERT INTO pago (referencia, turno_id, profesional_id, paciente_id, transaction_id, estado, monto, fecha_pago) "
                + "VALUES ('83', 83, 2, 1, '6001', 'APROBADO', 60000, now())");
        LocalDate fecha = LocalDate.now().plusDays(5);
        TurnoCanceladoData cancelado = new TurnoCanceladoData(83L, 1L, 2L, "PACIENTE", "Cancelado por usuario", true, fecha,
                "10:00", true, "EN_PROCESO");

        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "83", cancelado);
        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "83", cancelado);
        esperar(() -> contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.PAGOS_REEMBOLSO_REALIZADO_V1) == 1);
        Thread.sleep(500);

        verify(mercadoPago, times(1)).reembolsar("APP_USR-lucia", 6001L);
        assertThat(contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.PAGOS_REEMBOLSO_REALIZADO_V1)).isEqualTo(1);
        JsonNode reembolso = evento(Topicos.PAGOS_REEMBOLSO_REALIZADO_V1);
        assertThat(reembolso.get("avisarPaciente").asBoolean()).isTrue();
        assertThat(reembolso.get("pacienteId").asLong()).isEqualTo(1L);
        assertThat(contar("SELECT count(*) FROM pago WHERE transaction_id = '6001' AND estado = 'REEMBOLSADO'")).isEqualTo(1);
    }

    @Test
    void cancelacion_sinReembolsoOHechaPorElMonolito_noLlamaAMercadoPago() throws Exception {
        jdbc.update("INSERT INTO pago (referencia, turno_id, profesional_id, paciente_id, transaction_id, estado, monto, fecha_pago) "
                + "VALUES ('84', 84, 2, 1, '6002', 'APROBADO', 60000, now())");
        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "84", new TurnoCanceladoData(84L, 1L, 2L, "PACIENTE", "x", false,
                LocalDate.now().plusDays(1), "10:00", true, "SIN_REEMBOLSO"));
        // En la vuelta atrás el monolito ya reembolsó y lo informa como REEMBOLSADO.
        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "84", new TurnoCanceladoData(84L, 1L, 2L, "PACIENTE", "x", true,
                LocalDate.now().plusDays(5), "10:00", true, "REEMBOLSADO"));
        esperar(() -> contar("SELECT count(*) FROM eventos_procesados WHERE consumidor = 'reembolso'") == 2);

        verify(mercadoPago, never()).reembolsar(anyString(), anyLong());
        assertThat(contar("SELECT count(*) FROM reembolso")).isZero();
    }

    @Test
    void reembolsoRechazadoPorMercadoPago_quedaFallidoYAvisa() throws Exception {
        jdbc.update("INSERT INTO pago (referencia, turno_id, profesional_id, paciente_id, transaction_id, estado, monto, fecha_pago) "
                + "VALUES ('85', 85, 2, 1, '6003', 'APROBADO', 60000, now())");
        doThrow(new IllegalStateException("saldo insuficiente")).when(mercadoPago).reembolsar(anyString(), eq(6003L));

        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "85", new TurnoCanceladoData(85L, 1L, 2L, "PROFESIONAL", "x", true,
                LocalDate.now().plusDays(1), "10:00", true, "EN_PROCESO"));
        esperar(() -> contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.PAGOS_REEMBOLSO_FALLIDO_V1) == 1);

        assertThat(contar("SELECT count(*) FROM reembolso WHERE turno_id = 85 AND estado = 'FALLIDO'")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pago WHERE transaction_id = '6003' AND estado = 'APROBADO'")).isEqualTo(1);
    }

    @Test
    void panelDeMercadoPago_mismasRespuestasQueElMonolito() throws Exception {
        String dra = token("dra@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/mercadopago/status", dra).getBody())
                .contains("\"connected\":true").contains("\"mpUserId\":\"777\"").contains("\"mercadopagoEnabled\":true");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/mercadopago/status", token("pac@mail.com", "PACIENTE"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(HttpMethod.GET, "/api/medicos/mercadopago/status", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir(HttpMethod.POST, "/api/medicos/mercadopago/connect-simulado", dra).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String url = pedir(HttpMethod.GET, "/api/medicos/mercadopago/connect", dra).getBody();
        assertThat(url).contains("auth.mercadopago.com.ar/authorization").contains("client_id=cliente")
                .contains("redirect_uri=https%3A%2F%2Ftranqui.test%2Fapi%2Fmedicos%2Fmercadopago%2Fcallback");

        assertThat(pedir(HttpMethod.POST, "/api/medicos/mercadopago/disconnect", dra).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(pedir(HttpMethod.GET, "/api/medicos/mercadopago/status", dra).getBody()).contains("\"connected\":false");
        assertThat(evento(Topicos.PAGOS_CUENTA_MP_DESVINCULADA_V1).get("profesionalId").asLong()).isEqualTo(2L);

        // El callback vuelve a vincular con los tokens que da Mercado Pago.
        when(mercadoPago.pedirTokens(anyString())).thenReturn(new MercadoPagoApi.TokensOAuth("APP_USR-nuevo", "TG-nuevo", 15552000, "888"));
        String state = cifrado.firmar(2L);
        assertThat(redireccion("/api/medicos/mercadopago/callback?code=abc&state=" + state)).isEqualTo("https://front.test/?mp=success");
        assertThat(cifrado.descifrar(cuentas.buscar(2L).orElseThrow().accessTokenCifrado())).isEqualTo("APP_USR-nuevo");
        assertThat(evento(Topicos.PAGOS_CUENTA_MP_VINCULADA_V1).get("mpUserId").asText()).isEqualTo("888");

        assertThat(redireccion("/api/medicos/mercadopago/callback?code=abc&state=2.falso"))
                .isEqualTo("https://front.test/?mp=error&reason=invalid_state");
    }

    @Test
    void transicion_tokenHs256_esAceptado() {
        String draHs = JwtTestHelper.tokenHs256("dra@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/mercadopago/status", draHs).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rutasDePagos_conTokenRs256FirmadoConOtraClave_devuelve401() {
        String tokenOtra = JwtTestHelper.tokenRs256OtraClave("dra@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/mercadopago/status", tokenOtra).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
