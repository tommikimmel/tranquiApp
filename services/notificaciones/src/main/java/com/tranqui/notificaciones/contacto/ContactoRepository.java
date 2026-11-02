package com.tranqui.notificaciones.contacto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {

    @Query("select c from Contacto c where lower(c.email) = lower(?1)")
    Optional<Contacto> buscarPorEmail(String email);

    List<Contacto> findByRol(String rol);
}
