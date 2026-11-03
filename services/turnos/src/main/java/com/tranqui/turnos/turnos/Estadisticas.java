package com.tranqui.turnos.turnos;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.web.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Estadísticas del dashboard del profesional (portado de MedicoService.obtenerStats del monolito).
 */
@Service
public class Estadisticas {

    private static final Set<String> PERIODOS_VALIDOS = Set.of("DIARIO", "SEMANAL", "MENSUAL");

    private final Turnos turnos;
    private final Personas personas;

    public Estadisticas(Turnos turnos, Personas personas) {
        this.turnos = turnos;
        this.personas = personas;
    }

    @Transactional(readOnly = true)
    public Dtos.DashboardStatsDto obtenerStats(String email, String periodo) {
        Personas.Persona medico = personas.porEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado con el email: " + email));

        String periodoNorm = (periodo == null || periodo.isBlank()) ? "MENSUAL" : periodo.trim().toUpperCase();
        if (!PERIODOS_VALIDOS.contains(periodoNorm)) {
            throw new IllegalArgumentException("Período inválido: " + periodo);
        }

        LocalDate hoy = LocalDate.now();
        LocalDate currentStart;
        LocalDate currentEnd;
        LocalDate previousStart;
        LocalDate previousEnd;
        String changeSuffix;
        String flatMessage;

        switch (periodoNorm) {
            case "DIARIO":
                currentStart = hoy;
                currentEnd = hoy;
                previousStart = hoy.minusDays(1);
                previousEnd = hoy.minusDays(1);
                changeSuffix = "vs ayer";
                flatMessage = "igual que ayer";
                break;
            case "SEMANAL":
                currentStart = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                currentEnd = currentStart.plusDays(6);
                previousStart = currentStart.minusWeeks(1);
                previousEnd = previousStart.plusDays(6);
                changeSuffix = "vs sem. anterior";
                flatMessage = "igual que la semana anterior";
                break;
            case "MENSUAL":
            default:
                currentStart = hoy.with(TemporalAdjusters.firstDayOfMonth());
                currentEnd = hoy.with(TemporalAdjusters.lastDayOfMonth());
                previousStart = currentStart.minusMonths(1);
                previousEnd = currentStart.minusDays(1);
                changeSuffix = "vs mes anterior";
                flatMessage = "igual que el mes anterior";
                break;
        }

        List<Turno> todos = turnos.entreFechas(medico.id(), previousStart, currentEnd);
        List<Turno> noCancelados = todos.stream()
                .filter(t -> !Turno.CANCELADO.equals(t.estado))
                .toList();
        List<Turno> cancelados = todos.stream()
                .filter(t -> Turno.CANCELADO.equals(t.estado))
                .toList();
        List<Turno> canceladosSinReembolso = cancelados.stream()
                .filter(Turno::tienePagoAprobado)
                .toList();

        // 1. Sesiones del período vs período anterior
        int sessionsCurrent = (int) noCancelados.stream()
                .filter(t -> !t.fecha.isBefore(currentStart) && !t.fecha.isAfter(currentEnd))
                .count();
        int sessionsPrevious = (int) noCancelados.stream()
                .filter(t -> !t.fecha.isBefore(previousStart) && !t.fecha.isAfter(previousEnd))
                .count();

        String sessionsChange;
        int diffSessions = sessionsCurrent - sessionsPrevious;
        if (diffSessions > 0) {
            sessionsChange = "+" + diffSessions + " " + changeSuffix;
        } else if (diffSessions < 0) {
            sessionsChange = diffSessions + " " + changeSuffix;
        } else {
            sessionsChange = flatMessage;
        }

        // 2. Ganancias: CONFIRMADO + cancelados con pago aprobado sin reembolsar
        BigDecimal earningsCurrent = Stream.concat(
                        noCancelados.stream().filter(t -> Turno.CONFIRMADO.equals(t.estado)),
                        canceladosSinReembolso.stream())
                .filter(t -> !t.fecha.isBefore(currentStart) && !t.fecha.isAfter(currentEnd))
                .map(t -> t.precio != null ? t.precio : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal earningsPrevious = Stream.concat(
                        noCancelados.stream().filter(t -> Turno.CONFIRMADO.equals(t.estado)),
                        canceladosSinReembolso.stream())
                .filter(t -> !t.fecha.isBefore(previousStart) && !t.fecha.isAfter(previousEnd))
                .map(t -> t.precio != null ? t.precio : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String earningsChange;
        if (earningsPrevious.compareTo(BigDecimal.ZERO) == 0) {
            earningsChange = earningsCurrent.compareTo(BigDecimal.ZERO) == 0
                    ? "0% " + changeSuffix
                    : "+100% " + changeSuffix;
        } else {
            BigDecimal percentChange = earningsCurrent.subtract(earningsPrevious)
                    .multiply(new BigDecimal("100"))
                    .divide(earningsPrevious, 1, RoundingMode.HALF_UP);
            if (percentChange.compareTo(BigDecimal.ZERO) > 0) {
                earningsChange = "+" + percentChange + "% " + changeSuffix;
            } else if (percentChange.compareTo(BigDecimal.ZERO) < 0) {
                earningsChange = percentChange + "% " + changeSuffix;
            } else {
                earningsChange = "0% " + changeSuffix;
            }
        }

        // 3. Pacientes activos (distintos pacientes atendidos) y nuevos (su primer turno no cancelado cae en el período)
        Set<Long> activePatientIds = noCancelados.stream()
                .filter(t -> !t.fecha.isBefore(currentStart) && !t.fecha.isAfter(currentEnd))
                .map(t -> t.pacienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int activePatients = activePatientIds.size();

        Map<Long, LocalDate> primeraFechaPorPaciente = turnos.primeraFechaPorPaciente(medico.id());
        long newPatientsInPeriod = primeraFechaPorPaciente.values().stream()
                .filter(d -> d != null && !d.isBefore(currentStart) && !d.isAfter(currentEnd))
                .count();

        String activePatientsChange = "+" + newPatientsInPeriod + " en el período";

        // 4. Inasistencias: turnos AUSENTE más todos los turnos cancelados
        int noShowsCurrent = (int) noCancelados.stream()
                .filter(t -> "AUSENTE".equals(t.asistencia)
                        && !t.fecha.isBefore(currentStart)
                        && !t.fecha.isAfter(currentEnd))
                .count()
                + (int) cancelados.stream()
                .filter(t -> !t.fecha.isBefore(currentStart) && !t.fecha.isAfter(currentEnd))
                .count();

        int noShowsPrevious = (int) noCancelados.stream()
                .filter(t -> "AUSENTE".equals(t.asistencia)
                        && !t.fecha.isBefore(previousStart)
                        && !t.fecha.isAfter(previousEnd))
                .count()
                + (int) cancelados.stream()
                .filter(t -> !t.fecha.isBefore(previousStart) && !t.fecha.isAfter(previousEnd))
                .count();

        String noShowsChange;
        int diffNoShows = noShowsCurrent - noShowsPrevious;
        if (diffNoShows > 0) {
            noShowsChange = "+" + diffNoShows + " " + changeSuffix;
        } else if (diffNoShows < 0) {
            noShowsChange = diffNoShows + " " + changeSuffix;
        } else {
            noShowsChange = flatMessage;
        }

        return new Dtos.DashboardStatsDto(
                sessionsCurrent,
                sessionsChange,
                earningsCurrent,
                earningsChange,
                activePatients,
                activePatientsChange,
                noShowsCurrent,
                noShowsChange
        );
    }
}
