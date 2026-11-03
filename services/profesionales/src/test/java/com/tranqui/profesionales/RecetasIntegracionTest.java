package com.tranqui.profesionales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Personas.Persona;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Profesionales.DatosMonolito;
import com.tranqui.profesionales.datos.Recetas;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import com.tranqui.profesionales.interno.ClienteChat;
import com.tranqui.profesionales.interno.ClienteInterno;
import com.tranqui.profesionales.recetas.Qbi2CatalogoDtos;
import com.tranqui.profesionales.recetas.Qbi2RecipeClient;
import com.tranqui.profesionales.recetas.Qbi2RecipeException;
import com.tranqui.profesionales.recetas.Qbi2RecetaDtos;
import com.tranqui.profesionales.recetas.RecetaDto;
import com.tranqui.profesionales.recetas.RecetaResponseDto;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.tranqui.profesionales.seguridad.JwtTestHelper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de integración para la Fase 7 (H2c): recetas electrónicas (QBI2 / Innovamed)
 * en el servicio de profesionales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "profesionales.carga-inicial.habilitada=false",
        "profesionales.clinica.habilitada=false",
        "profesionales.sincronizacion-segundos=3600",
        "jwt.secret=" + RecetasIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + RecetasIntegracionTest.CLAVE_SERVICIO,
        "tranqui.gateway.url=http://localhost:1"
})
class RecetasIntegracionTest {

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
    @MockitoBean ClienteChat clienteChat;
    @MockitoBean Qbi2RecipeClient qbi2;

    @Autowired Personas personas;
    @Autowired Profesionales profesionales;
    @Autowired Recetas recetas;
    @Autowired TurnosAtendidos turnosAtendidos;
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

        for (String tabla : List.of("receta", "turno_atendido", "persona", "profesional",
                "outbox", "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);
        reset(clienteChat);
        reset(qbi2);
        when(clienteChat.resumen(anyLong())).thenReturn(ClienteChat.Resumen.vacio());
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

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long inicio = System.currentTimeMillis();
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() - inicio > Duration.ofSeconds(10).toMillis()) {
                throw new AssertionError("Timeout esperando condición");
            }
            Thread.sleep(100);
        }
    }

    private Qbi2RecetaDtos.RecetaResponse mockQbiResponse(String s3Link) {
        Qbi2RecetaDtos.RecetaResult result = Qbi2RecetaDtos.RecetaResult.builder()
                .id("MOCK-RES-1")
                .idReceta("REC-12345")
                .nroCUIR(List.of("CUIR-1"))
                .s3Link(s3Link)
                .verificador("VERIF-999")
                .build();
        Qbi2RecetaDtos.RecetaDetalle detalle = Qbi2RecetaDtos.RecetaDetalle.builder()
                .fecha("2026-10-26")
                .fechavencimiento("2026-11-26")
                .status("OK")
                .build();
        return Qbi2RecetaDtos.RecetaResponse.builder()
                .recetas(List.of(result))
                .errores(Collections.emptyList())
                .response(List.of(detalle))
                .idTransaccion("TX-555")
                .build();
    }

    private void sembrarMedicoYPaciente(boolean puedeRecetar, boolean emailHabilitado) {
        PerfilProfesionalData perfil = new PerfilProfesionalData(
                "contacto@dra-ana.com", "Av. Siempre Viva 742, Córdoba", -31.4, -64.1,
                null, null, null, null,
                "12345", "MP", "Córdoba", 12345, null, null, null, null,
                "Médica", "Psiquiatría", "20301112229",
                null, null, true, true, null, null, null, null, null, null,
                "123456789012", "Dra. Ana Paz", "Psiquiatría", "MP 12345", null,
                null, null, null, null, 45, 15
        );
        DatosMonolito datos = new DatosMonolito(
                1L, 1, "dra.ana@tranqui.com", "PSIQUIATRA", "Psiquiatra",
                "Ana", "Paz", "+54 11 1111-2222", "F",
                LocalDate.of(1985, 5, 20), 20301112229L, "DNI", 30111222,
                null, null, true, true, "active",
                LocalDateTime.now().plusMonths(1), puedeRecetar
        );
        profesionales.alta(datos, perfil);

        Persona personaMedico = new Persona(
                1L, 1, "PSIQUIATRA", "dra.ana@tranqui.com", "Ana", "Paz",
                "+54 11 1111-2222", "30111222", "Av. Siempre Viva 742, Córdoba",
                null, null, "F", LocalDate.of(1985, 5, 20), 20301112229L, "DNI",
                30111222, null, null, null, null, null, true,
                "Av. Siempre Viva", "742", null, null, "5000", "Córdoba", "Córdoba", "Argentina"
        );
        personas.guardar(personaMedico);

        Persona personaPaciente = new Persona(
                2L, 1, "PACIENTE", "juan.paciente@gmail.com", "Juan", "Gomez",
                "+54 11 9999-8888", "40111222", "Calle Falsa 123",
                "OSDE", "1234567", "M", LocalDate.of(1995, 3, 15), 20401112229L, "DNI",
                40111222, null, 10, "1234567", "Plan 210", null, emailHabilitado,
                "Calle Falsa", "123", null, null, "1414", "CABA", "CABA", "Argentina"
        );
        personas.guardar(personaPaciente);
    }

    private String jsonDto(Long pacienteId, String diag, String notas, String medNombre, Boolean noSustituible, String lab) {
        String noSust = noSustituible != null ? String.valueOf(noSustituible) : "null";
        String labJson = lab != null ? ("\"" + lab + "\"") : "null";
        String diagJson = diag != null ? ("\"" + diag + "\"") : "null";
        return """
                {
                  "pacienteId": %d,
                  "diagnosis": %s,
                  "notes": "%s",
                  "medications": [
                    {
                      "name": "%s",
                      "dosage": "50mg",
                      "frequency": "Cada 24hs",
                      "duration": "30 días",
                      "noSustituible": %s,
                      "laboratorio": %s
                    }
                  ]
                }
                """.formatted(pacienteId, diagJson, notas, medNombre, noSust, labJson);
    }

    // =========================================================================================
    // 1. Emitir OK: guarda receta, outbox sin datos de salud, mail a notificaciones
    // =========================================================================================
    @Test
    @DisplayName("Emitir receta OK: persiste receta, outbox sin datos de salud y envía mail si emailHabilitado=true")
    void emitirReceta_ok_persiste_publicaOutbox_enviaMail() throws Exception {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        when(qbi2.generarReceta(any())).thenReturn(mockQbiResponse("https://s3.qbi2.com/receta-12345.pdf"));

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String cuerpo = jsonDto(2L, "Depresión mayor", "Tomar por la mañana", "Sertralina", false, "Roemmers");

        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, cuerpo);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        RecetaResponseDto dto = mapper.readValue(res.getBody(), RecetaResponseDto.class);
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getPdfUrl()).isEqualTo("https://s3.qbi2.com/receta-12345.pdf");
        assertThat(dto.getQbi2IdReceta()).isEqualTo("REC-12345");
        assertThat(dto.getMedico().getNombre()).isEqualTo("Ana");
        assertThat(dto.getMedico().getMatricula()).isEqualTo("12345");
        assertThat(dto.getPaciente().getNombre()).isEqualTo("Juan");
        assertThat(dto.getLaboratorios()).containsExactly("Roemmers");

        // 1. Persistió en la tabla receta
        assertThat(recetas.porId(dto.getId())).isPresent();

        // 2. Publicó en la outbox sin datos de salud
        List<Map<String, Object>> filasOutbox = jdbc.queryForList(
                "SELECT aggregate_type, aggregate_id, topic, payload FROM outbox WHERE topic = ?",
                Topicos.PROFESIONALES_RECETA_EMITIDA_V1
        );
        assertThat(filasOutbox).hasSize(1);
        String payload = filasOutbox.get(0).get("payload").toString();
        assertThat(payload).contains("\"profesionalId\": 1");
        assertThat(payload).contains("\"pacienteId\": 2");
        assertThat(payload).doesNotContain("Sertralina");
        assertThat(payload).doesNotContain("Depresión");
        assertThat(payload).doesNotContain("https://s3.qbi2.com");

        // 3. Pide el mail a notificaciones con el link
        ArgumentCaptor<ClienteInterno.NotificacionReceta> captorMail = ArgumentCaptor.forClass(ClienteInterno.NotificacionReceta.class);
        verify(interno).enviarMailReceta(captorMail.capture());
        ClienteInterno.NotificacionReceta mail = captorMail.getValue();
        assertThat(mail.email()).isEqualTo("juan.paciente@gmail.com");
        assertThat(mail.pacienteNombre()).isEqualTo("Juan");
        assertThat(mail.medicoNombre()).isEqualTo("Ana");
        assertThat(mail.matricula()).isEqualTo("12345");
        assertThat(mail.pdfUrl()).isEqualTo("https://s3.qbi2.com/receta-12345.pdf");
        assertThat(mail.nroRecetario()).isEqualTo("REC-12345");
    }

    @Test
    @DisplayName("Emitir receta con email_habilitado=false no manda mail a notificaciones")
    void emitirReceta_emailDeshabilitado_noEnviaMail() throws Exception {
        sembrarMedicoYPaciente(true, false);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        when(qbi2.generarReceta(any())).thenReturn(mockQbiResponse("https://s3.qbi2.com/receta.pdf"));

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String cuerpo = jsonDto(2L, "Ansiedad", "Tomar con agua", "Clonazepam", null, null);

        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, cuerpo);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(interno, never()).enviarMailReceta(any());
    }

    // =========================================================================================
    // 2. Datos faltantes -> 422 con mensaje exacto del monolito, sin llamar a QBI2
    // =========================================================================================
    @Test
    @DisplayName("Falta DNI del paciente -> 422 sin llamar a QBI2")
    void faltaDniPaciente_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        jdbc.update("UPDATE persona SET dni = NULL, numero_documento = NULL WHERE id = 2");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("falta completar el DNI del paciente (en su ficha clínica)");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("Falta fecha de nacimiento del paciente -> 422 sin llamar a QBI2")
    void faltaFechaNacimientoPaciente_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        jdbc.update("UPDATE persona SET fecha_nacimiento = NULL WHERE id = 2");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("falta completar la fecha de nacimiento del paciente (en su ficha clínica)");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("Falta DNI del médico -> 422 sin llamar a QBI2")
    void faltaDniMedico_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        jdbc.update("UPDATE persona SET dni = NULL, numero_documento = NULL WHERE id = 1");
        jdbc.update("UPDATE profesional SET numero_documento = NULL WHERE id = 1");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("falta completar tu DNI (en Configuración > Perfil profesional)");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("Falta matrícula del médico -> 422 sin llamar a QBI2")
    void faltaMatriculaMedico_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        // Borrar matrícula en el JSON perfil
        jdbc.update("UPDATE profesional SET perfil = perfil - 'matricula' - 'matriculaNumero' WHERE id = 1");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("falta completar tu número de matrícula (en Configuración > Perfil profesional)");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("REFEPS inválido (no tiene 12 dígitos) -> 422 sin llamar a QBI2")
    void refepsInvalido_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        jdbc.update("UPDATE profesional SET perfil = jsonb_set(perfil, '{codigoRefeps}', '\"12345\"') WHERE id = 1");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("tu código REFEPS (tiene que ser el número de 12 dígitos que te asignó SISA");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("Falta domicilio del médico -> 422 sin llamar a QBI2")
    void faltaDomicilioMedico_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        jdbc.update("UPDATE profesional SET perfil = perfil - 'domicilioAtencion' WHERE id = 1");
        jdbc.update("UPDATE persona SET direccion = NULL, domicilio_calle = NULL WHERE id = 1");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("falta completar tu dirección profesional (en Configuración > Perfil profesional — QBI2 la exige aunque atiendas 100% online)");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("Afiliado inválido (<6 caracteres con entidad informada) -> 422 sin llamar a QBI2")
    void afiliadoInvalido_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        jdbc.update("UPDATE persona SET credencial_pan = '123' WHERE id = 2");

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("falta completar el número de afiliado del paciente (tiene que tener entre 6 y 30 caracteres");
        verify(qbi2, never()).generarReceta(any());
    }

    // =========================================================================================
    // 3. Plan sin recetas -> 409; Paciente no atendido -> 403
    // =========================================================================================
    @Test
    @DisplayName("Plan sin recetas (puedeRecetar=false) -> 409 sin llamar a QBI2")
    void planSinRecetas_409() {
        sembrarMedicoYPaciente(false, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("Tu plan actual no incluye recetas electrónicas oficiales");
        verify(qbi2, never()).generarReceta(any());
    }

    @Test
    @DisplayName("Paciente no atendido (sin turno ni chat) -> 403 sin llamar a QBI2")
    void pacienteNoAtendido_403() {
        sembrarMedicoYPaciente(true, true);
        // Sin turno atendido ni chat

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).contains("El paciente no está entre los que atendés.");
        verify(qbi2, never()).generarReceta(any());
    }

    // =========================================================================================
    // 4. QBI2 tira excepción -> 422 recortado a 240; QBI2 sin recetas -> 422
    // =========================================================================================
    @Test
    @DisplayName("QBI2 tira excepción HTTP -> 422 con mensaje recortado a 240 caracteres")
    void qbi2Error_422Recortado() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);

        String qbiJson = "{\"error\":\"QBI235\",\"mensaje\":\"EL CAMPO MEDICO IDREFEPS NO CUMPLE EL RANGO MINIMO O MAXIMO DE CARACTERES\"}";
        when(qbi2.generarReceta(any())).thenThrow(new Qbi2RecipeException(400, qbiJson, "Bad request"));

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("QBI2/Innovamed la rechazó (QBI235: EL CAMPO MEDICO IDREFEPS NO CUMPLE EL RANGO MINIMO O MAXIMO DE CARACTERES)");
        assertThat(res.getBody().length()).isLessThanOrEqualTo(240);
    }

    @Test
    @DisplayName("QBI2 responde sin recetas -> 422 con detalle de errores")
    void qbi2SinRecetas_422() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);

        Qbi2RecetaDtos.MedicamentoError err = Qbi2RecetaDtos.MedicamentoError.builder()
                .error("QBI124")
                .mensaje("Afiliado inválido para financiador")
                .build();
        Qbi2RecetaDtos.RecetaResponse sinRecetas = Qbi2RecetaDtos.RecetaResponse.builder()
                .recetas(Collections.emptyList())
                .errores(List.of(err))
                .build();
        when(qbi2.generarReceta(any())).thenReturn(sinRecetas);

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("QBI2/Innovamed no la validó (Afiliado inválido para financiador)");
        assertThat(res.getBody().length()).isLessThanOrEqualTo(240);
    }

    // =========================================================================================
    // 5. Notificaciones caído -> la receta igual se emite (200)
    // =========================================================================================
    @Test
    @DisplayName("Servicio de notificaciones caído -> la receta se emite exitosamente (200)")
    void notificacionesCaido_emiteRecetaOk() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        when(qbi2.generarReceta(any())).thenReturn(mockQbiResponse("https://s3.qbi2.com/receta.pdf"));
        doThrow(new RuntimeException("Gateway 503 Service Unavailable")).when(interno).enviarMailReceta(any());

        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, jsonDto(2L, "A", "N", "M", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recetas.cantidad()).isEqualTo(1);
    }

    // =========================================================================================
    // 6. /me de paciente y médico, /{id} ajeno -> 403, /financiadores sin sesión -> 200
    // =========================================================================================
    @Test
    @DisplayName("Rutas /me, /{id} ajeno -> 403, y /financiadores pública")
    void rutasConsulta_permisos_y_publica() throws Exception {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        when(qbi2.generarReceta(any())).thenReturn(mockQbiResponse("https://s3.qbi2.com/receta.pdf"));

        String tokenMed = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String tokenPac = token("juan.paciente@gmail.com", "PACIENTE");
        String tokenAjeno = token("otro.paciente@gmail.com", "PACIENTE");

        // Emitir una receta
        ResponseEntity<String> emitirRes = pedir(HttpMethod.POST, "/api/recetas/enviar", tokenMed, jsonDto(2L, "A", "N", "M", null, null));
        assertThat(emitirRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecetaResponseDto emitida = mapper.readValue(emitirRes.getBody(), RecetaResponseDto.class);
        Long recetaId = emitida.getId();

        // GET /me médico
        ResponseEntity<String> meMed = pedir(HttpMethod.GET, "/api/recetas/me", tokenMed, null);
        assertThat(meMed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meMed.getBody()).contains("receta.pdf");

        // GET /me paciente
        ResponseEntity<String> mePac = pedir(HttpMethod.GET, "/api/recetas/me", tokenPac, null);
        assertThat(mePac.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mePac.getBody()).contains("receta.pdf");

        // GET /{id} por paciente dueño -> 200
        ResponseEntity<String> idPac = pedir(HttpMethod.GET, "/api/recetas/" + recetaId, tokenPac, null);
        assertThat(idPac.getStatusCode()).isEqualTo(HttpStatus.OK);

        // GET /{id} por ajeno -> 403
        Persona ajeno = new Persona(3L, 1, "PACIENTE", "otro.paciente@gmail.com", "Pedro", "Otro",
                null, "22333444", null, null, null, "M", LocalDate.of(1992, 1, 1),
                null, "DNI", 22333444, null, null, null, null, null, false,
                null, null, null, null, null, null, null, null);
        personas.guardar(ajeno);

        ResponseEntity<String> idAjeno = pedir(HttpMethod.GET, "/api/recetas/" + recetaId, tokenAjeno, null);
        assertThat(idAjeno.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // GET /financiadores sin sesión -> 200
        when(qbi2.buscarFinanciadores()).thenReturn(
                Qbi2CatalogoDtos.FinanciadorResponse.builder()
                        .financiadores(List.of(Qbi2CatalogoDtos.FinanciadorItem.builder().idfinanciador(10).nombreComercial("OSDE").build()))
                        .build()
        );
        ResponseEntity<String> fin = pedir(HttpMethod.GET, "/api/recetas/financiadores", null, null);
        assertThat(fin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fin.getBody()).contains("OSDE");
    }

    // =========================================================================================
    // 7. Request a QBI2 igual al del monolito (defaults: diagnóstico, Córdoba, Psiquiatría, sello)
    // =========================================================================================
    @Test
    @DisplayName("Request a QBI2 arma los mismos defaults que el monolito campo por campo")
    void requestQbi2_igualAlMonolito() {
        sembrarMedicoYPaciente(true, true);
        turnosAtendidos.guardar(100L, 1L, 2L, LocalDate.now(), "CONFIRMADO", 1);
        when(qbi2.generarReceta(any())).thenReturn(mockQbiResponse("https://s3.qbi2.com/receta.pdf"));

        // Enviar con diagnosis vacío, noSustituible=true
        String token = token("dra.ana@tranqui.com", "PSIQUIATRA");
        String cuerpo = jsonDto(2L, null, "Tomar con abundante agua", "Paracetamol", true, null);

        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/recetas/enviar", token, cuerpo);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2).generarReceta(captor.capture());
        Qbi2RecetaDtos.RecetaRequest req = captor.getValue();

        // Defaults probados contra el monolito
        assertThat(req.getDiagnostico()).isEqualTo("TRASTORNO DE ANSIEDAD GENERALIZADA");
        assertThat(req.getObservaciones()).isEqualTo("Emitida desde TranquiApp");
        assertThat(req.getIndicaciones()).isEqualTo("Tomar con abundante agua");

        // Paciente
        assertThat(req.getPaciente().getNombre()).isEqualTo("Juan");
        assertThat(req.getPaciente().getApellido()).isEqualTo("Gomez");
        assertThat(req.getPaciente().getTipoDoc()).isEqualTo("DNI");
        assertThat(req.getPaciente().getNroDoc()).isEqualTo("40111222");
        assertThat(req.getPaciente().getPais()).isEqualTo("Argentina");

        // Médico
        assertThat(req.getMedico().getNombre()).isEqualTo("Ana");
        assertThat(req.getMedico().getApellido()).isEqualTo("Paz");
        assertThat(req.getMedico().getProfesion()).isEqualTo("Médico");
        assertThat(req.getMedico().getIdREFEPS()).isEqualTo("123456789012");
        assertThat(req.getMedico().getEspecialidad()).isEqualTo("Psiquiatría");
        assertThat(req.getMedico().getMatricula().getTipo()).isEqualTo("MP");
        assertThat(req.getMedico().getMatricula().getNumero()).isEqualTo("12345");
        assertThat(req.getMedico().getMatricula().getProvincia()).isEqualTo("Córdoba");

        // Sello
        assertThat(req.getMedico().getSello().getLinea1()).isEqualTo("Dra. Ana Paz");
        assertThat(req.getMedico().getSello().getLinea2()).isEqualTo("Psiquiatría");
        assertThat(req.getMedico().getSello().getLinea3()).isEqualTo("MP 12345");

        // Medicamento
        assertThat(req.getMedicamentos()).hasSize(1);
        Qbi2RecetaDtos.MedicamentoRequest medReq = req.getMedicamentos().get(0);
        assertThat(medReq.getNombreProducto()).isEqualTo("Paracetamol");
        assertThat(medReq.getNombreDroga()).isEqualTo("Paracetamol");
        assertThat(medReq.getPresentacion()).isEqualTo("50mg");
        assertThat(medReq.getCantidad()).isEqualTo(1);
        assertThat(medReq.getPermiteSustitucion()).isEqualTo("N"); // noSustituible=true -> "N"
    }
}
