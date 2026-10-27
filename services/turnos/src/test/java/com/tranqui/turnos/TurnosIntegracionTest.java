package com.tranqui.turnos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.CargaInicial;
import com.tranqui.turnos.interno.ClienteInterno;
import com.tranqui.turnos.turnos.Cobros;
import com.tranqui.turnos.turnos.Programadas;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El servicio de turnos completo contra PostgreSQL y RabbitMQ reales (Fase 6), con el monolito simulado. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "turnos.carga-inicial.habilitada=false",
        "turnos.resincronizacion-profesionales-minutos=600",
        "app.public-url=https://tranqui.test",
        "jwt.secret=" + TurnosIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + TurnosIntegracionTest.CLAVE_SERVICIO,
        "tranqui.gateway.url=http://localhost:1"
})
class TurnosIntegracionTest {

    static final String CLAVE_JWT = "clave-de-test-de-al-menos-32-bytes-ok";
    static final String CLAVE_SERVICIO = "clave-servicio-test-de-32-bytes-ok";
    static final long MEDICO = 16L;
    static final long PACIENTE = 30L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;

    @Autowired Turnos turnos;
    @Autowired Personas personas;
    @Autowired Profesionales profesionales;
    @Autowired Cobros cobros;
    @Autowired Programadas programadas;
    @Autowired CargaInicial cargaInicial;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate rabbit;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("turnos"));
            return info != null && info.getConsumerCount() > 0;
        });
        for (String tabla : List.of("turno", "persona", "profesional", "disponibilidad", "tarifa", "outbox", "eventos_procesados",
                "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.queryForObject("SELECT setval('turno_id_seq', 100)", Long.class);
        reset(interno);
        personas.guardar(persona(MEDICO, "PSIQUIATRA", "Dra. Lucía", "lucia@mail.com"));
        personas.guardar(persona(PACIENTE, "PACIENTE", "Martina", "martina@mail.com"));
        profesionales.guardarPerfil(MEDICO, perfil());
        // Todos los días de 9 a 12, online.
        profesionales.reemplazarDisponibilidad(MEDICO, java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(d -> new DisponibilidadProfesionalData.Franja(d, "09:00", "12:00", "ONLINE")).toList());
        profesionales.reemplazarTarifas(MEDICO, List.of(
                new TarifasProfesionalData.Tarifa("particular", "Consulta particular", new BigDecimal("60000"), true, false, null,
                        new BigDecimal("65000"), null, true, false),
                new TarifasProfesionalData.Tarifa("certificado", "Certificado", new BigDecimal("55000"), true, false, null, null, null,
                        false, false)));
        when(interno.buscarOCrearPaciente(any())).thenReturn(pacienteMonolito());
    }

    // ------------------------------------------------------------------ datos

    static Personas.Persona persona(long id, String rol, String nombre, String email) {
        return new Personas.Persona(id, 1, rol, email, nombre, "Paz", "+54 11 5555", "30111222", null, "OSDE", "123", "F",
                LocalDate.of(1990, 3, 7), 27123456780L, "DNI", 30111222, null, null, null, null, null, true, false);
    }

    static ClienteInterno.PacienteMonolito pacienteMonolito() {
        return new ClienteInterno.PacienteMonolito(PACIENTE, 3, "PACIENTE", "martina@mail.com", "Martina", "Paz", "+54 11 5555",
                "30111222", null, "OSDE", "123", "F", LocalDate.of(1990, 3, 7), 27123456780L, "DNI", 30111222, null, null, null,
                null, null, true);
    }

    static PerfilProfesionalData perfil() {
        return new PerfilProfesionalData(null, "Av. Corrientes 1234", null, null, null, null, null, null, "1", "MN", "CABA", 1, null,
                null, null, null, "Médica", "Psiquiatría", null, null, null, true, false, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, 45, 0);
    }

    private Turno turnoGuardado(long id, String estado, LocalDate fecha, LocalTime hora, String pagoEstado, LocalDateTime creado) {
        Turno t = new Turno();
        t.id = id;
        t.version = 5;
        t.pacienteId = PACIENTE;
        t.medicoId = MEDICO;
        t.fecha = fecha;
        t.horaInicio = hora;
        t.horaFin = hora.plusMinutes(45);
        t.tipo = "PARTICULAR";
        t.modalidad = "ONLINE";
        t.estado = estado;
        t.precio = new BigDecimal("60000");
        t.servicioId = "particular";
        t.fechaCreacion = creado;
        t.tokenReserva = "reserva-" + id;
        t.pagoEstado = pagoEstado;
        t.pagoTransactionId = pagoEstado != null ? "mp-" + id : null;
        turnos.copiar(t);
        return t;
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
        return Jwts.builder().setClaims(Map.of("rol", rol)).setSubject(email).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE_JWT.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String token, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private long contar(String topico) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE topic = ?", Long.class, topico);
        return n != null ? n : 0;
    }

    private JsonNode evento(String topico) throws Exception {
        return mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox WHERE topic = ? ORDER BY id DESC LIMIT 1",
                String.class, topico));
    }

    private static String reserva(LocalDate fecha, String hora) {
        return "{\"medicoId\":" + MEDICO + ",\"fecha\":\"" + fecha + "\",\"hora\":\"" + hora + ":00\",\"tipo\":\"PARTICULAR\","
                + "\"modalidad\":\"ONLINE\",\"nombrePaciente\":\"Martina\",\"emailPaciente\":\" Martina@Mail.com \","
                + "\"telefonoPaciente\":\"11 5555\"}";
    }

    // ------------------------------------------------------------------ agenda y reserva

    @Test
    void horariosLibres_reservaYElHorarioQuedaTomado() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        ResponseEntity<String> libres = pedir(HttpMethod.GET, "/api/medicos/" + MEDICO + "/turnos-disponibles?fecha=" + fecha
                + "&modalidad=ONLINE", null, null);
        assertThat(libres.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(libres.getBody()).isEqualTo("[\"09:00:00\",\"09:45:00\",\"10:30:00\",\"11:15:00\"]");

        ResponseEntity<String> r = pedir(HttpMethod.POST, "/api/turnos/reservar", null, reserva(fecha, "09:45"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cuerpo = mapper.readTree(r.getBody());
        assertThat(cuerpo.get("turnoId").asLong()).isGreaterThan(100);
        assertThat(cuerpo.get("estado").asText()).isEqualTo("PENDIENTE_PAGO");
        assertThat(cuerpo.get("precio").decimalValue()).isEqualByComparingTo("65000");
        assertThat(cuerpo.get("fecha").asText()).isEqualTo(fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        assertThat(cuerpo.get("checkoutPendiente").asBoolean()).isTrue();
        assertThat(cuerpo.get("tokenReserva").asText()).isNotBlank();
        verify(interno).buscarOCrearPaciente(new ClienteInterno.BuscarOCrearPaciente("martina@mail.com", "Martina", "11 5555", null, null));
        JsonNode reservado = evento(Topicos.TURNOS_TURNO_RESERVADO_V1);
        assertThat(reservado.get("aggregateType").asText()).isEqualTo("turno");
        assertThat(reservado.get("aggregateVersion").asInt()).isEqualTo(1);
        assertThat(reservado.get("data").get("tituloCobro").asText()).isEqualTo("Consulta Psiquiátrica - Dra. Lucía");
        assertThat(contar(Topicos.TURNOS_TURNO_ACTUALIZADO_V1)).isEqualTo(1);
        assertThat(evento(Topicos.TURNOS_TURNO_ACTUALIZADO_V1).get("data").toString()).doesNotContain("tokenReserva");

        assertThat(pedir(HttpMethod.GET, "/api/medicos/" + MEDICO + "/turnos-disponibles?fecha=" + fecha + "&modalidad=ONLINE", null, null)
                .getBody()).doesNotContain("09:45");
        ResponseEntity<String> otra = pedir(HttpMethod.POST, "/api/turnos/reservar", null, reserva(fecha, "10:30"));
        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(otra.getBody()).contains("Ya tenés un turno activo");
    }

    @Test
    void reserva_conElMonolitoCaido_503YNoQuedaNada() {
        when(interno.buscarOCrearPaciente(any())).thenThrow(new ResourceAccessException("caído"));

        ResponseEntity<String> r = pedir(HttpMethod.POST, "/api/turnos/reservar", null, reserva(LocalDate.now().plusDays(5), "09:00"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(turnos.cantidad()).isZero();
        assertThat(contar(Topicos.TURNOS_TURNO_RESERVADO_V1)).isZero();
    }

    @Test
    void reserva_deUnHorarioQueNoEstaLibre_409() {
        ResponseEntity<String> r = pedir(HttpMethod.POST, "/api/turnos/reservar", null, reserva(LocalDate.now().plusDays(5), "09:10"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).contains("El horario seleccionado ya no está disponible");
        verify(interno, never()).buscarOCrearPaciente(any());
    }

    // ------------------------------------------------------------------ pagos

    @Test
    void pagoAprobado_porEventoConfirmaElTurno() throws Exception {
        Turno t = turnoGuardado(50, Turno.PENDIENTE_PAGO, LocalDate.now().plusDays(4), LocalTime.of(10, 30), null, LocalDateTime.now());

        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.PAGOS_PAGO_APROBADO_V1, MessageBuilder
                .withBody(mapper.writeValueAsBytes(EventEnvelope.<PagoAprobadoData>builder().eventId(UUID.randomUUID())
                        .eventType("pagos.pago.aprobado").producer("pagos").aggregateType("pago").aggregateId("9").aggregateVersion(1)
                        .data(new PagoAprobadoData(9L, t.id, "mp-9", new BigDecimal("60000"), "mercadopago")).build()))
                .setContentType("application/json").build());

        esperar(() -> Turno.CONFIRMADO.equals(turnos.porId(t.id).orElseThrow().estado));
        Turno confirmado = turnos.porId(t.id).orElseThrow();
        assertThat(confirmado.pagoEstado).isEqualTo("APROBADO");
        assertThat(confirmado.pagoTransactionId).isEqualTo("mp-9");
        assertThat(confirmado.version).isEqualTo(6);
        JsonNode data = evento(Topicos.TURNOS_TURNO_CONFIRMADO_V1).get("data");
        assertThat(data.get("tipoLabel").asText()).isEqualTo("Consulta particular");
        assertThat(data.get("origen").asText()).isEqualTo("PAGO");
        assertThat(evento(Topicos.TURNOS_TURNO_ACTUALIZADO_V1).get("data").get("pagoEstado").asText()).isEqualTo("APROBADO");
    }

    @Test
    void pagoDeUnTurnoVencido_conElHorarioTomadoSeReembolsa_yConElHorarioLibreSeConfirma() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(4);
        Turno vencido = turnoGuardado(60, Turno.CANCELADO, fecha, LocalTime.of(9, 0), null, LocalDateTime.now().minusMinutes(20));
        turnoGuardado(61, Turno.CONFIRMADO, fecha, LocalTime.of(9, 0), "APROBADO", LocalDateTime.now().minusMinutes(10));
        Turno libre = turnoGuardado(62, Turno.CANCELADO, fecha, LocalTime.of(11, 15), null, LocalDateTime.now().minusMinutes(20));

        cobros.pagoAprobado(new PagoAprobadoData(1L, vencido.id, "mp-v", new BigDecimal("60000"), "mercadopago"));
        cobros.pagoAprobado(new PagoAprobadoData(2L, libre.id, "mp-l", new BigDecimal("60000"), "mercadopago"));

        Turno reembolsado = turnos.porId(vencido.id).orElseThrow();
        assertThat(reembolsado.estado).isEqualTo(Turno.CANCELADO);
        assertThat(reembolsado.pagoEstado).isEqualTo("APROBADO");
        JsonNode cancelado = evento(Topicos.TURNOS_TURNO_CANCELADO_V1).get("data");
        assertThat(cancelado.get("turnoId").asLong()).isEqualTo(60);
        assertThat(cancelado.get("canceladoPor").asText()).isEqualTo("SISTEMA");
        assertThat(cancelado.get("correspondeReembolso").asBoolean()).isTrue();
        assertThat(cancelado.get("resultadoReembolso").asText()).isEqualTo("EN_PROCESO");
        assertThat(turnos.porId(libre.id).orElseThrow().estado).isEqualTo(Turno.CONFIRMADO);
    }

    // ------------------------------------------------------------------ cancelación

    @Test
    void cancelar_conLaPoliticaDe48Horas() throws Exception {
        Turno lejos = turnoGuardado(70, Turno.CONFIRMADO, LocalDate.now().plusDays(6), LocalTime.of(9, 0), "APROBADO", LocalDateTime.now());
        Turno cerca = turnoGuardado(71, Turno.CONFIRMADO, LocalDate.now().plusDays(1), LocalTime.of(9, 0), "APROBADO", LocalDateTime.now());
        Turno delProfesional = turnoGuardado(72, Turno.CONFIRMADO, LocalDate.now().plusDays(1), LocalTime.of(10, 30), "APROBADO",
                LocalDateTime.now());

        assertThat(pedir(HttpMethod.POST, "/api/turnos/" + lejos.id + "/cancelar", token("martina@mail.com", "PACIENTE"), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evento(Topicos.TURNOS_TURNO_CANCELADO_V1).get("data").get("resultadoReembolso").asText()).isEqualTo("EN_PROCESO");

        pedir(HttpMethod.POST, "/api/turnos/" + cerca.id + "/cancelar", token("martina@mail.com", "PACIENTE"), null);
        JsonNode sinReembolso = evento(Topicos.TURNOS_TURNO_CANCELADO_V1).get("data");
        assertThat(sinReembolso.get("resultadoReembolso").asText()).isEqualTo("SIN_REEMBOLSO");
        assertThat(sinReembolso.get("canceladoPor").asText()).isEqualTo("PACIENTE");

        pedir(HttpMethod.POST, "/api/turnos/" + delProfesional.id + "/cancelar", token("lucia@mail.com", "PSIQUIATRA"), null);
        JsonNode porProfesional = evento(Topicos.TURNOS_TURNO_CANCELADO_V1).get("data");
        assertThat(porProfesional.get("canceladoPor").asText()).isEqualTo("PROFESIONAL");
        assertThat(porProfesional.get("resultadoReembolso").asText()).isEqualTo("EN_PROCESO");

        assertThat(pedir(HttpMethod.POST, "/api/turnos/" + lejos.id + "/cancelar", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cancelarAsistenciaYReprogramar_soloElDuenioDelTurno() {
        personas.guardar(persona(31L, "PACIENTE", "Otro", "otro@mail.com"));
        personas.guardar(persona(17L, "PSIQUIATRA", "Dr. Otro", "otro.dr@mail.com"));
        Turno t = turnoGuardado(75, Turno.CONFIRMADO, LocalDate.now().plusDays(6), LocalTime.of(9, 0), "APROBADO", LocalDateTime.now());

        assertThat(pedir(HttpMethod.POST, "/api/turnos/" + t.id + "/cancelar", token("otro@mail.com", "PACIENTE"), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(HttpMethod.PUT, "/api/turnos/" + t.id + "/asistencia?asistencia=LLEGO", token("otro.dr@mail.com", "PSIQUIATRA"), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(HttpMethod.PUT, "/api/turnos/" + t.id + "/reprogramar?fecha=" + LocalDate.now().plusDays(7) + "&hora=10:30",
                token("otro.dr@mail.com", "PSIQUIATRA"), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(turnos.porId(t.id).orElseThrow().estado).isEqualTo(Turno.CONFIRMADO);

        assertThat(pedir(HttpMethod.PUT, "/api/turnos/" + t.id + "/asistencia?asistencia=LLEGO", token("lucia@mail.com", "PSIQUIATRA"), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pedir(HttpMethod.PUT, "/api/turnos/" + t.id + "/reprogramar?fecha=" + LocalDate.now().plusDays(7) + "&hora=10:30",
                token("lucia@mail.com", "PSIQUIATRA"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        Turno cambiado = turnos.porId(t.id).orElseThrow();
        assertThat(cambiado.asistencia).isEqualTo("LLEGO");
        assertThat(cambiado.horaInicio).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void abandonarPago_soloConElTokenDeLaReserva() {
        Turno t = turnoGuardado(80, Turno.PENDIENTE_PAGO, LocalDate.now().plusDays(3), LocalTime.of(9, 0), null, LocalDateTime.now());

        assertThat(pedir(HttpMethod.POST, "/api/turnos/80/abandonar-pago?token=otro", null, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(HttpMethod.POST, "/api/turnos/80/abandonar-pago?token=" + t.tokenReserva, null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(turnos.porId(80L).orElseThrow().estado).isEqualTo(Turno.CANCELADO);
    }

    // ------------------------------------------------------------------ agenda del profesional y del paciente

    @Test
    void misTurnos_delProfesionalYDelPaciente() throws Exception {
        turnoGuardado(90, Turno.CONFIRMADO, LocalDate.now().plusDays(2), LocalTime.of(9, 0), "APROBADO", LocalDateTime.now());

        ResponseEntity<String> profesional = pedir(HttpMethod.GET, "/api/medicos/turnos", token("lucia@mail.com", "PSIQUIATRA"), null);
        assertThat(profesional.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode turno = mapper.readTree(profesional.getBody()).get(0);
        assertThat(turno.get("patientName").asText()).isEqualTo("Martina");
        assertThat(turno.get("status").asText()).isEqualTo("confirmed");
        assertThat(turno.get("horaInicio").asText()).isEqualTo("09:00");
        assertThat(turno.get("patientInfo").get("dni").asText()).isEqualTo("30111222");
        assertThat(turno.get("patientInfo").get("fechaNacimiento").asText()).isEqualTo("1990-03-07");
        assertThat(turno.get("patientInfo").get("datosOfuscado").asText()).isEqualTo("N");

        ResponseEntity<String> paciente = pedir(HttpMethod.GET, "/api/pacientes/me/turnos", token("martina@mail.com", "PACIENTE"), null);
        JsonNode suyo = mapper.readTree(paciente.getBody()).get(0);
        assertThat(suyo.get("patientName").asText()).isEqualTo("Dra. Lucía");
        assertThat(suyo.get("domicilioAtencion").asText()).isEqualTo("Av. Corrientes 1234");

        assertThat(pedir(HttpMethod.GET, "/api/medicos/turnos", token("martina@mail.com", "PACIENTE"), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void documentoEnviado_loMandaNotificacionesYQuedaMarcado() throws Exception {
        Turno doc = turnoGuardado(95, Turno.CONFIRMADO, LocalDate.now(), LocalTime.of(8, 0), "APROBADO", LocalDateTime.now());
        jdbc.update("UPDATE turno SET ocupa_agenda = false, servicio_id = 'certificado' WHERE id = 95");

        ResponseEntity<String> r = pedir(HttpMethod.POST, "/api/turnos/95/documento-enviado", token("lucia@mail.com", "PSIQUIATRA"),
                "{\"archivoData\":\"data:application/pdf;base64,JVBERi0=\",\"archivoNombre\":\"certificado.pdf\"}");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(interno).enviarDocumento(new ClienteInterno.DocumentoAdjunto("martina@mail.com", "Martina", "Dra. Lucía", "Certificado",
                "data:application/pdf;base64,JVBERi0=", "certificado.pdf"));
        assertThat(turnos.porId(doc.id).orElseThrow().documentoEnviado).isTrue();
        JsonNode enviado = evento(Topicos.TURNOS_DOCUMENTO_ENVIADO_V1);
        assertThat(enviado.get("aggregateType").asText()).isEqualTo("turno");
        assertThat(enviado.get("data").get("origen").asText()).isEqualTo("ADJUNTO");
    }

    // ------------------------------------------------------------------ tareas programadas

    @Test
    void vencimientoYRecordatorio() throws Exception {
        turnoGuardado(100, Turno.PENDIENTE_PAGO, LocalDate.now().plusDays(3), LocalTime.of(9, 0), null, LocalDateTime.now().minusMinutes(6));
        turnoGuardado(101, Turno.CONFIRMADO, LocalDate.now().plusDays(2), LocalTime.of(10, 30), "APROBADO", LocalDateTime.now());

        programadas.vencerReservas();
        programadas.recordatorios();

        assertThat(turnos.porId(100L).orElseThrow().estado).isEqualTo(Turno.CANCELADO);
        assertThat(contar(Topicos.TURNOS_TURNO_EXPIRADO_V1)).isEqualTo(1);
        Turno recordado = turnos.porId(101L).orElseThrow();
        assertThat(recordado.confirmacionAsistenciaEmailEnviado).isTrue();
        assertThat(recordado.tokenConfirmacionAsistencia).isNotBlank();
        assertThat(evento(Topicos.TURNOS_RECORDATORIO_PROGRAMADO_V1).get("data").get("modalidad").asText()).isEqualTo("Online (videollamada)");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(new ServiceJwt(CLAVE_SERVICIO).emitirToken("notificaciones"));
        ResponseEntity<String> enlaces = http.exchange("/internal/turnos/turnos/101/enlaces-asistencia", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(enlaces.getBody()).contains("https://tranqui.test/api/turnos/101/confirmar-asistencia?token="
                + recordado.tokenConfirmacionAsistencia);

        ResponseEntity<String> pagina = pedir(HttpMethod.GET, "/api/turnos/101/confirmar-asistencia?token="
                + recordado.tokenConfirmacionAsistencia, null, null);
        assertThat(pagina.getBody()).contains("¡Asistencia confirmada!");
        assertThat(contar(Topicos.TURNOS_ASISTENCIA_CONFIRMADA_V1)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ carga inicial y resincronización

    @Test
    void cargaInicial_copiaConLosIdsYLaSecuenciaSigueDespues() {
        jdbc.update("DELETE FROM persona");
        jdbc.update("DELETE FROM profesional");
        when(interno.pacientes(eq(0), anyInt())).thenReturn(List.of(pacienteMonolito(),
                new ClienteInterno.PacienteMonolito(MEDICO, 2, "PSIQUIATRA", "lucia@mail.com", "Dra. Lucía", null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, true)));
        when(interno.profesionales(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.ProfesionalMonolito(MEDICO, "PSIQUIATRA",
                true, perfil(), List.of(new DisponibilidadProfesionalData.Franja(1, "09:00", "10:00", null)), List.of())));
        TurnoActualizadoData d = new TurnoActualizadoData(4000L, PACIENTE, MEDICO, LocalDate.now().plusDays(1), LocalTime.of(9, 0),
                LocalTime.of(9, 45), "PARTICULAR", "ONLINE", "CONFIRMADO", "ESPERANDO", new BigDecimal("60000"), null, "particular",
                true, null, null, null, null, false, false, LocalDateTime.now().minusDays(2), false, false, "mp-4000", "APROBADO",
                new BigDecimal("60000"), LocalDateTime.now().minusDays(2));
        when(interno.turnos(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.TurnoMonolito(17, d, "r-4000", "a-4000")));

        cargaInicial.cargar();

        Turno copiado = turnos.porId(4000L).orElseThrow();
        assertThat(copiado.version).isEqualTo(17);
        assertThat(copiado.tokenConfirmacionAsistencia).isEqualTo("a-4000");
        assertThat(copiado.pagoEstado).isEqualTo("APROBADO");
        assertThat(personas.porId(MEDICO).orElseThrow().mpVinculada()).isTrue();
        Long siguiente = jdbc.queryForObject("SELECT nextval('turno_id_seq')", Long.class);
        assertThat(siguiente).isEqualTo(4001L);
    }

    @Test
    void resincronizacion_soloConTokenDeServicio_yConLaFechaEnIso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(3);
        turnoGuardado(110, Turno.CONFIRMADO, fecha, LocalTime.of(9, 0), "APROBADO", LocalDateTime.now());

        assertThat(pedir(HttpMethod.GET, "/internal/turnos/turnos", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(new ServiceJwt(CLAVE_SERVICIO).emitirToken("monolito"));
        ResponseEntity<String> r = http.exchange("/internal/turnos/turnos", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        JsonNode uno = mapper.readTree(r.getBody()).get(0);
        assertThat(uno.get("version").asInt()).isEqualTo(5);
        assertThat(uno.get("turno").get("fecha").asText()).isEqualTo(fecha.toString());
        assertThat(r.getBody()).doesNotContain("reserva-110");
    }
}
