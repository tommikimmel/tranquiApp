package com.tranqui.app.repository;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByMpUserId(String mpUserId);
    List<Usuario> findByRol(Rol rol);
    List<Usuario> findByRolAndGoogleCalendarConnectedTrue(Rol rol);
    Optional<Usuario> findByGoogleWatchChannelId(String googleWatchChannelId);
    List<Usuario> findByGoogleWatchActiveTrueAndGoogleWatchExpiresAtBefore(LocalDateTime limite);
}
