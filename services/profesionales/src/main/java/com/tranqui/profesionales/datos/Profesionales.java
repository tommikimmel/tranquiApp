package com.tranqui.profesionales.datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.data.PerfilProfesionalData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Los profesionales. Los datos del monolito (identidad, verificación, suscripción y Mercado Pago) se
 * guardan tal cual llegan; el perfil, la disponibilidad y las tarifas son de este servicio.
 */
@Repository
public class Profesionales {

    /** Lo que el monolito sabe de un profesional; {@code fotoUrl} null es "la foto no vino" (ver {@code fotoHash}). */
    public record DatosMonolito(Long id, int version, String email, String rol, String profession,
                                String nombre, String apellido, String telefono, String sexo,
                                LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                                String fotoUrl, String fotoHash, Boolean verificadoAdmin, boolean mpVinculada,
                                String suscripcionEstado, LocalDateTime suscripcionPeriodoHasta, boolean puedeRecetar) {

        /** Los mismos datos con la foto que se acaba de guardar (el monolito no la devuelve). */
        public DatosMonolito conFoto(String foto) {
            return new DatosMonolito(id, version, email, rol, profession, nombre, apellido, telefono, sexo, fechaNacimiento,
                    cuil, tipoDocumento, numeroDocumento, foto, foto != null ? fotoHash : null, verificadoAdmin, mpVinculada,
                    suscripcionEstado, suscripcionPeriodoHasta, puedeRecetar);
        }
    }

    public record Profesional(Long id, String email, String rol, String profession,
                              String nombre, String apellido, String telefono, String sexo,
                              LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                              String fotoUrl, String fotoHash, Boolean verificadoAdmin, boolean mpVinculada,
                              String suscripcionEstado, LocalDateTime suscripcionPeriodoHasta, boolean puedeRecetar,
                              PerfilProfesionalData perfil, int version, int disponibilidadVersion, int tarifasVersion,
                              OffsetDateTime actualizadoEn) {}

    private static final String COLUMNAS = "id, email, rol, profession, nombre, apellido, telefono, sexo, fecha_nacimiento, "
            + "cuil, tipo_documento, numero_documento, foto_url, foto_hash, verificado_admin, mp_vinculada, suscripcion_estado, "
            + "suscripcion_periodo_hasta, puede_recetar, perfil, version, disponibilidad_version, tarifas_version, actualizado_en";

    // Sin la foto (puede pesar varios MB): para la resincronización del monolito, que no la usa.
    private static final String COLUMNAS_SIN_FOTO = COLUMNAS.replace("foto_url, ", "NULL AS foto_url, ");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = EventObjectMapperFactory.create();
    private final RowMapper<Profesional> mapeo = (rs, i) -> new Profesional(
            rs.getLong("id"), rs.getString("email"), rs.getString("rol"), rs.getString("profession"),
            rs.getString("nombre"), rs.getString("apellido"), rs.getString("telefono"), rs.getString("sexo"),
            rs.getObject("fecha_nacimiento", LocalDate.class), (Long) rs.getObject("cuil"), rs.getString("tipo_documento"),
            (Integer) rs.getObject("numero_documento"), rs.getString("foto_url"), rs.getString("foto_hash"),
            (Boolean) rs.getObject("verificado_admin"), rs.getBoolean("mp_vinculada"), rs.getString("suscripcion_estado"),
            rs.getObject("suscripcion_periodo_hasta", LocalDateTime.class), rs.getBoolean("puede_recetar"),
            leerPerfil(rs.getString("perfil")), rs.getInt("version"), rs.getInt("disponibilidad_version"),
            rs.getInt("tarifas_version"), rs.getObject("actualizado_en", OffsetDateTime.class));

    public Profesionales(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Profesional> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM profesional WHERE id = ?", mapeo, id).stream().findFirst();
    }

    public Optional<Profesional> porEmail(String email) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM profesional WHERE lower(email) = lower(?)", mapeo, email)
                .stream().findFirst();
    }

    /** Los que el monolito tiene como profesionales, con la foto (el buscador la muestra). */
    public List<Profesional> profesionales() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM profesional WHERE rol = 'PSIQUIATRA' ORDER BY id", mapeo);
    }

    /** Para la resincronización del monolito: todos ({@code desde} null) o los que cambiaron desde ahí. */
    public List<Profesional> cambiadosDesde(OffsetDateTime desde) {
        if (desde == null) {
            return jdbc.query("SELECT " + COLUMNAS_SIN_FOTO + " FROM profesional ORDER BY id", mapeo);
        }
        return jdbc.query("SELECT " + COLUMNAS_SIN_FOTO + " FROM profesional WHERE actualizado_en >= ? ORDER BY id",
                mapeo, desde);
    }

    public List<Long> ids() {
        return jdbc.queryForList("SELECT id FROM profesional", Long.class);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM profesional", Long.class);
        return n != null ? n : 0;
    }

    /** Alta con el perfil que tenía el monolito (carga inicial o un profesional nuevo). */
    @Transactional
    public void alta(DatosMonolito d, PerfilProfesionalData perfil) {
        jdbc.update("INSERT INTO profesional (id, email, rol, profession, nombre, apellido, telefono, sexo, fecha_nacimiento, "
                        + "cuil, tipo_documento, numero_documento, foto_url, foto_hash, verificado_admin, mp_vinculada, "
                        + "suscripcion_estado, suscripcion_periodo_hasta, puede_recetar, perfil, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) ON CONFLICT (id) DO NOTHING",
                d.id(), d.email(), d.rol(), d.profession(), d.nombre(), d.apellido(), d.telefono(), d.sexo(),
                fecha(d.fechaNacimiento()), d.cuil(), d.tipoDocumento(), d.numeroDocumento(), d.fotoUrl(), d.fotoHash(),
                d.verificadoAdmin(), d.mpVinculada(), d.suscripcionEstado(), momento(d.suscripcionPeriodoHasta()),
                d.puedeRecetar(), escribirPerfil(perfil), d.version());
    }

    /**
     * Pisa los datos del monolito. La foto solo si vino ({@code fotoUrl} no nulo o el hash dice que ya no hay
     * foto). La versión nunca baja: queda por encima de la del usuario en el monolito.
     */
    @Transactional
    public boolean guardarDatosMonolito(DatosMonolito d) {
        boolean conFoto = d.fotoUrl() != null || d.fotoHash() == null;
        int n = jdbc.update("UPDATE profesional SET email = ?, rol = ?, profession = ?, nombre = ?, apellido = ?, "
                        + "telefono = ?, sexo = ?, fecha_nacimiento = ?, cuil = ?, tipo_documento = ?, numero_documento = ?, "
                        + (conFoto ? "foto_url = ?, foto_hash = ?, " : "")
                        + "verificado_admin = ?, mp_vinculada = ?, suscripcion_estado = ?, suscripcion_periodo_hasta = ?, "
                        + "puede_recetar = ?, version = GREATEST(version, ?) WHERE id = ?",
                conFoto
                        ? new Object[]{d.email(), d.rol(), d.profession(), d.nombre(), d.apellido(), d.telefono(), d.sexo(),
                        fecha(d.fechaNacimiento()), d.cuil(), d.tipoDocumento(), d.numeroDocumento(), d.fotoUrl(), d.fotoHash(),
                        d.verificadoAdmin(), d.mpVinculada(), d.suscripcionEstado(), momento(d.suscripcionPeriodoHasta()),
                        d.puedeRecetar(), d.version(), d.id()}
                        : new Object[]{d.email(), d.rol(), d.profession(), d.nombre(), d.apellido(), d.telefono(), d.sexo(),
                        fecha(d.fechaNacimiento()), d.cuil(), d.tipoDocumento(), d.numeroDocumento(),
                        d.verificadoAdmin(), d.mpVinculada(), d.suscripcionEstado(), momento(d.suscripcionPeriodoHasta()),
                        d.puedeRecetar(), d.version(), d.id()});
        return n > 0;
    }

    /**
     * Fase 9, con la fuente de identidad: pisa solo lo que es de identidad (y la verificación del administrador). Mercado
     * Pago y la suscripción no se tocan: identidad los manda en null y llegan de pagos ({@link #guardarMercadoPago},
     * {@link #guardarSuscripcion}). La foto, como en {@link #guardarDatosMonolito}.
     */
    @Transactional
    public boolean guardarDatosIdentidad(DatosMonolito d) {
        boolean conFoto = d.fotoUrl() != null || d.fotoHash() == null;
        int n = jdbc.update("UPDATE profesional SET email = ?, rol = ?, profession = ?, nombre = ?, apellido = ?, "
                        + "telefono = ?, sexo = ?, fecha_nacimiento = ?, cuil = ?, tipo_documento = ?, numero_documento = ?, "
                        + (conFoto ? "foto_url = ?, foto_hash = ?, " : "")
                        + "verificado_admin = ?, version = GREATEST(version, ?) WHERE id = ?",
                conFoto
                        ? new Object[]{d.email(), d.rol(), d.profession(), d.nombre(), d.apellido(), d.telefono(), d.sexo(),
                        fecha(d.fechaNacimiento()), d.cuil(), d.tipoDocumento(), d.numeroDocumento(), d.fotoUrl(), d.fotoHash(),
                        d.verificadoAdmin(), d.version(), d.id()}
                        : new Object[]{d.email(), d.rol(), d.profession(), d.nombre(), d.apellido(), d.telefono(), d.sexo(),
                        fecha(d.fechaNacimiento()), d.cuil(), d.tipoDocumento(), d.numeroDocumento(),
                        d.verificadoAdmin(), d.version(), d.id()});
        return n > 0;
    }

    /**
     * Mercado Pago vinculado, de pagos (evento o resincronización). Solo si la versión que llega no es más vieja que
     * la guardada: el evento y la resincronización pueden llegar en cualquier orden.
     */
    @Transactional
    public boolean guardarMercadoPago(Long id, boolean vinculada, long version) {
        return jdbc.update("UPDATE profesional SET mp_vinculada = ?, mp_version = ? WHERE id = ? AND mp_version <= ?",
                vinculada, version, id, version) > 0;
    }

    /** El estado de la suscripción, de pagos (evento v2 o resincronización), con el mismo control de versión. */
    @Transactional
    public boolean guardarSuscripcion(Long id, String estado, LocalDateTime periodoHasta, boolean puedeRecetar, long version) {
        return jdbc.update("UPDATE profesional SET suscripcion_estado = ?, suscripcion_periodo_hasta = ?, puede_recetar = ?, "
                        + "suscripcion_version = ? WHERE id = ? AND suscripcion_version <= ?",
                estado, momento(periodoHasta), puedeRecetar, version, id, version) > 0;
    }

    /** Guarda el perfil y devuelve la versión nueva para {@code profesional.actualizado}. */
    @Transactional
    public int guardarPerfil(Long id, PerfilProfesionalData perfil) {
        return jdbc.queryForObject("UPDATE profesional SET perfil = ?::jsonb, version = version + 1, actualizado_en = now() "
                + "WHERE id = ? RETURNING version", Integer.class, escribirPerfil(perfil), id);
    }

    @Transactional
    public int subirVersionDisponibilidad(Long id) {
        return jdbc.queryForObject("UPDATE profesional SET disponibilidad_version = disponibilidad_version + 1, "
                + "actualizado_en = now() WHERE id = ? RETURNING disponibilidad_version", Integer.class, id);
    }

    @Transactional
    public int subirVersionTarifas(Long id) {
        return jdbc.queryForObject("UPDATE profesional SET tarifas_version = tarifas_version + 1, "
                + "actualizado_en = now() WHERE id = ? RETURNING tarifas_version", Integer.class, id);
    }

    @Transactional
    public void eliminar(Long id) {
        jdbc.update("DELETE FROM profesional WHERE id = ?", id);
    }

    private PerfilProfesionalData leerPerfil(String texto) {
        try {
            return json.readValue(texto, PerfilProfesionalData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Perfil ilegible en la base", e);
        }
    }

    private String escribirPerfil(PerfilProfesionalData perfil) {
        try {
            return json.writeValueAsString(perfil);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo guardar el perfil", e);
        }
    }

    private static Date fecha(LocalDate d) {
        return d != null ? Date.valueOf(d) : null;
    }

    private static Timestamp momento(LocalDateTime d) {
        return d != null ? Timestamp.valueOf(d) : null;
    }
}
