package com.tranqui.app.scheduler;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TurnoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TurnoCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TurnoCleanupScheduler.class);

    @Autowired
    private TurnoRepository turnoRepository;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void limpiarReservasExpiradas() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(5);
        List<Turno> expirados = turnoRepository.findByEstadoAndFechaCreacionBefore(EstadoTurno.PENDIENTE_PAGO, limite);
        if (!expirados.isEmpty()) {
            for (Turno t : expirados) {
                t.setEstado(EstadoTurno.EXPIRADO);
            }
            turnoRepository.saveAll(expirados);
            log.info("Se marcaron {} reservas temporales como EXPIRADAS (> 5 min sin pago).", expirados.size());
        }
    }
}
