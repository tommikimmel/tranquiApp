package com.tranqui.app.repository;

import com.tranqui.app.model.TicketMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketMensajeRepository extends JpaRepository<TicketMensaje, Long> {
    List<TicketMensaje> findByTicketIdOrderByFechaEnvioAsc(Long ticketId);
}
