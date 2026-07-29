package com.tranqui.app.repository;

import com.tranqui.app.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);

    List<Notificacion> findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(Long usuarioId, java.time.LocalDateTime cutoff);

    long countByUsuarioIdAndLeidoFalse(Long usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leido = true WHERE n.usuario.id = :usuarioId AND n.leido = false")
    void markAllAsRead(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.fechaCreacion < :cutoff")
    void deleteByFechaCreacionBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
}
