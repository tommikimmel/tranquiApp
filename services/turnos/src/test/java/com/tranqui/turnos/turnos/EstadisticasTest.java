package com.tranqui.turnos.turnos;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.web.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstadisticasTest {

    private Turnos turnos;
    private Personas personas;
    private Estadisticas estadisticas;

    private static final Long MEDICO_ID = 5L;
    private static final String MEDICO_EMAIL = "doctor@test.com";

    @BeforeEach
    void setUp() {
        turnos = mock(Turnos.class);
        personas = mock(Personas.class);
        estadisticas = new Estadisticas(turnos, personas);

        Personas.Persona medico = new Personas.Persona(
                MEDICO_ID, 1, "PSIQUIATRA", MEDICO_EMAIL, "Doctor", "Test",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, true, true
        );
        when(personas.porEmail(MEDICO_EMAIL)).thenReturn(Optional.of(medico));
    }

    private Turno crearTurno(Long id, Long pacienteId, LocalDate fecha, String estado, String asistencia,
                             BigDecimal precio, String pagoEstado) {
        Turno t = new Turno();
        t.id = id;
        t.medicoId = MEDICO_ID;
        t.pacienteId = pacienteId;
        t.fecha = fecha;
        t.horaInicio = LocalTime.of(10, 0);
        t.horaFin = LocalTime.of(10, 45);
        t.estado = estado;
        t.asistencia = asistencia;
        t.precio = precio;
        t.pagoEstado = pagoEstado;
        t.fechaCreacion = LocalDateTime.now();
        return t;
    }

    @Test
    void periodoInvalido_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> estadisticas.obtenerStats(MEDICO_EMAIL, "ANUAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> estadisticas.obtenerStats(MEDICO_EMAIL, "OTRO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void medicoNoEncontrado_lanzaNoEncontradoException() {
        when(personas.porEmail("otro@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> estadisticas.obtenerStats("otro@test.com", "MENSUAL"))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void statsDiario_calculaMetricasCorrectamente() {
        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);

        List<Turno> lista = new ArrayList<>();
        // Hoy: 2 turnos confirmados ($10000 cada uno), 1 ausente, 1 cancelado sin reembolso ($10000)
        lista.add(crearTurno(1L, 101L, hoy, Turno.CONFIRMADO, "ESPERANDO", new BigDecimal("10000"), null));
        lista.add(crearTurno(2L, 102L, hoy, Turno.CONFIRMADO, "AUSENTE", new BigDecimal("10000"), null));
        lista.add(crearTurno(3L, 103L, hoy, Turno.CANCELADO, "ESPERANDO", new BigDecimal("10000"), Turno.PAGO_APROBADO));
        // Hoy: 1 cancelado reembolsado (no suma a ganancias)
        lista.add(crearTurno(4L, 104L, hoy, Turno.CANCELADO, "ESPERANDO", new BigDecimal("10000"), Turno.PAGO_REEMBOLSADO));

        // Ayer: 1 confirmado ($10000), 1 cancelado
        lista.add(crearTurno(5L, 101L, ayer, Turno.CONFIRMADO, "COMPLETADA", new BigDecimal("10000"), null));
        lista.add(crearTurno(6L, 105L, ayer, Turno.CANCELADO, "ESPERANDO", new BigDecimal("10000"), null));

        when(turnos.entreFechas(eq(MEDICO_ID), eq(ayer), eq(hoy))).thenReturn(lista);
        when(turnos.primeraFechaPorPaciente(MEDICO_ID)).thenReturn(Map.of(
                101L, ayer,   // paciente existente (primer turno ayer)
                102L, hoy     // nuevo paciente en el período hoy
        ));

        Dtos.DashboardStatsDto stats = estadisticas.obtenerStats(MEDICO_EMAIL, "DIARIO");

        // Sesiones hoy: no cancelados = 2 (id 1 y 2). Ayer = 1 (id 5). Diff = +1 vs ayer
        assertThat(stats.sessionsToday()).isEqualTo(2);
        assertThat(stats.sessionsTodayChange()).isEqualTo("+1 vs ayer");

        // Ganancias hoy: confirmados ($20000) + cancelado sin reembolso ($10000) = $30000.
        // Ganancias ayer: confirmado ($10000) = $10000.
        // Diff %: (30000 - 10000)/10000 = +200.0% vs ayer
        assertThat(stats.earningsThisWeek()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(stats.earningsThisWeekChange()).isEqualTo("+200.0% vs ayer");

        // Pacientes activos hoy: 2 (101L y 102L). Nuevos hoy: 1 (102L).
        assertThat(stats.activePatients()).isEqualTo(2);
        assertThat(stats.activePatientsChange()).isEqualTo("+1 en el período");

        // Inasistencias hoy: ausente (1) + cancelados (2) = 3.
        // Inasistencias ayer: cancelados (1) = 1. Diff = +2 vs ayer
        assertThat(stats.noShowsThisMonth()).isEqualTo(3);
        assertThat(stats.noShowsChange()).isEqualTo("+2 vs ayer");
    }

    @Test
    void statsSemanal_yMensual_defaultMensual() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate finMes = hoy.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate inicioMesAnt = inicioMes.minusMonths(1);

        when(turnos.entreFechas(eq(MEDICO_ID), any(), any())).thenReturn(List.of());
        when(turnos.primeraFechaPorPaciente(MEDICO_ID)).thenReturn(Map.of());

        // default MENSUAL con periodo null o vacío
        Dtos.DashboardStatsDto statsNull = estadisticas.obtenerStats(MEDICO_EMAIL, null);
        assertThat(statsNull.sessionsToday()).isEqualTo(0);
        assertThat(statsNull.sessionsTodayChange()).isEqualTo("igual que el mes anterior");
        assertThat(statsNull.earningsThisWeek()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(statsNull.earningsThisWeekChange()).isEqualTo("0% vs mes anterior");
        assertThat(statsNull.activePatients()).isEqualTo(0);
        assertThat(statsNull.activePatientsChange()).isEqualTo("+0 en el período");
        assertThat(statsNull.noShowsThisMonth()).isEqualTo(0);
        assertThat(statsNull.noShowsChange()).isEqualTo("igual que el mes anterior");

        // SEMANAL
        Dtos.DashboardStatsDto statsSemana = estadisticas.obtenerStats(MEDICO_EMAIL, "SEMANAL");
        assertThat(statsSemana.sessionsTodayChange()).isEqualTo("igual que la semana anterior");
        assertThat(statsSemana.earningsThisWeekChange()).isEqualTo("0% vs sem. anterior");
        assertThat(statsSemana.noShowsChange()).isEqualTo("igual que la semana anterior");
    }
}
