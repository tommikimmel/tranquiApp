package com.tranqui.app.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Turno;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
@Slf4j
public class WhatsAppService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.number}")
    private String fromNumber;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isEmpty() && !accountSid.equals("ACmockaccount")) {
            try {
                Twilio.init(accountSid, authToken);
                log.info("Twilio initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize Twilio", e);
            }
        } else {
            log.info("Twilio initialized in mock/offline mode");
        }
    }

    public void enviarMensajeRecordatorio(Turno turno) {
        String cuerpoMensaje = construirCuerpoMensaje(turno);
        String para = "whatsapp:" + turno.getPaciente().getTelefono();
        String de = "whatsapp:" + fromNumber;

        createTwilioMessage(para, de, cuerpoMensaje);
    }

    public String construirCuerpoMensaje(Turno turno) {
        if (turno == null) {
            throw new IllegalArgumentException("El turno no puede ser nulo");
        }
        if (turno.getPaciente() == null) {
            throw new IllegalArgumentException("El paciente no puede ser nulo");
        }
        if (turno.getPaciente().getNombre() == null || turno.getPaciente().getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del paciente es requerido");
        }
        if (turno.getPaciente().getTelefono() == null || turno.getPaciente().getTelefono().trim().isEmpty()) {
            throw new IllegalArgumentException("El teléfono del paciente es requerido");
        }
        if (turno.getMedico() == null) {
            throw new IllegalArgumentException("El médico no puede ser nulo");
        }
        if (turno.getMedico().getNombre() == null || turno.getMedico().getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del médico es requerido");
        }
        if (turno.getFecha() == null) {
            throw new IllegalArgumentException("La fecha del turno es requerida");
        }
        if (turno.getHoraInicio() == null) {
            throw new IllegalArgumentException("La hora de inicio del turno es requerida");
        }
        // Los turnos PRESENCIALES nunca tienen (ni necesitan) telemedicinaUrl — exigirla acá
        // hacía que este método tirara IllegalArgumentException para CUALQUIER turno presencial,
        // y como NotificationScheduler atrapa esa excepción por-turno, los pacientes presenciales
        // nunca recibían su recordatorio de WhatsApp. Para turnos ONLINE (o turnos viejos sin
        // modalidad cargada, de antes de que existiera este campo) se mantiene la validación
        // original: siguen siendo por videollamada, así que el link es obligatorio.
        boolean esPresencial = turno.getModalidad() == Modalidad.PRESENCIAL;
        if (!esPresencial && (turno.getTelemedicinaUrl() == null || turno.getTelemedicinaUrl().trim().isEmpty())) {
            throw new IllegalArgumentException("La URL de telemedicina es requerida");
        }

        if (esPresencial) {
            return String.format(
                "Hola %s, recordatorio de tu turno con el Dr. %s mañana %s a las %s hs. Te esperamos en el consultorio.",
                turno.getPaciente().getNombre(),
                turno.getMedico().getNombre(),
                turno.getFecha().toString(),
                turno.getHoraInicio().toString()
            );
        }

        return String.format(
            "Hola %s, recordatorio de tu turno con el Dr. %s mañana %s a las %s hs. Enlace de la videollamada: %s",
            turno.getPaciente().getNombre(),
            turno.getMedico().getNombre(),
            turno.getFecha().toString(),
            turno.getHoraInicio().toString(),
            turno.getTelemedicinaUrl()
        );
    }

    public void enviarMensajeWhatsApp(String telefono, String texto) {
        String para = "whatsapp:" + telefono;
        String de = "whatsapp:" + fromNumber;
        createTwilioMessage(para, de, texto);
    }

    protected void createTwilioMessage(String para, String de, String cuerpoMensaje) {
        if (accountSid == null || accountSid.isEmpty() || accountSid.equals("ACmockaccount")) {
            log.info("Mock/Offline mode: Message to {} from {}: {}", para, de, cuerpoMensaje);
            return;
        }
        Message.creator(
            new PhoneNumber(para),
            new PhoneNumber(de),
            cuerpoMensaje
        ).create();
    }
}
