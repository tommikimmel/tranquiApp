package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Pagos de Mercado Pago, uno por {@code transaction_id} (el id del pago en Mercado Pago). La restricción
 * única es lo que evita registrar dos veces el mismo pago cuando llegan el webhook y "verificar" a la vez,
 * o Mercado Pago repite el aviso.
 */
@Repository
public class Pagos {

    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String REEMBOLSADO = "REEMBOLSADO";

    public record Pago(Long id, String referencia, Long turnoId, Long solicitudId, Long profesionalId, Long pacienteId,
                       String transactionId, String estado, BigDecimal monto, LocalDateTime fechaPago) {}

    private static final RowMapper<Pago> FILA = (rs, i) -> new Pago(rs.getLong("id"), rs.getString("referencia"),
            rs.getObject("turno_id", Long.class), rs.getObject("solicitud_id", Long.class),
            rs.getObject("profesional_id", Long.class), rs.getObject("paciente_id", Long.class),
            rs.getString("transaction_id"), rs.getString("estado"), rs.getBigDecimal("monto"),
            rs.getTimestamp("fecha_pago").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public Pagos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra el pago si es la primera vez que se ve su {@code transaction_id}; devuelve su id, o vacío si
     * ya estaba registrado (aviso repetido).
     */
    public Optional<Long> registrar(Pago p, Long pagoMonolitoId) {
        return jdbc.query("INSERT INTO pago (referencia, turno_id, solicitud_id, profesional_id, paciente_id, transaction_id, "
                        + "estado, monto, fecha_pago, pago_monolito_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (transaction_id) DO NOTHING RETURNING id",
                (rs, i) -> rs.getLong(1), p.referencia(), p.turnoId(), p.solicitudId(), p.profesionalId(), p.pacienteId(),
                p.transactionId(), p.estado(), p.monto(), Timestamp.valueOf(p.fechaPago()), pagoMonolitoId)
                .stream().findFirst();
    }

    /** El pago aprobado de un turno (el que se reembolsa si se cancela). */
    public Optional<Pago> aprobadoDeTurno(Long turnoId) {
        return jdbc.query("SELECT * FROM pago WHERE turno_id = ? AND estado = ? ORDER BY id DESC", FILA, turnoId, APROBADO)
                .stream().findFirst();
    }

    public boolean hayAprobadoPara(String referencia) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM pago WHERE referencia = ? AND estado IN (?, ?)", Long.class,
                referencia, APROBADO, REEMBOLSADO);
        return n != null && n > 0;
    }

    public void cambiarEstado(Long id, String estado) {
        jdbc.update("UPDATE pago SET estado = ? WHERE id = ?", estado, id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM pago", Long.class);
        return n != null ? n : 0;
    }
}
