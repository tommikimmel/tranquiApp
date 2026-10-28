package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cuentas de Mercado Pago de los profesionales (antes, columnas mp_* de usuario en el monolito).
 * La versión persistida y el estado vinculada permiten la resincronización de profesionales (I3c).
 * Los tokens se guardan cifrados (ver Cifrado).
 */
@Repository
public class CuentasMp {

    public record Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                         LocalDateTime tokenVenceEn, String mpUserId, boolean vinculada, long version) {
        public Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                      LocalDateTime tokenVenceEn, String mpUserId) {
            this(profesionalId, accessTokenCifrado, refreshTokenCifrado, tokenVenceEn, mpUserId, true, 1L);
        }
    }

    public record CuentaMpResumenDto(Long profesionalId, boolean vinculada, long version) {}

    private static final RowMapper<Cuenta> FILA = (rs, i) -> new Cuenta(rs.getLong("profesional_id"),
            rs.getString("access_token_cifrado"), rs.getString("refresh_token_cifrado"),
            rs.getTimestamp("token_vence_en") != null ? rs.getTimestamp("token_vence_en").toLocalDateTime() : null,
            rs.getString("mp_user_id"),
            rs.getBoolean("vinculada"),
            rs.getLong("version"));

    private final JdbcTemplate jdbc;

    public CuentasMp(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> buscar(Long profesionalId) {
        return jdbc.query("SELECT * FROM cuenta_mp WHERE profesional_id = ? AND vinculada = true", FILA, profesionalId).stream().findFirst();
    }

    public Optional<Cuenta> buscarCualquiera(Long profesionalId) {
        return jdbc.query("SELECT * FROM cuenta_mp WHERE profesional_id = ?", FILA, profesionalId).stream().findFirst();
    }

    /** El webhook de un pago trae el {@code user_id} del vendedor; si no es un profesional, es la plataforma. */
    public Optional<Cuenta> porMpUserId(String mpUserId) {
        return jdbc.query("SELECT * FROM cuenta_mp WHERE mp_user_id = ? AND vinculada = true ORDER BY actualizado_en DESC", FILA, mpUserId)
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
        jdbc.update("INSERT INTO cuenta_mp (profesional_id, access_token_cifrado, refresh_token_cifrado, token_vence_en, mp_user_id, vinculada, version, actualizado_en) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, now()) ON CONFLICT (profesional_id) DO UPDATE SET "
                        + "access_token_cifrado = EXCLUDED.access_token_cifrado, "
                        + "refresh_token_cifrado = COALESCE(EXCLUDED.refresh_token_cifrado, cuenta_mp.refresh_token_cifrado), "
                        + "token_vence_en = EXCLUDED.token_vence_en, "
                        + "mp_user_id = COALESCE(EXCLUDED.mp_user_id, cuenta_mp.mp_user_id), "
                        + "vinculada = EXCLUDED.vinculada, "
                        + "version = EXCLUDED.version, actualizado_en = now()",
                c.profesionalId(), c.accessTokenCifrado(), c.refreshTokenCifrado(),
                c.tokenVenceEn() != null ? Timestamp.valueOf(c.tokenVenceEn()) : null, c.mpUserId(),
                c.vinculada(), c.version());
    }

    public void desvincular(Long profesionalId, long version) {
        int actualizados = jdbc.update("UPDATE cuenta_mp SET vinculada = false, version = ?, "
                + "access_token_cifrado = null, refresh_token_cifrado = null, token_vence_en = null, mp_user_id = null, actualizado_en = now() "
                + "WHERE profesional_id = ?", version, profesionalId);
        if (actualizados == 0) {
            jdbc.update("INSERT INTO cuenta_mp (profesional_id, vinculada, version, access_token_cifrado, actualizado_en) "
                    + "VALUES (?, false, ?, null, now())", profesionalId, version);
        }
    }

    public List<CuentaMpResumenDto> paginadas(int pagina, int tamanio) {
        int offset = pagina * tamanio;
        return jdbc.query("SELECT profesional_id, vinculada, version FROM cuenta_mp ORDER BY profesional_id ASC LIMIT ? OFFSET ?",
                (rs, i) -> new CuentaMpResumenDto(rs.getLong("profesional_id"), rs.getBoolean("vinculada"), rs.getLong("version")),
                tamanio, offset);
    }

    public boolean borrar(Long profesionalId) {
        return jdbc.update("DELETE FROM cuenta_mp WHERE profesional_id = ?", profesionalId) > 0;
    }
}
