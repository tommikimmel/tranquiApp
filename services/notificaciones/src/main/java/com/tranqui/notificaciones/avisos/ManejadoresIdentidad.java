package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.CodigoVerificacionGeneradoData;
import com.tranqui.eventos.data.TicketCreadoData;
import com.tranqui.eventos.data.TicketMensajeRecibidoData;
import com.tranqui.eventos.data.TicketRespondidoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.contacto.ContactoService;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Avisos de cuenta y soporte (AuthController y TicketService del monolito). */
@Configuration
public class ManejadoresIdentidad {

    @Bean
    ManejadorEvento<CodigoVerificacionGeneradoData> mailCodigoVerificacion(Avisos avisos, ResendEmailService mails,
                                                                           ContactoService contactos) {
        String consumidor = "mail-codigo-verificacion";
        return ManejadorEvento.de(consumidor, Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1,
                CodigoVerificacionGeneradoData.class, e -> {
                    CodigoVerificacionGeneradoData d = e.data();
                    // El código no espera a la copia de contactos: mail y nombre vienen en el evento.
                    String nombre = d.nombre() != null ? d.nombre()
                            : contactos.buscarPorEmail(d.email()).map(Contacto::getNombre).orElse(null);
                    if ("PASSWORD_RESET".equals(d.tipo())) {
                        avisos.mail(e, consumidor, () -> mails.enviarCodigoRecuperacion(d.email(), nombre, d.codigo()));
                    } else {
                        avisos.mail(e, consumidor, () -> mails.enviarCodigoVerificacion(d.email(), nombre, d.codigo()));
                    }
                });
    }

    @Bean
    ManejadorEvento<TicketRespondidoData> notifTicketRespondido(Avisos avisos) {
        return ManejadorEvento.de("notif-ticket-respondido", Topicos.IDENTIDAD_TICKET_RESPONDIDO_V1,
                TicketRespondidoData.class, e -> {
                    Contacto creador = avisos.contacto(e.data().usuarioId());
                    avisos.notificar(creador, "Respondieron tu ticket",
                            "Soporte respondió: \"" + e.data().asunto() + "\"", "TICKET_RESPUESTA");
                });
    }

    @Bean
    ManejadorEvento<TicketRespondidoData> mailTicketRespondido(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-ticket-respondido";
        return ManejadorEvento.de(consumidor, Topicos.IDENTIDAD_TICKET_RESPONDIDO_V1, TicketRespondidoData.class, e -> {
            TicketRespondidoData d = e.data();
            Contacto creador = avisos.contacto(d.usuarioId());
            String email = d.email() != null ? d.email() : creador.getEmail();
            avisos.mail(e, consumidor, () -> mails.enviarRespuestaTicket(email, creador.getNombre(), d.asunto(), d.respuesta()));
        });
    }

    @Bean
    ManejadorEvento<TicketCreadoData> notifTicketCreado(Avisos avisos) {
        return ManejadorEvento.de("notif-ticket-creado", Topicos.IDENTIDAD_TICKET_CREADO_V1, TicketCreadoData.class, e -> {
            for (Contacto admin : avisos.administradores()) {
                avisos.notificar(admin, "Nuevo ticket de soporte",
                        e.data().creadorNombre() + " abrió un ticket: " + e.data().asunto(), "TICKET_NUEVO");
            }
        });
    }

    @Bean
    ManejadorEvento<TicketMensajeRecibidoData> notifTicketMensajeRecibido(Avisos avisos) {
        return ManejadorEvento.de("notif-ticket-mensaje-recibido", Topicos.IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1,
                TicketMensajeRecibidoData.class, e -> {
                    for (Contacto admin : avisos.administradores()) {
                        avisos.notificar(admin, "Nueva respuesta en un ticket",
                                e.data().autorNombre() + " respondió: \"" + e.data().asunto() + "\"", "TICKET_RESPUESTA");
                    }
                });
    }
}
