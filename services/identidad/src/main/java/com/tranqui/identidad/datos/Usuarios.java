package com.tranqui.identidad.datos;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso JDBC a la tabla {@code usuario} (Fase 9, I2a), replicando las consultas
 * de {@code UsuarioRepository} con control de concurrencia optimista sobre {@code version}.
 */
@Repository
public class Usuarios {

    public static final String COLUMNAS = """
            id, version, email, password, rol, fecha_registro, email_verificado,
            codigo_verificacion, codigo_verificacion_expires_at, reset_password_code,
            reset_password_expires_at, must_change_password, terminos_aceptados_en,
            perfil_completo, cuenta_eliminada, anonimizado_en, notificaciones_email_habilitadas,
            recibir_novedades, nombre, apellido, telefono, sexo, fecha_nacimiento,
            tipo_documento, numero_documento, dni, cuil, direccion, foto_url,
            domicilio_calle, domicilio_numero, domicilio_piso, domicilio_dpto,
            domicilio_codigo_postal, domicilio_localidad, domicilio_provincia, domicilio_pais,
            obra_social, num_afiliado, datos_ofuscado, credencial_cod_entidad,
            credencial_pan, credencial_plan, credencial_token, verificado_admin,
            profession, license_type, license_number, license_jurisdiction,
            license_document_url, license_verified_at, license_verified_by, tax_id_type,
            tax_id, legal_name, iva_condition_id, fiscal_address
            """;

    private final JdbcTemplate jdbc;

    private final RowMapper<Usuario> mapeo = (rs, rowNum) -> new Usuario(
            rs.getLong("id"),
            rs.getInt("version"),
            rs.getString("email"),
            rs.getString("password"),
            rs.getString("rol"),
            rs.getTimestamp("fecha_registro") != null ? rs.getTimestamp("fecha_registro").toLocalDateTime() : null,
            rs.getObject("email_verificado", Boolean.class),
            rs.getString("codigo_verificacion"),
            rs.getTimestamp("codigo_verificacion_expires_at") != null ? rs.getTimestamp("codigo_verificacion_expires_at").toLocalDateTime() : null,
            rs.getString("reset_password_code"),
            rs.getTimestamp("reset_password_expires_at") != null ? rs.getTimestamp("reset_password_expires_at").toLocalDateTime() : null,
            rs.getBoolean("must_change_password"),
            rs.getTimestamp("terminos_aceptados_en") != null ? rs.getTimestamp("terminos_aceptados_en").toLocalDateTime() : null,
            rs.getObject("perfil_completo", Boolean.class),
            rs.getObject("cuenta_eliminada", Boolean.class),
            rs.getTimestamp("anonimizado_en") != null ? rs.getTimestamp("anonimizado_en").toLocalDateTime() : null,
            rs.getObject("notificaciones_email_habilitadas", Boolean.class),
            rs.getObject("recibir_novedades", Boolean.class),
            rs.getString("nombre"),
            rs.getString("apellido"),
            rs.getString("telefono"),
            rs.getString("sexo"),
            rs.getDate("fecha_nacimiento") != null ? rs.getDate("fecha_nacimiento").toLocalDate() : null,
            rs.getString("tipo_documento"),
            rs.getObject("numero_documento", Integer.class),
            rs.getString("dni"),
            rs.getObject("cuil", Long.class),
            rs.getString("direccion"),
            rs.getString("foto_url"),
            rs.getString("domicilio_calle"),
            rs.getString("domicilio_numero"),
            rs.getString("domicilio_piso"),
            rs.getString("domicilio_dpto"),
            rs.getString("domicilio_codigo_postal"),
            rs.getString("domicilio_localidad"),
            rs.getString("domicilio_provincia"),
            rs.getString("domicilio_pais"),
            rs.getString("obra_social"),
            rs.getString("num_afiliado"),
            rs.getString("datos_ofuscado"),
            rs.getObject("credencial_cod_entidad", Integer.class),
            rs.getString("credencial_pan"),
            rs.getString("credencial_plan"),
            rs.getString("credencial_token"),
            rs.getObject("verificado_admin", Boolean.class),
            rs.getString("profession"),
            rs.getString("license_type"),
            rs.getString("license_number"),
            rs.getString("license_jurisdiction"),
            rs.getString("license_document_url"),
            rs.getTimestamp("license_verified_at") != null ? rs.getTimestamp("license_verified_at").toLocalDateTime() : null,
            rs.getString("license_verified_by"),
            rs.getString("tax_id_type"),
            rs.getString("tax_id"),
            rs.getString("legal_name"),
            rs.getObject("iva_condition_id", Integer.class),
            rs.getString("fiscal_address")
    );

    public Usuarios(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Usuario> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<Usuario> r = jdbc.query("SELECT " + COLUMNAS + " FROM usuario WHERE id = ?", mapeo, id);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<Usuario> r = jdbc.query(
                "SELECT " + COLUMNAS + " FROM usuario WHERE LOWER(email) = LOWER(TRIM(?))",
                mapeo, email
        );
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public List<Usuario> todos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario ORDER BY id ASC", mapeo);
    }

    public List<Usuario> paginados(int pagina, int tamanio) {
        int offset = Math.max(0, pagina) * tamanio;
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario ORDER BY id ASC LIMIT ? OFFSET ?", mapeo, tamanio, offset);
    }

    public List<Usuario> buscarPorRol(String rol) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario WHERE rol = ? ORDER BY id ASC", mapeo, rol);
    }

    public List<Usuario> buscarPorRolPaginados(String rol, int pagina, int tamanio) {
        int offset = Math.max(0, pagina) * tamanio;
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario WHERE rol = ? ORDER BY id ASC LIMIT ? OFFSET ?",
                mapeo, rol, tamanio, offset);
    }

    public List<Usuario> buscarSuscriptores(int pagina, int tamanio) {
        int offset = Math.max(0, pagina) * tamanio;
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario WHERE rol = 'PSIQUIATRA' OR (profession IS NOT NULL AND TRIM(profession) != '') ORDER BY id ASC LIMIT ? OFFSET ?",
                mapeo, tamanio, offset);
    }

    /** Carga inicial: inserta con el id y versión explícitos del monolito. */
    @Transactional
    public void insertar(Usuario u) {
        jdbc.update("""
                INSERT INTO usuario (
                    id, version, email, password, rol, fecha_registro, email_verificado,
                    codigo_verificacion, codigo_verificacion_expires_at, reset_password_code,
                    reset_password_expires_at, must_change_password, terminos_aceptados_en,
                    perfil_completo, cuenta_eliminada, anonimizado_en, notificaciones_email_habilitadas,
                    recibir_novedades, nombre, apellido, telefono, sexo, fecha_nacimiento,
                    tipo_documento, numero_documento, dni, cuil, direccion, foto_url,
                    domicilio_calle, domicilio_numero, domicilio_piso, domicilio_dpto,
                    domicilio_codigo_postal, domicilio_localidad, domicilio_provincia, domicilio_pais,
                    obra_social, num_afiliado, datos_ofuscado, credencial_cod_entidad,
                    credencial_pan, credencial_plan, credencial_token, verificado_admin,
                    profession, license_type, license_number, license_jurisdiction,
                    license_document_url, license_verified_at, license_verified_by, tax_id_type,
                    tax_id, legal_name, iva_condition_id, fiscal_address
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?
                )
                """,
                u.id(),
                u.version() != null ? u.version() : 0,
                u.emailNormalizado(),
                u.password(),
                u.rol(),
                u.fechaRegistro() != null ? Timestamp.valueOf(u.fechaRegistro()) : Timestamp.valueOf(LocalDateTime.now()),
                u.emailVerificado() != null ? u.emailVerificado() : false,
                u.codigoVerificacion(),
                u.codigoVerificacionExpiresAt() != null ? Timestamp.valueOf(u.codigoVerificacionExpiresAt()) : null,
                u.resetPasswordCode(),
                u.resetPasswordExpiresAt() != null ? Timestamp.valueOf(u.resetPasswordExpiresAt()) : null,
                u.mustChangePassword(),
                u.terminosAceptadosEn() != null ? Timestamp.valueOf(u.terminosAceptadosEn()) : null,
                u.perfilCompleto(),
                u.cuentaEliminada() != null ? u.cuentaEliminada() : false,
                u.anonimizadoEn() != null ? Timestamp.valueOf(u.anonimizadoEn()) : null,
                u.notificacionesEmailHabilitadas() != null ? u.notificacionesEmailHabilitadas() : true,
                u.recibirNovedades() != null ? u.recibirNovedades() : true,
                u.nombre(),
                u.apellido(),
                u.telefono(),
                u.sexo(),
                u.fechaNacimiento() != null ? Date.valueOf(u.fechaNacimiento()) : null,
                u.tipoDocumento(),
                u.numeroDocumento(),
                u.dni(),
                u.cuil(),
                u.direccion(),
                u.fotoUrl(),
                u.domicilioCalle(),
                u.domicilioNumero(),
                u.domicilioPiso(),
                u.domicilioDpto(),
                u.domicilioCodigoPostal(),
                u.domicilioLocalidad(),
                u.domicilioProvincia(),
                u.domicilioPais(),
                u.obraSocial(),
                u.numAfiliado(),
                u.datosOfuscado(),
                u.credencialCodEntidad(),
                u.credencialPan(),
                u.credencialPlan(),
                u.credencialToken(),
                u.verificadoAdmin(),
                u.profession(),
                u.licenseType(),
                u.licenseNumber(),
                u.licenseJurisdiction(),
                u.licenseDocumentUrl(),
                u.licenseVerifiedAt() != null ? Timestamp.valueOf(u.licenseVerifiedAt()) : null,
                u.licenseVerifiedBy(),
                u.taxIdType(),
                u.taxId(),
                u.legalName(),
                u.ivaConditionId(),
                u.fiscalAddress()
        );
    }

    @Transactional
    public Long insertarNuevo(Usuario u) {
        Long id = jdbc.queryForObject("SELECT nextval('usuario_id_seq')", Long.class);
        Usuario nuevo = new Usuario(
                id,
                u.version() != null ? u.version() : 0,
                u.email(),
                u.password(),
                u.rol(),
                u.fechaRegistro() != null ? u.fechaRegistro() : LocalDateTime.now(),
                u.emailVerificado(),
                u.codigoVerificacion(),
                u.codigoVerificacionExpiresAt(),
                u.resetPasswordCode(),
                u.resetPasswordExpiresAt(),
                u.mustChangePassword(),
                u.terminosAceptadosEn(),
                u.perfilCompleto(),
                u.cuentaEliminada(),
                u.anonimizadoEn(),
                u.notificacionesEmailHabilitadas(),
                u.recibirNovedades(),
                u.nombre(),
                u.apellido(),
                u.telefono(),
                u.sexo(),
                u.fechaNacimiento(),
                u.tipoDocumento(),
                u.numeroDocumento(),
                u.dni(),
                u.cuil(),
                u.direccion(),
                u.fotoUrl(),
                u.domicilioCalle(),
                u.domicilioNumero(),
                u.domicilioPiso(),
                u.domicilioDpto(),
                u.domicilioCodigoPostal(),
                u.domicilioLocalidad(),
                u.domicilioProvincia(),
                u.domicilioPais(),
                u.obraSocial(),
                u.numAfiliado(),
                u.datosOfuscado(),
                u.credencialCodEntidad(),
                u.credencialPan(),
                u.credencialPlan(),
                u.credencialToken(),
                u.verificadoAdmin(),
                u.profession(),
                u.licenseType(),
                u.licenseNumber(),
                u.licenseJurisdiction(),
                u.licenseDocumentUrl(),
                u.licenseVerifiedAt(),
                u.licenseVerifiedBy(),
                u.taxIdType(),
                u.taxId(),
                u.legalName(),
                u.ivaConditionId(),
                u.fiscalAddress()
        );
        insertar(nuevo);
        return id;
    }

    /**
     * Actualiza el usuario con control de versión optimista.
     * Incrementa {@code version} en 1 y falla si la versión en la base difiere.
     */
    @Transactional
    public Usuario actualizar(Usuario u) {
        int filas = jdbc.update("""
                UPDATE usuario SET
                    version = version + 1,
                    email = ?,
                    password = ?,
                    rol = ?,
                    fecha_registro = ?,
                    email_verificado = ?,
                    codigo_verificacion = ?,
                    codigo_verificacion_expires_at = ?,
                    reset_password_code = ?,
                    reset_password_expires_at = ?,
                    must_change_password = ?,
                    terminos_aceptados_en = ?,
                    perfil_completo = ?,
                    cuenta_eliminada = ?,
                    anonimizado_en = ?,
                    notificaciones_email_habilitadas = ?,
                    recibir_novedades = ?,
                    nombre = ?,
                    apellido = ?,
                    telefono = ?,
                    sexo = ?,
                    fecha_nacimiento = ?,
                    tipo_documento = ?,
                    numero_documento = ?,
                    dni = ?,
                    cuil = ?,
                    direccion = ?,
                    foto_url = ?,
                    domicilio_calle = ?,
                    domicilio_numero = ?,
                    domicilio_piso = ?,
                    domicilio_dpto = ?,
                    domicilio_codigo_postal = ?,
                    domicilio_localidad = ?,
                    domicilio_provincia = ?,
                    domicilio_pais = ?,
                    obra_social = ?,
                    num_afiliado = ?,
                    datos_ofuscado = ?,
                    credencial_cod_entidad = ?,
                    credencial_pan = ?,
                    credencial_plan = ?,
                    credencial_token = ?,
                    verificado_admin = ?,
                    profession = ?,
                    license_type = ?,
                    license_number = ?,
                    license_jurisdiction = ?,
                    license_document_url = ?,
                    license_verified_at = ?,
                    license_verified_by = ?,
                    tax_id_type = ?,
                    tax_id = ?,
                    legal_name = ?,
                    iva_condition_id = ?,
                    fiscal_address = ?
                WHERE id = ? AND version = ?
                """,
                u.emailNormalizado(),
                u.password(),
                u.rol(),
                u.fechaRegistro() != null ? Timestamp.valueOf(u.fechaRegistro()) : Timestamp.valueOf(LocalDateTime.now()),
                u.emailVerificado() != null ? u.emailVerificado() : false,
                u.codigoVerificacion(),
                u.codigoVerificacionExpiresAt() != null ? Timestamp.valueOf(u.codigoVerificacionExpiresAt()) : null,
                u.resetPasswordCode(),
                u.resetPasswordExpiresAt() != null ? Timestamp.valueOf(u.resetPasswordExpiresAt()) : null,
                u.mustChangePassword(),
                u.terminosAceptadosEn() != null ? Timestamp.valueOf(u.terminosAceptadosEn()) : null,
                u.perfilCompleto(),
                u.cuentaEliminada() != null ? u.cuentaEliminada() : false,
                u.anonimizadoEn() != null ? Timestamp.valueOf(u.anonimizadoEn()) : null,
                u.notificacionesEmailHabilitadas() != null ? u.notificacionesEmailHabilitadas() : true,
                u.recibirNovedades() != null ? u.recibirNovedades() : true,
                u.nombre(),
                u.apellido(),
                u.telefono(),
                u.sexo(),
                u.fechaNacimiento() != null ? Date.valueOf(u.fechaNacimiento()) : null,
                u.tipoDocumento(),
                u.numeroDocumento(),
                u.dni(),
                u.cuil(),
                u.direccion(),
                u.fotoUrl(),
                u.domicilioCalle(),
                u.domicilioNumero(),
                u.domicilioPiso(),
                u.domicilioDpto(),
                u.domicilioCodigoPostal(),
                u.domicilioLocalidad(),
                u.domicilioProvincia(),
                u.domicilioPais(),
                u.obraSocial(),
                u.numAfiliado(),
                u.datosOfuscado(),
                u.credencialCodEntidad(),
                u.credencialPan(),
                u.credencialPlan(),
                u.credencialToken(),
                u.verificadoAdmin(),
                u.profession(),
                u.licenseType(),
                u.licenseNumber(),
                u.licenseJurisdiction(),
                u.licenseDocumentUrl(),
                u.licenseVerifiedAt() != null ? Timestamp.valueOf(u.licenseVerifiedAt()) : null,
                u.licenseVerifiedBy(),
                u.taxIdType(),
                u.taxId(),
                u.legalName(),
                u.ivaConditionId(),
                u.fiscalAddress(),
                u.id(),
                u.version()
        );

        if (filas == 0) {
            throw new OptimisticLockingFailureException(
                    "Conflicto de versión optimista al actualizar usuario id=" + u.id() + " con versión " + u.version());
        }
        // Devuelve la fila como quedó: la versión ya subida. Los eventos usan esa versión + 1, igual que el monolito
        // (que publica después de saveAndFlush), así las copias no descartan el primer evento después del corte.
        return u.toBuilder().version((u.version() != null ? u.version() : 0) + 1).build();
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM usuario", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('usuario_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM usuario), 1))",
                Long.class);
    }
}
