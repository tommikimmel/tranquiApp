package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.CodigoVerificacionGeneradoData;
import com.tranqui.eventos.data.PasswordTemporalGeneradaData;
import com.tranqui.eventos.data.SolicitudDatosData;
import com.tranqui.eventos.data.TicketCreadoData;
import com.tranqui.eventos.data.TicketMensajeRecibidoData;
import com.tranqui.eventos.data.TicketRespondidoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.contacto.ContactoService;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

/** Avisos de cuenta y soporte (AuthController y TicketService del monolito). */
@Configuration
public class ManejadoresIdentidad {

    private static final Pattern PATRON_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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

    @Bean
    ManejadorEvento<PasswordTemporalGeneradaData> mailPasswordTemporal(Avisos avisos, ResendEmailService mails,
                                                                      ContactoService contactos) {
        String consumidor = "mail-password-temporal";
        return ManejadorEvento.de(consumidor, Topicos.IDENTIDAD_PASSWORD_TEMPORAL_GENERADA_V1,
                PasswordTemporalGeneradaData.class, e -> {
                    PasswordTemporalGeneradaData d = e.data();
                    if (d == null) {
                        throw new EventoInvalidoException("El evento no contiene data");
                    }
                    if (d.usuarioId() == null) {
                        throw new EventoInvalidoException("Falta usuarioId");
                    }
                    if (d.email() == null || !PATRON_EMAIL.matcher(d.email()).matches()) {
                        throw new EventoInvalidoException("Email inválido o faltante");
                    }
                    if (d.passwordTemporal() == null || d.passwordTemporal().isBlank()) {
                        throw new EventoInvalidoException("Falta passwordTemporal");
                    }
                    String nombre = d.nombre() != null ? d.nombre()
                            : contactos.buscarPorEmail(d.email()).map(Contacto::getNombre).orElse(null);
                    // Por seguridad nunca se registra la contraseña temporal en los logs.
                    avisos.mail(e, consumidor, () -> mails.enviarPasswordTemporal(d.email(), nombre, d.passwordTemporal()));
                });
    }

    @Bean
    ManejadorEvento<SolicitudDatosData> mailSolicitudDatos(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-soporte-solicitud-datos";
        return ManejadorEvento.de(consumidor, Topicos.IDENTIDAD_SOPORTE_SOLICITUD_DATOS_V1,
                SolicitudDatosData.class, e -> {
                    SolicitudDatosData d = e.data();
                    if (d == null) {
                        throw new EventoInvalidoException("El evento no contiene data");
                    }
                    if (d.usuarioId() == null) {
                        throw new EventoInvalidoException("Falta usuarioId");
                    }
                    if (d.email() == null || !PATRON_EMAIL.matcher(d.email()).matches()) {
                        throw new EventoInvalidoException("Email inválido o faltante");
                    }
                    if (d.rol() == null || d.rol().isBlank()) {
                        throw new EventoInvalidoException("Falta rol");
                    }
                    avisos.mail(e, consumidor, () -> mails.enviarSolicitudCopiaDatos(d.nombreCompleto(), d.email(), d.rol()));
                });
    }
}
