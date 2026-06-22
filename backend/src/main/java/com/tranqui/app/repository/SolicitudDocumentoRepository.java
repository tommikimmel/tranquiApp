package com.tranqui.app.repository;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.SolicitudDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SolicitudDocumentoRepository extends JpaRepository<SolicitudDocumento, Long> {
    List<SolicitudDocumento> findByMedicoIdAndEstado(Long medicoId, EstadoPago estado);
    List<SolicitudDocumento> findByMedicoIdAndEstadoAndEmitido(Long medicoId, EstadoPago estado, boolean emitido);
    List<SolicitudDocumento> findByEstadoAndFechaCreacionBefore(EstadoPago estado, LocalDateTime limite);
}
