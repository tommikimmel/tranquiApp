package com.tranqui.identidad.novedades;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El mail de novedades (Fase 9): la misma plantilla y el mismo envío individual que el monolito
 * ({@code ResendEmailService.htmlNovedades}, {@code enviarHtml} y {@code urlMiCuenta}). Es el único mail que sale de
 * identidad: los transaccionales los manda notificaciones por evento.
 *
 * <p>El envío individual (prueba de novedades y entorno local) va por SMTP con {@code mail.transport=smtp} (Mailpit) o
 * por la API de Resend; sin API key solo se registra en el log, como el monolito.
 */
@Component
public class MailNovedades {

    private static final Logger log = LoggerFactory.getLogger(MailNovedades.class);

    private static final String COLOR_INK = "#0f172a";
    private static final String COLOR_MUTED = "#64748b";
    private static final String COLOR_BORDER = "#e2e8f0";
    private static final String COLOR_CANVAS = "#eef2f7";
    private static final String COLOR_PANEL = "#f8fafc";
    // {badge-texto, badge-fondo, color-de-acento}, como TONO_INFO del monolito.
    private static final String[] TONO_INFO = {"#196530", "#eaf6ee", "#2fa84f"};

    private final ObjectProvider<JavaMailSender> mailSender;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${mail.transport:resend}")
    private String transporte;

    public MailNovedades(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * HTML del mail de novedades. urlBaja es el link de baja: en los Broadcasts de Resend,
     * "{{{RESEND_UNSUBSCRIBE_URL}}}" (lo reemplaza Resend por contacto); en los envíos individuales, Mi Cuenta.
     */
    public String htmlNovedades(String titulo, List<String> items, String urlBaja) {
        StringBuilder lista = new StringBuilder("<ul style=\"margin:0 0 8px;padding-left:20px;\">");
        for (String item : items) {
            lista.append("<li style=\"margin:0 0 8px;\">").append(escaparHtml(item)).append("</li>");
        }
        lista.append("</ul>");
        String cuerpo = "<p>Terminamos una actualización de Tranqui App. Esto es lo nuevo:</p>" + lista +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";margin-top:24px;\">" +
                "Recibís este mail porque tenés una cuenta en Tranqui App. Si no querés recibir más novedades, " +
                "<a href=\"" + urlBaja + "\" style=\"color:" + COLOR_MUTED + ";\">date de baja acá</a>. " +
                "Vas a seguir recibiendo los mails de tus turnos.</p>";
        return plantilla("Novedades", TONO_INFO, escaparHtml(titulo), cuerpo, "Ir a Tranqui App", frontendUrl);
    }

    public String urlMiCuenta() {
        return frontendUrl + "/mi-cuenta";
    }

    public String remitente() {
        return "Tranqui App <" + fromEmail + ">";
    }

    /** Envío individual de un HTML ya armado (prueba de novedades y envío en el entorno local). */
    public void enviarHtml(String destinatario, String asunto, String html) {
        if ("smtp".equalsIgnoreCase(transporte)) {
            enviarPorSmtp(destinatario, asunto, html);
            return;
        }
        if (apiKey == null || !apiKey.trim().startsWith("re_")) {
            log.info("[Novedades] Sin RESEND_API_KEY: no se envió el mail '{}' (simulado).", asunto);
            return;
        }
        try {
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("from", remitente());
            cuerpo.put("to", List.of(destinatario));
            cuerpo.put("subject", asunto);
            cuerpo.put("html", html);
            HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                log.error("[Novedades] Resend respondió {} al enviar '{}'", respuesta.statusCode(), asunto);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Novedades] Interrumpido enviando '{}'", asunto);
        } catch (Exception e) {
            log.error("[Novedades] Error enviando '{}' por Resend", asunto, e);
        }
    }

    private void enviarPorSmtp(String destinatario, String asunto, String html) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("[Novedades] mail.transport=smtp pero no hay spring.mail.host configurado: no se envió '{}'", asunto);
            return;
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(remitente());
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            sender.send(mensaje);
        } catch (Exception e) {
            log.error("[Novedades] Error enviando '{}' por SMTP", asunto, e);
        }
    }

    // La misma plantilla común que el resto de los mails de la app (ResendEmailService del monolito). Sin emojis
    // ni íconos: el badge es solo texto con color.
    private String plantilla(String badgeTexto, String[] tono, String titulo, String cuerpoHtml,
                             String ctaTexto, String ctaUrl) {
        String badgeColor = tono[0];
        String badgeFondo = tono[1];
        String acento = tono[2];

        String cta = "";
        if (ctaTexto != null && ctaUrl != null) {
            cta = "<table role=\"presentation\" width=\"100%\" style=\"margin:26px 0 4px;\"><tr><td align=\"center\">" +
                    "<a href=\"" + ctaUrl + "\" style=\"display:inline-block;background:" + acento + ";color:#ffffff;" +
                    "text-decoration:none;font-weight:600;font-size:14px;padding:13px 30px;border-radius:8px;\">" +
                    ctaTexto + "</a></td></tr></table>";
        }

        return "<div style=\"background:" + COLOR_CANVAS + ";padding:32px 16px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;\">" +
                "<table role=\"presentation\" width=\"100%\" style=\"max-width:560px;margin:0 auto;border-collapse:collapse;\">" +
                "<tr><td style=\"background:" + COLOR_INK + ";padding:26px 32px;border-radius:16px 16px 0 0;text-align:center;\">" +
                "<span style=\"font-size:21px;font-weight:700;color:#ffffff;letter-spacing:0.3px;\">Tranqui<span style=\"color:#4fbd58;\">App</span></span>" +
                "<div style=\"font-size:12px;color:#94a3b8;margin-top:4px;\">Salud mental, simple y segura</div>" +
                "</td></tr>" +
                "<tr><td style=\"background:#ffffff;padding:36px 32px 8px;border-left:1px solid " + COLOR_BORDER + ";border-right:1px solid " + COLOR_BORDER + ";\">" +
                "<span style=\"display:inline-block;background:" + badgeFondo + ";color:" + badgeColor + ";font-size:12px;font-weight:700;padding:5px 12px;border-radius:999px;letter-spacing:0.2px;\">" +
                badgeTexto + "</span>" +
                "<h1 style=\"font-size:21px;color:" + COLOR_INK + ";margin:16px 0 18px;line-height:1.3;\">" + titulo + "</h1>" +
                "<div style=\"font-size:15px;color:#334155;line-height:1.65;\">" + cuerpoHtml + "</div>" +
                cta +
                "</td></tr>" +
                "<tr><td style=\"background:" + COLOR_PANEL + ";padding:20px 32px;border:1px solid " + COLOR_BORDER + ";border-top:none;border-radius:0 0 16px 16px;\">" +
                "<p style=\"font-size:12px;color:#94a3b8;margin:0;line-height:1.5;\">Este es un mensaje automático de Tranqui App — por favor no respondas a este correo.<br>" +
                "© " + java.time.Year.now() + " Tranqui App. Todos los derechos reservados.</p>" +
                "</td></tr></table></div>";
    }

    private static String escaparHtml(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
