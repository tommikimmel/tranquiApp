package com.tranqui.identidad.datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.data.PerfilProfesionalData;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Acceso JDBC a la tabla {@code perfil_registro} (Fase 9, I2b).
 * Almacena el perfil profesional que llega durante el registro en formato JSONB
 * ({@link PerfilProfesionalData} de {@code libs/eventos}), desacoplándolo de identidad.
 */
@Repository
public class PerfilesRegistro {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    public PerfilesRegistro(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void guardar(Long usuarioId, PerfilProfesionalData perfil) {
        if (usuarioId == null || perfil == null) {
            throw new IllegalArgumentException("usuarioId y perfil son obligatorios");
        }
        String json;
        try {
            json = mapper.writeValueAsString(perfil);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar PerfilProfesionalData", e);
        }
        jdbc.update("""
                INSERT INTO perfil_registro (usuario_id, perfil)
                VALUES (?, CAST(? AS jsonb))
                ON CONFLICT (usuario_id) DO UPDATE SET perfil = EXCLUDED.perfil
                """, usuarioId, json);
    }

    public Optional<PerfilProfesionalData> buscarPorUsuarioId(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        try {
            String json = jdbc.queryForObject(
                    "SELECT perfil FROM perfil_registro WHERE usuario_id = ?",
                    String.class,
                    usuarioId
            );
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(json, PerfilProfesionalData.class));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al deserializar PerfilProfesionalData para usuario " + usuarioId, e);
        }
    }
}
