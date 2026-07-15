package com.tranqui.app.repository;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByMpUserId(String mpUserId);
    List<Usuario> findByRol(Rol rol);
}
