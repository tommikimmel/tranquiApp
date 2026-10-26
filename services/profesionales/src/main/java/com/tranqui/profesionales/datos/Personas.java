package com.tranqui.profesionales.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copia de los usuarios (pacientes y profesionales) con los datos que necesitan la ficha, la lista
 * de pacientes y la receta electrónica.
 * Es del monolito (identidad): se copia en la carga inicial y se refresca con los eventos de identidad.
 */
@Repository
public class Personas {

    public record Persona(
            Long id,
            int version,
            String rol,
            String email,
            String nombre,
            String apellido,
            String telefono,
            String dni,
            String direccion,
            String obraSocial,
            String numAfiliado,
            String sexo,
            LocalDate fechaNacimiento,
            Long cuil,
            String tipoDocumento,
            Integer numeroDocumento,
            String datosOfuscado,
            Integer credencialCodEntidad,
            String credencialPan,
            String credencialPlan,
            String credencialToken,
            boolean emailHabilitado,
            String domicilioCalle,
            String domicilioNumero,
            String domicilioPiso,
            String domicilioDpto,
            String domicilioCodigoPostal,
            String domicilioLocalidad,
            String domicilioProvincia,
            String domicilioPais
    ) {}

    private static final String COLUMNAS = "id, version, rol, email, nombre, apellido, telefono, dni, direccion, "
            + "obra_social, num_afiliado, sexo, fecha_nacimiento, cuil, tipo_documento, numero_documento, datos_ofuscado, "
            + "credencial_cod_entidad, credencial_pan, credencial_plan, credencial_token, email_habilitado, "
            + "domicilio_calle, domicilio_numero, domicilio_piso, domicilio_dpto, domicilio_codigo_postal, "
            + "domicilio_localidad, domicilio_provincia, domicilio_pais";

    private final JdbcTemplate jdbc;
    private final RowMapper<Persona> mapeo = (rs, i) -> new Persona(
            rs.getLong("id"),
            rs.getInt("version"),
            rs.getString("rol"),
            rs.getString("email"),
            rs.getString("nombre"),
            rs.getString("apellido"),
            rs.getString("telefono"),
            rs.getString("dni"),
            rs.getString("direccion"),
            rs.getString("obra_social"),
            rs.getString("num_afiliado"),
            rs.getString("sexo"),
            rs.getObject("fecha_nacimiento", LocalDate.class),
            (Long) rs.getObject("cuil"),
            rs.getString("tipo_documento"),
            (Integer) rs.getObject("numero_documento"),
            rs.getString("datos_ofuscado"),
            (Integer) rs.getObject("credencial_cod_entidad"),
            rs.getString("credencial_pan"),
            rs.getString("credencial_plan"),
            rs.getString("credencial_token"),
            rs.getBoolean("email_habilitado"),
            rs.getString("domicilio_calle"),
            rs.getString("domicilio_numero"),
            rs.getString("domicilio_piso"),
            rs.getString("domicilio_dpto"),
            rs.getString("domicilio_codigo_postal"),
            rs.getString("domicilio_localidad"),
            rs.getString("domicilio_provincia"),
            rs.getString("domicilio_pais")
    );

    public Personas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Persona> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM persona WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public Map<Long, Persona> porIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        String marcas = String.join(",", ids.stream().map(i -> "?").toList());
        return jdbc.query("SELECT " + COLUMNAS + " FROM persona WHERE id IN (" + marcas + ")", mapeo, ids.toArray())
                .stream()
                .collect(Collectors.toMap(Persona::id, Function.identity()));
    }

    /** Alta o actualización con lo que manda el monolito. */
    @Transactional
    public void guardar(Persona p) {
        String sql = "INSERT INTO persona (" + COLUMNAS + ") VALUES ("
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "version = EXCLUDED.version, rol = EXCLUDED.rol, email = EXCLUDED.email, "
                + "nombre = EXCLUDED.nombre, apellido = EXCLUDED.apellido, telefono = EXCLUDED.telefono, "
                + "dni = EXCLUDED.dni, direccion = EXCLUDED.direccion, obra_social = EXCLUDED.obra_social, "
                + "num_afiliado = EXCLUDED.num_afiliado, sexo = EXCLUDED.sexo, fecha_nacimiento = EXCLUDED.fecha_nacimiento, "
                + "cuil = EXCLUDED.cuil, tipo_documento = EXCLUDED.tipo_documento, numero_documento = EXCLUDED.numero_documento, "
                + "datos_ofuscado = EXCLUDED.datos_ofuscado, credencial_cod_entidad = EXCLUDED.credencial_cod_entidad, "
                + "credencial_pan = EXCLUDED.credencial_pan, credencial_plan = EXCLUDED.credencial_plan, "
                + "credencial_token = EXCLUDED.credencial_token, email_habilitado = EXCLUDED.email_habilitado, "
                + "domicilio_calle = EXCLUDED.domicilio_calle, domicilio_numero = EXCLUDED.domicilio_numero, "
                + "domicilio_piso = EXCLUDED.domicilio_piso, domicilio_dpto = EXCLUDED.domicilio_dpto, "
                + "domicilio_codigo_postal = EXCLUDED.domicilio_codigo_postal, "
                + "domicilio_localidad = EXCLUDED.domicilio_localidad, domicilio_provincia = EXCLUDED.domicilio_provincia, "
                + "domicilio_pais = EXCLUDED.domicilio_pais";

        jdbc.update(sql,
                p.id(), p.version(), p.rol(), p.email(), p.nombre(), p.apellido(), p.telefono(), p.dni(),
                p.direccion(), p.obraSocial(), p.numAfiliado(), p.sexo(),
                p.fechaNacimiento() != null ? Date.valueOf(p.fechaNacimiento()) : null,
                p.cuil(), p.tipoDocumento(), p.numeroDocumento(), p.datosOfuscado(),
                p.credencialCodEntidad(), p.credencialPan(), p.credencialPlan(), p.credencialToken(),
                p.emailHabilitado(), p.domicilioCalle(), p.domicilioNumero(), p.domicilioPiso(),
                p.domicilioDpto(), p.domicilioCodigoPostal(), p.domicilioLocalidad(), p.domicilioProvincia(),
                p.domicilioPais());
    }

    @Transactional
    public void eliminar(Long id) {
        jdbc.update("DELETE FROM persona WHERE id = ?", id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM persona", Long.class);
        return n != null ? n : 0;
    }
}
