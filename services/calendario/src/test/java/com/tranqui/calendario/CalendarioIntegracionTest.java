package com.tranqui.calendario;

import com.tranqui.eventos.contratos.Contratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.calendario.interno.CargaInicial;
import com.tranqui.calendario.interno.ClienteInterno;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.TurnoReprogramadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.jwt.ServiceJwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.tranqui.calendario.seguridad.JwtTestHelper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * El servicio de calendario completo contra PostgreSQL y RabbitMQ reales (Fase 3), con Google
 * simulado como en local: los turnos online confirmados reciben un Meet simulado por eventos.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "calendario.carga-inicial.habilitada=false",
        "app.local-simulations=true",
        "app.frontend-url=https://front.test",
        "jwt.secret=" + CalendarioIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + CalendarioIntegracionTest.CLAVE_SERVICIO,
        "security.encryption.key=clave-de-cifrado-de-test-de-32-bytes",
        "tranqui.gateway.url=http://localhost:1"
})
class CalendarioIntegracionTest {

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

    @Autowired Participantes participantes;
    @Autowired CuentasGoogle cuentas;
    @Autowired EventosExternos externos;
    @Autowired CargaInicial carga;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate rabbit;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("calendario"));
            return info != null && info.getConsumerCount() > 0;
        });
        for (String tabla : List.of("turno_agendado", "evento_externo", "cuenta_google", "participante", "outbox",
                "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        participantes.guardar(1L, "pac@mail.com", "Ana", "Paz", "PACIENTE");
        participantes.guardar(2L, "dra@mail.com", "Dra. Ruiz", "Ruiz", "PSIQUIATRA");
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

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        return http.exchange(ruta, metodo, new HttpEntity<>(headers), String.class);
    }

    private <T> void publicar(String topico, String aggregateId, int version, T data) throws Exception {
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, topico, MessageBuilder
                .withBody(mapper.writeValueAsBytes(EventEnvelope.<T>builder().eventId(UUID.randomUUID())
                        .eventType(Topicos.extraerTipoEvento(topico)).producer("monolito").aggregateType("turno")
                        .aggregateId(aggregateId).aggregateVersion(version).data(data).build()))
                .setContentType("application/json").build());
    }

    private long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n != null ? n : 0;
    }

    @Test
    void turnoOnlineConfirmado_meetSimuladoPorEventoYSinTokens() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(3);
        publicar(Topicos.TURNOS_TURNO_CONFIRMADO_V1, "70", 2, new TurnoConfirmadoData(70L, 1L, 2L, fecha, "10:00",
                "ONLINE", BigDecimal.TEN, true, "Consulta", null, null, null, TurnoConfirmadoData.ORIGEN_PAGO, "10:45"));
        // Un documento pedido no ocupa agenda: no va a Google.
        publicar(Topicos.TURNOS_TURNO_CONFIRMADO_V1, "71", 2, new TurnoConfirmadoData(71L, 1L, 2L, fecha, "00:00",
                "ONLINE", BigDecimal.TEN, false, null, "Receta", null, null, TurnoConfirmadoData.ORIGEN_PAGO, null));
        esperar(() -> contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.CALENDARIO_MEET_CREADO_V1) == 1);

        String meet = jdbc.queryForObject("SELECT meet_url FROM turno_agendado WHERE turno_id = 70", String.class);
        assertThat(meet).matches("https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}");
        assertThat(contar("SELECT count(*) FROM turno_agendado WHERE turno_id = 71")).isZero();
        JsonNode data = mapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox WHERE topic = ?", String.class, Topicos.CALENDARIO_MEET_CREADO_V1)).get("data");
        assertThat(data.get("turnoId").asLong()).isEqualTo(70L);
        assertThat(data.get("meetUrl").asText()).isEqualTo(meet);
        assertThat(data.get("linkNuevo").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT string_agg(payload::text, '') FROM outbox", String.class))
                .doesNotContainIgnoringCase("token");

        // Reprogramar mueve la copia; cancelar la borra.
        publicar(Topicos.TURNOS_TURNO_REPROGRAMADO_V1, "70", 3, new TurnoReprogramadoData(70L, 1L, 2L, fecha, "10:00",
                fecha.plusDays(1), "15:00", "ONLINE", null, meet, "15:45"));
        esperar(() -> contar("SELECT count(*) FROM turno_agendado WHERE turno_id = 70 AND hora_inicio = '15:00' "
                + "AND hora_fin = '15:45'") == 1);
        publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, "70", 4, new TurnoCanceladoData(70L, 1L, 2L, "PACIENTE", "motivo", true));
        esperar(() -> contar("SELECT count(*) FROM turno_agendado") == 0);
    }

    @Test
    void rutasDelPanel_mismasRespuestasQueElMonolito() {
        String dra = token("dra@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/status", dra).getBody())
                .contains("\"connected\":false").contains("\"simulated\":true");
        assertThat(pedir(HttpMethod.POST, "/api/medicos/google-calendar/connect-simulado", dra).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/status", dra).getBody()).contains("\"connected\":true");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/connect", dra).getBody())
                .contains("accounts.google.com").contains("state=2.");

        externos.guardar(2L, "ext-1", "Dentista", LocalDateTime.of(2026, 11, 3, 9, 0), LocalDateTime.of(2026, 11, 3, 10, 30),
                false, false, "confirmed");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/eventos", dra).getBody())
                .contains("\"id\":\"ext-1\"").contains("\"title\":\"Dentista\"").contains("\"fecha\":\"2026-11-03\"")
                .contains("\"hour\":\"09:00\"").contains("\"endHour\":\"10:30\"").contains("\"allDay\":false");

        assertThat(pedir(HttpMethod.POST, "/api/medicos/google-calendar/disconnect", dra).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cuentas.buscar(2L)).isEmpty();
        assertThat(externos.deProfesional(2L)).isEmpty();

        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/status", token("pac@mail.com", "PACIENTE"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/status", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rutasDelPanel_conTokenHs256DuranteTransicion_esAceptado() {
        String draHs = JwtTestHelper.tokenHs256("dra@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/status", draHs).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void rutasDelPanel_conTokenRs256FirmadoConOtraClave_devuelve401() {
        String tokenOtra = JwtTestHelper.tokenRs256OtraClave("dra@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/google-calendar/status", tokenOtra).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @LocalServerPort int puerto;

    @Test
    void webhookYCallback_sinSesion() throws Exception {
        assertThat(pedir(HttpMethod.POST, "/api/medicos/google-calendar/webhook", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Sin seguir la redirección: el destino es el frontend.
        java.net.http.HttpResponse<Void> callback = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()
                .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + puerto
                                + "/api/medicos/google-calendar/callback?code=x&state=2.firma-falsa")).build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location"))
                .hasValue("https://front.test/?googleCalendar=error&reason=invalid_state");
    }

    @Test
    void horariosOcupados_paraLaDisponibilidadDelMonolito() {
        LocalDate dia = LocalDate.of(2026, 11, 4);
        externos.guardar(2L, "e-normal", "Reunión", dia.atTime(9, 0), dia.atTime(10, 0), false, false, "confirmed");
        externos.guardar(2L, "e-libre", "Disponible", dia.atTime(12, 0), dia.atTime(13, 0), false, true, "confirmed");
        externos.guardar(2L, "e-cruza", "Guardia", dia.minusDays(1).atTime(22, 0), dia.atTime(2, 0), false, false, "confirmed");
        externos.guardar(2L, "e-otro-dia", "Otro", dia.plusDays(1).atTime(9, 0), dia.plusDays(1).atTime(10, 0), false, false, "confirmed");
        String servicio = "Bearer " + new ServiceJwt(CLAVE_SERVICIO).emitirToken("monolito");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, servicio);

        ResponseEntity<String> r = http.exchange("/internal/calendario/profesionales/2/ocupado?fecha=" + dia, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        Contratos.mismaForma("internas/calendario/ocupado.json", r.getBody());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo("[{\"inicio\":\"00:00:00\",\"fin\":\"02:00:00\",\"todoElDia\":false},"
                + "{\"inicio\":\"09:00:00\",\"fin\":\"10:00:00\",\"todoElDia\":false}]");
        assertThat(http.getForEntity("/internal/calendario/profesionales/2/ocupado?fecha=" + dia, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cargaInicial_copiaCuentasConLosTokensCifradosTalCual() {
        jdbc.update("DELETE FROM participante");
        when(interno.contactos(anyInt(), anyInt())).thenReturn(List.of(
                new ClienteInterno.Contacto(2L, "dra@mail.com", "Dra. Ruiz", "Ruiz", "PSIQUIATRA", true)));
        when(interno.cuentas()).thenReturn(List.of(new ClienteInterno.Cuenta(2L, "acceso-cifrado", "refresh-cifrado",
                LocalDateTime.now().plusMinutes(30), null, "sync-1", "canal-1", "recurso-1", LocalDateTime.now().plusDays(3), true)));
        when(interno.eventosExternos(anyLong(), anyInt())).thenReturn(List.of(new ClienteInterno.EventoExterno(40L, 2L,
                "ext-40", "Dentista", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1), false, "confirmed")));
        when(interno.turnosConfirmados()).thenReturn(List.of(
                new ClienteInterno.TurnoConfirmado(80L, 1L, 2L, LocalDate.now().plusDays(2), "11:00", "11:45", "ONLINE", true,
                        "g-80", "https://meet.google.com/aaa-bbbb-ccc"),
                new ClienteInterno.TurnoConfirmado(81L, 1L, 2L, LocalDate.now().plusDays(2), "00:00", null, "ONLINE", false, null, null)));

        carga.cargar();

        CuentasGoogle.Cuenta cuenta = cuentas.buscar(2L).orElseThrow();
        assertThat(cuenta.accessTokenCifrado()).isEqualTo("acceso-cifrado");
        assertThat(cuenta.refreshTokenCifrado()).isEqualTo("refresh-cifrado");
        assertThat(cuenta.watchChannelId()).isEqualTo("canal-1");
        assertThat(externos.deProfesional(2L)).extracting(EventosExternos.Evento::id).containsExactly(40L);
        assertThat(jdbc.queryForObject("SELECT google_event_id FROM turno_agendado WHERE turno_id = 80", String.class)).isEqualTo("g-80");
        assertThat(contar("SELECT count(*) FROM turno_agendado WHERE turno_id = 81")).isZero();
        // Los eventos nuevos siguen después de los copiados.
        assertThat(externos.guardar(2L, "ext-nuevo", "x", LocalDateTime.now(), LocalDateTime.now().plusHours(1), false, false,
                "confirmed").id()).isGreaterThan(40L);
    }

    @Test
    void profesionalEliminado_seBorranSuCuentaYSusEventos() throws Exception {
        cuentas.guardar(new CuentasGoogle.Cuenta(2L, null, null, null, null, null, null, null, null, false));
        externos.guardar(2L, "ext-9", "x", LocalDateTime.now(), LocalDateTime.now().plusHours(1), false, false, "confirmed");

        publicar(Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, "2", 9, new UsuarioEliminadoData(2L, "baja"));

        esperar(() -> cuentas.buscar(2L).isEmpty() && participantes.porId(2L).isEmpty());
        assertThat(externos.deProfesional(2L)).isEmpty();
    }
}
