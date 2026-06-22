package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurnoService {

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private GoogleCalendarService calendarService;

    @Transactional
    public Turno confirmarTurnoOsde(Long turnoId, String numeroAfiliado) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        turno.setTipo(TipoTurno.OSDE);
        turno.setMetadataAfiliado(numeroAfiliado);
        
        // Muta a confirmado
        turno.setEstado(EstadoTurno.CONFIRMADO);
        
        // Sincronizar agenda en Google Calendar
        String meetUrl = calendarService.crearEventoReunion(turno);
        turno.setTelemedicinaUrl(meetUrl);

        return turnoRepository.save(turno);
    }
}
