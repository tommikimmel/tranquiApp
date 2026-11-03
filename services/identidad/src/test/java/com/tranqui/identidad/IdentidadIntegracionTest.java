package com.tranqui.identidad;

import com.tranqui.identidad.datos.*;
import com.tranqui.identidad.interno.CargaInicialIdentidad;
import com.tranqui.identidad.interno.ClienteInterno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * Tests de integración de la Fase 9 (I2a): esquema, datos y carga inicial
 * del servicio de identidad sobre PostgreSQL y RabbitMQ reales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "identidad.carga.habilitada=false",
        "jwt.secret=clave-de-test-de-al-menos-32-bytes-ok",
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "tranqui.gateway.url=http://localhost:1"
})
class IdentidadIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;

    @Autowired Usuarios usuarios;
    @Autowired Tickets tickets;
    @Autowired TicketMensajes ticketMensajes;
    @Autowired NovedadesEnvios novedadesEnvios;
    @Autowired CargaInicialIdentidad cargaInicial;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM ticket_mensaje");
        jdbc.update("DELETE FROM ticket");
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
                                   'outbox', 'eventos_procesados', 'versiones_aplicadas')
                """, Integer.class);
        assertThat(tablas).isEqualTo(7);

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
}
