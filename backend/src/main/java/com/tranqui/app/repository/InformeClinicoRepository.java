package com.tranqui.app.repository;

import com.tranqui.app.model.InformeClinico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InformeClinicoRepository extends JpaRepository<InformeClinico, Long> {

    List<InformeClinico> findByPacienteIdOrderByFechaDesc(Long pacienteId);

    List<InformeClinico> findByPacienteIdAndMedicoIdOrderByFechaDesc(Long pacienteId, Long medicoId);
}
