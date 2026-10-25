package com.tranqui.notificaciones.interno;

import com.tranqui.notificaciones.mail.EnvioMailException;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fase 6 (decisión 3): el mail con el documento que el profesional le manda al paciente (certificado,
 * informe). Lo pide el servicio de turnos en el momento, con el archivo en el cuerpo: no viaja en eventos
 * (sección 5.3) y acá no se guarda. Solo con JWT de servicio ({@link FiltroServicioInterno}).
 */
@RestController
@RequestMapping("/internal/notificaciones")
public class DocumentosController {

    private static final Logger log = LoggerFactory.getLogger(DocumentosController.class);

    public record DocumentoAdjunto(String email, String pacienteNombre, String medicoNombre, String tipoDocumento,
                                   String archivoDataUri, String archivoNombre) {}

    public record Resultado(boolean enviado) {}

    private final ResendEmailService mails;

    public DocumentosController(ResendEmailService mails) {
        this.mails = mails;
    }

    /** {@code enviado=false} si el mail no salió (el profesional ve el error y puede reintentar). */
    @PostMapping("/documentos")
    public ResponseEntity<Resultado> enviar(@RequestBody DocumentoAdjunto d) {
        if (d.email() == null || d.email().isBlank()) {
            return ResponseEntity.badRequest().body(new Resultado(false));
        }
        try {
            mails.enviarDocumentoAdjunto(d.email(), d.pacienteNombre(), d.medicoNombre(), d.tipoDocumento(),
                    d.archivoDataUri(), d.archivoNombre());
            return ResponseEntity.ok(new Resultado(true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new Resultado(false));
        } catch (EnvioMailException e) {
            log.error("No se pudo mandar el documento adjunto: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new Resultado(false));
        }
    }
}
