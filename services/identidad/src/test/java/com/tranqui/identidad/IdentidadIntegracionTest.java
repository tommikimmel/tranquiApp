package com.tranqui.identidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.config.DataInitializer;
import com.tranqui.identidad.cuenta.AccountService;
import com.tranqui.identidad.datos.*;
import com.tranqui.identidad.interno.CargaInicialIdentidad;
import com.tranqui.identidad.interno.ClienteInterno;
import com.tranqui.identidad.seguridad.GoogleAuthService;
import com.tranqui.identidad.web.dto.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Tests de integración de la Fase 9 (I2a e I2b): esquema, datos, carga inicial,
 * cuentas, sesiones y JWT RS256 del servicio de identidad sobre PostgreSQL y RabbitMQ reales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "identidad.carga.habilitada=false",
        "identidad.google.simulado=true",
        "app.seed-test-accounts=true",
        "jwt.secret=clave-de-test-de-al-menos-32-bytes-ok",
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "tranqui.gateway.url=http://localhost:1"
})
class IdentidadIntegracionTest {

    private static final KeyPair CLAVES_TEST;
    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            CLAVES_TEST = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void registrarPropiedades(DynamicPropertyRegistry registry) {
        String privPem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(CLAVES_TEST.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----";
        String pubPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(CLAVES_TEST.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----";
        registry.add("jwt.private-key", () -> privPem);
        registry.add("jwt.public-key", () -> pubPem);
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;

    @Autowired Usuarios usuarios;
    @Autowired PerfilesRegistro perfilesRegistro;
    @Autowired Tickets tickets;
    @Autowired TicketMensajes ticketMensajes;
    @Autowired NovedadesEnvios novedadesEnvios;
    @Autowired CargaInicialIdentidad cargaInicial;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate restTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EmisorJwtUsuario emisorJwtUsuario;
    @Autowired VerificadorJwtUsuario verificadorJwtUsuario;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired GoogleAuthService googleAuthService;
    @Autowired AccountService accountService;
    @Autowired DataInitializer dataInitializer;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM ticket_mensaje");
        jdbc.update("DELETE FROM ticket");
        jdbc.update("DELETE FROM perfil_registro");
        jdbc.update("DELETE FROM usuario");
        jdbc.update("DELETE FROM novedades_envio");
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM eventos_procesados");
        jdbc.update("DELETE FROM versiones_aplicadas");
        reset(interno);
    }

    @Test
    void migracion_creaLasTablasYSecuencias() {
        Integer tablas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'
                AND table_name IN ('usuario', 'ticket', 'ticket_mensaje', 'novedades_envio',
                                   'outbox', 'eventos_procesados', 'versiones_aplicadas', 'perfil_registro')
                """, Integer.class);
        assertThat(tablas).isEqualTo(8);

        Integer secuencias = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public'
                AND sequence_name IN ('usuario_id_seq', 'ticket_id_seq', 'ticket_mensaje_id_seq')
                """, Integer.class);
        assertThat(secuencias).isEqualTo(3);
    }

    @Test
    void cargaInicial_copiaFielYSecuenciasQuedanArribaDelMaximo() {
        // 1. Simulación del monolito
        Map<String, Object> uMap = usuarioValidoMap(100L, "Dra.Ruiz@Tranqui.com", "PSIQUIATRA");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(uMap)));

        Map<String, Object> tMap = ticketValidoMap(200L, 100L);
        when(interno.tickets(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.TicketMonolito(tMap)));

        Map<String, Object> mMap = mensajeValidoMap(300L, 200L, 100L);
        when(interno.ticketMensajes(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.TicketMensajeMonolito(mMap)));

        Map<String, Object> nMap = novedadesValidoMap("envio-2026-10");
        when(interno.novedadesEnvios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.NovedadesEnvioMonolito(nMap)));

        // 2. Ejecutar carga
        int total = cargaInicial.cargar();
        assertThat(total).isEqualTo(4);

        // 3. Verificación de copia fiel de ids, hashes, versiones y fechas
        Usuario uGuardado = usuarios.buscarPorId(100L).orElseThrow();
        assertThat(uGuardado.id()).isEqualTo(100L);
        assertThat(uGuardado.version()).isEqualTo(1);
        assertThat(uGuardado.email()).isEqualTo("dra.ruiz@tranqui.com");
        assertThat(uGuardado.password()).isEqualTo("$2a$10$xyz123hashedPassword");
        assertThat(uGuardado.rol()).isEqualTo("PSIQUIATRA");
        assertThat(uGuardado.fechaRegistro()).isEqualTo(LocalDateTime.of(2026, 10, 28, 9, 0));
        assertThat(uGuardado.fechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(uGuardado.cuil()).isEqualTo(20351234567L);

        Ticket tGuardado = tickets.buscarPorId(200L).orElseThrow();
        assertThat(tGuardado.id()).isEqualTo(200L);
        assertThat(tGuardado.creadorId()).isEqualTo(100L);
        assertThat(tGuardado.asunto()).isEqualTo("Problema con la cuenta");
        assertThat(tGuardado.estado()).isEqualTo("PENDIENTE");

        TicketMensaje mGuardado = ticketMensajes.buscarPorId(300L).orElseThrow();
        assertThat(mGuardado.id()).isEqualTo(300L);
        assertThat(mGuardado.ticketId()).isEqualTo(200L);
        assertThat(mGuardado.autorId()).isEqualTo(100L);

        NovedadesEnvio nGuardado = novedadesEnvios.buscarPorId("envio-2026-10").orElseThrow();
        assertThat(nGuardado.id()).isEqualTo("envio-2026-10");
        assertThat(nGuardado.estado()).isEqualTo("ENVIADO");

        // 4. Verificación de que las secuencias quedaron arriba del máximo (un nuevo insert no choca)
        Long nuevoUsuarioId = usuarios.insertarNuevo(new Usuario(
                null, 0, "nuevo@tranqui.com", "pass", "PACIENTE", LocalDateTime.now(),
                false, null, null, null, null, false, null, null,
                false, null, true, true, "Nuevo", "Paciente", null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        ));
        assertThat(nuevoUsuarioId).isGreaterThan(100L);

        Long nuevoTicketId = tickets.insertarNuevo(new Ticket(
                null, 0, 100L, "Otro ticket", "PENDIENTE", LocalDateTime.now(), LocalDateTime.now()
        ));
        assertThat(nuevoTicketId).isGreaterThan(200L);

        Long nuevoMensajeId = ticketMensajes.insertarNuevo(new TicketMensaje(
                null, 200L, 100L, "Mensaje nuevo", LocalDateTime.now()
        ));
        assertThat(nuevoMensajeId).isGreaterThan(300L);
    }

    @Test
    void cargaInicial_deshabilitada_noCopiaNada() {
        cargaInicial.alArrancar();
        assertThat(usuarios.cantidad()).isEqualTo(0);
        assertThat(tickets.cantidad()).isEqualTo(0);
    }

    @Test
    void cargaInicial_conDatosEnTablas_noCopia() {
        // Se crea un usuario previamente en la base
        usuarios.insertarNuevo(new Usuario(
                null, 0, "existente@tranqui.com", "pass", "PACIENTE", LocalDateTime.now(),
                true, null, null, null, null, false, null, null,
                false, null, true, true, "Existente", "User", null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        ));
        assertThat(usuarios.cantidad()).isEqualTo(1);

        Map<String, Object> uMap = usuarioValidoMap(50L, "otro@tranqui.com", "PACIENTE");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(uMap)));

        cargaInicial.cargarConReintentos();
        assertThat(usuarios.cantidad()).isEqualTo(1);
        assertThat(usuarios.buscarPorId(50L)).isEmpty();
    }

    @Test
    void cargaInicial_aborta_conMailRepetido() {
        Map<String, Object> u1 = usuarioValidoMap(1L, "repetido@tranqui.com", "PACIENTE");
        Map<String, Object> u2 = usuarioValidoMap(2L, "REPETIDO@TRANQUI.COM", "PSIQUIATRA");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(
                new ClienteInterno.UsuarioMonolito(u1),
                new ClienteInterno.UsuarioMonolito(u2)
        ));

        assertThatThrownBy(() -> cargaInicial.cargar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email duplicado");

        assertThat(usuarios.cantidad()).isEqualTo(0);
    }

    @Test
    void cargaInicial_aborta_conRolInvalido() {
        Map<String, Object> u1 = usuarioValidoMap(1L, "admin@tranqui.com", "SUPERADMIN");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(u1)));

        assertThatThrownBy(() -> cargaInicial.cargar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rol inválido");

        assertThat(usuarios.cantidad()).isEqualTo(0);
    }

    @Test
    void cargaInicial_aborta_conClaveDeMas() {
        Map<String, Object> u1 = usuarioValidoMap(1L, "user@tranqui.com", "PACIENTE");
        u1.put("clave_extra_invalida", "valor");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(u1)));

        assertThatThrownBy(() -> cargaInicial.cargar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Contrato roto");

        assertThat(usuarios.cantidad()).isEqualTo(0);
    }

    @Test
    void cargaInicial_aborta_conMensajeDeTicketInexistente() {
        Map<String, Object> u1 = usuarioValidoMap(1L, "user@tranqui.com", "PACIENTE");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(u1)));

        Map<String, Object> t1 = ticketValidoMap(10L, 1L);
        when(interno.tickets(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.TicketMonolito(t1)));

        // Mensaje apunta a ticket 999 inexistente
        Map<String, Object> m1 = mensajeValidoMap(100L, 999L, 1L);
        when(interno.ticketMensajes(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.TicketMensajeMonolito(m1)));

        assertThatThrownBy(() -> cargaInicial.cargar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ticket_id inexistente");

        assertThat(usuarios.cantidad()).isEqualTo(0);
        assertThat(tickets.cantidad()).isEqualTo(0);
        assertThat(ticketMensajes.cantidad()).isEqualTo(0);
    }

    @Test
    void controlDeVersionOptimista_rechazaEscrituraConVersionVieja() {
        Map<String, Object> uMap = usuarioValidoMap(1L, "optimista@tranqui.com", "PACIENTE");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(uMap)));
        cargaInicial.cargar();

        Usuario original = usuarios.buscarPorId(1L).orElseThrow();
        assertThat(original.version()).isEqualTo(1);

        // Primera actualización exitosa: version pasa de 1 a 2
        Usuario paraActualizar = new Usuario(
                original.id(), 1, original.email(), original.password(), original.rol(),
                original.fechaRegistro(), original.emailVerificado(), original.codigoVerificacion(),
                original.codigoVerificacionExpiresAt(), original.resetPasswordCode(),
                original.resetPasswordExpiresAt(), original.mustChangePassword(), original.terminosAceptadosEn(),
                original.perfilCompleto(), original.cuentaEliminada(), original.anonimizadoEn(),
                original.notificacionesEmailHabilitadas(), original.recibirNovedades(),
                "Juan Modificado", original.apellido(), original.telefono(), original.sexo(),
                original.fechaNacimiento(), original.tipoDocumento(), original.numeroDocumento(),
                original.dni(), original.cuil(), original.direccion(), original.fotoUrl(),
                original.domicilioCalle(), original.domicilioNumero(), original.domicilioPiso(),
                original.domicilioDpto(), original.domicilioCodigoPostal(), original.domicilioLocalidad(),
                original.domicilioProvincia(), original.domicilioPais(), original.obraSocial(),
                original.numAfiliado(), original.datosOfuscado(), original.credencialCodEntidad(),
                original.credencialPan(), original.credencialPlan(), original.credencialToken(),
                original.verificadoAdmin(), original.profession(), original.licenseType(),
                original.licenseNumber(), original.licenseJurisdiction(), original.licenseDocumentUrl(),
                original.licenseVerifiedAt(), original.licenseVerifiedBy(), original.taxIdType(),
                original.taxId(), original.legalName(), original.ivaConditionId(), original.fiscalAddress()
        );
        usuarios.actualizar(paraActualizar);

        Usuario actualizado = usuarios.buscarPorId(1L).orElseThrow();
        assertThat(actualizado.version()).isEqualTo(2);
        assertThat(actualizado.nombre()).isEqualTo("Juan Modificado");

        // Intentar actualizar de nuevo con version 1 (vieja) debe fallar
        assertThatThrownBy(() -> usuarios.actualizar(paraActualizar))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void busquedaUsuarios_porEmail_insensibleAMayusculas() {
        Map<String, Object> uMap = usuarioValidoMap(1L, "busqueda.Email@Tranqui.com", "PACIENTE");
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.UsuarioMonolito(uMap)));
        cargaInicial.cargar();

        assertThat(usuarios.buscarPorEmail("busqueda.email@tranqui.com")).isPresent();
        assertThat(usuarios.buscarPorEmail("BUSQUEDA.EMAIL@TRANQUI.COM")).isPresent();
        assertThat(usuarios.buscarPorEmail("  busqueda.email@tranqui.com  ")).isPresent();
        assertThat(usuarios.buscarPorEmail("inexistente@tranqui.com")).isEmpty();
    }

    @Test
    void busquedaUsuarios_porRol_y_suscriptores() {
        Map<String, Object> u1 = usuarioValidoMap(1L, "psi@tranqui.com", "PSIQUIATRA");
        Map<String, Object> u2 = usuarioValidoMap(2L, "pac@tranqui.com", "PACIENTE");
        // Suscriptor = rol PSIQUIATRA o con profesión cargada: el paciente no tiene profesión.
        u2.put("profession", null);
        when(interno.usuarios(eq(0), anyInt())).thenReturn(List.of(
                new ClienteInterno.UsuarioMonolito(u1),
                new ClienteInterno.UsuarioMonolito(u2)
        ));
        cargaInicial.cargar();

        List<Usuario> psiquiatras = usuarios.buscarPorRol("PSIQUIATRA");
        assertThat(psiquiatras).hasSize(1);
        assertThat(psiquiatras.get(0).id()).isEqualTo(1L);

        List<Usuario> suscriptores = usuarios.buscarSuscriptores(0, 10);
        assertThat(suscriptores).hasSize(1);
        assertThat(suscriptores.get(0).id()).isEqualTo(1L);

        List<Usuario> paginados = usuarios.paginados(0, 1);
        assertThat(paginados).hasSize(1);
    }

    private Map<String, Object> usuarioValidoMap(Long id, String email, String rol) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("version", 1);
        map.put("email", email);
        map.put("password", "$2a$10$xyz123hashedPassword");
        map.put("rol", rol);
        map.put("fecha_registro", "2026-10-28T09:00:00");
        map.put("email_verificado", true);
        map.put("codigo_verificacion", null);
        map.put("codigo_verificacion_expires_at", null);
        map.put("reset_password_code", null);
        map.put("reset_password_expires_at", null);
        map.put("must_change_password", false);
        map.put("terminos_aceptados_en", "2026-10-28T09:05:00");
        map.put("perfil_completo", true);
        map.put("cuenta_eliminada", false);
        map.put("anonimizado_en", null);
        map.put("notificaciones_email_habilitadas", true);
        map.put("recibir_novedades", true);
        map.put("nombre", "Juan");
        map.put("apellido", "Perez");
        map.put("telefono", "1122334455");
        map.put("sexo", "M");
        map.put("fecha_nacimiento", "1990-05-15");
        map.put("tipo_documento", "DNI");
        map.put("numero_documento", 35123456);
        map.put("dni", "35123456");
        map.put("cuil", 20351234567L);
        map.put("direccion", "Av Santa Fe 1234");
        map.put("foto_url", "https://img.test/foto.jpg");
        map.put("domicilio_calle", "Santa Fe");
        map.put("domicilio_numero", "1234");
        map.put("domicilio_piso", "4");
        map.put("domicilio_dpto", "B");
        map.put("domicilio_codigo_postal", "1425");
        map.put("domicilio_localidad", "Palermo");
        map.put("domicilio_provincia", "CABA");
        map.put("domicilio_pais", "Argentina");
        map.put("obra_social", "OSDE");
        map.put("num_afiliado", "12345678");
        map.put("datos_ofuscado", "0");
        map.put("credencial_cod_entidad", 1);
        map.put("credencial_pan", "1234-5678");
        map.put("credencial_plan", "OSDE 310");
        map.put("credencial_token", "tok-123");
        map.put("verificado_admin", true);
        map.put("profession", "psiquiatra");
        map.put("license_type", "MN");
        map.put("license_number", "12345");
        map.put("license_jurisdiction", "Nacional");
        map.put("license_document_url", "https://docs.test/mn.pdf");
        map.put("license_verified_at", "2026-10-28T09:10:00");
        map.put("license_verified_by", "admin@tranqui.com");
        map.put("tax_id_type", "CUIT");
        map.put("tax_id", "20351234567");
        map.put("legal_name", "Juan Perez");
        map.put("iva_condition_id", 6);
        map.put("fiscal_address", "Av Santa Fe 1234");
        return map;
    }

    private Map<String, Object> ticketValidoMap(Long id, Long creadorId) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("version", 0);
        map.put("creador_id", creadorId);
        map.put("asunto", "Problema con la cuenta");
        map.put("estado", "PENDIENTE");
        map.put("fecha_creacion", "2026-10-28T09:00:00");
        map.put("fecha_actualizacion", "2026-10-28T09:00:00");
        return map;
    }

    private Map<String, Object> mensajeValidoMap(Long id, Long ticketId, Long autorId) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("ticket_id", ticketId);
        map.put("autor_id", autorId);
        map.put("contenido", "Hola, necesito ayuda con mi acceso");
        map.put("fecha_envio", "2026-10-28T09:01:00");
        return map;
    }

    private Map<String, Object> novedadesValidoMap(String id) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("titulo", "Novedades Octubre");
        map.put("estado", "ENVIADO");
        map.put("detalle", "150 pacientes, 20 profesionales");
        map.put("creado_en", "2026-10-28T08:00:00");
        map.put("finalizado_en", "2026-10-28T08:15:00");
        map.put("enviado_pacientes", true);
        map.put("enviado_profesionales", true);
        return map;
    }

    private List<JsonNode> buscarEventosOutbox(String topico) {
        return jdbc.query("SELECT payload FROM outbox WHERE topic = ? ORDER BY id ASC",
                (rs, rowNum) -> {
                    try {
                        return mapper.readTree(rs.getString("payload"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                topico
        );
    }

    @Test
    void registro_paciente_creaUsuarioYPublicaEventosSinPerfilRegistro() {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setEmail("paciente.nuevo@tranqui.com");
        dto.setPassword("Tranqui123");
        dto.setRol("PACIENTE");
        dto.setNombre("Mariana");
        dto.setApellido("Lopez");
        dto.setFechaNacimiento(LocalDate.of(1995, 5, 10));
        dto.setSexo("F");
        dto.setTipoDocumento("DNI");
        dto.setNumeroDocumento(38123456);
        dto.setTelefono("1165432109");
        dto.setAceptaTerminos(true);

        ResponseEntity<Map> res = restTemplate.postForEntity("/api/auth/register", dto, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("requiresVerification")).isEqualTo(true);

        Usuario u = usuarios.buscarPorEmail("paciente.nuevo@tranqui.com").orElseThrow();
        assertThat(u.rol()).isEqualTo("PACIENTE");
        assertThat(u.emailVerificado()).isFalse();
        assertThat(u.telefono()).isEqualTo("+54 1165432109");
        assertThat(u.codigoVerificacion()).isNotNull();

        assertThat(perfilesRegistro.buscarPorUsuarioId(u.id())).isEmpty();

        List<JsonNode> regEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1);
        assertThat(regEvents).hasSize(1);
        assertThat(regEvents.get(0).get("aggregateVersion").asInt()).isEqualTo(1);
        assertThat(regEvents.get(0).get("data").get("email").asText()).isEqualTo("paciente.nuevo@tranqui.com");

        List<JsonNode> codEvents = buscarEventosOutbox(Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1);
        assertThat(codEvents).hasSize(1);
        assertThat(codEvents.get(0).get("data").get("tipo").asText()).isEqualTo("REGISTRO");
    }

    @Test
    void registro_profesional_creaUsuarioYPerfilRegistroYPublicaEventos() {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setEmail("dra.suarez@tranqui.com");
        dto.setPassword("Tranqui123");
        dto.setRol("PSIQUIATRA");
        dto.setNombre("Laura");
        dto.setApellido("Suarez");
        dto.setFechaNacimiento(LocalDate.of(1980, 2, 14));
        dto.setSexo("F");
        dto.setTipoDocumento("DNI");
        dto.setNumeroDocumento(28123456);
        dto.setTelefono("+54 1198765432");
        dto.setAceptaTerminos(true);
        dto.setMatricula("MN 54321");
        dto.setMatriculaTipo("MN");
        dto.setMatriculaProvincia("CABA");
        dto.setMatriculaNumero(54321);
        dto.setTitulo("Médica Psiquiatra");
        dto.setSpecialty("Psiquiatría Infanto-Juvenil");
        dto.setCuit("27281234564");
        dto.setDomicilioAtencion("Av. Corrientes 2500, CABA");
        dto.setDomicilioLat(-34.6037);
        dto.setDomicilioLng(-58.3816);
        dto.setOfreceOnline(true);
        dto.setOfrecePresencial(true);

        ResponseEntity<Map> res = restTemplate.postForEntity("/api/auth/register", dto, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Usuario u = usuarios.buscarPorEmail("dra.suarez@tranqui.com").orElseThrow();
        assertThat(u.rol()).isEqualTo("PSIQUIATRA");
        assertThat(u.profession()).isEqualTo("psiquiatra");
        assertThat(u.licenseType()).isEqualTo("MN");
        assertThat(u.licenseNumber()).isEqualTo("MN 54321");
        assertThat(u.licenseJurisdiction()).isEqualTo("CABA");
        assertThat(u.taxId()).isEqualTo("27281234564");
        assertThat(u.fiscalAddress()).isEqualTo("Av. Corrientes 2500, CABA");

        Optional<PerfilProfesionalData> perfilOpt = perfilesRegistro.buscarPorUsuarioId(u.id());
        assertThat(perfilOpt).isPresent();
        PerfilProfesionalData perfil = perfilOpt.get();
        assertThat(perfil.domicilioAtencion()).isEqualTo("Av. Corrientes 2500, CABA");
        assertThat(perfil.matricula()).isEqualTo("MN 54321");
        assertThat(perfil.matriculaNumero()).isEqualTo(54321);
        assertThat(perfil.especialidad()).isEqualTo("Psiquiatría Infanto-Juvenil");
        assertThat(perfil.cuit()).isEqualTo("27281234564");
        assertThat(perfil.ofreceOnline()).isTrue();
        assertThat(perfil.ofrecePresencial()).isTrue();

        List<JsonNode> regEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1);
        assertThat(regEvents).hasSize(1);
    }

    @Test
    void registro_rolAdminOInvalido_esRechazadoCon400() {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setEmail("hacker@tranqui.com");
        dto.setPassword("Tranqui123");
        dto.setRol("ADMIN");
        dto.setNombre("Malicioso");
        dto.setAceptaTerminos(true);

        ResponseEntity<String> resAdmin = restTemplate.postForEntity("/api/auth/register", dto, String.class);
        assertThat(resAdmin.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resAdmin.getBody()).contains("El rol debe ser PACIENTE o PSIQUIATRA.");

        dto.setRol("OTRO");
        ResponseEntity<String> resOtro = restTemplate.postForEntity("/api/auth/register", dto, String.class);
        assertThat(resOtro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resOtro.getBody()).contains("El rol debe ser PACIENTE o PSIQUIATRA.");
    }

    @Test
    void verificacionMail_codigoInvalidoOExpirado_fallaYVerificadoExitoso() {
        Usuario u = Usuario.builder()
                .email("verif@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Usuario")
                .emailVerificado(false)
                .codigoVerificacion("123456")
                .codigoVerificacionExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        usuarios.insertarNuevo(u);

        // Código incorrecto
        ResponseEntity<String> resBadCode = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new VerifyEmailDto("verif@tranqui.com", "999999"),
                String.class
        );
        assertThat(resBadCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resBadCode.getBody()).contains("Código de verificación incorrecto.");

        // Código expirado
        Usuario expirado = usuarios.buscarPorEmail("verif@tranqui.com").orElseThrow().toBuilder()
                .codigoVerificacionExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        usuarios.actualizar(expirado);

        ResponseEntity<String> resExp = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new VerifyEmailDto("verif@tranqui.com", "123456"),
                String.class
        );
        assertThat(resExp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resExp.getBody()).contains("El código ha expirado. Solicitá uno nuevo.");

        // Código válido
        Usuario valido = usuarios.buscarPorEmail("verif@tranqui.com").orElseThrow().toBuilder()
                .codigoVerificacionExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        usuarios.actualizar(valido);

        ResponseEntity<String> resOk = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new VerifyEmailDto("verif@tranqui.com", "123456"),
                String.class
        );
        assertThat(resOk.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resOk.getBody()).contains("Email verificado correctamente. Ya podés iniciar sesión.");

        Usuario verificadoDb = usuarios.buscarPorEmail("verif@tranqui.com").orElseThrow();
        assertThat(verificadoDb.emailVerificado()).isTrue();
        assertThat(verificadoDb.codigoVerificacion()).isNull();

        // Repetir verificación
        ResponseEntity<String> resRepetido = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new VerifyEmailDto("verif@tranqui.com", "123456"),
                String.class
        );
        assertThat(resRepetido.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resRepetido.getBody()).contains("Tu email ya está verificado.");
    }

    @Test
    void reenvioYRecuperacion_emitenEventosConTipoCorrecto() {
        Usuario u = Usuario.builder()
                .email("recup@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Pedro")
                .emailVerificado(false)
                .build();
        usuarios.insertarNuevo(u);

        // Reenvío de código
        ResponseEntity<String> resReenvio = restTemplate.postForEntity(
                "/api/auth/resend-code",
                new VerifyEmailDto("recup@tranqui.com", null),
                String.class
        );
        assertThat(resReenvio.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<JsonNode> reenvioEvents = buscarEventosOutbox(Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1);
        assertThat(reenvioEvents).hasSize(1);
        assertThat(reenvioEvents.get(0).get("data").get("tipo").asText()).isEqualTo("EMAIL_VERIFICATION");

        // Recuperación de contraseña (forgot-password)
        ResponseEntity<String> resForgot = restTemplate.postForEntity(
                "/api/auth/forgot-password",
                new ForgotPasswordDto("recup@tranqui.com"),
                String.class
        );
        assertThat(resForgot.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<JsonNode> todosCodigos = buscarEventosOutbox(Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1);
        assertThat(todosCodigos).hasSize(2);
        JsonNode forgotEvent = todosCodigos.get(1);
        assertThat(forgotEvent.get("data").get("tipo").asText()).isEqualTo("PASSWORD_RESET");
        String resetCode = forgotEvent.get("data").get("codigo").asText();

        // Reset password
        ResetPasswordDto resetDto = new ResetPasswordDto("recup@tranqui.com", resetCode, "NuevaPass123");
        ResponseEntity<String> resReset = restTemplate.postForEntity("/api/auth/reset-password", resetDto, String.class);
        assertThat(resReset.getStatusCode()).isEqualTo(HttpStatus.OK);

        Usuario actualizado = usuarios.buscarPorEmail("recup@tranqui.com").orElseThrow();
        assertThat(passwordEncoder.matches("NuevaPass123", actualizado.password())).isTrue();
        assertThat(actualizado.resetPasswordCode()).isNull();
    }

    @Test
    void login_credencialesIncorrectas_cuentaEliminada_mailSinVerificar_yAdmin() {
        Usuario uNormal = Usuario.builder()
                .email("login.test@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Lucas")
                .emailVerificado(false)
                .build();
        usuarios.insertarNuevo(uNormal);

        // Mail sin verificar -> 403
        ResponseEntity<String> res403 = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("login.test@tranqui.com", "Pass1234"),
                String.class
        );
        assertThat(res403.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res403.getBody()).contains("Debés verificar tu correo electrónico antes de ingresar.");

        // Password incorrecto -> 401
        ResponseEntity<String> res401Pass = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("login.test@tranqui.com", "Erronea123"),
                String.class
        );
        assertThat(res401Pass.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Usuario inexistente -> 401
        ResponseEntity<String> res401Inexistente = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("noexiste@tranqui.com", "Pass1234"),
                String.class
        );
        assertThat(res401Inexistente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Cuenta eliminada -> 401
        Usuario uEliminado = Usuario.builder()
                .email("borrado@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Borrado")
                .cuentaEliminada(true)
                .emailVerificado(true)
                .build();
        usuarios.insertarNuevo(uEliminado);

        ResponseEntity<String> res401Borrado = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("borrado@tranqui.com", "Pass1234"),
                String.class
        );
        assertThat(res401Borrado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res401Borrado.getBody()).contains("Esta cuenta fue eliminada.");

        // Admin sin verificar email -> entra con 200 OK
        Usuario adminNoVerif = Usuario.builder()
                .email("admin.nov@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("ADMIN")
                .nombre("Admin")
                .emailVerificado(false)
                .build();
        usuarios.insertarNuevo(adminNoVerif);

        ResponseEntity<UserResponseDto> resAdmin = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("admin.nov@tranqui.com", "Pass1234"),
                UserResponseDto.class
        );
        assertThat(resAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resAdmin.getBody().getEmail()).isEqualTo("admin.nov@tranqui.com");
    }

    @Test
    void cookieSesion_atributosCorrectosYLimpiezaEnLogout() {
        Usuario u = Usuario.builder()
                .email("cookie.user@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Cookie")
                .emailVerificado(true)
                .build();
        usuarios.insertarNuevo(u);

        ResponseEntity<UserResponseDto> resLogin = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("cookie.user@tranqui.com", "Pass1234"),
                UserResponseDto.class
        );
        assertThat(resLogin.getStatusCode()).isEqualTo(HttpStatus.OK);

        String setCookie = resLogin.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("SESSION-TOKEN=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=604800");

        // Logout
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", setCookie);
        HttpEntity<?> logoutReq = new HttpEntity<>(headers);

        ResponseEntity<String> resLogout = restTemplate.postForEntity("/api/auth/logout", logoutReq, String.class);
        assertThat(resLogout.getStatusCode()).isEqualTo(HttpStatus.OK);

        String logoutCookie = resLogout.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(logoutCookie).isNotNull();
        assertThat(logoutCookie).contains("Max-Age=0");
    }

    @Test
    void jwt_rs256_conUidValidadoConClavePublica() {
        Usuario u = Usuario.builder()
                .email("rs256.user@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PSIQUIATRA")
                .nombre("Doctor RS256")
                .emailVerificado(true)
                .build();
        Long uid = usuarios.insertarNuevo(u);

        ResponseEntity<UserResponseDto> res = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequestDto("rs256.user@tranqui.com", "Pass1234"),
                UserResponseDto.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        String setCookie = res.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String token = setCookie.split(";")[0].substring("SESSION-TOKEN=".length());

        // Validar con VerificadorJwtUsuario
        Optional<UsuarioToken> tokenValidado = verificadorJwtUsuario.validar(token);
        assertThat(tokenValidado).isPresent();
        assertThat(tokenValidado.get().email()).isEqualTo("rs256.user@tranqui.com");
        assertThat(tokenValidado.get().rol()).isEqualTo("PSIQUIATRA");
        assertThat(tokenValidado.get().uid()).isEqualTo(uid);

        // Validar claims directamente con la clave pública RSA del test
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(CLAVES_TEST.getPublic())
                .build()
                .parseClaimsJws(token)
                .getBody();
        assertThat(claims.getIssuer()).isEqualTo("tranqui-identidad");
        assertThat(claims.getSubject()).isEqualTo("rs256.user@tranqui.com");
        assertThat(claims.get("uid", Long.class)).isEqualTo(uid);
    }

    @Test
    void jwt_hs256_transicionAceptadoYRechazadoConFlag() {
        Usuario u = Usuario.builder()
                .email("hs256.transicion@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Usuario Transición")
                .emailVerificado(true)
                .build();
        usuarios.insertarNuevo(u);

        SecretKey hmacKey = Keys.hmacShaKeyFor("clave-de-test-de-al-menos-32-bytes-ok".getBytes(StandardCharsets.UTF_8));
        String hs256Token = Jwts.builder()
                .setSubject("hs256.transicion@tranqui.com")
                .claim("rol", "PACIENTE")
                .claim("nombre", "Usuario Transición")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(Duration.ofDays(7))))
                .signWith(hmacKey, SignatureAlgorithm.HS256)
                .compact();

        // 1. Aceptado por el filtro en la app actual (jwt.hs256-aceptado=true)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hs256Token);
        HttpEntity<?> req = new HttpEntity<>(headers);

        ResponseEntity<UserResponseDto> resMe = restTemplate.exchange("/api/auth/me", HttpMethod.GET, req, UserResponseDto.class);
        assertThat(resMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resMe.getBody().getEmail()).isEqualTo("hs256.transicion@tranqui.com");

        // 2. Rechazado cuando jwt.hs256-aceptado=false
        VerificadorJwtUsuario verificadorEstricto = new VerificadorJwtUsuario(
                (RSAPublicKey) CLAVES_TEST.getPublic(),
                "clave-de-test-de-al-menos-32-bytes-ok",
                false
        );
        assertThat(verificadorEstricto.validar(hs256Token)).isEmpty();
    }

    @Test
    void google_simuladoAceptadoConPropiedadYRechazadoSinElla() {
        // 1. Simulado habilitado -> login con mock-token exitoso
        GoogleLoginDto dto = new GoogleLoginDto("mock-token");
        ResponseEntity<UserResponseDto> res = restTemplate.postForEntity("/api/auth/google", dto, UserResponseDto.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getEmail()).isEqualTo("paula@tranqui.com");
        assertThat(res.getBody().getRol()).isEqualTo("PACIENTE");

        List<JsonNode> regEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1);
        assertThat(regEvents).anyMatch(ev -> "paula@tranqui.com".equals(ev.get("data").get("email").asText()));

        // 2. Simulado deshabilitado -> verifyGoogleToken rechaza tokens mock
        GoogleAuthService googleReal = new GoogleAuthService(usuarios, outboxPublisher, "dummy-client-id", false);
        GoogleIdToken.Payload payload = googleReal.verifyGoogleToken("mock-token");
        assertThat(payload).isNull();
    }

    @Test
    void me_y_miCuenta_actualizacionesYPreferencias() {
        Usuario u = Usuario.builder()
                .email("micuenta.test@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Natalia")
                .apellido("Gomez")
                .telefono("+54 1144332211")
                .emailVerificado(true)
                .perfilCompleto(true)
                .build();
        Long uid = usuarios.insertarNuevo(u);

        String token = emisorJwtUsuario.emitir("micuenta.test@tranqui.com", "PACIENTE", "Natalia", uid);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // GET /api/auth/me
        ResponseEntity<UserResponseDto> resMe = restTemplate.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), UserResponseDto.class);
        assertThat(resMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resMe.getBody().getNombre()).isEqualTo("Natalia");

        // GET /api/auth/mi-cuenta
        ResponseEntity<MiCuentaDto> resMiCuenta = restTemplate.exchange("/api/auth/mi-cuenta", HttpMethod.GET, new HttpEntity<>(headers), MiCuentaDto.class);
        assertThat(resMiCuenta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resMiCuenta.getBody().getEmail()).isEqualTo("micuenta.test@tranqui.com");

        // PUT /api/auth/mi-cuenta
        MiCuentaDto updateDto = new MiCuentaDto();
        updateDto.setNombre("Natalia Sofia");
        updateDto.setApellido("Gomez");
        updateDto.setDomicilioCalle("Av. Libertador");
        updateDto.setDomicilioNumero("1000");
        updateDto.setTelefono("1155667788");

        HttpEntity<MiCuentaDto> updateReq = new HttpEntity<>(updateDto, headers);
        ResponseEntity<MiCuentaDto> resUpdate = restTemplate.exchange("/api/auth/mi-cuenta", HttpMethod.PUT, updateReq, MiCuentaDto.class);
        assertThat(resUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resUpdate.getBody().getNombre()).isEqualTo("Natalia Sofia");
        assertThat(resUpdate.getBody().getTelefono()).isEqualTo("+54 1155667788");

        // PUT /api/auth/mi-cuenta/notificaciones
        PreferenciasNotificacionDto prefDto = new PreferenciasNotificacionDto(false);
        HttpEntity<PreferenciasNotificacionDto> prefReq = new HttpEntity<>(prefDto, headers);
        ResponseEntity<MiCuentaDto> resPref = restTemplate.exchange("/api/auth/mi-cuenta/notificaciones", HttpMethod.PUT, prefReq, MiCuentaDto.class);
        assertThat(resPref.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resPref.getBody().isNotificacionesEmailHabilitadas()).isFalse();

        List<JsonNode> actEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1);
        assertThat(actEvents.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void cambioPassword_y_setNewPassword() {
        Usuario u = Usuario.builder()
                .email("pass.change@tranqui.com")
                .password(passwordEncoder.encode("OldPass123"))
                .rol("PACIENTE")
                .nombre("Martin")
                .emailVerificado(true)
                .build();
        Long uid = usuarios.insertarNuevo(u);

        String token = emisorJwtUsuario.emitir("pass.change@tranqui.com", "PACIENTE", "Martin", uid);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // Password actual erróneo
        CambiarPasswordDto badDto = new CambiarPasswordDto("Erroneo123", "NewPass123");
        ResponseEntity<String> resBad = restTemplate.exchange(
                "/api/auth/mi-cuenta/password",
                HttpMethod.POST,
                new HttpEntity<>(badDto, headers),
                String.class
        );
        assertThat(resBad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resBad.getBody()).contains("La contraseña actual es incorrecta.");

        // Password actual correcto
        CambiarPasswordDto goodDto = new CambiarPasswordDto("OldPass123", "NewPass123");
        ResponseEntity<String> resGood = restTemplate.exchange(
                "/api/auth/mi-cuenta/password",
                HttpMethod.POST,
                new HttpEntity<>(goodDto, headers),
                String.class
        );
        assertThat(resGood.getStatusCode()).isEqualTo(HttpStatus.OK);

        Usuario modificado = usuarios.buscarPorEmail("pass.change@tranqui.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass123", modificado.password())).isTrue();

        // Set new password (forzoso, post reseteo admin)
        modificado = modificado.toBuilder().mustChangePassword(true).build();
        usuarios.actualizar(modificado);

        SetNewPasswordDto setNewDto = new SetNewPasswordDto("ForzadaPass123");
        ResponseEntity<UserResponseDto> resForzada = restTemplate.exchange(
                "/api/auth/set-new-password",
                HttpMethod.POST,
                new HttpEntity<>(setNewDto, headers),
                UserResponseDto.class
        );
        assertThat(resForzada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resForzada.getBody().getMustChangePassword()).isFalse();

        Usuario finalDb = usuarios.buscarPorEmail("pass.change@tranqui.com").orElseThrow();
        assertThat(passwordEncoder.matches("ForzadaPass123", finalDb.password())).isTrue();
        assertThat(finalDb.mustChangePassword()).isFalse();
    }

    @Test
    void eliminarCuenta_anonimizaYInvalidaSesion() {
        Usuario u = Usuario.builder()
                .email("eliminar.test@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Federico")
                .apellido("Paz")
                .emailVerificado(true)
                .build();
        Long uid = usuarios.insertarNuevo(u);

        String token = emisorJwtUsuario.emitir("eliminar.test@tranqui.com", "PACIENTE", "Federico", uid);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // Password erróneo al eliminar
        ResponseEntity<String> resBadPass = restTemplate.exchange(
                "/api/auth/mi-cuenta/eliminar",
                HttpMethod.POST,
                new HttpEntity<>(new EliminarCuentaDto("Erronea123"), headers),
                String.class
        );
        assertThat(resBadPass.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Eliminar correctamente
        ResponseEntity<String> resOk = restTemplate.exchange(
                "/api/auth/mi-cuenta/eliminar",
                HttpMethod.POST,
                new HttpEntity<>(new EliminarCuentaDto("Pass1234"), headers),
                String.class
        );
        assertThat(resOk.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resOk.getBody()).contains("Cuenta eliminada.");

        Usuario borrado = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(borrado.email()).isEqualTo("deleted-" + uid + "@tranquiapp.local");
        assertThat(borrado.nombre()).isEqualTo("Usuario eliminado");
        assertThat(borrado.apellido()).isNull();
        assertThat(borrado.password()).isNull();
        assertThat(borrado.isCuentaEliminada()).isTrue();
        assertThat(borrado.anonimizadoEn()).isNotNull();

        List<JsonNode> elimEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1);
        assertThat(elimEvents).hasSize(1);
        assertThat(elimEvents.get(0).get("data").get("motivo").asText()).isEqualTo("solicitud_usuario");

        // Con el mismo token, ahora GET /me responde 401 Unauthorized
        ResponseEntity<String> resMePostEliminar = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        assertThat(resMePostEliminar.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void semilla_creaCuentasYPublicaEventos() throws Exception {
        dataInitializer.ejecutarSincronico();

        assertThat(usuarios.buscarPorEmail("admin@tranqui.com")).isPresent();
        assertThat(usuarios.buscarPorEmail("medico.verificado@gmail.com")).isPresent();
        assertThat(usuarios.buscarPorEmail("paciente.completo@gmail.com")).isPresent();

        List<JsonNode> seededEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1);
        assertThat(seededEvents.size()).isGreaterThanOrEqualTo(3);

        // Una segunda pasada (reinicio) no duplica: actualiza las cuentas de prueba que ya existen.
        int antes = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1).size();
        dataInitializer.ejecutarSincronico();
        assertThat(buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1)).hasSize(antes);
        assertThat(dataInitializer.estaListo()).isTrue();
        assertThat(usuarios.buscarPorEmail("admin.test@tranqui.com").orElseThrow().rol()).isEqualTo("ADMIN");
    }

    @Test
    void escritura_subeVersionYEventoLLevaVersionMasUno() {
        Usuario u = Usuario.builder()
                .email("version.test@tranqui.com")
                .password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE")
                .nombre("Clara")
                .emailVerificado(true)
                .build();
        Long uid = usuarios.insertarNuevo(u);

        Usuario inicial = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(inicial.version()).isEqualTo(0);

        String token = emisorJwtUsuario.emitir("version.test@tranqui.com", "PACIENTE", "Clara", uid);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // 1ª actualización: /mi-cuenta
        MiCuentaDto miCuentaDto = new MiCuentaDto();
        miCuentaDto.setNombre("Clara Elena");
        restTemplate.exchange("/api/auth/mi-cuenta", HttpMethod.PUT, new HttpEntity<>(miCuentaDto, headers), MiCuentaDto.class);

        Usuario v1 = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(v1.version()).isEqualTo(1);

        List<JsonNode> actEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1);
        assertThat(actEvents).hasSize(1);
        // El evento lleva la versión guardada + 1, como el monolito (que publica después de saveAndFlush): así las
        // copias no descartan el primer evento de identidad después del corte.
        assertThat(actEvents.get(0).get("aggregateVersion").asInt()).isEqualTo(2);

        // 2ª actualización: /mi-cuenta/notificaciones
        restTemplate.exchange("/api/auth/mi-cuenta/notificaciones", HttpMethod.PUT, new HttpEntity<>(new PreferenciasNotificacionDto(false), headers), MiCuentaDto.class);

        Usuario v2 = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(v2.version()).isEqualTo(2);

        List<JsonNode> actEvents2 = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1);
        assertThat(actEvents2).hasSize(2);
        assertThat(actEvents2.get(1).get("aggregateVersion").asInt()).isEqualTo(3);

        // 3ª actualización: /mi-cuenta/eliminar
        restTemplate.exchange("/api/auth/mi-cuenta/eliminar", HttpMethod.POST, new HttpEntity<>(new EliminarCuentaDto("Pass1234"), headers), String.class);

        Usuario v3 = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(v3.version()).isEqualTo(3);

        List<JsonNode> elimEvents = buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1);
        assertThat(elimEvents).hasSize(1);
        assertThat(elimEvents.get(0).get("aggregateVersion").asInt()).isEqualTo(4);
    }

    @Test
    void rutasDeCuenta_sinSesion_responden401() {
        for (String[] ruta : new String[][]{{"GET", "/api/auth/me"}, {"POST", "/api/auth/aceptar-terminos"},
                {"GET", "/api/auth/mi-cuenta"}, {"PUT", "/api/auth/mi-cuenta"}, {"PUT", "/api/auth/mi-cuenta/notificaciones"},
                {"GET", "/api/auth/mi-cuenta/novedades"}, {"PUT", "/api/auth/mi-cuenta/novedades"},
                {"POST", "/api/auth/mi-cuenta/password"}, {"POST", "/api/auth/set-new-password"},
                {"POST", "/api/auth/mi-cuenta/eliminar"}, {"POST", "/api/auth/complete-profile"}}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            ResponseEntity<String> res = restTemplate.exchange(ruta[1], HttpMethod.valueOf(ruta[0]), new HttpEntity<>("{}", headers), String.class);
            assertThat(res.getStatusCode()).as(ruta[0] + " " + ruta[1]).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void completarPerfil_terminosYNuevaContrasena() throws Exception {
        Long uid = usuarios.insertarNuevo(Usuario.builder().email("perfil.test@tranqui.com").password(passwordEncoder.encode("Pass1234"))
                .rol("PACIENTE").nombre("Sin").apellido("Completar").emailVerificado(true).perfilCompleto(false).mustChangePassword(true).build());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(emisorJwtUsuario.emitir("perfil.test@tranqui.com", "PACIENTE", null, uid));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        java.util.Map<String, Object> incompleto = new java.util.HashMap<>(java.util.Map.of("nombre", "Lara", "apellido", " "));
        assertThat(restTemplate.exchange("/api/auth/complete-profile", HttpMethod.POST, new HttpEntity<>(incompleto, headers), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        java.util.Map<String, Object> perfil = java.util.Map.of("nombre", " Lara ", "apellido", "Diaz", "sexo", "F",
                "fechaNacimiento", "1995-03-04", "tipoDocumento", "DNI", "numeroDocumento", 40111222, "telefono", "1155556666",
                "obraSocial", "OSDE", "numAfiliado", "123");
        ResponseEntity<String> completo = restTemplate.exchange("/api/auth/complete-profile", HttpMethod.POST, new HttpEntity<>(perfil, headers), String.class);
        assertThat(completo.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario guardado = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(guardado.nombre()).isEqualTo("Lara");
        assertThat(guardado.telefono()).isEqualTo("+54 1155556666");
        assertThat(guardado.perfilCompleto()).isTrue();
        assertThat(buscarEventosOutbox(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1).stream()
                .anyMatch(e -> e.get("aggregateId").asText().equals(String.valueOf(uid)))).isTrue();

        assertThat(restTemplate.exchange("/api/auth/aceptar-terminos", HttpMethod.POST, new HttpEntity<>(headers), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarios.buscarPorId(uid).orElseThrow().terminosAceptadosEn()).isNotNull();

        assertThat(restTemplate.exchange("/api/auth/set-new-password", HttpMethod.POST,
                new HttpEntity<>(java.util.Map.of("newPassword", "debil"), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.exchange("/api/auth/set-new-password", HttpMethod.POST,
                new HttpEntity<>(java.util.Map.of("newPassword", "NuevaPass1"), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario conClave = usuarios.buscarPorId(uid).orElseThrow();
        assertThat(conClave.mustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches("NuevaPass1", conClave.password())).isTrue();
    }
}
