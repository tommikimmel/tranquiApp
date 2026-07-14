package com.tranqui.app.repository;

import com.tranqui.app.model.TarifaMedico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TarifaMedicoRepository extends JpaRepository<TarifaMedico, Long> {
    List<TarifaMedico> findByMedicoId(Long medicoId);
    List<TarifaMedico> findByMedicoIdIn(List<Long> medicoIds);
    Optional<TarifaMedico> findByMedicoIdAndServicioId(Long medicoId, String servicioId);
}
