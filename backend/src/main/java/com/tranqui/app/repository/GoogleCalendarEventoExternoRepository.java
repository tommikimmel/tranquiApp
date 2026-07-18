package com.tranqui.app.repository;

import com.tranqui.app.model.GoogleCalendarEventoExterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleCalendarEventoExternoRepository extends JpaRepository<GoogleCalendarEventoExterno, Long> {
    List<GoogleCalendarEventoExterno> findByMedicoId(Long medicoId);
    Optional<GoogleCalendarEventoExterno> findByGoogleEventId(String googleEventId);
    void deleteByGoogleEventId(String googleEventId);
    void deleteByMedicoId(Long medicoId);
}
