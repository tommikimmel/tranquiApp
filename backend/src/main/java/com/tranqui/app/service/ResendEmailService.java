package com.tranqui.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

    private void enviarCorreo(String toEmail, String asunto, String htmlBody) {
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
            String jsonPayload = String.format(
                    "{\"from\":\"Tranqui App <%s>\",\"to\":[\"%s\"],\"subject\":\"%s\",\"html\":%s}",
                    fromEmail,
                    toEmail,
                    escapeJson(asunto),
                    escapeJson(htmlBody)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
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

    private String escapeJson(String input) {
        if (input == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
