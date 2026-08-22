package com.tranqui.app.repository;

import com.tranqui.app.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByCreadorIdOrderByFechaActualizacionDesc(Long creadorId);
    List<Ticket> findAllByOrderByFechaActualizacionDesc();
}
