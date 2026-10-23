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

    /** Fase 4: cuentas de Mercado Pago vinculadas, para la carga inicial del servicio de pagos. */
    List<Usuario> findByMpAccessTokenEncryptedIsNotNull();
    List<Usuario> findByRol(Rol rol);

    // Fase 5: carga inicial del servicio de profesionales, paginada (la foto va en base64 dentro de la fila).
    List<Usuario> findByRolOrderByIdAsc(Rol rol, org.springframework.data.domain.Pageable pageable);
    List<Usuario> findByRolAndGoogleCalendarConnectedTrue(Rol rol);
    Optional<Usuario> findByGoogleWatchChannelId(String googleWatchChannelId);
    List<Usuario> findByGoogleWatchActiveTrueAndGoogleWatchExpiresAtBefore(LocalDateTime limite);
}
