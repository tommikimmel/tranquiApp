package com.tranqui.app.repository;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByMedicoIdAndFecha(Long medicoId, LocalDate fecha);
    
    List<Turno> findByMedicoIdAndFechaAndEstadoNot(Long medicoId, LocalDate fecha, EstadoTurno estado);
}
