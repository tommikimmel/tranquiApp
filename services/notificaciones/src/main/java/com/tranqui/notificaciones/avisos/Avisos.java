package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.contacto.ContactoService;
import com.tranqui.notificaciones.mail.ClaveIdempotencia;
import com.tranqui.notificaciones.notificacion.NotificacionService;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/** Lo que comparten los handlers: contactos, notificaciones en la app y mails idempotentes. */
@Component
public class Avisos {

    private final ContactoService contactos;
    private final NotificacionService notificaciones;

    public Avisos(ContactoService contactos, NotificacionService notificaciones) {
        this.contactos = contactos;
        this.notificaciones = notificaciones;
    }

    public Contacto contacto(Long id) {
        return contactos.obtener(id);
    }

    public List<Contacto> administradores() {
        return contactos.administradores();
    }

    public void notificar(Contacto destinatario, String titulo, String mensaje, String tipo) {
        notificaciones.crear(destinatario, titulo, mensaje, tipo);
    }

    /**
     * Manda un mail con la clave de idempotencia {@code eventId:consumidor}: si el evento se
     * reintenta, el proveedor no lo vuelve a mandar.
     */
    public void mail(EventEnvelope<?> evento, String consumidor, Runnable envio) {
        ClaveIdempotencia.con(evento.eventId() + ":" + consumidor, envio);
    }

    /** Mails al paciente: el monolito los salteaba si el usuario desactivó los mails en Mi Cuenta. */
    public boolean aceptaMails(Contacto contacto) {
        return contacto.getEmail() != null && contacto.isEmailHabilitado();
    }

    public static LocalTime hora(String hora) {
        return hora == null || hora.isBlank() ? null : LocalTime.parse(hora);
    }
}
