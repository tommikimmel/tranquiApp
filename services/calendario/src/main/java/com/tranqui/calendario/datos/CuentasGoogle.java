package com.tranqui.calendario.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cuentas de Google conectadas (antes, columnas google_* de usuario en el monolito). Que exista la
 * fila es estar conectado; desvincular la borra. Los tokens se guardan cifrados (ver Cifrado).
 */
@Repository
public class CuentasGoogle {

    public record Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                         LocalDateTime tokenVenceEn, String calendarId, String syncToken,
                         String watchChannelId, String watchResourceId, LocalDateTime watchVenceEn,
                         boolean watchActivo) {

        /** Siempre "primary" hoy, como en el monolito. */
        public String calendario() {
            return calendarId != null ? calendarId : "primary";
        }
    }

    private static final RowMapper<Cuenta> FILA = (rs, i) -> new Cuenta(
            rs.getLong("profesional_id"), rs.getString("access_token_cifrado"), rs.getString("refresh_token_cifrado"),
            fecha(rs.getTimestamp("token_vence_en")), rs.getString("calendar_id"), rs.getString("sync_token"),
            rs.getString("watch_channel_id"), rs.getString("watch_resource_id"), fecha(rs.getTimestamp("watch_vence_en")),
            rs.getBoolean("watch_activo"));

    private final JdbcTemplate jdbc;

    public CuentasGoogle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> buscar(Long profesionalId) {
        return jdbc.query("SELECT * FROM cuenta_google WHERE profesional_id = ?", FILA, profesionalId).stream().findFirst();
    }

    public List<Cuenta> todas() {
        return jdbc.query("SELECT * FROM cuenta_google ORDER BY profesional_id", FILA);
    }

    public List<Cuenta> canalesPorVencer(LocalDateTime limite) {
        return jdbc.query("SELECT * FROM cuenta_google WHERE watch_activo AND watch_vence_en < ?", FILA, Timestamp.valueOf(limite));
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM cuenta_google", Long.class);
        return n != null ? n : 0;
    }

    /** Alta o reemplazo completo (carga inicial y conexión simulada). */
    @Transactional
    public void guardar(Cuenta c) {
        jdbc.update("INSERT INTO cuenta_google (profesional_id, access_token_cifrado, refresh_token_cifrado, token_vence_en, "
                        + "calendar_id, sync_token, watch_channel_id, watch_resource_id, watch_vence_en, watch_activo) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (profesional_id) DO UPDATE SET "
                        + "access_token_cifrado = EXCLUDED.access_token_cifrado, refresh_token_cifrado = EXCLUDED.refresh_token_cifrado, "
                        + "token_vence_en = EXCLUDED.token_vence_en, calendar_id = EXCLUDED.calendar_id, sync_token = EXCLUDED.sync_token, "
                        + "watch_channel_id = EXCLUDED.watch_channel_id, watch_resource_id = EXCLUDED.watch_resource_id, "
                        + "watch_vence_en = EXCLUDED.watch_vence_en, watch_activo = EXCLUDED.watch_activo, actualizado_en = now()",
                c.profesionalId(), c.accessTokenCifrado(), c.refreshTokenCifrado(), ts(c.tokenVenceEn()), c.calendarId(),
                c.syncToken(), c.watchChannelId(), c.watchResourceId(), ts(c.watchVenceEn()), c.watchActivo());
    }

    /**
     * Tokens nuevos de Google (conexión o renovación). Un refresh token nulo no pisa el guardado: Google
     * solo lo manda en la primera autorización.
     */
    @Transactional
    public void guardarTokens(Long profesionalId, String accessCifrado, String refreshCifrado, LocalDateTime venceEn) {
        jdbc.update("INSERT INTO cuenta_google (profesional_id, access_token_cifrado, refresh_token_cifrado, token_vence_en) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (profesional_id) DO UPDATE SET "
                        + "access_token_cifrado = EXCLUDED.access_token_cifrado, "
                        + "refresh_token_cifrado = COALESCE(EXCLUDED.refresh_token_cifrado, cuenta_google.refresh_token_cifrado), "
                        + "token_vence_en = EXCLUDED.token_vence_en, actualizado_en = now()",
                profesionalId, accessCifrado, refreshCifrado, ts(venceEn));
    }

    @Transactional
    public void guardarSyncToken(Long profesionalId, String syncToken) {
        jdbc.update("UPDATE cuenta_google SET sync_token = ?, actualizado_en = now() WHERE profesional_id = ?", syncToken, profesionalId);
    }

    @Transactional
    public void guardarCanal(Long profesionalId, String channelId, String resourceId, LocalDateTime venceEn, boolean activo) {
        jdbc.update("UPDATE cuenta_google SET watch_channel_id = ?, watch_resource_id = ?, watch_vence_en = ?, watch_activo = ?, "
                + "actualizado_en = now() WHERE profesional_id = ?", channelId, resourceId, ts(venceEn), activo, profesionalId);
    }

    @Transactional
    public void borrar(Long profesionalId) {
        jdbc.update("DELETE FROM cuenta_google WHERE profesional_id = ?", profesionalId);
    }

    private static LocalDateTime fecha(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    private static Timestamp ts(LocalDateTime f) {
        return f != null ? Timestamp.valueOf(f) : null;
    }
}
