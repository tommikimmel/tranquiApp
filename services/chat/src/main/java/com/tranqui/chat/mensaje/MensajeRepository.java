package com.tranqui.chat.mensaje;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Consultas del chat. Las de canales son las del {@code MensajeRepository} del monolito, contra las
 * copias locales ({@code participante} y {@code turno_confirmado}) en vez de {@code usuario} y {@code turno}.
 */
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    /** Un canal (conversación) visto desde un usuario. Mismos campos que {@code CanalPrioritarioDto}. */
    interface Canal {
        Long getId();
        String getNombre();
        String getEmail();
        String getPrioridadClinica();
        LocalDateTime getUltimoMensaje();
        Integer getMensajesSinLeer();
    }

    interface NoLeidosPorRemitente {
        Long getRemitenteId();
        Integer getCantidad();
    }

    @Query("SELECT m FROM Mensaje m WHERE (m.remitenteId = :u1 AND m.destinatarioId = :u2) "
            + "OR (m.remitenteId = :u2 AND m.destinatarioId = :u1) ORDER BY m.fechaEnvio DESC")
    Page<Mensaje> historial(@Param("u1") Long u1, @Param("u2") Long u2, Pageable pagina);

    @Modifying
    @Query("UPDATE Mensaje m SET m.leido = true WHERE m.remitenteId = :remitenteId "
            + "AND m.destinatarioId = :destinatarioId AND m.leido = false")
    int marcarLeidos(@Param("remitenteId") Long remitenteId, @Param("destinatarioId") Long destinatarioId);

    @Query("SELECT COUNT(m) > 0 FROM Mensaje m WHERE m.destinatarioId = :destinatarioId AND m.leido = false")
    boolean tieneNoLeidos(@Param("destinatarioId") Long destinatarioId);

    @Query("SELECT m.remitenteId AS remitenteId, CAST(COUNT(m) AS int) AS cantidad FROM Mensaje m "
            + "WHERE m.destinatarioId = :destinatarioId AND m.leido = false GROUP BY m.remitenteId")
    List<NoLeidosPorRemitente> noLeidosPorRemitente(@Param("destinatarioId") Long destinatarioId);

    @Query(value = "SELECT DISTINCT p.id FROM participante p WHERE p.rol = 'PACIENTE' AND EXISTS ("
            + "SELECT 1 FROM mensaje m WHERE (m.remitente_id = p.id AND m.destinatario_id = :medicoId) "
            + "OR (m.remitente_id = :medicoId AND m.destinatario_id = p.id))", nativeQuery = true)
    List<Long> pacientesConChat(@Param("medicoId") Long medicoId);

    @Query(value = "SELECT p.id AS id, p.nombre AS nombre, p.email AS email, "
            + "CASE WHEN EXISTS ("
            + "  SELECT 1 FROM turno_confirmado t "
            + "  WHERE t.paciente_id = p.id AND t.profesional_id = :medicoId "
            + "    AND (t.fecha > :fechaActual OR (t.fecha = :fechaActual AND t.hora_inicio >= :horaActual)) "
            + "    AND (t.fecha < :fechaLimite OR (t.fecha = :fechaLimite AND t.hora_inicio <= :horaLimite))"
            + ") THEN 'PRIORIDAD_ALTA' ELSE 'PRIORIDAD_BAJA' END AS prioridadClinica, "
            + "MAX(m.fecha_envio) AS ultimoMensaje, "
            + "CAST(COALESCE((SELECT COUNT(m2.id) FROM mensaje m2 WHERE m2.remitente_id = p.id "
            + "  AND m2.destinatario_id = :medicoId AND m2.leido = false), 0) AS INTEGER) AS mensajesSinLeer "
            + "FROM participante p "
            + "INNER JOIN mensaje m ON (m.remitente_id = p.id OR m.destinatario_id = p.id) "
            + "WHERE (m.remitente_id = :medicoId OR m.destinatario_id = :medicoId) AND p.rol = 'PACIENTE' "
            + "GROUP BY p.id, p.nombre, p.email "
            + "ORDER BY prioridadClinica ASC, ultimoMensaje DESC", nativeQuery = true)
    List<Canal> canalesPrioritarios(@Param("medicoId") Long medicoId, @Param("fechaActual") LocalDate fechaActual,
                                    @Param("horaActual") LocalTime horaActual, @Param("fechaLimite") LocalDate fechaLimite,
                                    @Param("horaLimite") LocalTime horaLimite);

    /** Canales de un usuario con interlocutores de un rol dado, siempre con prioridad baja. */
    @Query(value = "SELECT p.id AS id, p.nombre AS nombre, p.email AS email, "
            + "'PRIORIDAD_BAJA' AS prioridadClinica, "
            + "MAX(m.fecha_envio) AS ultimoMensaje, "
            + "CAST(COALESCE((SELECT COUNT(m2.id) FROM mensaje m2 WHERE m2.remitente_id = p.id "
            + "  AND m2.destinatario_id = :usuarioId AND m2.leido = false), 0) AS INTEGER) AS mensajesSinLeer "
            + "FROM participante p "
            + "INNER JOIN mensaje m ON (m.remitente_id = p.id OR m.destinatario_id = p.id) "
            + "WHERE (m.remitente_id = :usuarioId OR m.destinatario_id = :usuarioId) AND p.rol = :rol "
            + "GROUP BY p.id, p.nombre, p.email "
            + "ORDER BY ultimoMensaje DESC", nativeQuery = true)
    List<Canal> canalesConRol(@Param("usuarioId") Long usuarioId, @Param("rol") String rol);

    /** Mayor id copiado del monolito, para dejar la secuencia después (carga inicial). */
    @Query(value = "SELECT COALESCE(MAX(id), 0) FROM mensaje", nativeQuery = true)
    long maxId();
}
