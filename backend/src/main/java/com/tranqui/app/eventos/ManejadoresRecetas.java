package com.tranqui.app.eventos;

import com.tranqui.app.service.TurnoService;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.RecetaEmitidaData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 7: vuelta atrás de la Fase 6 con la Fase 7 activa.
 * El servicio de profesionales emite la receta y publica profesionales.receta.emitida.v1.
 * Si turnos.externo es false (los turnos los administra el monolito), el monolito marca
 * enviadas las compras de recetas pendientes de ese paciente. Con turnos.externo=true no hace
 * nada porque ya lo hace el servicio de turnos.
 */
@Configuration
public class ManejadoresRecetas {

    @Bean
    ManejadorEvento<RecetaEmitidaData> recetaEmitidaMonolito(
            TurnoService turnoService,
            @Value("${turnos.externo:false}") boolean turnosExterno) {
        return ManejadorEvento.de("receta-emitida-monolito", Topicos.PROFESIONALES_RECETA_EMITIDA_V1, RecetaEmitidaData.class,
                e -> procesar(turnoService, turnosExterno, e.data()));
    }

    static void procesar(TurnoService turnoService, boolean turnosExterno, RecetaEmitidaData data) {
        if (!turnosExterno && data != null && data.profesionalId() != null && data.pacienteId() != null) {
            turnoService.marcarRecetasEnviadasParaPaciente(data.profesionalId(), data.pacienteId());
        }
    }
}
