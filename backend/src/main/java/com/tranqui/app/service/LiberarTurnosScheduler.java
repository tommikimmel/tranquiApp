package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TurnoExpiradoData;
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

    @Autowired
    private OutboxPublisher outboxPublisher;

    // Fase 6: con el flag, el vencimiento del bloqueo lo hace el servicio de turnos.
    @org.springframework.beans.factory.annotation.Value("${turnos.externo:false}")
    private boolean turnosExterno;

    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void liberarTurnosExpirados() {
        if (turnosExterno) {
            return;
        }
        LocalDateTime limite = LocalDateTime.now().minusMinutes(5);
        List<Turno> turnosExpirados = turnoRepository.findByEstadoAndFechaCreacionBefore(
                EstadoTurno.PENDIENTE_PAGO, limite
        );

        for (Turno turno : turnosExpirados) {
            turno.setEstado(EstadoTurno.CANCELADO);
            Turno guardado = turnoRepository.saveAndFlush(turno);
            if (guardado != null) {
                turno = guardado;
            }

            outboxPublisher.publicar(
                    Topicos.TURNOS_TURNO_EXPIRADO_V1,
                    String.valueOf(turno.getId()),
                    turno.getAggregateVersion(),
                    new TurnoExpiradoData(
                            turno.getId(),
                            turno.getPaciente() != null ? turno.getPaciente().getId() : null,
                            turno.getMedico() != null ? turno.getMedico().getId() : null
                    )
            );
        }
    }
}
