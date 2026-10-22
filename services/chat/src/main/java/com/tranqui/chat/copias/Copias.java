package com.tranqui.chat.copias;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Copias locales que necesita el chat (sección 6.6), alimentadas por eventos y por la carga inicial:
 * los participantes (nombre, mail y rol, para los canales) y los turnos confirmados de hoy en
 * adelante (para la prioridad clínica de los canales del profesional).
 */
@Service
public class Copias {

    /** Un usuario de la copia local. */
    public record Participante(Long id, String email, String nombre, String rol) {}

    private final JdbcTemplate jdbc;

    public Copias(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ participantes

    /** Alta o actualización; los campos nulos no pisan lo que había (productores viejos). */
    @Transactional
    public void guardarParticipante(Long id, String email, String nombre, String apellido, String rol) {
        int actualizados = jdbc.update("UPDATE participante SET email = COALESCE(?, email), nombre = COALESCE(?, nombre), "
                + "apellido = COALESCE(?, apellido), rol = COALESCE(?, rol), actualizado_en = now() WHERE id = ?",
                email, nombre, apellido, rol, id);
        if (actualizados == 0 && email != null) {
            jdbc.update("INSERT INTO participante (id, email, nombre, apellido, rol) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (id) DO NOTHING", id, email, nombre, apellido, rol);
        }
    }

    @Transactional
    public void eliminarParticipante(Long id) {
        jdbc.update("DELETE FROM participante WHERE id = ?", id);
    }

    public Optional<Participante> participantePorEmail(String email) {
        List<Participante> lista = jdbc.query("SELECT id, email, nombre, rol FROM participante WHERE lower(email) = lower(?)",
                (rs, i) -> new Participante(rs.getLong("id"), rs.getString("email"), rs.getString("nombre"), rs.getString("rol")),
                email);
        return lista.stream().findFirst();
    }

    public Optional<Participante> participante(Long id) {
        List<Participante> lista = jdbc.query("SELECT id, email, nombre, rol FROM participante WHERE id = ?",
                (rs, i) -> new Participante(rs.getLong("id"), rs.getString("email"), rs.getString("nombre"), rs.getString("rol")),
                id);
        return lista.stream().findFirst();
    }

    public long cantidadParticipantes() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM participante", Long.class);
        return n != null ? n : 0;
    }

    // ------------------------------------------------------------------ turnos confirmados

    @Transactional
    public void guardarTurnoConfirmado(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha, String horaInicio) {
        jdbc.update("INSERT INTO turno_confirmado (turno_id, paciente_id, profesional_id, fecha, hora_inicio) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (turno_id) DO UPDATE SET fecha = EXCLUDED.fecha, "
                        + "hora_inicio = EXCLUDED.hora_inicio",
                turnoId, pacienteId, profesionalId, fecha, hora(horaInicio));
    }

    /** Reprogramación: solo si el turno estaba confirmado (si no, no está en la copia). */
    @Transactional
    public void moverTurno(Long turnoId, LocalDate fecha, String horaInicio) {
        jdbc.update("UPDATE turno_confirmado SET fecha = ?, hora_inicio = ? WHERE turno_id = ?", fecha, hora(horaInicio), turnoId);
    }

    @Transactional
    public void quitarTurno(Long turnoId) {
        jdbc.update("DELETE FROM turno_confirmado WHERE turno_id = ?", turnoId);
    }

    /** Los turnos que ya pasaron no cambian la prioridad: se borran una vez por día. */
    @Transactional
    public int borrarTurnosPasados() {
        return jdbc.update("DELETE FROM turno_confirmado WHERE fecha < ?", LocalDate.now());
    }

    private static LocalTime hora(String hora) {
        return hora == null || hora.isBlank() ? null : LocalTime.parse(hora);
    }
}
