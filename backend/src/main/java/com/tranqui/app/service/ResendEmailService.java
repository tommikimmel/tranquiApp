package com.tranqui.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ResendEmailService {

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void enviarCodigoVerificacion(String toEmail, String nombre, String codigo) {
        String asunto = "Código de verificación - Tranqui App";
        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 500px; border: 1px solid #e2e8f0; border-radius: 10px;\">" +
                        "<h2 style=\"color: #009ee3;\">¡Hola, %s!</h2>" +
                        "<p>Tu código de verificación para activar tu cuenta en <strong>Tranqui App</strong> es:</p>" +
                        "<div style=\"background: #f1f5f9; padding: 15px; text-align: center; border-radius: 8px; font-size: 24px; font-weight: bold; letter-spacing: 4px; color: #1e293b;\">%s</div>" +
                        "<p style=\"font-size: 13px; color: #64748b; margin-top: 15px;\">Este código es válido durante 15 minutos. Si no solicitaste este registro, podés ignorar este correo.</p>" +
                        "</div>",
                nombre != null ? nombre : "Usuario",
                codigo
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarCodigoRecuperacion(String toEmail, String nombre, String codigo) {
        String asunto = "Recuperación de contraseña - Tranqui App";
        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 500px; border: 1px solid #e2e8f0; border-radius: 10px;\">" +
                        "<h2 style=\"color: #1a73e8;\">Recuperación de contraseña</h2>" +
                        "<p>Hola %s, solicitaste restablecer tu contraseña en <strong>Tranqui App</strong>. Tu código de recuperación es:</p>" +
                        "<div style=\"background: #f1f5f9; padding: 15px; text-align: center; border-radius: 8px; font-size: 24px; font-weight: bold; letter-spacing: 4px; color: #1e293b;\">%s</div>" +
                        "<p style=\"font-size: 13px; color: #64748b; margin-top: 15px;\">El código expira en 15 minutos. Si no solicitaste este cambio, te recomendamos revisar la seguridad de tu cuenta.</p>" +
                        "</div>",
                nombre != null ? nombre : "Usuario",
                codigo
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarSolicitudCopiaDatos(String nombreUsuario, String emailUsuario, String rolUsuario) {
        String asuntoFinal = "[Ley 25.326 - Acceso a datos] Solicitud de " + rolUsuario;
        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 600px; border: 1px solid #e2e8f0; border-radius: 10px;\">" +
                        "<h2 style=\"color: #009ee3;\">Solicitud de copia de datos personales (Ley 25.326)</h2>" +
                        "<p><strong>De:</strong> %s (%s)</p>" +
                        "<p><strong>Rol:</strong> %s</p>" +
                        "<p>El usuario solicitó, desde \"Mi Cuenta\" &gt; \"Privacidad\", una copia de los datos " +
                        "personales que Tranqui App tiene registrados a su nombre.</p>" +
                        "</div>",
                nombreUsuario != null ? nombreUsuario : "Usuario",
                emailUsuario,
                rolUsuario
        );

        enviarCorreo("soporte@tranquisalud.com", asuntoFinal, htmlContent, emailUsuario);
    }

    // Sent from TicketService#agregarMensaje whenever an admin replies to a support ticket —
    // the creator (paciente/profesional) gets emailed instead of having to keep the app open to
    // notice, same rationale as every other "something happened while you were away" email here.
    public void enviarRespuestaTicket(String toEmail, String nombreUsuario, String asuntoTicket, String respuesta) {
        String asunto = "Respondieron tu consulta: " + asuntoTicket;
        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 500px; border: 1px solid #e2e8f0; border-radius: 10px;\">" +
                        "<h2 style=\"color: #009ee3;\">¡Hola, %s!</h2>" +
                        "<p>El equipo de soporte de <strong>Tranqui App</strong> respondió tu consulta \"%s\":</p>" +
                        "<div style=\"background: #f1f5f9; padding: 15px; border-radius: 8px; white-space: pre-wrap; color: #1e293b;\">%s</div>" +
                        "<p style=\"font-size: 13px; color: #64748b; margin-top: 15px;\">Podés ver la conversación completa y responder desde \"Mis Tickets\" en Tranqui App.</p>" +
                        "</div>",
                nombreUsuario != null ? nombreUsuario : "Usuario",
                asuntoTicket,
                respuesta
        );
        enviarCorreo(toEmail, asunto, htmlContent);
    }

    // Sent from AdminController#resetPassword — the only place this plaintext password exists
    // outside the admin's one-time response is this email, since it's stored hashed immediately.
    public void enviarPasswordTemporal(String toEmail, String nombreUsuario, String passwordTemporal) {
        String asunto = "Tu nueva contraseña temporal - Tranqui App";
        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 500px; border: 1px solid #e2e8f0; border-radius: 10px;\">" +
                        "<h2 style=\"color: #009ee3;\">¡Hola, %s!</h2>" +
                        "<p>Un administrador reseteó tu contraseña en <strong>Tranqui App</strong>. Tu contraseña temporal es:</p>" +
                        "<div style=\"background: #f1f5f9; padding: 15px; text-align: center; border-radius: 8px; font-size: 22px; font-weight: bold; letter-spacing: 2px; color: #1e293b;\">%s</div>" +
                        "<p style=\"font-size: 13px; color: #64748b; margin-top: 15px;\">Es temporal: al iniciar sesión con ella, te vamos a pedir que elijas una contraseña nueva antes de poder usar el resto de la aplicación. Si no solicitaste este cambio, contactate con nosotros.</p>" +
                        "</div>",
                nombreUsuario != null ? nombreUsuario : "Usuario",
                passwordTemporal
        );
        enviarCorreo(toEmail, asunto, htmlContent);
    }

    // Certificados/informes never get generated or stored server-side (see
    // TurnoService#marcarDocumentoEnviado) — the médico uploads whatever they wrote up
    // externally, and this sends it straight to the patient as a real email attachment instead
    // of persisting it anywhere in TranquiApp. Returns false (rather than throwing) on any
    // failure so the caller can decide not to mark the turno as sent when the patient never
    // actually got the file.
    public boolean enviarDocumentoAdjunto(String toEmail, String pacienteNombre, String medicoNombre, String tipoDocumento, String archivoDataUri, String archivoNombre) {
        String tipoLower = tipoDocumento != null ? tipoDocumento.toLowerCase() : "documento";
        String asunto = "Tu " + tipoLower + " de " + medicoNombre;
        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 500px; border: 1px solid #e2e8f0; border-radius: 10px;\">" +
                        "<h2 style=\"color: #009ee3;\">¡Hola, %s!</h2>" +
                        "<p><strong>%s</strong> te envió tu %s adjunto en este correo.</p>" +
                        "<p style=\"font-size: 13px; color: #64748b; margin-top: 15px;\">Enviado a través de Tranqui App.</p>" +
                        "</div>",
                pacienteNombre != null ? pacienteNombre : "Usuario",
                medicoNombre,
                tipoLower
        );

        String base64Content = extraerBase64(archivoDataUri);
        if (base64Content == null || base64Content.isBlank()) {
            log.error("enviarDocumentoAdjunto llamado sin contenido de archivo válido para {}", toEmail);
            return false;
        }
        String filename = archivoNombre != null && !archivoNombre.isBlank() ? archivoNombre : "documento";

        return enviarCorreoConAdjunto(toEmail, asunto, htmlContent, filename, base64Content);
    }

    private String extraerBase64(String dataUri) {
        if (dataUri == null) return null;
        int comma = dataUri.indexOf(',');
        return comma >= 0 ? dataUri.substring(comma + 1) : dataUri;
    }

    private boolean enviarCorreoConAdjunto(String toEmail, String asunto, String htmlBody, String filename, String base64Content) {
        if (apiKey == null || apiKey.trim().isEmpty() || !apiKey.startsWith("re_")) {
            log.info("==================================================================");
            log.info("[RESEND MOCK/DEV MODE] No hay API Key de Resend configurada.");
            log.info("Destinatario: {}", toEmail);
            log.info("Asunto: {}", asunto);
            log.info("Adjunto: {} ({} caracteres en base64)", filename, base64Content.length());
            log.info("Colocá RESEND_API_KEY en tu .env para envíos reales.");
            log.info("==================================================================");
            return true;
        }

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("from", String.format("Tranqui App <%s>", fromEmail));
            payloadMap.put("to", List.of(toEmail));
            payloadMap.put("subject", asunto);
            payloadMap.put("html", htmlBody);
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("filename", filename);
            attachment.put("content", base64Content);
            payloadMap.put("attachments", List.of(attachment));

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Documento enviado exitosamente mediante Resend a {}. Status: {}", toEmail, response.statusCode());
                return true;
            }
            log.error("Error al enviar documento adjunto vía Resend a {}. Status: {}. Respuesta: {}", toEmail, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("Excepción al enviar documento adjunto mediante Resend a {}", toEmail, e);
            return false;
        }
    }

    private void enviarCorreo(String toEmail, String asunto, String htmlBody) {
        enviarCorreo(toEmail, asunto, htmlBody, null);
    }

    private void enviarCorreo(String toEmail, String asunto, String htmlBody, String replyTo) {
        if (apiKey == null || apiKey.trim().isEmpty() || !apiKey.startsWith("re_")) {
            log.info("==================================================================");
            log.info("[RESEND MOCK/DEV MODE] No hay API Key de Resend configurada.");
            log.info("Destinatario: {}", toEmail);
            log.info("Asunto: {}", asunto);
            log.info("Contenido enviado (Simulación). Colocá RESEND_API_KEY en tu .env para envíos reales.");
            log.info("==================================================================");
            return;
        }

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("from", String.format("Tranqui App <%s>", fromEmail));
            payloadMap.put("to", List.of(toEmail));
            payloadMap.put("subject", asunto);
            payloadMap.put("html", htmlBody);
            if (replyTo != null && !replyTo.isBlank()) {
                payloadMap.put("reply_to", List.of(replyTo));
            }

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Correo enviado exitosamente mediante Resend a {}. Status: {}", toEmail, response.statusCode());
            } else {
                log.error("Error al enviar correo vía Resend a {}. Status: {}. Respuesta: {}", toEmail, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Excepción al enviar correo mediante Resend a {}", toEmail, e);
        }
    }
}
