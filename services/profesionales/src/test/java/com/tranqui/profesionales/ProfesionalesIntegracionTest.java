package com.tranqui.profesionales;

import com.tranqui.eventos.contratos.Contratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.ProfesionalVisibilidadCambiadaData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.profesionales.datos.Agendas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Tarifas;
import com.tranqui.profesionales.interno.CargaInicial;
import com.tranqui.profesionales.interno.ClienteInterno;
import com.tranqui.profesionales.interno.SincronizacionMonolito;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.tranqui.profesionales.seguridad.JwtTestHelper;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El servicio de profesionales completo contra PostgreSQL y RabbitMQ reales (Fase 5), con el monolito simulado. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "profesionales.carga-inicial.habilitada=false",
        "profesionales.sincronizacion-segundos=3600",
        "jwt.secret=" + ProfesionalesIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + ProfesionalesIntegracionTest.CLAVE_SERVICIO,
        "tranqui.gateway.url=http://localhost:1"
})
class ProfesionalesIntegracionTest {

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

    @Autowired Profesionales profesionales;
    @Autowired Agendas agendas;
    @Autowired Tarifas tarifas;
    @Autowired SincronizacionMonolito sincronizacion;
    @Autowired CargaInicial cargaInicial;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate rabbit;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("profesionales"));
            return info != null && info.getConsumerCount() > 0;
        });
        for (String tabla : List.of("disponibilidad", "tarifa", "profesional", "outbox", "eventos_procesados",
                "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);
    }

    // ------------------------------------------------------------------ datos

    static PerfilProfesionalData perfil(boolean online, boolean presencial) {
        return new PerfilProfesionalData("contacto@mail.com", presencial ? "Av. Corrientes 1234, CABA" : null, null, null,
                null, null, null, null, "1234", "MN", "CABA", 1234, null, null, null, null,
                "Médica", "Psiquiatría", "20-12345678-9", null, null, online, presencial,
                "[{\"lugar\":\"Hospital\"}]", null, null, null, null, null, null, null, null, null,
                "Ansiedad,Estrés", "Descripción", "UBA", 10, "Adultos", null, null);
    }

    static ClienteInterno.ProfesionalMonolito monolito(long id, String email, int version, String estado,
                                                       LocalDateTime hasta, boolean verificado, String foto) {
        return new ClienteInterno.ProfesionalMonolito(id, version, email, "PSIQUIATRA", "psiquiatra", "Ana", "Paz",
                "+54 11 5555", "F", LocalDate.of(1980, 5, 4), 27123456780L, "DNI", 12345678, foto,
                foto != null ? "hash-" + foto.length() : null, verificado, false, estado, hasta, false,
                perfil(true, false), List.of(), List.of());
    }

    private void alta(long id, String email, String estado, LocalDateTime hasta, boolean verificado) {
        ClienteInterno.ProfesionalMonolito p = monolito(id, email, 10, estado, hasta, verificado, "data:image/png;base64,AAAA");
        profesionales.alta(p.datos(), p.perfil());
    }

    // ------------------------------------------------------------------ ayudas

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

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String token, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n != null ? n : 0;
    }

    private JsonNode evento(String topico) throws Exception {
        return mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox WHERE topic = ? ORDER BY id DESC LIMIT 1",
                String.class, topico));
    }

    // ------------------------------------------------------------------ buscador

    @Test
    void buscador_soloVerificadosConSuscripcionAlDia_yConLasTarifasPorDefecto() throws Exception {
        LocalDateTime futuro = LocalDateTime.now().plusDays(10);
        alta(1L, "visible@mail.com", "ACTIVE", futuro, true);
        alta(2L, "vencida@mail.com", "ACTIVE", LocalDateTime.now().minusMinutes(1), true);
        alta(3L, "sinverificar@mail.com", "ACTIVE", futuro, false);
        alta(4L, "cancelada@mail.com", "CANCELLED", futuro, true);

        ResponseEntity<String> r = pedir(HttpMethod.GET, "/api/medicos", null, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lista = mapper.readTree(r.getBody());
        assertThat(lista).hasSize(2);
        assertThat(lista.findValuesAsText("email")).containsExactlyInAnyOrder("visible@mail.com", "cancelada@mail.com");
        JsonNode ana = lista.get(0);
        assertThat(ana.get("name").asText()).isEqualTo("Ana Paz");
        assertThat(ana.get("initials").asText()).isEqualTo("A");
        assertThat(ana.get("verificado").asBoolean()).isTrue();
        assertThat(ana.get("price").decimalValue()).isEqualByComparingTo("60000");
        assertThat(ana.get("tariffs")).hasSize(6);
        assertThat(ana.get("duracionTurnoMinutos").asInt()).isEqualTo(45);
        assertThat(ana.get("matriculaInfo").get("numero").asInt()).isEqualTo(1234);
        // Las tarifas por defecto se avisan al monolito, y sin poder recetar la receta queda deshabilitada.
        assertThat(contar("SELECT count(*) FROM outbox WHERE topic = ?", Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V2)).isEqualTo(2);
        assertThat(tarifas.deProfesional(1L)).anyMatch(t -> t.servicioId().equals("receta-fuera") && !t.habilitado() && !t.esReceta());
    }

    // ------------------------------------------------------------------ perfil

    @Test
    void perfil_exigeSesionYRolDeProfesional() {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);

        assertThat(pedir(HttpMethod.GET, "/api/medicos/perfil", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir(HttpMethod.GET, "/api/medicos/perfil", token("ana@mail.com", "PACIENTE"), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> r = pedir(HttpMethod.GET, "/api/medicos/perfil", token("ana@mail.com", "PSIQUIATRA"), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"email\":\"ana@mail.com\"").contains("\"specialty\":\"Psiquiatría\"")
                .contains("\"fechaNacimiento\":\"1980-05-04\"");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/perfil", token("otra@mail.com", "PSIQUIATRA"), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void transicion_tokenHs256_esAceptado() {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);
        String tokenHs = JwtTestHelper.tokenHs256("ana@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/perfil", tokenHs, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rutasDeProfesionales_conTokenRs256FirmadoConOtraClave_devuelve401() {
        String tokenOtra = JwtTestHelper.tokenRs256OtraClave("ana@mail.com", "PSIQUIATRA");
        assertThat(pedir(HttpMethod.GET, "/api/medicos/perfil", tokenOtra, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static final String CUERPO_PERFIL = "{\"nombre\":\"Ana María\",\"apellido\":\"Paz\",\"telefono\":\" +54 11 6666 \","
            + "\"sexo\":\"F\",\"fechaNacimiento\":\"1980-05-04\",\"cuil\":27123456780,\"tipoDocumento\":\"DNI\","
            + "\"numeroDocumento\":12345678,\"fotoUrl\":\"data:image/png;base64,BBBB\",\"degree\":\"Médica\","
            + "\"specialty\":\"Psiquiatría infantil\",\"ofreceOnline\":true,\"ofrecePresencial\":false,"
            + "\"matriculaInfo\":{\"tipo\":\"MP\",\"provincia\":\"Córdoba\",\"numero\":999},"
            + "\"tags\":[\"TDAH\"],\"pacientesAtiende\":[\"Niños\"],\"descripcionPerfil\":\"Hola\",\"experiencia\":\"[1]\","
            + "\"tariffs\":[{\"id\":\"particular\",\"label\":\"Consulta\",\"price\":70000,\"enabled\":true,\"requiereAgenda\":true},"
            + "{\"id\":\"receta\",\"label\":\"Receta\",\"price\":1000,\"enabled\":true,\"requiereAgenda\":false,\"esReceta\":true},"
            + "{\"id\":\"swiss\",\"label\":\"Swiss\",\"price\":500,\"enabled\":true,\"requiereAgenda\":true,\"obraSocial\":\" Swiss Medical \"}]}";

    @Test
    void actualizarPerfil_guardaLaIdentidadEnElMonolitoYElRestoAcaYAvisa() throws Exception {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);
        tarifas.guardar(1L, new TarifasProfesionalData.Tarifa("certificado", "Certificado", BigDecimal.ONE, true,
                false, null, null, null, false, false));
        when(interno.guardarIdentidad(eq(1L), any())).thenReturn(monolito(1L, "ana@mail.com", 25, "ACTIVE",
                LocalDateTime.now().plusDays(1), true, null));

        ResponseEntity<String> r = pedir(HttpMethod.PUT, "/api/medicos/perfil", token("ana@mail.com", "PSIQUIATRA"), CUERPO_PERFIL);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(interno).guardarIdentidad(eq(1L), eq(new ClienteInterno.Identidad("Ana María", "Paz", "+54 11 6666", "F",
                LocalDate.of(1980, 5, 4), 27123456780L, "DNI", 12345678, "data:image/png;base64,BBBB")));
        Profesionales.Profesional p = profesionales.porId(1L).orElseThrow();
        assertThat(p.perfil().especialidad()).isEqualTo("Psiquiatría infantil");
        assertThat(p.perfil().matriculaNumero()).isEqualTo(999);
        assertThat(p.perfil().matricula()).isEqualTo("999");
        assertThat(p.perfil().tags()).isEqualTo("TDAH");
        assertThat(p.fotoUrl()).isEqualTo("data:image/png;base64,BBBB");
        // La versión queda por encima de la del usuario en el monolito (25).
        assertThat(p.version()).isEqualTo(26);
        JsonNode v1 = evento(Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1);
        assertThat(v1.get("aggregateType").asText()).isEqualTo("profesional");
        assertThat(v1.get("aggregateVersion").asInt()).isEqualTo(26);
        assertThat(v1.get("data").get("perfil").get("especialidad").asText()).isEqualTo("Psiquiatría infantil");
        assertThat(v1.get("data").get("especialidad").asText()).isEqualTo("Psiquiatría infantil");
        // Tarifas: sin poder recetar, la receta no queda como receta; la obra social se limpia y se exige; la
        // que no vino se borra.
        List<TarifasProfesionalData.Tarifa> guardadas = tarifas.deProfesional(1L);
        assertThat(guardadas).extracting(TarifasProfesionalData.Tarifa::servicioId).containsExactly("particular", "receta", "swiss");
        assertThat(guardadas.get(1).esReceta()).isFalse();
        assertThat(guardadas.get(2).obraSocial()).isEqualTo("Swiss Medical");
        assertThat(guardadas.get(2).requiereObraSocial()).isTrue();
        JsonNode v2 = evento(Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V2);
        assertThat(v2.get("data").get("tarifas")).hasSize(3);
    }

    @Test
    void actualizarPerfil_conUnDatoInvalido_400SinTocarElMonolito() {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);

        ResponseEntity<String> r = pedir(HttpMethod.PUT, "/api/medicos/perfil", token("ana@mail.com", "PSIQUIATRA"),
                "{\"nombre\":\"Ana\",\"codigoRefeps\":\"123\"}");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("REFEPS");
        verify(interno, never()).guardarIdentidad(anyLong(), any());
    }

    @Test
    void actualizarPerfil_conElMonolitoCaido_503YNoGuardaNada() {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);
        when(interno.guardarIdentidad(eq(1L), any())).thenThrow(new ResourceAccessException("caído"));

        ResponseEntity<String> r = pedir(HttpMethod.PUT, "/api/medicos/perfil", token("ana@mail.com", "PSIQUIATRA"), CUERPO_PERFIL);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(profesionales.porId(1L).orElseThrow().perfil().especialidad()).isEqualTo("Psiquiatría");
        assertThat(contar("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void configAgenda_cambiaDuracionEIntervaloYAvisa() throws Exception {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);

        ResponseEntity<String> r = pedir(HttpMethod.PUT, "/api/medicos/disponibilidad-config", token("ana@mail.com", "PSIQUIATRA"),
                "{\"duracionTurnoMinutos\":30,\"intervaloEntreTurnosMinutos\":5}");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"duracionTurnoMinutos\":30").contains("\"intervaloEntreTurnosMinutos\":5");
        JsonNode data = evento(Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1).get("data");
        assertThat(data.get("duracionTurnoMinutos").asInt()).isEqualTo(30);
        assertThat(data.get("intervaloEntreTurnosMinutos").asInt()).isEqualTo(5);
        assertThat(pedir(HttpMethod.PUT, "/api/medicos/disponibilidad-config", token("ana@mail.com", "PSIQUIATRA"),
                "{\"duracionTurnoMinutos\":0}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ disponibilidad

    @Test
    void disponibilidad_porDefectoEnLaModalidadPrioritaria_yGuardarReemplazaYAvisaTodo() throws Exception {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);
        String t = token("ana@mail.com", "PSIQUIATRA");

        assertThat(mapper.readTree(pedir(HttpMethod.GET, "/api/medicos/disponibilidad?modalidad=PRESENCIAL", t, null).getBody())).isEmpty();
        JsonNode online = mapper.readTree(pedir(HttpMethod.GET, "/api/medicos/disponibilidad?modalidad=ONLINE", t, null).getBody());
        assertThat(online).hasSize(10);
        assertThat(agendas.franjas(1L)).hasSize(10);

        ResponseEntity<String> r = pedir(HttpMethod.PUT, "/api/medicos/disponibilidad?modalidad=PRESENCIAL", t,
                "[{\"diaSemana\":6,\"horaInicio\":\"10:00\",\"horaFin\":\"13:00\"}]");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(agendas.franjas(1L)).hasSize(11);
        JsonNode v2 = evento(Topicos.PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2);
        assertThat(v2.get("aggregateVersion").asInt()).isEqualTo(2);
        assertThat(v2.get("data").get("franjas")).hasSize(11);
        assertThat(pedir(HttpMethod.GET, "/api/medicos/disponibilidad?modalidad=OTRA", t, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ monolito

    @Test
    void sincronizacion_daDeAltaActualizaPideLaFotoSoloSiCambioYBorraLosQueYaNoEstan() {
        LocalDateTime hasta = LocalDateTime.now().plusDays(5);
        alta(1L, "ana@mail.com", "ACTIVE", hasta, true);
        alta(2L, "se-fue@mail.com", "ACTIVE", hasta, true);
        String hashActual = profesionales.porId(1L).orElseThrow().fotoHash();
        // 1: misma foto, la suscripción venció. 3: nuevo. 2: ya no está en el monolito.
        ClienteInterno.ProfesionalMonolito ana = new ClienteInterno.ProfesionalMonolito(1L, 12, "ana@mail.com", "PSIQUIATRA",
                "psiquiatra", "Ana", "Paz", null, "F", LocalDate.of(1980, 5, 4), 1L, "DNI", 1, null, hashActual, true, true,
                "PAST_DUE", hasta, true, null, null, null);
        when(interno.profesional(3L)).thenReturn(monolito(3L, "nueva@mail.com", 4, "ACTIVE", hasta, false, "data:x")
                .withDisponibilidad(List.of(new DisponibilidadProfesionalData.Franja(1, "09:00", "10:00", null))));
        when(interno.profesional(2L)).thenThrow(HttpClientErrorException.create(HttpStatusCode.valueOf(404), "Not Found",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        sincronizacion.habilitar();
        sincronizacionEstado(List.of(ana, monolito(3L, "nueva@mail.com", 4, "ACTIVE", hasta, false, null)));

        Profesionales.Profesional actualizada = profesionales.porId(1L).orElseThrow();
        assertThat(actualizada.suscripcionEstado()).isEqualTo("PAST_DUE");
        assertThat(actualizada.mpVinculada()).isTrue();
        assertThat(actualizada.puedeRecetar()).isTrue();
        assertThat(actualizada.version()).isEqualTo(12);
        assertThat(actualizada.fotoUrl()).isEqualTo("data:image/png;base64,AAAA");
        verify(interno, never()).profesional(1L);
        assertThat(profesionales.porId(3L)).isPresent();
        assertThat(agendas.franjas(3L)).hasSize(1);
        assertThat(profesionales.porId(2L)).isEmpty();
    }

    private void sincronizacionEstado(List<ClienteInterno.ProfesionalMonolito> estado) {
        when(interno.estado()).thenReturn(estado);
        sincronizacion.sincronizar();
    }

    @Test
    void verificacionDelAdministrador_refrescaAlProfesionalEnseguida() throws Exception {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), false);
        when(interno.profesional(1L)).thenReturn(monolito(1L, "ana@mail.com", 11, "ACTIVE", LocalDateTime.now().plusDays(1),
                true, "data:image/png;base64,AAAA"));

        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<ProfesionalVisibilidadCambiadaData>builder()
                                .eventId(UUID.randomUUID()).eventType("profesionales.profesional.visibilidad-cambiada")
                                .producer("monolito").aggregateType("profesional").aggregateId("1").aggregateVersion(11)
                                .data(new ProfesionalVisibilidadCambiadaData(1L, true, "Verificado por admin")).build()))
                        .setContentType("application/json").build());

        verify(interno, timeout(20_000)).profesional(1L);
        esperar(() -> Boolean.TRUE.equals(profesionales.porId(1L).orElseThrow().verificadoAdmin()));
    }

    @Test
    void cargaInicial_copiaLosProfesionalesConSuDisponibilidadYTarifas() {
        when(interno.profesionales(eq(0), anyInt())).thenReturn(List.of(
                monolito(1L, "ana@mail.com", 7, "ACTIVE", LocalDateTime.now().plusDays(1), true, "data:foto")
                        .withDisponibilidad(List.of(new DisponibilidadProfesionalData.Franja(2, "08:00", "09:00", "ONLINE")))
                        .withTarifas(List.of(new TarifasProfesionalData.Tarifa("particular", "Consulta", BigDecimal.TEN, true,
                                false, null, null, null, true, false)))));

        assertThat(cargaInicial.cargar()).isEqualTo(1);

        Profesionales.Profesional p = profesionales.porId(1L).orElseThrow();
        assertThat(p.version()).isEqualTo(7);
        assertThat(p.fotoUrl()).isEqualTo("data:foto");
        assertThat(agendas.franjas(1L)).hasSize(1);
        assertThat(tarifas.deProfesional(1L)).hasSize(1);
    }

    @Test
    void rutaInterna_soloConTokenDeServicio_devuelveLosCambiadosSinLaFoto() throws Exception {
        alta(1L, "ana@mail.com", "ACTIVE", LocalDateTime.now().plusDays(1), true);
        agendas.reemplazarTodas(1L, List.of(new DisponibilidadProfesionalData.Franja(1, "09:00", "10:00", "ONLINE")));

        assertThat(pedir(HttpMethod.GET, "/internal/profesionales/profesionales", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(new ServiceJwt(CLAVE_SERVICIO).emitirToken("monolito"));
        ResponseEntity<String> r = http.exchange("/internal/profesionales/profesionales", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        Contratos.mismaForma("internas/profesionales/profesionales.json", r.getBody());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lista = mapper.readTree(r.getBody());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("version").asInt()).isEqualTo(10);
        assertThat(lista.get(0).get("disponibilidad")).hasSize(1);
        assertThat(r.getBody()).doesNotContain("base64");
        ResponseEntity<String> futuro = http.exchange("/internal/profesionales/profesionales?desde="
                        + java.time.OffsetDateTime.now().plusHours(1).toString().replace("+", "%2B"), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(mapper.readTree(futuro.getBody())).isEmpty();
    }
}
