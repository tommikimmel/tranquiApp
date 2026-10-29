package com.tranqui.profesionales.interno;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.profesionales.seguridad.JwtTestHelper;
import com.tranqui.profesionales.datos.Profesionales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Fase 9, I3b: con la fuente de identidad, profesionales toma la identidad del servicio de identidad sin pisar lo que
 * es de pagos (Mercado Pago y la suscripción), que llega por sus eventos y por la resincronización contra pagos, y
 * nunca escribe un dato viejo encima de uno nuevo ni un dato inválido.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "server.port=0",
        "profesionales.carga-inicial.habilitada=false",
        "profesionales.sincronizacion-segundos=3600",
        "jwt.secret=" + JwtTestHelper.CLAVE_JWT,
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "tranqui.gateway.url=http://localhost:1"
})
class FuenteIdentidadIntegracionTest {

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
    @Autowired SincronizacionMonolito sincronizacion;
    @Autowired Copias copias;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("profesionalMpVinculada") ManejadorEvento<CuentaMpVinculadaData> mpVinculada;
    @Autowired @Qualifier("profesionalMpDesvinculada") ManejadorEvento<CuentaMpDesvinculadaData> mpDesvinculada;
    @Autowired @Qualifier("profesionalSuscripcion") ManejadorEvento<SuscripcionCambioEstadoV2Data> suscripcion;

    @BeforeEach
    void preparar() {
        for (String tabla : List.of("disponibilidad", "tarifa", "profesional", "turno_atendido")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);
        when(interno.fuenteIdentidad()).thenReturn(true);
    }

    @Test
    void refrescoDesdeIdentidad_noPisaMercadoPagoNiLaSuscripcion() {
        altaConPagos(7L);
        // Identidad manda lo que no es suyo en null/false.
        when(interno.profesional(7L)).thenReturn(deIdentidad(7L, "Nueva", 12));

        sincronizacion.refrescar(7L);

        Profesionales.Profesional p = profesionales.porId(7L).orElseThrow();
        assertThat(p.nombre()).isEqualTo("Nueva");
        assertThat(p.version()).isEqualTo(12);
        assertThat(p.mpVinculada()).isTrue();
        assertThat(p.suscripcionEstado()).isEqualTo("ACTIVE");
        assertThat(p.puedeRecetar()).isTrue();
    }

    @Test
    void sincronizacionDeEstado_comparaSoloLoDeIdentidad() {
        altaConPagos(7L);
        Profesionales.Profesional antes = profesionales.porId(7L).orElseThrow();
        // Misma identidad y misma foto: los null de pagos no cuentan como cambio.
        ClienteInterno.ProfesionalMonolito igual = new ClienteInterno.ProfesionalMonolito(7L, antes.version(), antes.email(),
                antes.rol(), antes.profession(), antes.nombre(), antes.apellido(), antes.telefono(), antes.sexo(),
                antes.fechaNacimiento(), antes.cuil(), antes.tipoDocumento(), antes.numeroDocumento(), null, antes.fotoHash(),
                antes.verificadoAdmin(), false, null, null, false, null, null, null);
        when(interno.estado()).thenReturn(List.of(igual));

        assertThat(sincronizacion.sincronizarEstado(List.of(igual))).isZero();
        assertThat(profesionales.porId(7L).orElseThrow().mpVinculada()).isTrue();
    }

    @Test
    void resincronizacionContraPagos_aplicaYRespetaLaVersion() {
        altaConPagos(7L);
        when(interno.cuentasMp(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.CuentaMp(7L, false, 100L)));
        when(interno.estadoSuscripciones(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.EstadoSuscripcion(
                7L, "CANCELLED", "clinico", true, false, LocalDateTime.of(2026, 12, 1, 0, 0), null, 5L)));

        sincronizacion.sincronizarPagos();

        Profesionales.Profesional p = profesionales.porId(7L).orElseThrow();
        assertThat(p.mpVinculada()).isFalse();
        assertThat(p.suscripcionEstado()).isEqualTo("CANCELLED");
        assertThat(p.suscripcionPeriodoHasta()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(p.puedeRecetar()).isFalse();

        // Versiones más viejas que las guardadas: no pisan.
        when(interno.cuentasMp(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.CuentaMp(7L, true, 99L)));
        when(interno.estadoSuscripciones(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.EstadoSuscripcion(
                7L, "ACTIVE", "clinico", true, true, null, null, 4L)));
        sincronizacion.sincronizarPagos();
        p = profesionales.porId(7L).orElseThrow();
        assertThat(p.mpVinculada()).isFalse();
        assertThat(p.suscripcionEstado()).isEqualTo("CANCELLED");
    }

    @Test
    void resincronizacionContraPagos_salteaFilasInvalidas() {
        altaConPagos(7L);
        when(interno.cuentasMp(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.CuentaMp(-1L, false, 100L),
                new ClienteInterno.CuentaMp(7L, false, -3L)));
        when(interno.estadoSuscripciones(eq(0), anyInt())).thenReturn(List.of(new ClienteInterno.EstadoSuscripcion(
                7L, "", "clinico", false, false, null, null, 50L)));

        sincronizacion.sincronizarPagos();

        Profesionales.Profesional p = profesionales.porId(7L).orElseThrow();
        assertThat(p.mpVinculada()).isTrue();
        assertThat(p.suscripcionEstado()).isEqualTo("ACTIVE");
    }

    @Test
    void eventosDePagos_ponenMercadoPagoYSuscripcion_yDescartanLoViejo() {
        altaConPagos(7L);

        mpDesvinculada.manejar(evento(new CuentaMpDesvinculadaData(7L), 200));
        assertThat(profesionales.porId(7L).orElseThrow().mpVinculada()).isFalse();
        mpVinculada.manejar(evento(new CuentaMpVinculadaData(7L, "mp-1"), 150));
        assertThat(profesionales.porId(7L).orElseThrow().mpVinculada()).isFalse();
        mpVinculada.manejar(evento(new CuentaMpVinculadaData(7L, "mp-1"), 300));
        assertThat(profesionales.porId(7L).orElseThrow().mpVinculada()).isTrue();

        suscripcion.manejar(evento(new SuscripcionCambioEstadoV2Data(1L, 7L, "ACTIVE", "SUSPENDED", null, "clinico",
                false, false, null, null), 9));
        Profesionales.Profesional p = profesionales.porId(7L).orElseThrow();
        assertThat(p.suscripcionEstado()).isEqualTo("SUSPENDED");
        assertThat(p.puedeRecetar()).isFalse();
    }

    @Test
    void eventosDePagos_conDatosInvalidos_vanALaDlq() {
        altaConPagos(7L);
        assertThatThrownBy(() -> mpVinculada.manejar(evento(new CuentaMpVinculadaData(0L, "mp"), 5)))
                .isInstanceOf(EventoInvalidoException.class);
        assertThatThrownBy(() -> suscripcion.manejar(evento(new SuscripcionCambioEstadoV2Data(1L, 7L, "ACTIVE", null, null,
                "clinico", false, false, null, null), 9))).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void conLaFuenteDelMonolito_losEventosDePagosNoCambianNada() {
        altaConPagos(7L);
        when(interno.fuenteIdentidad()).thenReturn(false);
        mpDesvinculada.manejar(evento(new CuentaMpDesvinculadaData(7L), 500));
        assertThat(profesionales.porId(7L).orElseThrow().mpVinculada()).isTrue();
    }

    @Test
    void turnos_seResincronizanDesdeElServicioDeTurnos() {
        when(interno.turnosDelServicio()).thenReturn(List.of(
                new ClienteInterno.TurnoMonolito(3, turno(40L), null, null),
                new ClienteInterno.TurnoMonolito(1, null, null, null)));

        assertThat(copias.copiarTurnos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno_atendido", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ datos

    private void altaConPagos(long id) {
        ClienteInterno.ProfesionalMonolito p = new ClienteInterno.ProfesionalMonolito(id, 10, "ana@mail.com", "PSIQUIATRA",
                "psiquiatra", "Ana", "Paz", "+54 11 5555", "F", LocalDate.of(1980, 5, 4), 27123456780L, "DNI", 12345678,
                null, null, true, true, "ACTIVE", LocalDateTime.of(2027, 1, 1, 0, 0), true, perfil(), List.of(), List.of());
        profesionales.alta(p.datos(), p.perfil());
    }

    private static ClienteInterno.ProfesionalMonolito deIdentidad(long id, String nombre, int version) {
        return new ClienteInterno.ProfesionalMonolito(id, version, "ana@mail.com", "PSIQUIATRA", "psiquiatra", nombre, "Paz",
                "+54 11 5555", "F", LocalDate.of(1980, 5, 4), 27123456780L, "DNI", 12345678, null, null, true,
                false, null, null, false, null, null, null);
    }

    private static TurnoActualizadoData turno(long id) {
        return new TurnoActualizadoData(id, 20L, 7L, LocalDate.of(2026, 11, 3), java.time.LocalTime.of(10, 0), null,
                null, null, "CONFIRMADO", null, null, null, null, false, null, null, null, null, false, false, null, false,
                false, null, null, null, null);
    }

    private static PerfilProfesionalData perfil() {
        return new PerfilProfesionalData(null, null, null, null, null, null, null, null, "1234", "MN", "CABA", 1234,
                null, null, null, null, "Médica", "Psiquiatría", null, null, null, true, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static <T> EventEnvelope<T> evento(T data, int version) {
        return EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID())
                .eventType("prueba")
                .version(1)
                .occurredAt(OffsetDateTime.now())
                .producer("pagos")
                .aggregateType("profesional")
                .aggregateId("7")
                .aggregateVersion(version)
                .data(data)
                .build();
    }
}
