package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.eventos.data.ReembolsoFallidoData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.notificaciones.mail.ResendEmailService;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.notificacion.NotificacionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Avisos de pagos. Fase 2: la aprobación de un concepto, que antes salía del monolito por /ws-tranqui.
 * Fase 4: el segundo mail de una cancelación con reembolso (realizado o en revisión).
 */
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

    /**
     * Solo los reembolsos que hizo el servicio de pagos ({@code avisarPaciente}): los del monolito (vuelta
     * atrás) pasan dentro de la cancelación y su mail ya lo dice.
     */
    @Bean
    ManejadorEvento<ReembolsoRealizadoData> mailReembolsoRealizado(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-reembolso-realizado";
        return ManejadorEvento.de(consumidor, Topicos.PAGOS_REEMBOLSO_REALIZADO_V1, ReembolsoRealizadoData.class, e -> {
            ReembolsoRealizadoData d = e.data();
            if (!Boolean.TRUE.equals(d.avisarPaciente()) || d.pacienteId() == null) {
                return;
            }
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            avisos.mail(e, consumidor, () -> mails.enviarReembolsoRealizadoPaciente(paciente.getEmail(),
                    paciente.getNombre(), medico.getNombre(), d.fecha(), Avisos.hora(d.horaInicio()), d.monto()));
        });
    }

    @Bean
    ManejadorEvento<ReembolsoFallidoData> mailReembolsoFallido(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-reembolso-fallido";
        return ManejadorEvento.de(consumidor, Topicos.PAGOS_REEMBOLSO_FALLIDO_V1, ReembolsoFallidoData.class, e -> {
            ReembolsoFallidoData d = e.data();
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            avisos.mail(e, consumidor, () -> mails.enviarReembolsoPendientePaciente(paciente.getEmail(),
                    paciente.getNombre(), medico.getNombre(), d.fecha(), Avisos.hora(d.horaInicio())));
        });
    }
}
