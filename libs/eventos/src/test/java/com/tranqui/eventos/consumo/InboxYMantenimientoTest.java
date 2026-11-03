package com.tranqui.eventos.consumo;

import com.tranqui.eventos.outbox.OutboxMantenimiento;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La inbox (duplicados y versiones) y las métricas y limpieza de la outbox, sin base: con el JdbcTemplate simulado. */
class InboxYMantenimientoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final InboxHelper inbox = new InboxHelper(jdbc);
    private final UUID id = UUID.randomUUID();

    @Test
    void procesar_nuevoODuplicado() {
        when(jdbc.update(contains("ON CONFLICT (event_id"), any(), any(), any())).thenReturn(1, 0);
        AtomicInteger corridas = new AtomicInteger();
        assertThat(inbox.procesar(id, "c", (Runnable) corridas::incrementAndGet)).isTrue();
        assertThat(inbox.procesar(id, "c", (Runnable) corridas::incrementAndGet)).isFalse();
        assertThat(corridas).hasValue(1);
    }

    @Test
    void procesar_conResultadoYSinAccion() {
        when(jdbc.update(contains("ON CONFLICT (event_id"), any(), any(), any())).thenReturn(1, 1, 0);
        assertThat(inbox.procesar(id, "c", () -> "hecho")).contains("hecho");
        assertThat(inbox.procesar(id, "c", (java.util.function.Supplier<String>) null)).isEmpty();
        assertThat(inbox.procesar(id, "c", () -> "otra vez")).isEqualTo(Optional.empty());
        when(jdbc.update(contains("ON CONFLICT (event_id"), any(), any(), any())).thenReturn(1);
        assertThat(inbox.procesar(id, "c", (Runnable) null)).isTrue();
    }

    @Test
    void procesar_validaLaEntrada() {
        assertThatThrownBy(() -> inbox.procesar(null, "c", () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inbox.procesar(id, " ", () -> "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inbox.procesar(id, null, () -> { })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinOnConflict_usaLaConsultaPreviaYToleraLaCarrera() {
        when(jdbc.update(contains("ON CONFLICT (event_id"), any(), any(), any()))
                .thenThrow(new BadSqlGrammarException("insert", "sql", new SQLException("sintaxis")));
        when(jdbc.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(), any())).thenReturn(1, 0, 0);
        when(jdbc.update(eq("INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?)"), any(), any(), any()))
                .thenReturn(1).thenThrow(new DataIntegrityViolationException("duplicada"));
        assertThat(inbox.registrar(id, "c")).isFalse();
        assertThat(inbox.registrar(id, "c")).isTrue();
        assertThat(inbox.registrar(id, "c")).isFalse();
    }

    @Test
    void avanzarVersion_soloSiEsMasNueva() {
        when(jdbc.queryForList(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(List.of(), List.of(3), List.of(3));
        assertThat(inbox.avanzarVersion("c", "turno", "1", 1)).isTrue();
        assertThat(inbox.avanzarVersion("c", "turno", "1", 3)).isFalse();
        assertThat(inbox.avanzarVersion("c", "turno", "1", 4)).isTrue();
        verify(jdbc, org.mockito.Mockito.times(2)).update(contains("versiones_aplicadas"), any(), any(), any(), any());
    }

    @Test
    void limpiarAnteriores() {
        when(jdbc.update(contains("DELETE FROM eventos_procesados"), eq(30))).thenReturn(7);
        assertThat(inbox.limpiarAnteriores(30)).isEqualTo(7);
    }

    @Test
    void mantenimiento_metricasYLimpieza() {
        SimpleMeterRegistry metricas = new SimpleMeterRegistry();
        when(jdbc.queryForObject(contains("count(*)"), eq(Long.class))).thenReturn(4L, null).thenThrow(new IllegalStateException("caída"));
        when(jdbc.queryForObject(contains("min(created_at)"), eq(Timestamp.class)))
                .thenReturn(Timestamp.from(Instant.now().minusSeconds(120)), (Timestamp) null).thenThrow(new IllegalStateException("caída"));
        new OutboxMantenimiento(jdbc, metricas);

        assertThat(metricas.get("tranqui.outbox.pendientes").gauge().value()).isEqualTo(4);
        assertThat(metricas.get("tranqui.outbox.pendientes").gauge().value()).isZero();
        assertThat(metricas.get("tranqui.outbox.pendientes").gauge().value()).isZero();
        assertThat(metricas.get("tranqui.outbox.antiguedad.segundos").gauge().value()).isGreaterThanOrEqualTo(119);
        assertThat(metricas.get("tranqui.outbox.antiguedad.segundos").gauge().value()).isZero();
        assertThat(metricas.get("tranqui.outbox.antiguedad.segundos").gauge().value()).isZero();

        when(jdbc.update(contains("DELETE FROM outbox"))).thenReturn(3, 0);
        OutboxMantenimiento sinMetricas = new OutboxMantenimiento(jdbc, null);
        sinMetricas.limpiar();
        sinMetricas.limpiar();
        verify(jdbc, org.mockito.Mockito.times(2)).update(contains("DELETE FROM outbox"));
        verify(jdbc, never()).update(contains("versiones_aplicadas"), any(), any(), any(), any());
    }
}
