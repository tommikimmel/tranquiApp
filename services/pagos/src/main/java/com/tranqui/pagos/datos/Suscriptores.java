package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Copia de los suscriptores (datos fiscales, profesión, rol y versión en identidad) (Fase 8, S2a).
 * Mantiene la copia al día con los eventos y la resincronización periódica.
 */
@Repository
public class Suscriptores {

    public record Suscriptor(
            Long id,
            String email,
            String nombre,
            String apellido,
            String rol,
            String profession,
            Boolean verificadoAdmin,
            int version,
            String taxIdType,
            String taxId,
            String legalName,
            Integer ivaConditionId,
            String fiscalAddress,
            String cuit,
            Long cuil,
            String dni,
            Integer numeroDocumento,
            LocalDateTime actualizadoEn
    ) {

        public String taxIdEfectivo() {
            if (taxId != null && !taxId.isBlank()) return taxId;
            if (cuit != null && !cuit.isBlank()) return cuit;
            if (cuil != null) return String.valueOf(cuil);
            if (dni != null && !dni.isBlank()) return dni;
            if (numeroDocumento != null) return String.valueOf(numeroDocumento);
            return null;
        }

        public Integer condicionIvaEfectiva() {
            if (ivaConditionId != null) return ivaConditionId;
            return 6; // Default to Monotributo (6) for Argentina health professionals
        }

        public String nombreLegalEfectivo() {
            if (legalName != null && !legalName.isBlank()) return legalName;
            String full = ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
            return full.isEmpty() ? (email != null ? email : "-") : full;
        }
    }

    private static final String COLUMNAS = "id, email, nombre, apellido, rol, profession, verificado_admin, "
            + "version, tax_id_type, tax_id, legal_name, iva_condition_id, fiscal_address, cuit, cuil, "
            + "dni, numero_documento, actualizado_en";

    private final JdbcTemplate jdbc;

    private final RowMapper<Suscriptor> mapeo = (rs, i) -> new Suscriptor(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("nombre"),
            rs.getString("apellido"),
            rs.getString("rol"),
            rs.getString("profession"),
            rs.getObject("verificado_admin") != null ? rs.getBoolean("verificado_admin") : null,
            rs.getInt("version"),
            rs.getString("tax_id_type"),
            rs.getString("tax_id"),
            rs.getString("legal_name"),
            rs.getObject("iva_condition_id") != null ? rs.getInt("iva_condition_id") : null,
            rs.getString("fiscal_address"),
            rs.getString("cuit"),
            rs.getObject("cuil") != null ? rs.getLong("cuil") : null,
            rs.getString("dni"),
            rs.getObject("numero_documento") != null ? rs.getInt("numero_documento") : null,
            rs.getTimestamp("actualizado_en") != null ? rs.getTimestamp("actualizado_en").toLocalDateTime() : null
    );

    public Suscriptores(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Suscriptor> porEmail(String email) {
        if (email == null) return Optional.empty();
        return jdbc.query("SELECT " + COLUMNAS + " FROM suscriptor WHERE lower(email) = lower(?)", mapeo, email)
                .stream().findFirst();
    }

    public Optional<Suscriptor> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM suscriptor WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    /**
     * Guarda el suscriptor si no existe, o actualiza solo si la versión que llega es mayor o igual
     * a la que ya está persistida (descarte de versiones viejas).
     */
    @Transactional
    public void guardar(Suscriptor s) {
        jdbc.update("""
                INSERT INTO suscriptor (id, email, nombre, apellido, rol, profession, verificado_admin,
                                        version, tax_id_type, tax_id, legal_name, iva_condition_id,
                                        fiscal_address, cuit, cuil, dni, numero_documento, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    nombre = EXCLUDED.nombre,
                    apellido = EXCLUDED.apellido,
                    rol = EXCLUDED.rol,
                    profession = EXCLUDED.profession,
                    verificado_admin = EXCLUDED.verificado_admin,
                    version = EXCLUDED.version,
                    tax_id_type = EXCLUDED.tax_id_type,
                    tax_id = EXCLUDED.tax_id,
                    legal_name = EXCLUDED.legal_name,
                    iva_condition_id = EXCLUDED.iva_condition_id,
                    fiscal_address = EXCLUDED.fiscal_address,
                    cuit = EXCLUDED.cuit,
                    cuil = EXCLUDED.cuil,
                    dni = EXCLUDED.dni,
                    numero_documento = EXCLUDED.numero_documento,
                    actualizado_en = now()
                WHERE EXCLUDED.version >= suscriptor.version
                """,
                s.id(), s.email(), s.nombre(), s.apellido(), s.rol(), s.profession(),
                s.verificadoAdmin(), s.version(), s.taxIdType(), s.taxId(), s.legalName(),
                s.ivaConditionId(), s.fiscalAddress(), s.cuit(), s.cuil(), s.dni(),
                s.numeroDocumento());
    }

    @Transactional
    public void eliminar(Long id) {
        jdbc.update("DELETE FROM suscriptor WHERE id = ?", id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM suscriptor", Long.class);
        return n != null ? n : 0;
    }

    public List<Suscriptor> todos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM suscriptor ORDER BY id", mapeo);
    }
}
