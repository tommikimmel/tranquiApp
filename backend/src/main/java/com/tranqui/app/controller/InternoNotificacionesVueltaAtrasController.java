package com.tranqui.app.controller;

import com.tranqui.app.service.ResendEmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fase 6: la ruta del documento adjunto del servicio de notificaciones, con la misma URL. El gateway manda
 * {@code /internal/notificaciones/**} a {@code TRANQUI_SERVICES_NOTIFICACIONES_URI}, que sin el servicio (vuelta
 * atrás de la Fase 1) es el monolito: el servicio de turnos pide siempre la misma ruta.
 */
@RestController
@RequestMapping("/internal/notificaciones")
public class InternoNotificacionesVueltaAtrasController {

    public record DocumentoAdjunto(String email, String pacienteNombre, String medicoNombre, String tipoDocumento,
                                   String archivoDataUri, String archivoNombre) {}

    public record RecetaEmitida(String email, String pacienteNombre, String medicoNombre,
                                String matricula, String pdfUrl, String nroRecetario) {}

    public record Resultado(boolean enviado) {}

    private final ResendEmailService mails;

    public InternoNotificacionesVueltaAtrasController(ResendEmailService mails) {
        this.mails = mails;
    }

    @PostMapping("/documentos")
    public ResponseEntity<Resultado> enviar(@RequestBody DocumentoAdjunto d) {
        if (d.email() == null || d.email().isBlank()) {
            return ResponseEntity.badRequest().body(new Resultado(false));
        }
        boolean enviado = mails.enviarDocumentoAdjunto(d.email(), d.pacienteNombre(), d.medicoNombre(), d.tipoDocumento(),
                d.archivoDataUri(), d.archivoNombre());
        return ResponseEntity.status(enviado ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(new Resultado(enviado));
    }

    /**
     * Fase 7: vuelta atrás del mail de recetas del servicio de notificaciones.
     */
    @PostMapping("/recetas")
    public ResponseEntity<Resultado> enviarReceta(@RequestBody RecetaEmitida r) {
        if (r.email() == null || r.email().isBlank()) {
            return ResponseEntity.badRequest().body(new Resultado(false));
        }
        boolean enviado = mails.enviarRecetaEmitidaPaciente(r.email(), r.pacienteNombre(), r.medicoNombre(),
                r.matricula(), r.pdfUrl(), r.nroRecetario());
        return ResponseEntity.status(enviado ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(new Resultado(enviado));
    }
}
