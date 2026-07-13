package com.tranqui.app.repository;

import com.tranqui.app.model.SeguimientoDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SeguimientoDiarioRepository extends JpaRepository<SeguimientoDiario, Long> {

    List<SeguimientoDiario> findByPacienteIdOrderByFechaDesc(Long pacienteId);

    List<SeguimientoDiario> findByPacienteIdAndMedicoIdOrderByFechaDesc(Long pacienteId, Long medicoId);
}
