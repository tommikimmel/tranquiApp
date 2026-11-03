package com.tranqui.app.eventos;

import com.tranqui.app.service.CopiaTurnos;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.TurnoActualizadoData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 6: la copia del monolito de los turnos (estadísticas, historia clínica y recetas siguen leyendo
 * {@code turno}). Descarta versiones viejas; la resincronización periódica
 * ({@link com.tranqui.app.service.ResincronizacionTurnos}) corrige un evento perdido. Solo lo publica el
 * servicio de turnos. Se prende con {@code EVENTOS_CONSUMO_SERVICIO=monolito}.
 */
@Configuration
public class ManejadoresTurnos {

    @Bean
    ManejadorEvento<TurnoActualizadoData> copiaTurno(CopiaTurnos copia) {
        return ManejadorEvento.conVersion("copia-turno", Topicos.TURNOS_TURNO_ACTUALIZADO_V1, TurnoActualizadoData.class,
                e -> copia.aplicar(e.data(), e.aggregateVersion()));
    }
}
