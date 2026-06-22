package com.tranqui.app.repository;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

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
           "MAX(m.fecha_envio) AS ultimoMensaje " +
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
}
