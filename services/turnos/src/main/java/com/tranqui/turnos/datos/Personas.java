package com.tranqui.turnos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copia de los usuarios (pacientes y profesionales) con los datos que muestran la agenda y "Mis turnos".
 * Es del monolito (identidad): se copia en la carga inicial, con "buscar o crear paciente" y con los
 * eventos de identidad. Sin datos de salud.
 */
@Repository
public class Personas {

    public record Persona(Long id, int version, String rol, String email, String nombre, String apellido, String telefono,
                          String dni, String direccion, String obraSocial, String numAfiliado, String sexo,
                          LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                          String datosOfuscado, Integer credencialCodEntidad, String credencialPan, String credencialPlan,
                          String credencialToken, boolean emailHabilitado, boolean mpVinculada) {}

    private static final String COLUMNAS = "id, version, rol, email, nombre, apellido, telefono, dni, direccion, obra_social, "
            + "num_afiliado, sexo, fecha_nacimiento, cuil, tipo_documento, numero_documento, datos_ofuscado, credencial_cod_entidad, "
            + "credencial_pan, credencial_plan, credencial_token, email_habilitado, mp_vinculada";

    private final JdbcTemplate jdbc;
    private final RowMapper<Persona> mapeo = (rs, i) -> new Persona(rs.getLong("id"), rs.getInt("version"), rs.getString("rol"),
            rs.getString("email"), rs.getString("nombre"), rs.getString("apellido"), rs.getString("telefono"), rs.getString("dni"),
            rs.getString("direccion"), rs.getString("obra_social"), rs.getString("num_afiliado"), rs.getString("sexo"),
            rs.getObject("fecha_nacimiento", LocalDate.class), (Long) rs.getObject("cuil"), rs.getString("tipo_documento"),
            (Integer) rs.getObject("numero_documento"), rs.getString("datos_ofuscado"), (Integer) rs.getObject("credencial_cod_entidad"),
            rs.getString("credencial_pan"), rs.getString("credencial_plan"), rs.getString("credencial_token"),
            rs.getBoolean("email_habilitado"), rs.getBoolean("mp_vinculada"));

    public Personas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Persona> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM persona WHERE id = ?", mapeo, id).stream().findFirst();
    }

    public Optional<Persona> porEmail(String email) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM persona WHERE lower(email) = lower(?)", mapeo, email).stream().findFirst();
    }

    public Map<Long, Persona> porIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String marcas = String.join(",", ids.stream().map(i -> "?").toList());
        return jdbc.query("SELECT " + COLUMNAS + " FROM persona WHERE id IN (" + marcas + ")", mapeo, ids.toArray()).stream()
                .collect(Collectors.toMap(Persona::id, Function.identity()));
    }

    /** Alta o actualización con lo que manda el monolito (no toca si tiene Mercado Pago vinculado). */
    @Transactional
    public void guardar(Persona p) {
        Object[] datos = {p.version(), p.rol(), p.email(), p.nombre(), p.apellido(), p.telefono(), p.dni(), p.direccion(),
                p.obraSocial(), p.numAfiliado(), p.sexo(), p.fechaNacimiento() != null ? Date.valueOf(p.fechaNacimiento()) : null,
                p.cuil(), p.tipoDocumento(), p.numeroDocumento(), p.datosOfuscado(), p.credencialCodEntidad(), p.credencialPan(),
                p.credencialPlan(), p.credencialToken(), p.emailHabilitado()};
        int n = jdbc.update("UPDATE persona SET version = ?, rol = ?, email = ?, nombre = ?, apellido = ?, telefono = ?, dni = ?, "
                + "direccion = ?, obra_social = ?, num_afiliado = ?, sexo = ?, fecha_nacimiento = ?, cuil = ?, tipo_documento = ?, "
                + "numero_documento = ?, datos_ofuscado = ?, credencial_cod_entidad = ?, credencial_pan = ?, credencial_plan = ?, "
                + "credencial_token = ?, email_habilitado = ? WHERE id = ?", concatenar(datos, p.id()));
        if (n == 0) {
            jdbc.update("INSERT INTO persona (version, rol, email, nombre, apellido, telefono, dni, direccion, obra_social, num_afiliado, "
                    + "sexo, fecha_nacimiento, cuil, tipo_documento, numero_documento, datos_ofuscado, credencial_cod_entidad, "
                    + "credencial_pan, credencial_plan, credencial_token, email_habilitado, mp_vinculada, id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    concatenar(datos, p.mpVinculada(), p.id()));
        }
    }

    @Transactional
    public void mercadoPago(Long profesionalId, boolean vinculada) {
        jdbc.update("UPDATE persona SET mp_vinculada = ? WHERE id = ?", vinculada, profesionalId);
    }

    /** El nombre y apellido que manda profesionales en profesional.actualizado (los publica igual el monolito). */
    @Transactional
    public void nombre(Long id, String nombre, String apellido) {
        jdbc.update("UPDATE persona SET nombre = COALESCE(?, nombre), apellido = COALESCE(?, apellido) WHERE id = ?", nombre, apellido, id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM persona", Long.class);
        return n != null ? n : 0;
    }

    public List<Long> ids() {
        return jdbc.queryForList("SELECT id FROM persona", Long.class);
    }

    private static Object[] concatenar(Object[] a, Object... b) {
        Object[] r = new Object[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
