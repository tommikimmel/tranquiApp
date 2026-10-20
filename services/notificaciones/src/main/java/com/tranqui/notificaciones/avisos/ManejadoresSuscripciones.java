package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mails al profesional sobre su suscripción (SubscriptionService, SubscriptionReconciliationScheduler
 * y AdminSubscriptionController del monolito). Igual que antes, no dependen de la preferencia de mails.
 */
@Configuration
public class ManejadoresSuscripciones {

    @Bean
    ManejadorEvento<SuscripcionAvisoData> mailAvisoSuscripcion(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-aviso-suscripcion";
        return ManejadorEvento.de(consumidor, Topicos.SUSCRIPCIONES_AVISO_PROGRAMADO_V1, SuscripcionAvisoData.class, e -> {
            SuscripcionAvisoData d = e.data();
            Contacto prof = avisos.contacto(d.profesionalId());
            if (prof.getEmail() == null) {
                return;
            }
            String email = prof.getEmail();
            String nombre = prof.getNombre();
            String plan = d.nombrePlan();
            Runnable envio = switch (d.tipo()) {
                case SuscripcionAvisoData.PREAVISO_RENOVACION ->
                        () -> mails.enviarPreavisoRenovacionSuscripcion(email, nombre, plan, d.monto(), d.fecha(), d.ciclo());
                case SuscripcionAvisoData.PAGO_CONFIRMADO ->
                        () -> mails.enviarConfirmacionPagoSuscripcion(email, nombre, plan, d.monto(), d.fecha(),
                                d.fechaHasta(), d.comprobante());
                case SuscripcionAvisoData.PAGO_FALLIDO ->
                        () -> mails.enviarAvisoPagoFallidoSuscripcion(email, nombre, plan, d.monto(), d.fecha());
                case SuscripcionAvisoData.CANCELACION_CONFIRMADA ->
                        () -> mails.enviarConfirmacionCancelacionSuscripcion(email, nombre, plan, d.fecha());
                case SuscripcionAvisoData.SUSPENSION ->
                        () -> mails.enviarAvisoSuspensionSuscripcion(email, nombre, plan);
                case SuscripcionAvisoData.VENCIMIENTO_MANUAL ->
                        () -> mails.enviarAvisoVencimientoManualSuscripcion(email, nombre, plan, d.fecha(),
                                d.diasRestantes() != null ? d.diasRestantes() : 0);
                default -> throw new EventoInvalidoException("Tipo de aviso de suscripción desconocido: " + d.tipo());
            };
            avisos.mail(e, consumidor, envio);
        });
    }
}
