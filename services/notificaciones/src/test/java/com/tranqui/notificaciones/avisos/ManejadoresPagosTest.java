package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.notificacion.NotificacionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManejadoresPagosTest {

    @Test
    @SuppressWarnings("unchecked")
    void conceptoAprobado_seEmpujaEnVivoAlProfesionalSinGuardarse() {
        Avisos avisos = mock(Avisos.class);
        NotificacionService notificaciones = mock(NotificacionService.class);
        Contacto paciente = new Contacto(1L, "ana@mail.com");
        paciente.setNombre("Ana");
        when(avisos.contacto(1L)).thenReturn(paciente);

        new ManejadoresPagos().enVivoConceptoAprobado(avisos, notificaciones).manejar(EventEnvelope.<ConceptoAprobadoData>builder()
                .eventId(UUID.randomUUID()).eventType("pagos.concepto.aprobado")
                .data(new ConceptoAprobadoData(40L, 2L, 1L, "CERTIFICADO")).build());

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificaciones).empujarSinGuardar(eq(2L), payload.capture());
        assertThat(payload.getValue()).containsEntry("id", 40L).containsEntry("pacienteNombre", "Ana")
                .containsEntry("tipo", "CERTIFICADO").containsKey("fecha");
        verify(avisos, never()).notificar(any(), anyString(), anyString(), anyString());
    }
}
