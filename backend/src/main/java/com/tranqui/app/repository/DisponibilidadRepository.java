package com.tranqui.app.repository;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Modalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DisponibilidadRepository extends JpaRepository<Disponibilidad, Long> {
    List<Disponibilidad> findByMedicoId(Long medicoId);

    // Matches the requested modalidad plus any legacy row (modalidad IS NULL, created before
    // per-modalidad agendas existed) — see Disponibilidad.modalidad for why those are still
    // treated as valid until the médico explicitly saves a grid.
    @Query("select d from Disponibilidad d where d.medico.id = :medicoId and (d.modalidad = :modalidad or d.modalidad is null)")
    List<Disponibilidad> findByMedicoIdAndModalidadOLegacy(@Param("medicoId") Long medicoId, @Param("modalidad") Modalidad modalidad);
}
