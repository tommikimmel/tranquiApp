package com.tranqui.app.repository;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    @Modifying
    @Query("UPDATE Mensaje m SET m.leido = true WHERE m.remitente.id = :remitenteId AND m.destinatario.id = :destinatarioId AND m.leido = false")
    void markAsRead(@Param("remitenteId") Long remitenteId, @Param("destinatarioId") Long destinatarioId);

    @Query("SELECT COUNT(m) > 0 FROM Mensaje m WHERE m.destinatario.id = :destinatarioId AND m.leido = false")
    boolean hasUnreadMessages(@Param("destinatarioId") Long destinatarioId);

    @Query("SELECT CAST(COUNT(m) AS int) FROM Mensaje m WHERE m.remitente.id = :remitenteId AND m.destinatario.id = :destinatarioId AND m.leido = false")
    int countUnreadMessages(@Param("remitenteId") Long remitenteId, @Param("destinatarioId") Long destinatarioId);

    @Query("SELECT DISTINCT u FROM Usuario u WHERE u.rol = 'PACIENTE' AND EXISTS (" +
           "  SELECT 1 FROM Mensaje m WHERE " +
           "  (m.remitente.id = u.id AND m.destinatario.id = :medicoId) OR " +
           "  (m.remitente.id = :medicoId AND m.destinatario.id = u.id)" +
           ")")
    List<Usuario> findPacientesConMensajesConMedico(@Param("medicoId") Long medicoId);

    @Query("SELECT m FROM Mensaje m WHERE (m.remitente.id = :u1 AND m.destinatario.id = :u2) " +
           "OR (m.remitente.id = :u2 AND m.destinatario.id = :u1) ORDER BY m.fechaEnvio DESC")
    Page<Mensaje> findChatHistory(@Param("u1") Long u1, @Param("u2") Long u2, Pageable pageable);

    @Query(value = "SELECT u.id AS id, u.nombre AS nombre, u.email AS email, " +
           "CASE WHEN EXISTS (" +
           "  SELECT 1 FROM turno t " +
           "  WHERE t.paciente_id = u.id AND t.medico_id = :medicoId AND t.estado = 'CONFIRMADO' " +
           "    AND (t.fecha > :fechaActual OR (t.fecha = :fechaActual AND t.hora_inicio >= :horaActual)) " +
           "    AND (t.fecha < :fechaLimite OR (t.fecha = :fechaLimite AND t.hora_inicio <= :horaLimite))" +
           ") THEN 'PRIORIDAD_ALTA' ELSE 'PRIORIDAD_BAJA' END AS prioridadClinica, " +
           "MAX(m.fecha_envio) AS ultimoMensaje, " +
           "CAST(COALESCE((SELECT COUNT(m2.id) FROM mensaje m2 WHERE m2.remitente_id = u.id AND m2.destinatario_id = :medicoId AND m2.leido = false), 0) AS INTEGER) AS mensajesSinLeer " +
           "FROM usuario u " +
           "INNER JOIN mensaje m ON (m.remitente_id = u.id OR m.destinatario_id = u.id) " +
           "WHERE (m.remitente_id = :medicoId OR m.destinatario_id = :medicoId) " +
           "  AND u.rol = 'PACIENTE' " +
           "GROUP BY u.id, u.nombre, u.email " +
           "ORDER BY prioridadClinica ASC, ultimoMensaje DESC", nativeQuery = true)
    List<CanalPrioritarioDto> findPrioritizedChannels(
            @Param("medicoId") Long medicoId,
            @Param("fechaActual") LocalDate fechaActual,
            @Param("horaActual") LocalTime horaActual,
            @Param("fechaLimite") LocalDate fechaLimite,
            @Param("horaLimite") LocalTime horaLimite);

    @Query(value = "SELECT u.id AS id, u.nombre AS nombre, u.email AS email, " +
           "'PRIORIDAD_BAJA' AS prioridadClinica, " +
           "MAX(m.fecha_envio) AS ultimoMensaje, " +
           "CAST(COALESCE((SELECT COUNT(m2.id) FROM mensaje m2 WHERE m2.remitente_id = u.id AND m2.destinatario_id = :medicoId AND m2.leido = false), 0) AS INTEGER) AS mensajesSinLeer " +
           "FROM usuario u " +
           "INNER JOIN mensaje m ON (m.remitente_id = u.id OR m.destinatario_id = u.id) " +
           "WHERE (m.remitente_id = :medicoId OR m.destinatario_id = :medicoId) " +
           "  AND u.rol = 'VISITADOR' " +
           "GROUP BY u.id, u.nombre, u.email " +
           "ORDER BY ultimoMensaje DESC", nativeQuery = true)
    List<CanalPrioritarioDto> findVisitorChannels(@Param("medicoId") Long medicoId);

    // Patient-side counterpart of findPrioritizedChannels: same shape, but looking for the
    // patient's PSIQUIATRA messaging partners instead of a doctor's PACIENTE ones. No clinical
    // priority concept applies from this side, so it's always reported as 'PRIORIDAD_BAJA'
    // (mirrors findVisitorChannels above).
    @Query(value = "SELECT u.id AS id, u.nombre AS nombre, u.email AS email, " +
           "'PRIORIDAD_BAJA' AS prioridadClinica, " +
           "MAX(m.fecha_envio) AS ultimoMensaje, " +
           "CAST(COALESCE((SELECT COUNT(m2.id) FROM mensaje m2 WHERE m2.remitente_id = u.id AND m2.destinatario_id = :pacienteId AND m2.leido = false), 0) AS INTEGER) AS mensajesSinLeer " +
           "FROM usuario u " +
           "INNER JOIN mensaje m ON (m.remitente_id = u.id OR m.destinatario_id = u.id) " +
           "WHERE (m.remitente_id = :pacienteId OR m.destinatario_id = :pacienteId) " +
           "  AND u.rol = 'PSIQUIATRA' " +
           "GROUP BY u.id, u.nombre, u.email " +
           "ORDER BY ultimoMensaje DESC", nativeQuery = true)
    List<CanalPrioritarioDto> findPatientChannels(@Param("pacienteId") Long pacienteId);
}
