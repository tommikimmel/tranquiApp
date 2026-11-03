package com.tranqui.app.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.NovedadesEnvio;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Ticket;
import com.tranqui.app.model.TicketEstado;
import com.tranqui.app.model.TicketMensaje;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.NovedadesEnvioRepository;
import com.tranqui.app.repository.TicketMensajeRepository;
import com.tranqui.app.repository.TicketRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 9, I1: tests de las cuatro rutas internas para la carga inicial de identidad.
 * Valida protección con JWT de servicio (401 si falta), nombres exactos de columnas,
 * formato de fechas y timestamps ISO, nulos explícitos, orden y paginado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoIdentidadControllerTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketMensajeRepository ticketMensajeRepository;
    @Autowired private NovedadesEnvioRepository novedadesEnvioRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    private ResponseEntity<String> get(String ruta, boolean conToken) {
        HttpHeaders headers = new HttpHeaders();
        if (conToken) {
            headers.setBearerAuth(new ServiceJwt("clave-servicio-de-test-de-32-bytes-ok").emitirToken("identidad"));
        }
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(get("/internal/monolito/identidad/usuarios", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/identidad/tickets", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/identidad/ticket-mensajes", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/identidad/novedades-envios", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void usuarios_clavesExactas57_fechasIso_nullExplicito_ordenYPaginado() throws Exception {
        Usuario u1 = usuarioRepository.save(Usuario.builder()
                .nombre("Usuario Uno")
                .apellido("Test")
                .email("u1." + System.nanoTime() + "@mail.com")
                .password("hash-123")
                .rol(Rol.PACIENTE)
                .fechaNacimiento(LocalDate.of(1990, 5, 15))
                .cuil(20123456789L)
                .numeroDocumento(12345678)
                .ivaConditionId(1)
                .build());

        Usuario u2 = usuarioRepository.save(Usuario.builder()
                .nombre("Usuario Dos")
                .email("u2." + System.nanoTime() + "@mail.com")
                .password("hash-456")
                .rol(Rol.PSIQUIATRA)
                .build());

        ResponseEntity<String> res = get("/internal/monolito/identidad/usuarios?pagina=0&tamanio=1000", true);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> filas = mapper.readValue(res.getBody(), new TypeReference<>() {});
        assertThat(filas).isNotEmpty();

        // Encontrar fila correspondiente a u1
        Map<String, Object> fila1 = filas.stream()
                .filter(f -> ((Number) f.get("id")).longValue() == u1.getId())
                .findFirst()
                .orElseThrow();

        Set<String> columnasEsperadas = Set.of(
                "id", "version", "email", "password", "rol", "fecha_registro", "email_verificado", "codigo_verificacion",
                "codigo_verificacion_expires_at", "reset_password_code", "reset_password_expires_at", "must_change_password",
                "terminos_aceptados_en", "perfil_completo", "cuenta_eliminada", "anonimizado_en", "notificaciones_email_habilitadas",
                "recibir_novedades", "nombre", "apellido", "telefono", "sexo", "fecha_nacimiento", "tipo_documento", "numero_documento", "dni", "cuil",
                "direccion", "foto_url", "domicilio_calle", "domicilio_numero", "domicilio_piso", "domicilio_dpto", "domicilio_codigo_postal",
                "domicilio_localidad", "domicilio_provincia", "domicilio_pais", "obra_social", "num_afiliado", "datos_ofuscado",
                "credencial_cod_entidad", "credencial_pan", "credencial_plan", "credencial_token", "verificado_admin", "profession",
                "license_type", "license_number", "license_jurisdiction", "license_document_url", "license_verified_at",
                "license_verified_by", "tax_id_type", "tax_id", "legal_name", "iva_condition_id", "fiscal_address"
        );

        assertThat(columnasEsperadas).hasSize(57);
        assertThat(fila1.keySet()).containsExactlyInAnyOrderElementsOf(columnasEsperadas);

        // Verificar null explícito en campos no seteados
        assertThat(fila1.containsKey("codigo_verificacion")).isTrue();
        assertThat(fila1.get("codigo_verificacion")).isNull();

        // Verificar formato de fecha ISO (yyyy-MM-dd)
        assertThat(fila1.get("fecha_nacimiento")).isEqualTo("1990-05-15");

        // Verificar timestamp ISO (yyyy-MM-dd'T'HH:mm:ss...)
        assertThat(fila1.get("fecha_registro")).isNotNull();
        assertThat((String) fila1.get("fecha_registro")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");

        // Tipos JSON
        assertThat(fila1.get("cuil")).isEqualTo(20123456789L);
        assertThat(fila1.get("numero_documento")).isEqualTo(12345678);
        assertThat(fila1.get("iva_condition_id")).isEqualTo(1);
        assertThat(fila1.get("rol")).isEqualTo("PACIENTE");

        // Orden ascendente por id
        for (int i = 0; i < filas.size() - 1; i++) {
            long idActual = ((Number) filas.get(i).get("id")).longValue();
            long idSiguiente = ((Number) filas.get(i + 1).get("id")).longValue();
            assertThat(idActual).isLessThan(idSiguiente);
        }

        // Paginado
        ResponseEntity<String> pagVacia = get("/internal/monolito/identidad/usuarios?pagina=999999&tamanio=50", true);
        assertThat(pagVacia.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pagVacia.getBody()).isEqualTo("[]");
    }

    @Test
    void tickets_clavesExactas_fechasIso_ordenYPaginado() throws Exception {
        Usuario creador = usuarioRepository.save(Usuario.builder()
                .nombre("Creador Ticket")
                .email("creador." + System.nanoTime() + "@mail.com")
                .password("hash-123")
                .rol(Rol.PACIENTE)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .creador(creador)
                .asunto("Problema de acceso")
                .estado(TicketEstado.PENDIENTE)
                .build());

        ResponseEntity<String> res = get("/internal/monolito/identidad/tickets?pagina=0&tamanio=1000", true);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> filas = mapper.readValue(res.getBody(), new TypeReference<>() {});
        assertThat(filas).isNotEmpty();

        Map<String, Object> fila = filas.stream()
                .filter(f -> ((Number) f.get("id")).longValue() == ticket.getId())
                .findFirst()
                .orElseThrow();

        assertThat(fila.keySet()).containsExactlyInAnyOrder(
                "id", "version", "creador_id", "asunto", "estado", "fecha_creacion", "fecha_actualizacion"
        );
        assertThat(fila.get("creador_id")).isEqualTo(creador.getId().intValue());
        assertThat(fila.get("asunto")).isEqualTo("Problema de acceso");
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat((String) fila.get("fecha_creacion")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");
    }

    @Test
    void ticketMensajes_clavesExactas_ordenYPaginado() throws Exception {
        Usuario creador = usuarioRepository.save(Usuario.builder()
                .nombre("Autor Mensaje")
                .email("autor." + System.nanoTime() + "@mail.com")
                .password("hash-123")
                .rol(Rol.PACIENTE)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .creador(creador)
                .asunto("Consulta")
                .estado(TicketEstado.PENDIENTE)
                .build());

        TicketMensaje msg = ticketMensajeRepository.save(TicketMensaje.builder()
                .ticket(ticket)
                .autor(creador)
                .contenido("Hola, necesito ayuda")
                .build());

        ResponseEntity<String> res = get("/internal/monolito/identidad/ticket-mensajes?pagina=0&tamanio=1000", true);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> filas = mapper.readValue(res.getBody(), new TypeReference<>() {});
        assertThat(filas).isNotEmpty();

        Map<String, Object> fila = filas.stream()
                .filter(f -> ((Number) f.get("id")).longValue() == msg.getId())
                .findFirst()
                .orElseThrow();

        assertThat(fila.keySet()).containsExactlyInAnyOrder(
                "id", "ticket_id", "autor_id", "contenido", "fecha_envio"
        );
        assertThat(fila.get("ticket_id")).isEqualTo(ticket.getId().intValue());
        assertThat(fila.get("autor_id")).isEqualTo(creador.getId().intValue());
        assertThat(fila.get("contenido")).isEqualTo("Hola, necesito ayuda");
        assertThat((String) fila.get("fecha_envio")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");
    }

    @Test
    void novedadesEnvios_clavesExactas_ordenYPaginado() throws Exception {
        String idEnvio = "envio-test-" + System.nanoTime();
        NovedadesEnvio envio = novedadesEnvioRepository.save(NovedadesEnvio.builder()
                .id(idEnvio)
                .titulo("Novedades Octubre 2026")
                .estado(NovedadesEnvio.Estado.ENVIADO)
                .detalle("Segmentos ok")
                .enviadoPacientes(true)
                .enviadoProfesionales(true)
                .build());

        ResponseEntity<String> res = get("/internal/monolito/identidad/novedades-envios?pagina=0&tamanio=1000", true);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> filas = mapper.readValue(res.getBody(), new TypeReference<>() {});
        assertThat(filas).isNotEmpty();

        Map<String, Object> fila = filas.stream()
                .filter(f -> idEnvio.equals(f.get("id")))
                .findFirst()
                .orElseThrow();

        assertThat(fila.keySet()).containsExactlyInAnyOrder(
                "id", "titulo", "estado", "detalle", "creado_en", "finalizado_en", "enviado_pacientes", "enviado_profesionales"
        );
        assertThat(fila.get("id")).isEqualTo(idEnvio);
        assertThat(fila.get("titulo")).isEqualTo("Novedades Octubre 2026");
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("enviado_pacientes")).isEqualTo(true);
        assertThat(fila.get("enviado_profesionales")).isEqualTo(true);
        assertThat((String) fila.get("creado_en")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");
    }
}
