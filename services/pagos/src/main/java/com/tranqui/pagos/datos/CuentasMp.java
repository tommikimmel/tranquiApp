package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cuentas de Mercado Pago de los profesionales (antes, columnas mp_* de usuario en el monolito). Que exista
 * la fila es estar vinculado; desvincular la borra. Los tokens se guardan cifrados (ver Cifrado).
 */
@Repository
public class CuentasMp {

    public record Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                         LocalDateTime tokenVenceEn, String mpUserId) {}

    private static final RowMapper<Cuenta> FILA = (rs, i) -> new Cuenta(rs.getLong("profesional_id"),
            rs.getString("access_token_cifrado"), rs.getString("refresh_token_cifrado"),
            rs.getTimestamp("token_vence_en") != null ? rs.getTimestamp("token_vence_en").toLocalDateTime() : null,
            rs.getString("mp_user_id"));

    private final JdbcTemplate jdbc;

    public CuentasMp(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> buscar(Long profesionalId) {
        return jdbc.query("SELECT * FROM cuenta_mp WHERE profesional_id = ?", FILA, profesionalId).stream().findFirst();
    }

    /** El webhook de un pago trae el {@code user_id} del vendedor; si no es un profesional, es la plataforma. */
    public Optional<Cuenta> porMpUserId(String mpUserId) {
        return jdbc.query("SELECT * FROM cuenta_mp WHERE mp_user_id = ? ORDER BY actualizado_en DESC", FILA, mpUserId)
                .stream().findFirst();
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM cuenta_mp", Long.class);
        return n != null ? n : 0;
    }

    /**
     * Alta o reemplazo (conexión, renovación, carga inicial). Un refresh token nulo no pisa el guardado y un
     * mp_user_id nulo tampoco: Mercado Pago no siempre los manda al renovar.
     */
    public void guardar(Cuenta c) {
        jdbc.update("INSERT INTO cuenta_mp (profesional_id, access_token_cifrado, refresh_token_cifrado, token_vence_en, mp_user_id) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (profesional_id) DO UPDATE SET "
                        + "access_token_cifrado = EXCLUDED.access_token_cifrado, "
                        + "refresh_token_cifrado = COALESCE(EXCLUDED.refresh_token_cifrado, cuenta_mp.refresh_token_cifrado), "
                        + "token_vence_en = EXCLUDED.token_vence_en, "
                        + "mp_user_id = COALESCE(EXCLUDED.mp_user_id, cuenta_mp.mp_user_id), actualizado_en = now()",
                c.profesionalId(), c.accessTokenCifrado(), c.refreshTokenCifrado(),
                c.tokenVenceEn() != null ? Timestamp.valueOf(c.tokenVenceEn()) : null, c.mpUserId());
    }

    public boolean borrar(Long profesionalId) {
        return jdbc.update("DELETE FROM cuenta_mp WHERE profesional_id = ?", profesionalId) > 0;
    }
}
