package com.tranqui.profesionales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.profesionales.clinica.Dtos;
import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Personas.Persona;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.Solicitudes;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import com.tranqui.profesionales.interno.ClienteChat;
import com.tranqui.profesionales.interno.ClienteInterno;
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
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de integración para la Fase 7 (H2b): historia clínica, ficha de pacientes,
 * lista de pacientes y conceptos (documentos pagos) en el servicio de profesionales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "profesionales.carga-inicial.habilitada=false",
        "profesionales.clinica.habilitada=false",
        "profesionales.sincronizacion-segundos=3600",
        "jwt.secret=" + ClinicaH2bIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + ClinicaH2bIntegracionTest.CLAVE_SERVICIO,
        "tranqui.gateway.url=http://localhost:1"
})
class ClinicaH2bIntegracionTest {

    static final String CLAVE_JWT = "clave-de-test-de-al-menos-32-bytes-ok";
    static final String CLAVE_SERVICIO = "clave-servicio-test-de-32-bytes-ok";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;
    @MockitoBean ClienteChat clienteChat;

    @Autowired Personas personas;
    @Autowired TurnosAtendidos turnosAtendidos;
    @Autowired Seguimientos seguimientos;
    @Autowired Informes informes;
    @Autowired Solicitudes solicitudes;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void limpiar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("profesionales"));
            return info != null && info.getConsumerCount() > 0;
        });

        for (String tabla : List.of("seguimiento_diario", "informe_clinico", "receta", "solicitud_documento",
                "turno_atendido", "persona", "outbox", "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);
        reset(clienteChat);
        when(clienteChat.resumen(anyLong())).thenReturn(ClienteChat.Resumen.vacio());
    }

    private static String token(String email, String rol) {
        return Jwts.builder()
                .setClaims(Map.of("rol", rol))
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
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

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long inicio = System.currentTimeMillis();
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() - inicio > Duration.ofSeconds(10).toMillis()) {
                throw new AssertionError("Timeout esperando condición");
            }
            Thread.sleep(100);
        }
    }

    private Persona guardarPersona(Long id, String email, String rol, String nombre, String apellido) {
        Persona p = new Persona(id, 1, rol, email, nombre, apellido, "+54 11 5555", "30111222", "Calle 123",
                "OSDE", "12345", "M", LocalDate.of(1990, 1, 1), 20301112229L, "DNI", 30111222,
                "N", 10, "123456", "Plan 210", "tok1", true, "Av. Siempre Viva", "742", null, null,
                "1000", "CABA", "CABA", "Argentina");
        personas.guardar(p);
        return p;
    }

    // =========================================================================================
    // 1. Cargar, leer, anular y corregir informe (VIGENTE_CORREGIDO, ANEXO_CORRECCION, 403 otro)
    // =========================================================================================
    @Test
    void informe_cicloCompleto_cargarLeerAnularCorregir_y_rechazoOtroProfesional() throws Exception {
        Persona med1 = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona med2 = guardarPersona(2L, "dr.hacker@tranqui.com", "PSIQUIATRA", "Hacker", "Perez");
        Persona pac = guardarPersona(10L, "paciente1@mail.com", "PACIENTE", "Juan", "Gomez");

        // Dr. Ana atiende a Juan por turno confirmado
        turnosAtendidos.guardar(100L, med1.id(), pac.id(), LocalDate.now(), "CONFIRMADO", 1);

        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String tokenHacker = token("dr.hacker@tranqui.com", "PSIQUIATRA");

        // 1. Cargar informe
        String jsonCrear = """
                {
                    "tipoInforme": "Evolutivo",
                    "planTrabajo": "Continuar medicación",
                    "contenido": "Paciente estable con respuesta favorable",
                    "nombreArchivo": "informe1.pdf"
                }
                """;
        ResponseEntity<String> rCrear = pedir(HttpMethod.POST, "/api/pacientes/" + pac.id() + "/informes", tokenAna, jsonCrear);
        assertThat(rCrear.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode nodoCrear = mapper.readTree(rCrear.getBody());
        Long informeId = nodoCrear.get("id").asLong();
        assertThat(nodoCrear.get("estado").asText()).isEqualTo("VIGENTE");
        assertThat(nodoCrear.get("hashIntegridad").asText()).isNotEmpty();
        assertThat(nodoCrear.get("nombreMedico").asText()).isEqualTo("Ana Paz");

        // 2. Leer informes
        ResponseEntity<String> rLeer = pedir(HttpMethod.GET, "/api/pacientes/" + pac.id() + "/informes", tokenAna, null);
        assertThat(rLeer.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lista = mapper.readTree(rLeer.getBody());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("id").asLong()).isEqualTo(informeId);

        // 3. Otro profesional intenta anular o corregir -> 403
        ResponseEntity<String> rAnularHacker = pedir(HttpMethod.DELETE,
                "/api/pacientes/" + pac.id() + "/informes/" + informeId + "?motivo=Hacking", tokenHacker, null);
        assertThat(rAnularHacker.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String jsonEditar = """
                {
                    "tipoInforme": "Evolutivo",
                    "planTrabajo": "Cambio de plan",
                    "contenido": "Contenido corregido",
                    "motivo": "Corrección de dosis"
                }
                """;
        ResponseEntity<String> rEditarHacker = pedir(HttpMethod.PUT,
                "/api/pacientes/" + pac.id() + "/informes/" + informeId, tokenHacker, jsonEditar);
        assertThat(rEditarHacker.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. Ana corrige el informe -> se crea anexo, original queda VIGENTE_CORREGIDO
        ResponseEntity<String> rCorregir = pedir(HttpMethod.PUT,
                "/api/pacientes/" + pac.id() + "/informes/" + informeId, tokenAna, jsonEditar);
        assertThat(rCorregir.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode anexo = mapper.readTree(rCorregir.getBody());
        Long anexoId = anexo.get("id").asLong();
        assertThat(anexoId).isNotEqualTo(informeId);
        assertThat(anexo.get("estado").asText()).isEqualTo("ANEXO_CORRECCION");
        assertThat(anexo.get("informeOriginalId").asLong()).isEqualTo(informeId);
        assertThat(anexo.get("motivo").asText()).isEqualTo("Corrección de dosis");
        assertThat(anexo.get("hashIntegridad").asText()).isNotEmpty();

        // El original en base queda VIGENTE_CORREGIDO
        Informes.Informe origDb = informes.porId(informeId).orElseThrow();
        assertThat(origDb.estado()).isEqualTo("VIGENTE_CORREGIDO");

        // 5. Ana anula el informe original
        ResponseEntity<String> rAnular = pedir(HttpMethod.DELETE,
                "/api/pacientes/" + pac.id() + "/informes/" + informeId + "?motivo=Error de carga", tokenAna, null);
        assertThat(rAnular.getStatusCode()).isEqualTo(HttpStatus.OK);
        Informes.Informe anuladoDb = informes.porId(informeId).orElseThrow();
        assertThat(anuladoDb.estado()).isEqualTo("ANULADO");
        assertThat(anuladoDb.motivo()).isEqualTo("Error de carga");

        // Intentar corregir un informe anulado -> 409 Conflict
        ResponseEntity<String> rCorregirAnulado = pedir(HttpMethod.PUT,
                "/api/pacientes/" + pac.id() + "/informes/" + informeId, tokenAna, jsonEditar);
        assertThat(rCorregirAnulado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // =========================================================================================
    // 2. Cargar seguimiento (atendido por turno, por chat, no atendido 403, sin profesional 400)
    // =========================================================================================
    @Test
    void seguimiento_accesoAtendidoTurnoChat_noAtendido403_pacienteSinProfesional400() throws Exception {
        Persona med = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona pacTurno = guardarPersona(10L, "pac.turno@mail.com", "PACIENTE", "Turno", "Paciente");
        Persona pacChat = guardarPersona(11L, "pac.chat@mail.com", "PACIENTE", "Chat", "Paciente");
        Persona pacAjeno = guardarPersona(12L, "pac.ajeno@mail.com", "PACIENTE", "Ajeno", "Paciente");

        // pacTurno tiene turno confirmado
        turnosAtendidos.guardar(200L, med.id(), pacTurno.id(), LocalDate.now(), "CONFIRMADO", 1);

        // pacChat chateó con la doctora
        when(clienteChat.resumen(med.id())).thenReturn(new ClienteChat.Resumen(List.of(pacChat.id()), Map.of()));

        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String jsonSeg = """
                {
                    "estadoAnimo": "Bueno",
                    "sintomas": "Sin ansiedad",
                    "notas": "Buena adherencia"
                }
                """;

        // Atendido por turno -> OK
        ResponseEntity<String> rTurno = pedir(HttpMethod.POST, "/api/pacientes/" + pacTurno.id() + "/seguimientos", tokenAna, jsonSeg);
        assertThat(rTurno.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(rTurno.getBody()).get("nombreMedico").asText()).isEqualTo("Ana Paz");

        // Atendido por chat -> OK
        ResponseEntity<String> rChat = pedir(HttpMethod.POST, "/api/pacientes/" + pacChat.id() + "/seguimientos", tokenAna, jsonSeg);
        assertThat(rChat.getStatusCode()).isEqualTo(HttpStatus.OK);

        // No atendido -> 403 Forbidden
        ResponseEntity<String> rAjeno = pedir(HttpMethod.POST, "/api/pacientes/" + pacAjeno.id() + "/seguimientos", tokenAna, jsonSeg);
        assertThat(rAjeno.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rAjeno.getBody()).contains("El paciente no está entre los que atendés.");

        // Paciente carga seguimiento sin profesional -> 400 Bad Request
        String tokenPacTurno = token("pac.turno@mail.com", "PACIENTE");
        String jsonSinMed = """
                {
                    "estadoAnimo": "Regular",
                    "sintomas": "Insomnio"
                }
                """;
        ResponseEntity<String> rSinMed = pedir(HttpMethod.POST, "/api/pacientes/me/seguimientos", tokenPacTurno, jsonSinMed);
        assertThat(rSinMed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rSinMed.getBody()).contains("El seguimiento tiene que ser para un profesional que te atiende.");

        // Paciente carga seguimiento con profesional que lo atiende -> OK
        String jsonConMed = """
                {
                    "estadoAnimo": "Regular",
                    "sintomas": "Insomnio",
                    "medico": { "id": 1 }
                }
                """;
        ResponseEntity<String> rConMed = pedir(HttpMethod.POST, "/api/pacientes/me/seguimientos", tokenPacTurno, jsonConMed);
        assertThat(rConMed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode segCreado = mapper.readTree(rConMed.getBody());
        assertThat(segCreado.get("nombreMedico").asText()).isEqualTo("Ana Paz");
    }

    // =========================================================================================
    // 3. Evento historia-clinica.actualizada en la outbox sin contenido clínico
    // =========================================================================================
    @Test
    void eventoOutbox_historiaClinicaActualizada_sinContenidoClinico() throws Exception {
        Persona med = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona pac = guardarPersona(10L, "paciente@mail.com", "PACIENTE", "Juan", "Perez");
        turnosAtendidos.guardar(300L, med.id(), pac.id(), LocalDate.now(), "CONFIRMADO", 1);

        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String jsonInforme = """
                {
                    "tipoInforme": "Evaluativo",
                    "planTrabajo": "Plan Secreto",
                    "contenido": "Diagnóstico confidencial muy sensible",
                    "nombreArchivo": "eval.pdf"
                }
                """;
        pedir(HttpMethod.POST, "/api/pacientes/" + pac.id() + "/informes", tokenAna, jsonInforme);

        List<Map<String, Object>> filasOutbox = jdbc.queryForList(
                "SELECT topic, aggregate_type, payload::text AS payload FROM outbox WHERE topic = ?",
                Topicos.PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1);
        assertThat(filasOutbox).hasSize(1);

        Map<String, Object> fila = filasOutbox.get(0);
        assertThat(fila.get("aggregate_type")).isEqualTo("informe");
        String payloadJson = (String) fila.get("payload");
        JsonNode envelope = mapper.readTree(payloadJson);
        JsonNode data = envelope.get("data");

        assertThat(data.get("profesionalId").asLong()).isEqualTo(med.id());
        assertThat(data.get("pacienteId").asLong()).isEqualTo(pac.id());
        assertThat(data.get("tipo").asText()).isEqualTo("INFORME");
        assertThat(data.get("cargadoPor").asText()).isEqualTo("PROFESIONAL");
        assertThat(data.get("tipoInforme").asText()).isEqualTo("Evaluativo");

        // Verificación estricta: ningún dato de salud viaja en el evento
        assertThat(payloadJson).doesNotContain("Diagnóstico confidencial");
        assertThat(payloadJson).doesNotContain("Plan Secreto");
        assertThat(data.has("contenido")).isFalse();
        assertThat(data.has("planTrabajo")).isFalse();
    }

    // =========================================================================================
    // 4. Lista de Pacientes (prioridad, última visita, sin turno, no leídos y con chat caído)
    // =========================================================================================
    @Test
    void listaPacientes_prioridadUltimaVisitaSinTurnoNoLeidos_y_chatCaido() throws Exception {
        Persona med = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona pac1 = guardarPersona(10L, "p1@mail.com", "PACIENTE", "Prioridad", "Alta");
        Persona pac2 = guardarPersona(11L, "p2@mail.com", "PACIENTE", "Ultima", "Visita");
        Persona pac3 = guardarPersona(12L, "p3@mail.com", "PACIENTE", "Solo", "Chat");

        // pac1: turno confirmado en 2 días -> PRIORIDAD_ALTA
        turnosAtendidos.guardar(401L, med.id(), pac1.id(), LocalDate.now().plusDays(2), "CONFIRMADO", 1);

        // pac2: turno atendido en el pasado
        turnosAtendidos.guardar(402L, med.id(), pac2.id(), LocalDate.now().minusDays(5), "PENDIENTE_VALIDACION", 1);

        // pac3: solo chateó, tiene 4 mensajes sin leer
        when(clienteChat.resumen(med.id())).thenReturn(new ClienteChat.Resumen(List.of(pac3.id()), Map.of(pac3.id(), 4)));

        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> rLista = pedir(HttpMethod.GET, "/api/pacientes/atendidos", tokenAna, null);
        assertThat(rLista.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode array = mapper.readTree(rLista.getBody());
        assertThat(array).hasSize(3);

        JsonNode p1Node = findInArray(array, pac1.id());
        assertThat(p1Node.get("prioridadClinica").asText()).isEqualTo("PRIORIDAD_ALTA");
        assertThat(p1Node.get("sinTurno").asBoolean()).isFalse();

        JsonNode p2Node = findInArray(array, pac2.id());
        assertThat(p2Node.get("prioridadClinica").asText()).isEqualTo("PRIORIDAD_BAJA");
        assertThat(p2Node.get("ultimaVisita").asText()).isEqualTo(LocalDate.now().minusDays(5).toString());
        assertThat(p2Node.get("sinTurno").asBoolean()).isFalse();

        JsonNode p3Node = findInArray(array, pac3.id());
        assertThat(p3Node.get("sinTurno").asBoolean()).isTrue();
        assertThat(p3Node.get("ultimaVisita").asText()).isEqualTo("Sin turnos registrados");
        assertThat(p3Node.get("unreadMessagesCount").asInt()).isEqualTo(4);

        // Chat caído (simulado con resumen vacío): la lista sale igual con los que tienen turno
        when(clienteChat.resumen(med.id())).thenReturn(ClienteChat.Resumen.vacio());
        ResponseEntity<String> rListaCaida = pedir(HttpMethod.GET, "/api/pacientes/atendidos", tokenAna, null);
        assertThat(rListaCaida.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arrayCaido = mapper.readTree(rListaCaida.getBody());
        assertThat(arrayCaido).hasSize(2);
    }

    private JsonNode findInArray(JsonNode array, Long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        throw new AssertionError("ID no encontrado en JSON: " + id);
    }

    // =========================================================================================
    // 5. Ficha del paciente (OK actualiza copia, 400 monolito, 503 monolito caído, 403 no atendido)
    // =========================================================================================
    @Test
    void fichaPaciente_actualizacion_ok_error400_monolitoCaido503_noAtendido403() throws Exception {
        Persona med = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona pac = guardarPersona(10L, "paciente@mail.com", "PACIENTE", "Pedro", "Alvarez");
        Persona ajeno = guardarPersona(11L, "ajeno@mail.com", "PACIENTE", "No", "Atendido");

        turnosAtendidos.guardar(500L, med.id(), pac.id(), LocalDate.now(), "CONFIRMADO", 1);
        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");

        // 1. Paciente no atendido -> 403 sin llamar al monolito
        String jsonFicha = """
                {
                    "nombre": "Pedro Modificado",
                    "apellido": "Alvarez",
                    "dni": "30111222"
                }
                """;
        ResponseEntity<String> rAjeno = pedir(HttpMethod.PUT, "/api/pacientes/" + ajeno.id(), tokenAna, jsonFicha);
        assertThat(rAjeno.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(interno, never()).actualizarPaciente(any(), any());

        // 2. Monolito OK -> actualiza la copia persona y devuelve PacienteDto
        ClienteInterno.PacienteMonolito pacienteModificado = new ClienteInterno.PacienteMonolito(
                pac.id(), 2, "PACIENTE", "paciente@mail.com", "Pedro Modificado", "Alvarez",
                "+54 11 5555", "30111222", "Calle 123", "OSDE", "12345", "M", LocalDate.of(1990, 1, 1),
                20301112229L, "DNI", 30111222, "N", null, null, null, null, true,
                "Calle Nueva", "123", null, null, "1000", "CABA", "CABA", "Argentina");
        when(interno.actualizarPaciente(eq(pac.id()), any())).thenReturn(pacienteModificado);

        ResponseEntity<String> rOk = pedir(HttpMethod.PUT, "/api/pacientes/" + pac.id(), tokenAna, jsonFicha);
        assertThat(rOk.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode nodoOk = mapper.readTree(rOk.getBody());
        assertThat(nodoOk.get("nombre").asText()).isEqualTo("Pedro Modificado");
        assertThat(nodoOk.get("ultimaVisita").asText()).isEqualTo("Hoy");
        assertThat(nodoOk.get("sinTurno").asBoolean()).isFalse();

        // Verifica que la copia en persona se actualizó
        Persona enDb = personas.porId(pac.id()).orElseThrow();
        assertThat(enDb.nombre()).isEqualTo("Pedro Modificado");
        assertThat(enDb.domicilioCalle()).isEqualTo("Calle Nueva");

        // 3. Monolito responde 400 -> servicio devuelve 400 con el mismo mensaje
        when(interno.actualizarPaciente(eq(pac.id()), any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
                        "el número de documento debe tener entre 6 y 9 dígitos".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        ResponseEntity<String> r400 = pedir(HttpMethod.PUT, "/api/pacientes/" + pac.id(), tokenAna, jsonFicha);
        assertThat(r400.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r400.getBody()).contains("el número de documento debe tener entre 6 y 9 dígitos");

        // 4. Monolito caído -> 503 ServicioNoDisponibleException
        when(interno.actualizarPaciente(eq(pac.id()), any()))
                .thenThrow(new ResourceAccessException("Connection refused"));
        ResponseEntity<String> r503 = pedir(HttpMethod.PUT, "/api/pacientes/" + pac.id(), tokenAna, jsonFicha);
        assertThat(r503.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // =========================================================================================
    // 6. Conceptos (solicitar publica evento, pago aprobado pasa a APROBADO, pendientes, emitir)
    // =========================================================================================
    @Test
    void conceptos_solicitar_pagoAprobado_pendientes_emitir() throws Exception {
        Persona med = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona med2 = guardarPersona(2L, "dr.otro@tranqui.com", "PSIQUIATRA", "Otro", "Medico");
        Persona pac = guardarPersona(10L, "paciente@mail.com", "PACIENTE", "Juan", "Perez");

        String tokenPac = token("paciente@mail.com", "PACIENTE");
        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String tokenOtro = token("dr.otro@tranqui.com", "PSIQUIATRA");

        // 1. Paciente solicita concepto
        String jsonSol = """
                {
                    "medicoId": 1,
                    "tipoConcepto": "RECETA_CONTROL",
                    "precio": 25000.00
                }
                """;
        ResponseEntity<String> rSol = pedir(HttpMethod.POST, "/api/conceptos/solicitar", tokenPac, jsonSol);
        assertThat(rSol.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode nodoSol = mapper.readTree(rSol.getBody());
        Long solicitudId = nodoSol.get("id").asLong();
        assertThat(nodoSol.get("checkoutUrl").asText()).isEmpty();

        // Evento publicado en outbox con título correcto
        List<Map<String, Object>> outboxList = jdbc.queryForList(
                "SELECT payload::text AS payload FROM outbox WHERE topic = ?", Topicos.PROFESIONALES_CONCEPTO_SOLICITADO_V1);
        assertThat(outboxList).hasSize(1);
        JsonNode env = mapper.readTree((String) outboxList.get(0).get("payload"));
        assertThat(env.get("data").get("titulo").asText()).isEqualTo("RECETA_CONTROL - Ana");

        // 2. Pendientes: todavía no aparece porque está PENDIENTE de pago
        ResponseEntity<String> rPend1 = pedir(HttpMethod.GET, "/api/conceptos/pendientes", tokenAna, null);
        assertThat(rPend1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(rPend1.getBody())).isEmpty();

        // 3. Emitir documento no pagado -> 409 Conflict
        ResponseEntity<String> rEmitNoPago = pedir(HttpMethod.POST,
                "/api/conceptos/" + solicitudId + "/emitir?urlDescarga=https://doc.com/signed.pdf", tokenAna, null);
        assertThat(rEmitNoPago.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 4. Pago aprobado por evento RabbitMQ pagos.concepto.aprobado.v1
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.PAGOS_CONCEPTO_APROBADO_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<ConceptoAprobadoData>builder()
                                .eventId(UUID.randomUUID()).eventType("pagos.concepto.aprobado")
                                .producer("pagos").aggregateType("solicitud").aggregateId(String.valueOf(solicitudId)).aggregateVersion(1)
                                .data(new ConceptoAprobadoData(solicitudId, med.id(), pac.id(), "RECETA_CONTROL", "mp-payment-999"))
                                .build()))
                        .setContentType("application/json").build());

        esperar(() -> "APROBADO".equals(solicitudes.porId(solicitudId).map(Solicitudes.Solicitud::estado).orElse(null)));
        Solicitudes.Solicitud solDb = solicitudes.porId(solicitudId).orElseThrow();
        assertThat(solDb.transactionId()).isEqualTo("mp-payment-999");

        // 5. Pendientes: ahora sí aparece con paciente y médico anidados
        ResponseEntity<String> rPend2 = pedir(HttpMethod.GET, "/api/conceptos/pendientes", tokenAna, null);
        assertThat(rPend2.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pendArray = mapper.readTree(rPend2.getBody());
        assertThat(pendArray).hasSize(1);
        JsonNode item = pendArray.get(0);
        assertThat(item.get("id").asLong()).isEqualTo(solicitudId);
        assertThat(item.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(item.get("emitido").asBoolean()).isFalse();
        assertThat(item.get("paciente").get("nombre").asText()).isEqualTo("Juan");
        assertThat(item.get("medico").get("nombre").asText()).isEqualTo("Ana");

        // 6. Emitir: profesional no dueño -> 403 Forbidden
        ResponseEntity<String> rEmitNoDuenio = pedir(HttpMethod.POST,
                "/api/conceptos/" + solicitudId + "/emitir?urlDescarga=https://doc.com/signed.pdf", tokenOtro, null);
        assertThat(rEmitNoDuenio.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rEmitNoDuenio.getBody()).contains("No está autorizado para emitir este documento");

        // 7. Emitir: dueño -> 200 OK
        ResponseEntity<String> rEmitOk = pedir(HttpMethod.POST,
                "/api/conceptos/" + solicitudId + "/emitir?urlDescarga=https://doc.com/signed.pdf", tokenAna, null);
        assertThat(rEmitOk.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode emitidoNode = mapper.readTree(rEmitOk.getBody());
        assertThat(emitidoNode.get("emitido").asBoolean()).isTrue();
        assertThat(emitidoNode.get("urlDescarga").asText()).isEqualTo("https://doc.com/signed.pdf");
        assertThat(emitidoNode.get("fechaEmision").asText()).isNotEmpty();

        // Ya no aparece en pendientes porque fue emitido
        ResponseEntity<String> rPend3 = pedir(HttpMethod.GET, "/api/conceptos/pendientes", tokenAna, null);
        assertThat(mapper.readTree(rPend3.getBody())).isEmpty();
    }

    // =========================================================================================
    // 7. Formato JSON: campos exactos y fecha dd/MM/yyyy
    // =========================================================================================
    @Test
    void json_seguimientoEInforme_mismosCampos_y_fechaFormatoDdMmYyyy() throws Exception {
        Persona med = guardarPersona(1L, "dra.ana@tranqui.com", "PSIQUIATRA", "Ana", "Paz");
        Persona pac = guardarPersona(10L, "paciente@mail.com", "PACIENTE", "Juan", "Perez");
        turnosAtendidos.guardar(600L, med.id(), pac.id(), LocalDate.now(), "CONFIRMADO", 1);

        String tokenAna = token("dra.ana@tranqui.com", "PSIQUIATRA");

        // 1. Seguimiento
        String jsonSeg = """
                {
                    "fecha": "25/10/2026",
                    "estadoAnimo": "Bueno",
                    "sintomas": "Ninguno",
                    "notas": "Seguimiento test"
                }
                """;
        ResponseEntity<String> rSeg = pedir(HttpMethod.POST, "/api/pacientes/" + pac.id() + "/seguimientos", tokenAna, jsonSeg);
        assertThat(rSeg.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode nodoSeg = mapper.readTree(rSeg.getBody());

        // Comprobamos la fecha dd/MM/yyyy exacta
        assertThat(nodoSeg.get("fecha").asText()).isEqualTo("25/10/2026");
        assertThat(nodoSeg.has("id")).isTrue();
        assertThat(nodoSeg.has("estadoAnimo")).isTrue();
        assertThat(nodoSeg.has("sintomas")).isTrue();
        assertThat(nodoSeg.has("notas")).isTrue();
        assertThat(nodoSeg.has("fechaCreacion")).isTrue();
        assertThat(nodoSeg.has("estado")).isTrue();
        assertThat(nodoSeg.has("hashIntegridad")).isTrue();
        assertThat(nodoSeg.has("nombreMedico")).isTrue();
        assertThat(nodoSeg.has("paciente")).isFalse(); // @JsonIgnore en monolito
        assertThat(nodoSeg.has("medico")).isFalse();   // @JsonIgnore en monolito

        // 2. Informe
        String jsonInf = """
                {
                    "tipoInforme": "Evolutivo",
                    "planTrabajo": "Plan B",
                    "contenido": "Informe de prueba",
                    "nombreArchivo": "archivo.pdf"
                }
                """;
        ResponseEntity<String> rInf = pedir(HttpMethod.POST, "/api/pacientes/" + pac.id() + "/informes", tokenAna, jsonInf);
        assertThat(rInf.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode nodoInf = mapper.readTree(rInf.getBody());

        String fechaEsperada = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(nodoInf.get("fecha").asText()).isEqualTo(fechaEsperada);
        assertThat(nodoInf.has("id")).isTrue();
        assertThat(nodoInf.has("tipoInforme")).isTrue();
        assertThat(nodoInf.has("planTrabajo")).isTrue();
        assertThat(nodoInf.has("contenido")).isTrue();
        assertThat(nodoInf.has("nombreArchivo")).isTrue();
        assertThat(nodoInf.has("fechaCreacion")).isTrue();
        assertThat(nodoInf.has("estado")).isTrue();
        assertThat(nodoInf.has("informeOriginalId")).isTrue();
        assertThat(nodoInf.has("motivo")).isTrue();
        assertThat(nodoInf.has("hashIntegridad")).isTrue();
        assertThat(nodoInf.has("nombreMedico")).isTrue();
        assertThat(nodoInf.has("paciente")).isFalse(); // @JsonIgnore en monolito
        assertThat(nodoInf.has("medico")).isFalse();   // @JsonIgnore en monolito
    }
}
