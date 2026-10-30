package com.tranqui.identidad;

import com.tranqui.eventos.contratos.Contratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.identidad.datos.PerfilesRegistro;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.interno.ClienteInterno;
import com.tranqui.identidad.novedades.MailNovedades;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de integración de la Fase 9, I2c: tickets, soporte, administración de usuarios, portón del sitio, novedades y
 * las rutas internas {@code /internal/identidad/...} (mismo JSON que el monolito), sobre PostgreSQL y RabbitMQ reales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "identidad.carga.habilitada=false",
        "app.seed-test-accounts=false",
        "app.admin-initial-password=",
        "jwt.secret=clave-de-test-de-al-menos-32-bytes-ok",
        "tranqui.jwt.service-secret=" + IdentidadI2cIntegracionTest.CLAVE_SERVICIO,
        "tranqui.gateway.url=http://localhost:1",
        "site.access.password=" + IdentidadI2cIntegracionTest.PASSWORD_SITIO,
        "novedades.token=" + IdentidadI2cIntegracionTest.TOKEN_NOVEDADES
})
class IdentidadI2cIntegracionTest {

    static final String CLAVE_SERVICIO = "clave-servicio-test-de-32-bytes-ok";
    static final String PASSWORD_SITIO = "portal-de-prueba";
    static final String TOKEN_NOVEDADES = "token-de-novedades-de-prueba";

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
        registry.add("jwt.private-key", () -> Base64.getEncoder().encodeToString(CLAVES_TEST.getPrivate().getEncoded()));
        registry.add("jwt.public-key", () -> Base64.getEncoder().encodeToString(CLAVES_TEST.getPublic().getEncoded()));
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;
    @MockitoBean MailNovedades mailNovedades;

    @Autowired Usuarios usuarios;
    @Autowired PerfilesRegistro perfilesRegistro;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rest;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EmisorJwtUsuario emisor;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();
    private final String tokenServicio = "Bearer " + new ServiceJwt(CLAVE_SERVICIO).emitirToken("profesionales");

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM ticket_mensaje");
        jdbc.update("DELETE FROM ticket");
        jdbc.update("DELETE FROM perfil_registro");
        jdbc.update("DELETE FROM usuario");
        jdbc.update("DELETE FROM novedades_envio");
        jdbc.update("DELETE FROM outbox");
        reset(interno, mailNovedades);
        when(mailNovedades.htmlNovedades(anyString(), org.mockito.ArgumentMatchers.anyList(), anyString())).thenReturn("<p>html</p>");
        when(mailNovedades.urlMiCuenta()).thenReturn("http://localhost/mi-cuenta");
    }

    // ================================================================================
    // Tickets y soporte
    // ================================================================================

    @Test
    void tickets_crearListarDetalleYMensajes_conSusEventosYEstados() throws Exception {
        Usuario paciente = crear("paciente.ticket@tranqui.com", "PACIENTE");
        Usuario otro = crear("otro.ticket@tranqui.com", "PACIENTE");
        Usuario admin = crear("admin.ticket@tranqui.com", "ADMIN");

        ResponseEntity<String> creado = pedir(HttpMethod.POST, "/api/tickets", paciente,
                Map.of("asunto", "No puedo pagar", "mensaje", "Me da error el checkout"));
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ticket = mapper.readTree(creado.getBody());
        long ticketId = ticket.get("id").asLong();
        assertThat(ticket.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(ticket.get("mensajes")).hasSize(1);
        assertThat(eventos(Topicos.IDENTIDAD_TICKET_CREADO_V1)).hasSize(1);

        JsonNode mios = mapper.readTree(pedir(HttpMethod.GET, "/api/tickets/mios", paciente, null).getBody());
        assertThat(mios).hasSize(1);

        // El ticket de otro: 403.
        assertThat(pedir(HttpMethod.GET, "/api/tickets/" + ticketId, otro, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Responde el admin: ACTIVO y evento de respuesta.
        ResponseEntity<String> respuesta = pedir(HttpMethod.POST, "/api/tickets/" + ticketId + "/mensajes", admin,
                Map.of("contenido", "Ya lo revisamos"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(respuesta.getBody()).get("estado").asText()).isEqualTo("ACTIVO");
        List<JsonNode> respondidos = eventos(Topicos.IDENTIDAD_TICKET_RESPONDIDO_V1);
        assertThat(respondidos).hasSize(1);
        assertThat(respondidos.get(0).get("aggregateVersion").asInt())
                .isEqualTo(jdbc.queryForObject("SELECT version FROM ticket WHERE id = ?", Integer.class, ticketId) + 1);

        // El admin lo resuelve y el dueño vuelve a escribir: PENDIENTE y evento de mensaje recibido.
        assertThat(pedir(HttpMethod.PUT, "/api/admin/tickets/" + ticketId + "/estado", admin, Map.of("estado", "RESUELTO"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> reabierto = pedir(HttpMethod.POST, "/api/tickets/" + ticketId + "/mensajes", paciente,
                Map.of("contenido", "Sigue fallando"));
        assertThat(mapper.readTree(reabierto.getBody()).get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(eventos(Topicos.IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1)).hasSize(1);

        JsonNode todos = mapper.readTree(pedir(HttpMethod.GET, "/api/admin/tickets", admin, null).getBody());
        assertThat(todos).hasSize(1);
        assertThat(pedir(HttpMethod.GET, "/api/admin/tickets", paciente, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void soporte_solicitudDeDatos_publicaElEventoYNoMandaMail() {
        Usuario paciente = crear("datos@tranqui.com", "PACIENTE");
        assertThat(pedir(HttpMethod.POST, "/api/soporte/solicitud-datos", paciente, null).getStatusCode().is2xxSuccessful()).isTrue();
        List<JsonNode> eventos = eventos(Topicos.IDENTIDAD_SOPORTE_SOLICITUD_DATOS_V1);
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).at("/data/email").asText()).isEqualTo("datos@tranqui.com");
        assertThat(eventos.get(0).at("/data/rol").asText()).isEqualTo("PACIENTE");
    }

    // ================================================================================
    // Administración de usuarios
    // ================================================================================

    @Test
    void admin_listaSinDatosSensibles_yNoAdminRecibe403() throws Exception {
        Usuario admin = crear("admin.lista@tranqui.com", "ADMIN");
        Usuario psi = crear("psi.lista@tranqui.com", "PSIQUIATRA");
        perfilesRegistro.guardar(psi.id(), perfil("MN-1234"));

        ResponseEntity<String> r = pedir(HttpMethod.GET, "/api/admin/users", admin, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String sensible : List.of("password", "codigoVerificacion", "resetPasswordCode", "credencialToken",
                "mpAccessTokenEncrypted", "googleAccessTokenEncrypted", "qbi2ClientId")) {
            assertThat(r.getBody()).doesNotContain("\"" + sensible + "\"");
        }
        JsonNode lista = mapper.readTree(r.getBody());
        JsonNode delPsi = null;
        for (JsonNode n : lista) if (n.get("id").asLong() == psi.id()) delPsi = n;
        assertThat(delPsi).isNotNull();
        // La matrícula sale del perfil que cargó al registrarse.
        assertThat(delPsi.get("matricula").asText()).isEqualTo("MN-1234");

        assertThat(pedir(HttpMethod.GET, "/api/admin/users", psi, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_verificarYRechazar_publicanLosTresEventosConLaVersionDelMonolito() {
        Usuario admin = crear("admin.verifica@tranqui.com", "ADMIN");
        Usuario psi = crear("psi.verifica@tranqui.com", "PSIQUIATRA");

        assertThat(pedir(HttpMethod.POST, "/api/admin/users/" + psi.id() + "/verify", admin, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Usuario verificado = usuarios.buscarPorId(psi.id()).orElseThrow();
        assertThat(verificado.verificadoAdmin()).isTrue();
        assertThat(verificado.licenseVerifiedBy()).isEqualTo("admin");
        assertThat(verificado.licenseVerifiedAt()).isNotNull();
        assertThat(eventos(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1)).hasSize(1);
        assertThat(eventos(Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1)).hasSize(1);
        List<JsonNode> resueltas = eventos(Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1);
        assertThat(resueltas.get(0).at("/data/resultado").asText()).isEqualTo("VERIFICADO");
        // Versión guardada + 1, como el monolito después de saveAndFlush.
        assertThat(resueltas.get(0).get("aggregateVersion").asInt()).isEqualTo(verificado.version() + 1);

        assertThat(pedir(HttpMethod.POST, "/api/admin/users/" + psi.id() + "/reject", admin, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(usuarios.buscarPorId(psi.id()).orElseThrow().verificadoAdmin()).isFalse();
        // El rechazo también publica usuario.actualizado (el monolito no lo hacía y las copias quedaban viejas).
        assertThat(eventos(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1)).hasSize(2);
        assertThat(eventos(Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1).get(1).at("/data/resultado").asText())
                .isEqualTo("RECHAZADO");

        assertThat(pedir(HttpMethod.POST, "/api/admin/users/" + psi.id() + "/verify", psi, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_cambiarRolYResetPassword() throws Exception {
        Usuario admin = crear("admin.rol@tranqui.com", "ADMIN");
        Usuario u = crear("usuario.rol@tranqui.com", "PACIENTE");

        assertThat(pedir(HttpMethod.PUT, "/api/admin/users/" + u.id() + "/rol", admin, Map.of("rol", "PSIQUIATRA"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario cambiado = usuarios.buscarPorId(u.id()).orElseThrow();
        assertThat(cambiado.rol()).isEqualTo("PSIQUIATRA");
        assertThat(cambiado.verificadoAdmin()).isTrue();
        assertThat(pedir(HttpMethod.PUT, "/api/admin/users/" + u.id() + "/rol", admin, Map.of("rol", "SUPERADMIN"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> reset = pedir(HttpMethod.POST, "/api/admin/users/" + u.id() + "/reset-password", admin, null);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        String temporal = mapper.readTree(reset.getBody()).get("password").asText();
        assertThat(temporal).hasSize(10);
        Usuario reseteado = usuarios.buscarPorId(u.id()).orElseThrow();
        assertThat(reseteado.mustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(temporal, reseteado.password())).isTrue();
        List<JsonNode> eventos = eventos(Topicos.IDENTIDAD_PASSWORD_TEMPORAL_GENERADA_V1);
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).at("/data/email").asText()).isEqualTo("usuario.rol@tranqui.com");
    }

    // ================================================================================
    // Portón del sitio
    // ================================================================================

    @Test
    void porton_verificaLaContrasenaYEmiteLaCookie() throws Exception {
        ResponseEntity<String> sinCookie = rest.getForEntity("/api/site-access/estado", String.class);
        assertThat(mapper.readTree(sinCookie.getBody()).get("autorizado").asBoolean()).isFalse();

        ResponseEntity<String> mal = rest.postForEntity("/api/site-access/verificar", Map.of("password", "otra"), String.class);
        assertThat(mal.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> bien = rest.postForEntity("/api/site-access/verificar", Map.of("password", PASSWORD_SITIO), String.class);
        assertThat(bien.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = bien.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(PASSWORD_SITIO.getBytes(StandardCharsets.UTF_8)));
        assertThat(cookie).startsWith("SITE-ACCESS=" + esperado).contains("HttpOnly").contains("Max-Age=2592000");

        HttpHeaders conCookie = new HttpHeaders();
        conCookie.add(HttpHeaders.COOKIE, "SITE-ACCESS=" + esperado);
        ResponseEntity<String> autorizado = rest.exchange("/api/site-access/estado", HttpMethod.GET, new HttpEntity<>(conCookie), String.class);
        assertThat(mapper.readTree(autorizado.getBody()).get("autorizado").asBoolean()).isTrue();
    }

    // ================================================================================
    // Novedades
    // ================================================================================

    @Test
    void novedades_exigenElTokenYRechazanLoQuePasoPorUnProxy() {
        Map<String, Object> contenido = contenidoNovedades("mant-prueba-1");
        assertThat(novedades(contenido, null, false, false).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(novedades(contenido, "otro-token", false, false).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(novedades(contenido, TOKEN_NOVEDADES, true, false).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void novedades_pruebaALosAdminsYEnvioIndividualIdempotente() throws Exception {
        crear("admin.novedades@tranqui.com", "ADMIN");
        Usuario paciente = crear("paciente.novedades@tranqui.com", "PACIENTE");

        ResponseEntity<String> prueba = novedades(contenidoNovedades("mant-prueba-2"), TOKEN_NOVEDADES, false, true);
        assertThat(prueba.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Una versión por público con contenido (las generales llegan a los dos), al único admin.
        assertThat(mapper.readTree(prueba.getBody()).get("mailsEnviados").asInt()).isEqualTo(2);

        ResponseEntity<String> envio = novedades(contenidoNovedades("mant-real-1"), TOKEN_NOVEDADES, false, false);
        assertThat(envio.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        esperarEstado("mant-real-1", "ENVIADO");
        verify(mailNovedades, times(1)).enviarHtml(eq(paciente.email()), anyString(), anyString());

        // El mismo id no se vuelve a mandar.
        ResponseEntity<String> repetido = novedades(contenidoNovedades("mant-real-1"), TOKEN_NOVEDADES, false, false);
        assertThat(mapper.readTree(repetido.getBody()).get("yaExistia").asBoolean()).isTrue();

        assertThat(novedades(Map.of("id", "x", "titulo", "t"), TOKEN_NOVEDADES, false, false).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tokenConElUidDeOtraCuenta_401() {
        Usuario ana = crear("ana.uid@tranqui.com", "PACIENTE");
        Usuario otra = crear("otra.uid@tranqui.com", "PACIENTE");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(emisor.emitir(ana.email(), "PACIENTE", ana.nombre(), otra.id()));
        assertThat(rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void miCuenta_novedades_leeYCambiaLaPreferencia() throws Exception {
        Usuario paciente = crear("preferencia@tranqui.com", "PACIENTE");
        JsonNode antes = mapper.readTree(pedir(HttpMethod.GET, "/api/auth/mi-cuenta/novedades", paciente, null).getBody());
        assertThat(antes.get("recibir").asBoolean()).isTrue();

        JsonNode cambiado = mapper.readTree(pedir(HttpMethod.PUT, "/api/auth/mi-cuenta/novedades", paciente,
                Map.of("recibir", false)).getBody());
        assertThat(cambiado.get("recibir").asBoolean()).isFalse();
        assertThat(usuarios.buscarPorId(paciente.id()).orElseThrow().isRecibirNovedades()).isFalse();
    }

    // ================================================================================
    // Rutas internas
    // ================================================================================

    @Test
    void internas_sinJwtDeServicio_401() {
        assertThat(rest.getForEntity("/internal/identidad/contactos", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internas_pacienteConElMismoJsonQueElMonolito() throws Exception {
        Usuario u = usuarios.buscarPorId(usuarios.insertarNuevo(Usuario.builder()
                .email("ficha@tranqui.com").rol("PACIENTE").nombre("Ana").apellido("Pérez").telefono("+54 1155556666")
                .fechaNacimiento(LocalDate.of(1990, 5, 17)).obraSocial("OSDE 210").numAfiliado("123456")
                .credencialCodEntidad(7).credencialPan("123456789").domicilioCalle("Corrientes").emailVerificado(true)
                .build())).orElseThrow();

        JsonNode p = interna(HttpMethod.GET, "/internal/identidad/pacientes/" + u.id(), null, HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", p);
        assertThat(claves(p)).isEqualTo(new TreeSet<>(Set.of("id", "version", "rol", "email", "nombre", "apellido",
                "telefono", "dni", "direccion", "obraSocial", "numAfiliado", "sexo", "fechaNacimiento", "cuil",
                "tipoDocumento", "numeroDocumento", "datosOfuscado", "credencialCodEntidad", "credencialPan",
                "credencialPlan", "credencialToken", "emailHabilitado", "domicilioCalle", "domicilioNumero",
                "domicilioPiso", "domicilioDpto", "domicilioCodigoPostal", "domicilioLocalidad", "domicilioProvincia",
                "domicilioPais")));
        assertThat(p.get("fechaNacimiento").asText()).isEqualTo("1990-05-17");
        assertThat(p.get("version").asInt()).isEqualTo(1);
        assertThat(p.get("emailHabilitado").asBoolean()).isTrue();
        assertThat(p.get("credencialCodEntidad").asInt()).isEqualTo(7);

        interna(HttpMethod.GET, "/internal/identidad/pacientes/999999", null, HttpStatus.NOT_FOUND);
        JsonNode lista = interna(HttpMethod.GET, "/internal/identidad/pacientes?pagina=0&tamanio=10", null, HttpStatus.OK);
        assertThat(lista).hasSize(1);
        JsonNode contactos = interna(HttpMethod.GET, "/internal/identidad/contactos", null, HttpStatus.OK);
        assertThat(claves(contactos.get(0))).isEqualTo(new TreeSet<>(Set.of("id", "email", "nombre", "apellido", "rol", "emailHabilitado")));
    }

    @Test
    void internas_buscarOCrearPaciente_creaPisaYValida() throws Exception {
        JsonNode nuevo = interna(HttpMethod.POST, "/internal/identidad/pacientes",
                Map.of("email", " Nuevo@Tranqui.com ", "nombre", "Nuevo", "telefono", "1144445555"), HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", nuevo);
        assertThat(nuevo.get("email").asText()).isEqualTo("nuevo@tranqui.com");
        assertThat(nuevo.get("telefono").asText()).isEqualTo("+54 1144445555");
        assertThat(nuevo.get("rol").asText()).isEqualTo("PACIENTE");
        List<JsonNode> registrados = eventos(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1);
        assertThat(registrados).hasSize(1);
        assertThat(registrados.get(0).get("aggregateVersion").asInt()).isEqualTo(1);
        assertThat(usuarios.buscarPorEmail("nuevo@tranqui.com").orElseThrow().password()).isNull();

        // Ya existe: pisa la obra social y avisa el cambio.
        JsonNode pisado = interna(HttpMethod.POST, "/internal/identidad/pacientes",
                Map.of("email", "nuevo@tranqui.com", "obraSocial", "Swiss Medical", "numAfiliado", "998877"), HttpStatus.OK);
        assertThat(pisado.get("obraSocial").asText()).isEqualTo("Swiss Medical");
        assertThat(pisado.get("id").asLong()).isEqualTo(nuevo.get("id").asLong());
        List<JsonNode> actualizados = eventos(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1);
        assertThat(actualizados).hasSize(1);
        assertThat(actualizados.get(0).get("aggregateVersion").asInt()).isEqualTo(pisado.get("version").asInt());

        interna(HttpMethod.POST, "/internal/identidad/pacientes", Map.of("email", "no-es-un-mail"), HttpStatus.BAD_REQUEST);
        interna(HttpMethod.POST, "/internal/identidad/pacientes", Map.of("nombre", "Sin mail"), HttpStatus.BAD_REQUEST);
        interna(HttpMethod.POST, "/internal/identidad/pacientes",
                Map.of("email", "tel@tranqui.com", "telefono", "12"), HttpStatus.BAD_REQUEST);

        // El pedido del contrato, tal cual lo manda turnos.
        JsonNode delContrato = interna(HttpMethod.POST, "/internal/identidad/pacientes",
                Contratos.fixture("internas/fuente-identidad/pacientes-buscar-o-crear.request.json"), HttpStatus.OK);
        interna(HttpMethod.PUT, "/internal/identidad/pacientes/" + delContrato.get("id").asLong(),
                Contratos.fixture("internas/fuente-identidad/paciente-actualizar.request.json"), HttpStatus.OK);
    }

    @Test
    void internas_fichaDelPaciente_validaYBorraLaCredencialSiNoViene() throws Exception {
        Usuario u = usuarios.buscarPorId(usuarios.insertarNuevo(Usuario.builder()
                .email("ficha2@tranqui.com").rol("PACIENTE").nombre("Luis").obraSocial("OSDE").credencialCodEntidad(3)
                .credencialPan("ABC123456").emailVerificado(true).build())).orElseThrow();

        Map<String, Object> invalida = new HashMap<>();
        invalida.put("nombre", "");
        invalida.put("numeroDocumento", 12);
        invalida.put("tipoDocumento", "CI");
        ResponseEntity<String> r = rest.exchange("/internal/identidad/pacientes/" + u.id(), HttpMethod.PUT,
                new HttpEntity<>(invalida, headersServicio()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("el nombre del paciente es obligatorio").contains("DNI, LC, LE o Pasaporte")
                .contains("entre 6 y 9 dígitos");

        Map<String, Object> borrar = new HashMap<>();
        borrar.put("nombre", "Luis");
        borrar.put("fechaNacimiento", "1985-02-03");
        borrar.put("telefono", "1133334444");
        JsonNode p = interna(HttpMethod.PUT, "/internal/identidad/pacientes/" + u.id(), borrar, HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", p);
        assertThat(p.get("obraSocial").isNull()).isTrue();
        assertThat(p.get("credencialPan").isNull()).isTrue();
        assertThat(p.get("telefono").asText()).isEqualTo("+54 1133334444");
        assertThat(p.get("fechaNacimiento").asText()).isEqualTo("1985-02-03");
        assertThat(eventos(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1)).hasSize(1);
    }

    @Test
    void internas_profesional_conLoQueNoEsDeIdentidadEnNullYElPerfilDelRegistro() throws Exception {
        Usuario psi = usuarios.buscarPorId(usuarios.insertarNuevo(Usuario.builder()
                .email("prof@tranqui.com").rol("PSIQUIATRA").profession("psiquiatra").nombre("Marta")
                .fotoUrl("data:image/png;base64,AAAA").verificadoAdmin(true).emailVerificado(true)
                .taxIdType("CUIT").taxId("20123456789").build())).orElseThrow();
        perfilesRegistro.guardar(psi.id(), perfil("MP-5678"));
        Usuario paciente = crear("no.prof@tranqui.com", "PACIENTE");

        JsonNode p = interna(HttpMethod.GET, "/internal/identidad/profesionales/" + psi.id(), null, HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/profesional.json", p);
        assertThat(claves(p)).isEqualTo(new TreeSet<>(Set.of("id", "version", "email", "rol", "profession", "nombre",
                "apellido", "telefono", "sexo", "fechaNacimiento", "cuil", "tipoDocumento", "numeroDocumento", "fotoUrl",
                "fotoHash", "verificadoAdmin", "mpVinculada", "suscripcionEstado", "suscripcionPeriodoHasta",
                "puedeRecetar", "perfil", "disponibilidad", "tarifas")));
        for (String ajeno : List.of("mpVinculada", "suscripcionEstado", "suscripcionPeriodoHasta", "puedeRecetar",
                "disponibilidad", "tarifas")) {
            assertThat(p.get(ajeno).isNull()).as(ajeno).isTrue();
        }
        assertThat(p.at("/perfil/matricula").asText()).isEqualTo("MP-5678");
        assertThat(p.get("fotoUrl").asText()).startsWith("data:image/png");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("data:image/png;base64,AAAA".getBytes(StandardCharsets.UTF_8)));
        assertThat(p.get("fotoHash").asText()).isEqualTo(hash);

        JsonNode estado = interna(HttpMethod.GET, "/internal/identidad/profesionales/estado", null, HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/profesionales-estado.json", estado);
        assertThat(estado).hasSize(1);
        assertThat(estado.get(0).get("fotoUrl").isNull()).isTrue();
        assertThat(estado.get(0).get("perfil").isNull()).isTrue();
        assertThat(interna(HttpMethod.GET, "/internal/identidad/profesionales?foto=false", null, HttpStatus.OK).get(0)
                .get("fotoUrl").isNull()).isTrue();

        // Suscriptores: rol PSIQUIATRA o con profesión; el cuit sale del perfil del registro.
        JsonNode s = interna(HttpMethod.GET, "/internal/identidad/suscriptores/" + psi.id(), null, HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/suscriptor.json", s);
        assertThat(s.get("cuit").asText()).isEqualTo("20999999993");
        assertThat(s.get("taxId").asText()).isEqualTo("20123456789");
        interna(HttpMethod.GET, "/internal/identidad/suscriptores/" + paciente.id(), null, HttpStatus.NOT_FOUND);
        JsonNode suscriptores = interna(HttpMethod.GET, "/internal/identidad/suscriptores", null, HttpStatus.OK);
        assertThat(suscriptores).hasSize(1);
        Contratos.mismaForma("internas/fuente-identidad/suscriptores.json", suscriptores);
    }

    @Test
    void internas_identidadDelProfesional_guardaValidaYAvisa() throws Exception {
        Usuario psi = crear("perfil.psi@tranqui.com", "PSIQUIATRA");
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", "Jorge");
        body.put("apellido", "Gómez");
        body.put("telefono", "+54 1122223333");
        body.put("fechaNacimiento", "1980-01-01");
        body.put("cuil", 20123456789L);
        body.put("tipoDocumento", "DNI");
        body.put("numeroDocumento", 12345678);
        body.put("fotoUrl", "https://foto");
        JsonNode r = interna(HttpMethod.PUT, "/internal/identidad/profesionales/" + psi.id() + "/identidad", body, HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/profesional.json", r);
        assertThat(r.get("nombre").asText()).isEqualTo("Jorge");
        // Devuelve sin la foto, como el monolito.
        assertThat(r.get("fotoUrl").isNull()).isTrue();
        assertThat(usuarios.buscarPorId(psi.id()).orElseThrow().fotoUrl()).isEqualTo("https://foto");
        List<JsonNode> actualizados = eventos(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1);
        assertThat(actualizados).hasSize(1);
        assertThat(actualizados.get(0).get("aggregateVersion").asInt()).isEqualTo(r.get("version").asInt());

        body.put("numeroDocumento", 123);
        body.put("cuil", 123L);
        interna(HttpMethod.PUT, "/internal/identidad/profesionales/" + psi.id() + "/identidad", body, HttpStatus.BAD_REQUEST);
        interna(HttpMethod.PUT, "/internal/identidad/profesionales/999999/identidad", Map.of("nombre", "X"), HttpStatus.NOT_FOUND);
        // El pedido del contrato, tal cual lo manda profesionales.
        interna(HttpMethod.PUT, "/internal/identidad/profesionales/" + psi.id() + "/identidad",
                Contratos.fixture("internas/fuente-identidad/profesional-identidad.request.json"), HttpStatus.OK);
    }

    // ================================================================================

    private Usuario crear(String email, String rol) {
        Long id = usuarios.insertarNuevo(Usuario.builder()
                .email(email)
                .password(passwordEncoder.encode("Clave1234"))
                .rol(rol)
                .nombre("Nombre")
                .apellido("Apellido")
                .emailVerificado(true)
                .perfilCompleto(true)
                .terminosAceptadosEn(LocalDateTime.now())
                .build());
        return usuarios.buscarPorId(id).orElseThrow();
    }

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, Usuario usuario, Object cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(emisor.emitir(usuario.email(), usuario.rol(), usuario.nombre(), usuario.id()));
        return rest.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private HttpHeaders headersServicio() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, tokenServicio);
        return headers;
    }

    private JsonNode interna(HttpMethod metodo, String ruta, Object cuerpo, HttpStatus esperado) throws Exception {
        ResponseEntity<String> r = rest.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headersServicio()), String.class);
        assertThat(r.getStatusCode()).as(metodo + " " + ruta + ": " + r.getBody()).isEqualTo(esperado);
        return r.getBody() == null || r.getBody().isBlank() || !esperado.is2xxSuccessful() ? null : mapper.readTree(r.getBody());
    }

    private ResponseEntity<String> novedades(Map<String, Object> contenido, String token, boolean porProxy, boolean prueba) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.set("X-Novedades-Token", token);
        if (porProxy) headers.set("X-Forwarded-For", "1.2.3.4");
        return rest.exchange("/internal/identidad/novedades" + (prueba ? "?prueba=true" : ""), HttpMethod.POST,
                new HttpEntity<>(contenido, headers), String.class);
    }

    private void esperarEstado(String id, String estado) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Novedades-Token", TOKEN_NOVEDADES);
        for (int i = 0; i < 50; i++) {
            ResponseEntity<String> r = rest.exchange("/internal/identidad/novedades/" + id, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            if (r.getStatusCode().is2xxSuccessful() && estado.equals(mapper.readTree(r.getBody()).get("estado").asText())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El envío " + id + " no llegó a " + estado);
    }

    private static Map<String, Object> contenidoNovedades(String id) {
        Map<String, Object> c = new HashMap<>();
        c.put("id", id);
        c.put("titulo", "Novedades de prueba");
        c.put("general", List.of("Algo nuevo para todos"));
        return c;
    }

    private static PerfilProfesionalData perfil(String matricula) {
        return new PerfilProfesionalData(null, "Av. Siempreviva 742", null, null, null, null, null, null,
                matricula, "MN", "CABA", 1234, null, null, null, null, "Médico", "Psiquiatría", "20999999993",
                new BigDecimal("50000"), null, true, false, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private List<JsonNode> eventos(String topico) {
        return jdbc.query("SELECT payload FROM outbox WHERE topic = ? ORDER BY id ASC", (rs, n) -> {
            try {
                return mapper.readTree(rs.getString("payload"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, topico);
    }

    private static TreeSet<String> claves(JsonNode n) {
        TreeSet<String> claves = new TreeSet<>();
        Iterator<String> it = n.fieldNames();
        while (it.hasNext()) claves.add(it.next());
        return claves;
    }
}
