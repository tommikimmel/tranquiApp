package com.tranqui.profesionales.datos;

import com.tranqui.eventos.data.TarifasProfesionalData.Tarifa;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/** Los servicios de cada profesional ("Honorarios y servicios"), como {@code tarifa_medico} en el monolito. */
@Repository
public class Tarifas {

    private final JdbcTemplate jdbc;
    private final RowMapper<Tarifa> mapeo = (rs, i) -> new Tarifa(rs.getString("servicio_id"), rs.getString("label"),
            rs.getBigDecimal("precio"), rs.getBoolean("habilitado"), rs.getBoolean("requiere_obra_social"),
            rs.getString("obra_social"), rs.getBigDecimal("precio_online"), rs.getBigDecimal("precio_presencial"),
            rs.getBoolean("requiere_agenda"), rs.getBoolean("es_receta"));

    public Tarifas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Tarifa> deProfesional(Long profesionalId) {
        return jdbc.query("SELECT servicio_id, label, precio, habilitado, requiere_obra_social, obra_social, precio_online, "
                + "precio_presencial, requiere_agenda, es_receta FROM tarifa WHERE profesional_id = ? ORDER BY id", mapeo, profesionalId);
    }

    /** Alta o actualización por servicio (una tarifa existente conserva su lugar en la lista). */
    @Transactional
    public void guardar(Long profesionalId, Tarifa t) {
        jdbc.update("INSERT INTO tarifa (profesional_id, servicio_id, label, precio, habilitado, requiere_obra_social, obra_social, "
                        + "precio_online, precio_presencial, requiere_agenda, es_receta) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (profesional_id, servicio_id) DO UPDATE SET label = EXCLUDED.label, precio = EXCLUDED.precio, "
                        + "habilitado = EXCLUDED.habilitado, requiere_obra_social = EXCLUDED.requiere_obra_social, "
                        + "obra_social = EXCLUDED.obra_social, precio_online = EXCLUDED.precio_online, "
                        + "precio_presencial = EXCLUDED.precio_presencial, requiere_agenda = EXCLUDED.requiere_agenda, "
                        + "es_receta = EXCLUDED.es_receta",
                profesionalId, t.servicioId(), t.label(), t.precio(), t.habilitado(), t.requiereObraSocial(), t.obraSocial(),
                t.precioOnline(), t.precioPresencial(), t.requiereAgenda(), t.esReceta());
    }

    /** Borra las que no están en la lista (el profesional las eliminó). */
    @Transactional
    public void eliminarSalvo(Long profesionalId, Collection<String> servicioIds) {
        if (servicioIds.isEmpty()) {
            jdbc.update("DELETE FROM tarifa WHERE profesional_id = ?", profesionalId);
            return;
        }
        String marcas = String.join(",", servicioIds.stream().map(s -> "?").toList());
        Object[] parametros = new Object[servicioIds.size() + 1];
        parametros[0] = profesionalId;
        int i = 1;
        for (String s : servicioIds) {
            parametros[i++] = s;
        }
        jdbc.update("DELETE FROM tarifa WHERE profesional_id = ? AND servicio_id NOT IN (" + marcas + ")", parametros);
    }
}
