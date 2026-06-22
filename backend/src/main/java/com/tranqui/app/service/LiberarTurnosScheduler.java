package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TurnoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class LiberarTurnosScheduler {

    @Autowired
    private TurnoRepository turnoRepository;

    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void liberarTurnosExpirados() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(10);
        List<Turno> turnosExpirados = turnoRepository.findByEstadoAndFechaCreacionBefore(
                EstadoTurno.PENDIENTE_PAGO, limite
        );

        for (Turno turno : turnosExpirados) {
            turno.setEstado(EstadoTurno.CANCELADO);
            turnoRepository.save(turno);
        }
    }
}
