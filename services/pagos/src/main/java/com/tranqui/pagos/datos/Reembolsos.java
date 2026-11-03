package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/** Reembolsos de turnos cancelados: uno por turno como máximo (REALIZADO o FALLIDO). */
@Repository
public class Reembolsos {

    public static final String REALIZADO = "REALIZADO";
    public static final String FALLIDO = "FALLIDO";

    private final JdbcTemplate jdbc;

    public Reembolsos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existe(Long turnoId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM reembolso WHERE turno_id = ?", Long.class, turnoId);
        return n != null && n > 0;
    }

    /** Devuelve el id del reembolso registrado, o vacío si el turno ya tenía uno. */
    public Optional<Long> registrar(Long pagoId, Long turnoId, String estado, BigDecimal monto, String motivo, String error) {
        return jdbc.query("INSERT INTO reembolso (pago_id, turno_id, estado, monto, motivo, error) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (turno_id) DO NOTHING RETURNING id",
                (rs, i) -> rs.getLong(1), pagoId, turnoId, estado, monto, motivo, error).stream().findFirst();
    }
}
