package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.notificacion.NotificacionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Avisos de pagos (Fase 2: la aprobación de un concepto, que antes salía del monolito por /ws-tranqui). */
@Configuration
public class ManejadoresPagos {

    @Bean
    ManejadorEvento<ConceptoAprobadoData> enVivoConceptoAprobado(Avisos avisos, NotificacionService notificaciones) {
        return ManejadorEvento.de("en-vivo-concepto-aprobado", Topicos.PAGOS_CONCEPTO_APROBADO_V1,
                ConceptoAprobadoData.class, e -> {
                    ConceptoAprobadoData d = e.data();
                    Contacto paciente = avisos.contacto(d.pacienteId());
                    // Mismo payload que armaba PagoWebhookHandler#procesarAprobacionConcepto.
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", d.solicitudId());
                    payload.put("pacienteNombre", paciente.getNombre());
                    payload.put("tipo", d.tipoConcepto());
                    payload.put("fecha", LocalDateTime.now().toString());
                    notificaciones.empujarSinGuardar(d.profesionalId(), payload);
                });
    }
}
