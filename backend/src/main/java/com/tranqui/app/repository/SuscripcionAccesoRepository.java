package com.tranqui.app.repository;

import com.tranqui.app.model.SuscripcionAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuscripcionAccesoRepository extends JpaRepository<SuscripcionAcceso, Long> {
}
