package com.tranqui.app.eventos;

import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.MeetCreadoData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 3: primer evento que consume el monolito. El servicio de calendario crea el evento de Google y
 * el Meet de un turno confirmado y avisa por {@code calendario.meet.creado.v1}; el monolito guarda el
 * link en el turno (lo leen el paciente, los recordatorios y la sala) y el id del evento (para la
 * vuelta atrás). El mail con el link lo manda notificaciones con el mismo evento.
 *
 * <p>El consumo se prende solo con {@code EVENTOS_CONSUMO_SERVICIO=monolito} (los compose); sin esa
 * variable este handler no se registra en ningún consumidor.
 */
@Configuration
public class ManejadoresCalendario {

    private static final Logger log = LoggerFactory.getLogger(ManejadoresCalendario.class);

    @Bean
    ManejadorEvento<MeetCreadoData> meetEnTurno(TurnoRepository turnoRepository) {
        return ManejadorEvento.de("meet-en-turno", Topicos.CALENDARIO_MEET_CREADO_V1, MeetCreadoData.class,
                e -> guardarMeet(turnoRepository, e.data()));
    }

    static void guardarMeet(TurnoRepository turnoRepository, MeetCreadoData d) {
        turnoRepository.findById(d.turnoId()).ifPresentOrElse(turno -> {
            boolean cambia = false;
            if (d.meetUrl() != null && !d.meetUrl().isBlank() && !d.meetUrl().equals(turno.getTelemedicinaUrl())) {
                turno.setTelemedicinaUrl(d.meetUrl());
                cambia = true;
            }
            if (d.googleEventId() != null && !d.googleEventId().equals(turno.getGoogleEventId())) {
                turno.setGoogleEventId(d.googleEventId());
                cambia = true;
            }
            if (cambia) {
                turnoRepository.save(turno);
            }
        }, () -> log.warn("calendario.meet.creado de un turno que no existe en el monolito: {}", d.turnoId()));
    }
}
