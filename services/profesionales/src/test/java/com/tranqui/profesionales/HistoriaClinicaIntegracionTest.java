package com.tranqui.profesionales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Recetas;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.Solicitudes;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import com.tranqui.profesionales.interno.CargaInicialClinica;
import com.tranqui.profesionales.interno.ClienteInterno;
import com.tranqui.profesionales.interno.Copias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Tests de integración de la Fase 7 (H2a): esquema y copias de la historia clínica, recetas,
 * solicitudes de documentos, personas y turnos atendidos.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "profesionales.carga-inicial.habilitada=false",
        "profesionales.clinica.habilitada=false",
        "profesionales.sincronizacion-segundos=3600",
        "jwt.secret=clave-de-test-de-al-menos-32-bytes-ok",
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "tranqui.gateway.url=http://localhost:1"
})
class HistoriaClinicaIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;

    @Autowired Seguimientos seguimientos;
    @Autowired Informes informes;
    @Autowired Recetas recetas;
    @Autowired Solicitudes solicitudes;
    @Autowired Personas personas;
    @Autowired TurnosAtendidos turnosAtendidos;
    @Autowired Copias copias;
    @Autowired CargaInicialClinica cargaInicialClinica;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("profesionales"));
            return info != null && info.getConsumerCount() > 0;
        });
        for (String tabla : List.of("seguimiento_diario", "informe_clinico", "receta", "solicitud_documento",
                "turno_atendido", "persona", "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);
    }

    @Test
    void migracion_creaLasTablasYSecuencias() {
        Integer tablas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('seguimiento_diario', 'informe_clinico', 'receta', 'solicitud_documento', 'persona', 'turno_atendido')",
                Integer.class);
        assertThat(tablas).isEqualTo(6);

        Integer secuencias = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public' "
                        + "AND sequence_name IN ('seguimiento_diario_id_seq', 'informe_clinico_id_seq', 'receta_id_seq', 'solicitud_documento_id_seq')",
                Integer.class);
        assertThat(secuencias).isEqualTo(4);
    }

    @Test
    void cargaInicialClinica_copiaTalCualYSecuenciaQuedaArribaDelMaximo() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = LocalDate.now();

        ClienteInterno.SeguimientoMonolito s = new ClienteInterno.SeguimientoMonolito(
                100L, 10L, 20L, hoy, "Excelente", "Ninguno", "Notas seguimiento",
                ahora, "VIGENTE", null, "hash-seg-100");
        when(interno.seguimientos(eq(0), anyInt())).thenReturn(List.of(s));

        ClienteInterno.InformeMonolito inf = new ClienteInterno.InformeMonolito(
                200L, 10L, 20L, hoy, "Evolutivo", "Plan A", "Contenido del informe",
                "informe.pdf", ahora, "VIGENTE", null, null, "hash-inf-200");
        when(interno.informes(eq(0), anyInt())).thenReturn(List.of(inf));

        ClienteInterno.RecetaMonolito r = new ClienteInterno.RecetaMonolito(
                300L, 20L, 10L, "[{\"nombre\":\"Sertralina\"}]", "[\"Lab\"]", "F41.1",
                "1 diaria", "https://pdf.receta/300", "qbi-rec-300", "verif-300",
                "cuir-300", "2026-12-01", "tx-300", ahora);
        when(interno.recetas(eq(0), anyInt())).thenReturn(List.of(r));

        ClienteInterno.SolicitudMonolito sol = new ClienteInterno.SolicitudMonolito(
                400L, 10L, 20L, "CERTIFICADO", new BigDecimal("15000.00"), "APROBADO",
                "tx-sol-400", true, "https://dl/sol-400", ahora, ahora);
        when(interno.solicitudes(eq(0), anyInt())).thenReturn(List.of(sol));

        int total = cargaInicialClinica.cargar();
        assertThat(total).isEqualTo(4);

        // Verifica copias exactas
        Seguimientos.Seguimiento sGuardado = seguimientos.porId(100L).orElseThrow();
        assertThat(sGuardado.hashIntegridad()).isEqualTo("hash-seg-100");
        assertThat(sGuardado.estado()).isEqualTo("VIGENTE");
        assertThat(sGuardado.estadoAnimo()).isEqualTo("Excelente");

        Informes.Informe infGuardado = informes.porId(200L).orElseThrow();
        assertThat(infGuardado.hashIntegridad()).isEqualTo("hash-inf-200");
        assertThat(infGuardado.tipoInforme()).isEqualTo("Evolutivo");

        Recetas.Receta rGuardada = recetas.porId(300L).orElseThrow();
        assertThat(rGuardada.qbi2IdReceta()).isEqualTo("qbi-rec-300");
        assertThat(rGuardada.diagnostico()).isEqualTo("F41.1");

        Solicitudes.Solicitud solGuardada = solicitudes.porId(400L).orElseThrow();
        assertThat(solGuardada.transactionId()).isEqualTo("tx-sol-400");
        assertThat(solGuardada.precio()).isEqualByComparingTo("15000.00");

        // Verifica que la secuencia quedó arriba del máximo y un insert nuevo no choca
        Long nuevoSegId = seguimientos.insertarNuevo(new Seguimientos.Seguimiento(
                null, 10L, 20L, hoy, "Bueno", null, null, ahora, "VIGENTE", null, "nuevo-hash"));
        assertThat(nuevoSegId).isGreaterThan(100L);

        Long nuevoInfId = informes.insertarNuevo(new Informes.Informe(
                null, 10L, 20L, hoy, "General", null, "Texto", null, ahora, "VIGENTE", null, null, "hash-nuevo"));
        assertThat(nuevoInfId).isGreaterThan(200L);

        Long nuevoRecId = recetas.insertarNuevo(new Recetas.Receta(
                null, 20L, 10L, "Medicamento", null, null, null, null, null, null, null, null, null, ahora));
        assertThat(nuevoRecId).isGreaterThan(300L);

        Long nuevoSolId = solicitudes.insertarNuevo(new Solicitudes.Solicitud(
                null, 10L, 20L, "INFORME", BigDecimal.ZERO, "PENDIENTE", null, false, null, null, ahora));
        assertThat(nuevoSolId).isGreaterThan(400L);
    }

    @Test
    void cargaInicialClinica_deshabilitada_noCopiaNada() {
        assertThat(cargaInicialClinica.listo()).isTrue();
        assertThat(seguimientos.cantidad()).isZero();
        assertThat(informes.cantidad()).isZero();
        assertThat(recetas.cantidad()).isZero();
        assertThat(solicitudes.cantidad()).isZero();
    }

    @Test
    void personas_cargaInicial_y_refrescoPorEvento() throws Exception {
        ClienteInterno.PacienteMonolito paciente = new ClienteInterno.PacienteMonolito(
                55L, 1, "PACIENTE", "paciente@mail.com", "Carlos", "Perez", "+54 11 4444-5555",
                "20111222", "Calle 123", "OSDE", "123456", "M", LocalDate.of(1985, 4, 12),
                20201112229L, "DNI", 20111222, null, null, null, null, null, true,
                "San Martin", "456", "2", "B", "1425", "Palermo", "CABA", "Argentina");

        when(interno.pacientes(eq(0), anyInt())).thenReturn(List.of(paciente));
        int cargados = copias.copiarPersonas();
        assertThat(cargados).isEqualTo(1);

        Personas.Persona guardada = personas.porId(55L).orElseThrow();
        assertThat(guardada.nombre()).isEqualTo("Carlos");
        assertThat(guardada.domicilioCalle()).isEqualTo("San Martin");
        assertThat(guardada.domicilioLocalidad()).isEqualTo("Palermo");

        // Evento identidad.usuario.actualizado refresca desde el monolito
        ClienteInterno.PacienteMonolito pacienteModificado = new ClienteInterno.PacienteMonolito(
                55L, 2, "PACIENTE", "paciente@mail.com", "Carlos Alberto", "Perez", "+54 11 4444-5555",
                "20111222", "Calle 123", "OSDE", "123456", "M", LocalDate.of(1985, 4, 12),
                20201112229L, "DNI", 20111222, null, null, null, null, null, true,
                "Av. Corrientes", "1000", null, null, "1043", "San Nicolás", "CABA", "Argentina");
        when(interno.paciente(55L)).thenReturn(pacienteModificado);

        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<UsuarioActualizadoData>builder()
                                .eventId(UUID.randomUUID()).eventType("identidad.usuario.actualizado")
                                .producer("monolito").aggregateType("usuario").aggregateId("55").aggregateVersion(2)
                                .data(new UsuarioActualizadoData(55L, "paciente@mail.com", "Carlos Alberto", "Perez", "PACIENTE")).build()))
                        .setContentType("application/json").build());

        esperar(() -> "Carlos Alberto".equals(personas.porId(55L).map(Personas.Persona::nombre).orElse(null)));
        Personas.Persona actualizada = personas.porId(55L).orElseThrow();
        assertThat(actualizada.domicilioCalle()).isEqualTo("Av. Corrientes");
        assertThat(actualizada.version()).isEqualTo(2);

        // Evento identidad.usuario.eliminado borra a la persona
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<UsuarioEliminadoData>builder()
                                .eventId(UUID.randomUUID()).eventType("identidad.usuario.eliminado")
                                .producer("monolito").aggregateType("usuario").aggregateId("55").aggregateVersion(3)
                                .data(new UsuarioEliminadoData(55L, "Baja solicitada")).build()))
                        .setContentType("application/json").build());

        esperar(() -> personas.porId(55L).isEmpty());
    }

    @Test
    void turnoAtendido_respetaVersion_y_consultasReplicanMonolito() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(2);

        // Evento turnos.turno.actualizado.v1 con versión 5
        TurnoActualizadoData dataV5 = new TurnoActualizadoData(
                1001L, 10L, 50L, fecha, LocalTime.of(10, 0), LocalTime.of(10, 45),
                "PARTICULAR", "ONLINE", "CONFIRMADO", "ESPERANDO", new BigDecimal("50000"),
                null, "particular", true, null, null, null, null, false, false,
                LocalDateTime.now(), false, false, null, null, null, null);

        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.TURNOS_TURNO_ACTUALIZADO_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<TurnoActualizadoData>builder()
                                .eventId(UUID.randomUUID()).eventType("turnos.turno.actualizado")
                                .producer("turnos").aggregateType("turno").aggregateId("1001").aggregateVersion(5)
                                .data(dataV5).build()))
                        .setContentType("application/json").build());

        esperar(() -> turnosAtendidos.porId(1001L).isPresent());
        TurnosAtendidos.TurnoAtendido t = turnosAtendidos.porId(1001L).orElseThrow();
        assertThat(t.estado()).isEqualTo("CONFIRMADO");
        assertThat(t.version()).isEqualTo(5);

        // Versión vieja (4) no debe pisar la versión 5
        turnosAtendidos.guardar(1001L, 50L, 10L, fecha, "CANCELADO", 4);
        TurnosAtendidos.TurnoAtendido noPisado = turnosAtendidos.porId(1001L).orElseThrow();
        assertThat(noPisado.estado()).isEqualTo("CONFIRMADO");
        assertThat(noPisado.version()).isEqualTo(5);

        // Versión igual o mayor (6) sí pisa
        turnosAtendidos.guardar(1001L, 50L, 10L, fecha, "CANCELADO", 6);
        TurnosAtendidos.TurnoAtendido pisado = turnosAtendidos.porId(1001L).orElseThrow();
        assertThat(pisado.estado()).isEqualTo("CANCELADO");
        assertThat(pisado.version()).isEqualTo(6);

        // Escenario de consultas replicando TurnoRepository del monolito:
        // Profesional 70 atiende a varios pacientes
        turnosAtendidos.guardar(2001L, 70L, 101L, fecha, "CONFIRMADO", 1);
        turnosAtendidos.guardar(2002L, 70L, 101L, fecha.plusDays(1), "PENDIENTE_VALIDACION", 1);
        turnosAtendidos.guardar(2003L, 70L, 102L, fecha, "PENDIENTE_VALIDACION", 1);
        turnosAtendidos.guardar(2004L, 70L, 103L, fecha, "CANCELADO", 1);
        turnosAtendidos.guardar(2005L, 70L, 104L, fecha, "EXPIRADO", 1);
        turnosAtendidos.guardar(2006L, 80L, 101L, fecha, "CONFIRMADO", 1); // otro profesional

        // 1. existeConfirmadoOPendiente (existsByMedicoIdAndPacienteIdAndEstadoIn)
        assertThat(turnosAtendidos.existeConfirmadoOPendiente(70L, 101L)).isTrue();
        assertThat(turnosAtendidos.existeConfirmadoOPendiente(70L, 102L)).isTrue();
        assertThat(turnosAtendidos.existeConfirmadoOPendiente(70L, 103L)).isFalse(); // solo CANCELADO
        assertThat(turnosAtendidos.existeConfirmadoOPendiente(70L, 104L)).isFalse(); // solo EXPIRADO
        assertThat(turnosAtendidos.existeConfirmadoOPendiente(70L, 999L)).isFalse(); // no existe

        // 2. pacientesDistintos (findDistinctPacientesByMedicoId con CONFIRMADO y PENDIENTE_VALIDACION)
        List<Long> pacientes = turnosAtendidos.pacientesDistintos(70L);
        assertThat(pacientes).containsExactlyInAnyOrder(101L, 102L);

        // 3. turnosNoCancelados (findByMedicoIdAndEstadoNot con CANCELADO)
        List<TurnosAtendidos.TurnoAtendido> noCancelados = turnosAtendidos.turnosNoCancelados(70L);
        assertThat(noCancelados).extracting(TurnosAtendidos.TurnoAtendido::id)
                .containsExactlyInAnyOrder(2001L, 2002L, 2003L, 2005L);
        assertThat(noCancelados).noneMatch(turno -> "CANCELADO".equals(turno.estado()));
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long fin = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        while (System.currentTimeMillis() < fin) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("La condición no se cumplió a tiempo");
    }
}
