package com.tranqui.notificaciones.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(Long usuarioId, LocalDateTime desde);

    @Modifying
    @Query("update Notificacion n set n.leido = true where n.usuarioId = ?1 and n.leido = false")
    int marcarTodasComoLeidas(Long usuarioId);

    @Modifying
    @Query("delete from Notificacion n where n.fechaCreacion < ?1")
    int borrarAnterioresA(LocalDateTime limite);

    @Modifying
    @Query("delete from Notificacion n where n.usuarioId = ?1")
    int deleteByUsuarioId(Long usuarioId);
}
