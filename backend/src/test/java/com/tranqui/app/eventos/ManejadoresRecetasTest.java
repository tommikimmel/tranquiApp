package com.tranqui.app.eventos;

import com.tranqui.app.service.TurnoService;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.RecetaEmitidaData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ManejadoresRecetasTest {

    private final TurnoService turnoService = mock(TurnoService.class);

    @Test
    @DisplayName("Con turnos.externo=false el monolito llama a marcarRecetasEnviadasParaPaciente")
    void conTurnosExternoFalse_marcaRecetasEnviadas() {
        ManejadoresRecetas config = new ManejadoresRecetas();
        ManejadorEvento<RecetaEmitidaData> manejador = config.recetaEmitidaMonolito(turnoService, false);

        assertThat(manejador.consumidor()).isEqualTo("receta-emitida-monolito");
        assertThat(manejador.topicos()).containsExactly(Topicos.PROFESIONALES_RECETA_EMITIDA_V1);

        EventEnvelope<RecetaEmitidaData> envelope = EventEnvelope.<RecetaEmitidaData>builder()
                .eventType(Topicos.PROFESIONALES_RECETA_EMITIDA_V1)
                .aggregateType("receta")
                .aggregateId("10")
                .aggregateVersion(1)
                .producer("profesionales")
                .data(new RecetaEmitidaData(1L, 2L))
                .build();

        manejador.manejar(envelope);

        verify(turnoService).marcarRecetasEnviadasParaPaciente(1L, 2L);
    }

    @Test
    @DisplayName("Con turnos.externo=true no hace nada (lo administra el servicio de turnos)")
    void conTurnosExternoTrue_noLlamaATurnoService() {
        ManejadoresRecetas config = new ManejadoresRecetas();
        ManejadorEvento<RecetaEmitidaData> manejador = config.recetaEmitidaMonolito(turnoService, true);

        EventEnvelope<RecetaEmitidaData> envelope = EventEnvelope.<RecetaEmitidaData>builder()
                .eventType(Topicos.PROFESIONALES_RECETA_EMITIDA_V1)
                .aggregateType("receta")
                .aggregateId("11")
                .aggregateVersion(1)
                .producer("profesionales")
                .data(new RecetaEmitidaData(1L, 2L))
                .build();

        manejador.manejar(envelope);

        verify(turnoService, never()).marcarRecetasEnviadasParaPaciente(1L, 2L);
    }
}
