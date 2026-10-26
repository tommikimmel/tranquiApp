package com.tranqui.calendario.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Copia mínima de los usuarios (sección 6.6), alimentada por los eventos de identidad y por la carga
 * inicial: el mail identifica al profesional autenticado y el nombre del paciente va en el evento.
 */
@Repository
public class Participantes {

    public record Participante(Long id, String email, String nombre, String rol) {}

    private final JdbcTemplate jdbc;

    public Participantes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Alta o actualización; los campos nulos no pisan lo que había (productores viejos). */
    @Transactional
    public void guardar(Long id, String email, String nombre, String apellido, String rol) {
        int actualizados = jdbc.update("UPDATE participante SET email = COALESCE(?, email), nombre = COALESCE(?, nombre), "
                + "apellido = COALESCE(?, apellido), rol = COALESCE(?, rol), actualizado_en = now() WHERE id = ?",
                email, nombre, apellido, rol, id);
        if (actualizados == 0 && email != null) {
            jdbc.update("INSERT INTO participante (id, email, nombre, apellido, rol) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (id) DO NOTHING", id, email, nombre, apellido, rol);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        jdbc.update("DELETE FROM participante WHERE id = ?", id);
    }

    public Optional<Participante> porEmail(String email) {
        return primero(jdbc.query("SELECT id, email, nombre, rol FROM participante WHERE lower(email) = lower(?)",
                (rs, i) -> new Participante(rs.getLong("id"), rs.getString("email"), rs.getString("nombre"), rs.getString("rol")),
                email));
    }

    public Optional<Participante> porId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return primero(jdbc.query("SELECT id, email, nombre, rol FROM participante WHERE id = ?",
                (rs, i) -> new Participante(rs.getLong("id"), rs.getString("email"), rs.getString("nombre"), rs.getString("rol")),
                id));
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM participante", Long.class);
        return n != null ? n : 0;
    }

    private static <T> Optional<T> primero(List<T> lista) {
        return lista.stream().findFirst();
    }
}
