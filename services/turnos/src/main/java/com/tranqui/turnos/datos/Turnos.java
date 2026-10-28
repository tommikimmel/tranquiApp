package com.tranqui.turnos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Los turnos (JDBC). Las consultas son las de {@code TurnoRepository} del monolito. */
@Repository
public class Turnos {

    private static final String COLUMNAS = "id, version, paciente_id, medico_id, fecha, hora_inicio, hora_fin, tipo, modalidad, "
            + "estado, asistencia, precio, metadata_afiliado, servicio_id, ocupa_agenda, id_financiador, telemedicina_url, "
            + "google_event_id, checkout_url, documento_enviado, es_receta, fecha_creacion, confirmacion_asistencia_email_enviado, "
            + "asistencia_confirmada_paciente, token_confirmacion_asistencia, token_reserva, pago_transaction_id, pago_estado, "
            + "pago_monto, pago_fecha";

    private final JdbcTemplate jdbc;
    private final RowMapper<Turno> mapeo = (rs, i) -> {
        Turno t = new Turno();
        t.id = rs.getLong("id");
        t.version = rs.getInt("version");
        t.pacienteId = rs.getLong("paciente_id");
        t.medicoId = rs.getLong("medico_id");
        t.fecha = rs.getObject("fecha", LocalDate.class);
        t.horaInicio = rs.getObject("hora_inicio", LocalTime.class);
        t.horaFin = rs.getObject("hora_fin", LocalTime.class);
        t.tipo = rs.getString("tipo");
        t.modalidad = rs.getString("modalidad");
        t.estado = rs.getString("estado");
        t.asistencia = rs.getString("asistencia");
        t.precio = rs.getBigDecimal("precio");
        t.metadataAfiliado = rs.getString("metadata_afiliado");
        t.servicioId = rs.getString("servicio_id");
        t.ocupaAgenda = rs.getBoolean("ocupa_agenda");
        t.idFinanciador = rs.getString("id_financiador");
        t.telemedicinaUrl = rs.getString("telemedicina_url");
        t.googleEventId = rs.getString("google_event_id");
        t.checkoutUrl = rs.getString("checkout_url");
        t.documentoEnviado = rs.getBoolean("documento_enviado");
        t.esReceta = rs.getBoolean("es_receta");
        t.fechaCreacion = rs.getObject("fecha_creacion", LocalDateTime.class);
        t.confirmacionAsistenciaEmailEnviado = rs.getBoolean("confirmacion_asistencia_email_enviado");
        t.asistenciaConfirmadaPaciente = rs.getBoolean("asistencia_confirmada_paciente");
        t.tokenConfirmacionAsistencia = rs.getString("token_confirmacion_asistencia");
        t.tokenReserva = rs.getString("token_reserva");
        t.pagoTransactionId = rs.getString("pago_transaction_id");
        t.pagoEstado = rs.getString("pago_estado");
        t.pagoMonto = rs.getBigDecimal("pago_monto");
        t.pagoFecha = rs.getObject("pago_fecha", LocalDateTime.class);
        return t;
    };

    public Turnos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Turno> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE id = ?", mapeo, id).stream().findFirst();
    }

    /** Para actualizar: bloquea la fila hasta el fin de la transacción (un webhook y "verificar" a la vez). */
    public Optional<Turno> porIdParaActualizar(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE id = ? FOR UPDATE", mapeo, id).stream().findFirst();
    }

    /** Un turno nuevo, con el próximo id de la secuencia y la versión 1. */
    @Transactional
    public Turno crear(Turno t) {
        t.id = jdbc.queryForObject("SELECT nextval('turno_id_seq')", Long.class);
        t.version = 1;
        insertar(t);
        return t;
    }

    /** Un turno del monolito, con su id y su versión (carga inicial). */
    @Transactional
    public void copiar(Turno t) {
        insertar(t);
    }

    /** La secuencia sigue después del turno más alto (después de la carga inicial). */
    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('turno_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM turno), 1))", Long.class);
    }

    private void insertar(Turno t) {
        jdbc.update("INSERT INTO turno (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                t.id, t.version, t.pacienteId, t.medicoId, Date.valueOf(t.fecha), Time.valueOf(t.horaInicio), Time.valueOf(t.horaFin),
                t.tipo, t.modalidad, t.estado, t.asistencia, t.precio, t.metadataAfiliado, t.servicioId, t.ocupaAgenda,
                t.idFinanciador, t.telemedicinaUrl, t.googleEventId, t.checkoutUrl, t.documentoEnviado, t.esReceta,
                Timestamp.valueOf(t.fechaCreacion), t.confirmacionAsistenciaEmailEnviado, t.asistenciaConfirmadaPaciente,
                t.tokenConfirmacionAsistencia, t.tokenReserva, t.pagoTransactionId, t.pagoEstado, t.pagoMonto,
                t.pagoFecha != null ? Timestamp.valueOf(t.pagoFecha) : null);
    }

    /** Guarda todos los campos y sube la versión (la de los eventos que se publiquen después). */
    @Transactional
    public Turno guardar(Turno t) {
        t.version = t.version + 1;
        jdbc.update("UPDATE turno SET version = ?, paciente_id = ?, medico_id = ?, fecha = ?, hora_inicio = ?, hora_fin = ?, tipo = ?, "
                        + "modalidad = ?, estado = ?, asistencia = ?, precio = ?, metadata_afiliado = ?, servicio_id = ?, ocupa_agenda = ?, "
                        + "id_financiador = ?, telemedicina_url = ?, google_event_id = ?, checkout_url = ?, documento_enviado = ?, "
                        + "es_receta = ?, confirmacion_asistencia_email_enviado = ?, asistencia_confirmada_paciente = ?, "
                        + "token_confirmacion_asistencia = ?, token_reserva = ?, pago_transaction_id = ?, pago_estado = ?, "
                        + "pago_monto = ?, pago_fecha = ?, actualizado_en = now() WHERE id = ?",
                t.version, t.pacienteId, t.medicoId, Date.valueOf(t.fecha), Time.valueOf(t.horaInicio), Time.valueOf(t.horaFin),
                t.tipo, t.modalidad, t.estado, t.asistencia, t.precio, t.metadataAfiliado, t.servicioId, t.ocupaAgenda,
                t.idFinanciador, t.telemedicinaUrl, t.googleEventId, t.checkoutUrl, t.documentoEnviado, t.esReceta,
                t.confirmacionAsistenciaEmailEnviado, t.asistenciaConfirmadaPaciente, t.tokenConfirmacionAsistencia,
                t.tokenReserva, t.pagoTransactionId, t.pagoEstado, t.pagoMonto,
                t.pagoFecha != null ? Timestamp.valueOf(t.pagoFecha) : null, t.id);
        return t;
    }

    /** {@code findByMedicoIdAndFechaAndEstadoNot(medico, fecha, CANCELADO)}. */
    public List<Turno> delDia(Long medicoId, LocalDate fecha) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE medico_id = ? AND fecha = ? AND estado <> 'CANCELADO'",
                mapeo, medicoId, Date.valueOf(fecha));
    }

    public List<Turno> deProfesional(Long medicoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE medico_id = ? AND estado <> 'CANCELADO'", mapeo, medicoId);
    }

    public List<Turno> dePaciente(Long pacienteId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE paciente_id = ? AND estado <> 'CANCELADO'", mapeo, pacienteId);
    }

    /** Todos los turnos del médico entre dos fechas (para estadísticas del dashboard). */
    public List<Turno> entreFechas(Long medicoId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE medico_id = ? AND fecha BETWEEN ? AND ?",
                mapeo, medicoId, Date.valueOf(desde), Date.valueOf(hasta));
    }

    /** Primera fecha de turno no cancelado por cada paciente del médico (para pacientes nuevos). */
    public Map<Long, LocalDate> primeraFechaPorPaciente(Long medicoId) {
        return jdbc.query("SELECT paciente_id, MIN(fecha) AS primera_fecha FROM turno WHERE medico_id = ? AND estado <> 'CANCELADO' GROUP BY paciente_id",
                (rs, i) -> Map.entry(rs.getLong("paciente_id"), rs.getObject("primera_fecha", LocalDate.class)),
                medicoId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** {@code existsActiveTurnoByPacienteEmail}: un turno de agenda desde hoy que no esté cancelado ni vencido. */
    public boolean tieneTurnoActivo(String email, LocalDate desde) {
        Boolean hay = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM turno t JOIN persona p ON p.id = t.paciente_id "
                        + "WHERE lower(p.email) = lower(?) AND t.fecha >= ? AND t.estado NOT IN ('CANCELADO', 'EXPIRADO') AND t.ocupa_agenda)",
                Boolean.class, email, Date.valueOf(desde));
        return Boolean.TRUE.equals(hay);
    }

    /** {@code findPacienteEmailsConTurnoNoCancelado}: de esos mails, los que ya tienen un turno no cancelado. */
    public List<String> emailsConTurnoNoCancelado(Collection<String> emails) {
        if (emails.isEmpty()) {
            return List.of();
        }
        String marcas = String.join(",", emails.stream().map(e -> "?").toList());
        return jdbc.queryForList("SELECT DISTINCT p.email FROM turno t JOIN persona p ON p.id = t.paciente_id "
                + "WHERE p.email IN (" + marcas + ") AND t.estado <> 'CANCELADO'", String.class, emails.toArray());
    }

    /** Reservas sin pagar con el bloqueo de 5 minutos vencido. */
    public List<Turno> pendientesVencidos(LocalDateTime limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE estado = 'PENDIENTE_PAGO' AND fecha_creacion < ?",
                mapeo, Timestamp.valueOf(limite));
    }

    /** Turnos de agenda confirmados entre las dos fechas que todavía no recibieron el recordatorio. */
    public List<Turno> paraRecordatorio(LocalDate desde, LocalDate hasta) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE estado = 'CONFIRMADO' AND fecha BETWEEN ? AND ? "
                + "AND ocupa_agenda AND NOT confirmacion_asistencia_email_enviado", mapeo, Date.valueOf(desde), Date.valueOf(hasta));
    }

    /** Las "receta fuera de turno" pagas y sin enviar de un profesional para un paciente. */
    public List<Turno> recetasPendientes(Long medicoId, Long pacienteId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE medico_id = ? AND paciente_id = ? AND servicio_id = 'receta-fuera' "
                + "AND estado = 'CONFIRMADO' AND NOT documento_enviado", mapeo, medicoId, pacienteId);
    }

    /** Para la resincronización del monolito: todos, o los que cambiaron desde {@code desde}. */
    public List<Turno> cambiadosDesde(OffsetDateTime desde) {
        if (desde == null) {
            return jdbc.query("SELECT " + COLUMNAS + " FROM turno ORDER BY id", mapeo);
        }
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno WHERE actualizado_en >= ? ORDER BY id", mapeo, desde);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM turno", Long.class);
        return n != null ? n : 0;
    }
}
