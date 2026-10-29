package com.tranqui.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 9, I1: rutas internas del monolito para la carga inicial del servicio de identidad (I2a).
 * Expone usuarios (solo columnas de identidad), tickets, mensajes de tickets y envíos de novedades.
 * Protegidas por {@link com.tranqui.app.config.FiltroServicioInterno}, como el resto de {@code /internal/monolito}.
 */
@RestController
@RequestMapping("/internal/monolito/identidad")
public class InternoIdentidadController {

    private static final int TAMANIO_MAXIMO = 1000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/usuarios")
    public ResponseEntity<List<Map<String, Object>>> usuarios(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio
    ) {
        int page = Math.max(0, pagina);
        int size = Math.min(TAMANIO_MAXIMO, Math.max(1, tamanio));
        int offset = page * size;

        String sql = """
            SELECT
                id, version, email, password, rol, fecha_registro, email_verificado, codigo_verificacion,
                codigo_verificacion_expires_at, reset_password_code, reset_password_expires_at, must_change_password,
                terminos_aceptados_en, perfil_completo, cuenta_eliminada, anonimizado_en, notificaciones_email_habilitadas,
                recibir_novedades, nombre, apellido, telefono, sexo, fecha_nacimiento, tipo_documento, numero_documento, dni, cuil,
                direccion, foto_url, domicilio_calle, domicilio_numero, domicilio_piso, domicilio_dpto, domicilio_codigo_postal,
                domicilio_localidad, domicilio_provincia, domicilio_pais, obra_social, num_afiliado, datos_ofuscado,
                credencial_cod_entidad, credencial_pan, credencial_plan, credencial_token, verificado_admin, profession,
                license_type, license_number, license_jurisdiction, license_document_url, license_verified_at,
                license_verified_by, tax_id_type, tax_id, legal_name, iva_condition_id, fiscal_address
            FROM usuario
            ORDER BY id ASC
            LIMIT ? OFFSET ?
            """;

        List<Map<String, Object>> filas = jdbcTemplate.query(sql, (rs, rowNum) -> mapearUsuario(rs), size, offset);
        return ResponseEntity.ok(filas);
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<Map<String, Object>>> tickets(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio
    ) {
        int page = Math.max(0, pagina);
        int size = Math.min(TAMANIO_MAXIMO, Math.max(1, tamanio));
        int offset = page * size;

        String sql = """
            SELECT id, version, creador_id, asunto, estado, fecha_creacion, fecha_actualizacion
            FROM ticket
            ORDER BY id ASC
            LIMIT ? OFFSET ?
            """;

        List<Map<String, Object>> filas = jdbcTemplate.query(sql, (rs, rowNum) -> mapearTicket(rs), size, offset);
        return ResponseEntity.ok(filas);
    }

    @GetMapping("/ticket-mensajes")
    public ResponseEntity<List<Map<String, Object>>> ticketMensajes(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio
    ) {
        int page = Math.max(0, pagina);
        int size = Math.min(TAMANIO_MAXIMO, Math.max(1, tamanio));
        int offset = page * size;

        String sql = """
            SELECT id, ticket_id, autor_id, contenido, fecha_envio
            FROM ticket_mensaje
            ORDER BY id ASC
            LIMIT ? OFFSET ?
            """;

        List<Map<String, Object>> filas = jdbcTemplate.query(sql, (rs, rowNum) -> mapearTicketMensaje(rs), size, offset);
        return ResponseEntity.ok(filas);
    }

    @GetMapping("/novedades-envios")
    public ResponseEntity<List<Map<String, Object>>> novedadesEnvios(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio
    ) {
        int page = Math.max(0, pagina);
        int size = Math.min(TAMANIO_MAXIMO, Math.max(1, tamanio));
        int offset = page * size;

        String sql = """
            SELECT id, titulo, estado, detalle, creado_en, finalizado_en, enviado_pacientes, enviado_profesionales
            FROM novedades_envio
            ORDER BY id ASC
            LIMIT ? OFFSET ?
            """;

        List<Map<String, Object>> filas = jdbcTemplate.query(sql, (rs, rowNum) -> mapearNovedadesEnvio(rs), size, offset);
        return ResponseEntity.ok(filas);
    }

    private Map<String, Object> mapearUsuario(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getLong(rs, "id"));
        m.put("version", getInteger(rs, "version"));
        m.put("email", getString(rs, "email"));
        m.put("password", getString(rs, "password"));
        m.put("rol", getString(rs, "rol"));
        m.put("fecha_registro", getTimestampIso(rs, "fecha_registro"));
        m.put("email_verificado", getBoolean(rs, "email_verificado"));
        m.put("codigo_verificacion", getString(rs, "codigo_verificacion"));
        m.put("codigo_verificacion_expires_at", getTimestampIso(rs, "codigo_verificacion_expires_at"));
        m.put("reset_password_code", getString(rs, "reset_password_code"));
        m.put("reset_password_expires_at", getTimestampIso(rs, "reset_password_expires_at"));
        m.put("must_change_password", getBoolean(rs, "must_change_password"));
        m.put("terminos_aceptados_en", getTimestampIso(rs, "terminos_aceptados_en"));
        m.put("perfil_completo", getBoolean(rs, "perfil_completo"));
        m.put("cuenta_eliminada", getBoolean(rs, "cuenta_eliminada"));
        m.put("anonimizado_en", getTimestampIso(rs, "anonimizado_en"));
        m.put("notificaciones_email_habilitadas", getBoolean(rs, "notificaciones_email_habilitadas"));
        m.put("recibir_novedades", getBoolean(rs, "recibir_novedades"));
        m.put("nombre", getString(rs, "nombre"));
        m.put("apellido", getString(rs, "apellido"));
        m.put("telefono", getString(rs, "telefono"));
        m.put("sexo", getString(rs, "sexo"));
        m.put("fecha_nacimiento", getDateIso(rs, "fecha_nacimiento"));
        m.put("tipo_documento", getString(rs, "tipo_documento"));
        m.put("numero_documento", getInteger(rs, "numero_documento"));
        m.put("dni", getString(rs, "dni"));
        m.put("cuil", getLong(rs, "cuil"));
        m.put("direccion", getString(rs, "direccion"));
        m.put("foto_url", getString(rs, "foto_url"));
        m.put("domicilio_calle", getString(rs, "domicilio_calle"));
        m.put("domicilio_numero", getString(rs, "domicilio_numero"));
        m.put("domicilio_piso", getString(rs, "domicilio_piso"));
        m.put("domicilio_dpto", getString(rs, "domicilio_dpto"));
        m.put("domicilio_codigo_postal", getString(rs, "domicilio_codigo_postal"));
        m.put("domicilio_localidad", getString(rs, "domicilio_localidad"));
        m.put("domicilio_provincia", getString(rs, "domicilio_provincia"));
        m.put("domicilio_pais", getString(rs, "domicilio_pais"));
        m.put("obra_social", getString(rs, "obra_social"));
        m.put("num_afiliado", getString(rs, "num_afiliado"));
        m.put("datos_ofuscado", getString(rs, "datos_ofuscado"));
        m.put("credencial_cod_entidad", getInteger(rs, "credencial_cod_entidad"));
        m.put("credencial_pan", getString(rs, "credencial_pan"));
        m.put("credencial_plan", getString(rs, "credencial_plan"));
        m.put("credencial_token", getString(rs, "credencial_token"));
        m.put("verificado_admin", getBoolean(rs, "verificado_admin"));
        m.put("profession", getString(rs, "profession"));
        m.put("license_type", getString(rs, "license_type"));
        m.put("license_number", getString(rs, "license_number"));
        m.put("license_jurisdiction", getString(rs, "license_jurisdiction"));
        m.put("license_document_url", getString(rs, "license_document_url"));
        m.put("license_verified_at", getTimestampIso(rs, "license_verified_at"));
        m.put("license_verified_by", getString(rs, "license_verified_by"));
        m.put("tax_id_type", getString(rs, "tax_id_type"));
        m.put("tax_id", getString(rs, "tax_id"));
        m.put("legal_name", getString(rs, "legal_name"));
        m.put("iva_condition_id", getInteger(rs, "iva_condition_id"));
        m.put("fiscal_address", getString(rs, "fiscal_address"));
        return m;
    }

    private Map<String, Object> mapearTicket(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getLong(rs, "id"));
        m.put("version", getInteger(rs, "version"));
        m.put("creador_id", getLong(rs, "creador_id"));
        m.put("asunto", getString(rs, "asunto"));
        m.put("estado", getString(rs, "estado"));
        m.put("fecha_creacion", getTimestampIso(rs, "fecha_creacion"));
        m.put("fecha_actualizacion", getTimestampIso(rs, "fecha_actualizacion"));
        return m;
    }

    private Map<String, Object> mapearTicketMensaje(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getLong(rs, "id"));
        m.put("ticket_id", getLong(rs, "ticket_id"));
        m.put("autor_id", getLong(rs, "autor_id"));
        m.put("contenido", getString(rs, "contenido"));
        m.put("fecha_envio", getTimestampIso(rs, "fecha_envio"));
        return m;
    }

    private Map<String, Object> mapearNovedadesEnvio(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getString(rs, "id"));
        m.put("titulo", getString(rs, "titulo"));
        m.put("estado", getString(rs, "estado"));
        m.put("detalle", getString(rs, "detalle"));
        m.put("creado_en", getTimestampIso(rs, "creado_en"));
        m.put("finalizado_en", getTimestampIso(rs, "finalizado_en"));
        m.put("enviado_pacientes", getBoolean(rs, "enviado_pacientes"));
        m.put("enviado_profesionales", getBoolean(rs, "enviado_profesionales"));
        return m;
    }

    private static String getString(ResultSet rs, String col) throws SQLException {
        String val = rs.getString(col);
        return rs.wasNull() ? null : val;
    }

    private static Long getLong(ResultSet rs, String col) throws SQLException {
        long val = rs.getLong(col);
        return rs.wasNull() ? null : val;
    }

    private static Integer getInteger(ResultSet rs, String col) throws SQLException {
        int val = rs.getInt(col);
        return rs.wasNull() ? null : val;
    }

    private static Boolean getBoolean(ResultSet rs, String col) throws SQLException {
        boolean val = rs.getBoolean(col);
        return rs.wasNull() ? null : val;
    }

    private static String getTimestampIso(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        if (ts == null || rs.wasNull()) {
            return null;
        }
        return ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static String getDateIso(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        if (d == null || rs.wasNull()) {
            return null;
        }
        return d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
