package com.tranqui.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ================================================================================
    // DISEÑO COMPARTIDO — plantilla única de la que salen TODOS los correos de esta
    // clase, para que se vean como parte de un mismo producto en vez de cada uno con su
    // propio estilo suelto. Cambiar el diseño de un correo es cambiar esta sección.
    // ================================================================================

    private static final String COLOR_INK = "#0f172a";
    private static final String COLOR_MUTED = "#64748b";
    private static final String COLOR_BORDER = "#e2e8f0";
    private static final String COLOR_CANVAS = "#eef2f7";
    private static final String COLOR_PANEL = "#f8fafc";

    // {badge-texto, badge-fondo, color-de-acento (CTA / detalles)} por categoría semántica.
    private static final String[] TONO_INFO = {"#196530", "#eaf6ee", "#2fa84f"};
    private static final String[] TONO_EXITO = {"#15803d", "#dcfce7", "#16a34a"};
    private static final String[] TONO_ALERTA = {"#b45309", "#fef3c7", "#d97706"};
    private static final String[] TONO_PELIGRO = {"#b91c1c", "#fee2e2", "#dc2626"};
    private static final String[] TONO_NEUTRO = {"#475569", "#f1f5f9", "#475569"};

    private String plantilla(String badgeEmoji, String badgeTexto, String[] tono, String titulo, String cuerpoHtml) {
        return plantilla(badgeEmoji, badgeTexto, tono, titulo, cuerpoHtml, null, null);
    }

    private String plantilla(String badgeEmoji, String badgeTexto, String[] tono, String titulo, String cuerpoHtml,
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
                badgeEmoji + " " + badgeTexto + "</span>" +
                "<h1 style=\"font-size:21px;color:" + COLOR_INK + ";margin:16px 0 18px;line-height:1.3;\">" + titulo + "</h1>" +
                "<div style=\"font-size:15px;color:#334155;line-height:1.65;\">" + cuerpoHtml + "</div>" +
                cta +
                "</td></tr>" +
                "<tr><td style=\"background:" + COLOR_PANEL + ";padding:20px 32px;border:1px solid " + COLOR_BORDER + ";border-top:none;border-radius:0 0 16px 16px;\">" +
                "<p style=\"font-size:12px;color:#94a3b8;margin:0;line-height:1.5;\">Este es un mensaje automático de Tranqui App — por favor no respondas a este correo.<br>" +
                "© " + java.time.Year.now() + " Tranqui App. Todos los derechos reservados.</p>" +
                "</td></tr></table></div>";
    }

    // Caja destacada centrada — usada para códigos de verificación y contraseñas temporales.
    private String cajaDestacada(String valor) {
        return "<div style=\"background:" + COLOR_PANEL + ";border:1px solid " + COLOR_BORDER + ";border-radius:10px;" +
                "padding:16px;text-align:center;margin:18px 0;\">" +
                "<span style=\"font-size:26px;font-weight:700;letter-spacing:5px;color:" + COLOR_INK + ";\">" + valor + "</span></div>";
    }

    // Panel con filas clave/valor (fecha, profesional, monto, etc.) — la forma estándar de mostrar
    // los datos de un turno o una suscripción dentro del cuerpo del correo.
    private String panelInfo(String... filasLabelValor) {
        StringBuilder filas = new StringBuilder();
        for (int i = 0; i + 1 < filasLabelValor.length; i += 2) {
            String label = filasLabelValor[i];
            String valor = filasLabelValor[i + 1];
            if (valor == null || valor.isBlank()) continue;
            filas.append("<tr>")
                    .append("<td style=\"padding:9px 0;border-bottom:1px solid ").append(COLOR_BORDER)
                    .append(";font-size:13px;color:").append(COLOR_MUTED).append(";\">").append(label).append("</td>")
                    .append("<td style=\"padding:9px 0;border-bottom:1px solid ").append(COLOR_BORDER)
                    .append(";font-size:13px;color:").append(COLOR_INK).append(";font-weight:600;text-align:right;\">")
                    .append(valor).append("</td></tr>");
        }
        return "<div style=\"background:" + COLOR_PANEL + ";border:1px solid " + COLOR_BORDER + ";border-radius:10px;padding:2px 16px;margin:18px 0;\">" +
                "<table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;\">" + filas + "</table></div>";
    }

    // Fila de dos botones (acción principal + acción secundaria) — usada donde el destinatario
    // tiene dos caminos posibles desde el mismo correo (ej. confirmar o avisar que no puede asistir).
    private String dosBotones(String textoA, String urlA, String colorA, String textoB, String urlB, String colorB) {
        return "<table role=\"presentation\" width=\"100%\" style=\"margin:26px 0 4px;border-collapse:collapse;\"><tr>" +
                "<td width=\"50%\" style=\"padding:0 6px 0 0;\">" +
                "<a href=\"" + urlA + "\" style=\"display:block;text-align:center;background:" + colorA + ";color:#ffffff;" +
                "text-decoration:none;font-weight:600;font-size:14px;padding:13px 8px;border-radius:8px;\">" + textoA + "</a></td>" +
                "<td width=\"50%\" style=\"padding:0 0 0 6px;\">" +
                "<a href=\"" + urlB + "\" style=\"display:block;text-align:center;background:#ffffff;color:" + colorB + ";" +
                "text-decoration:none;font-weight:600;font-size:14px;padding:11.5px 8px;border-radius:8px;border:1.5px solid " + colorB + ";\">" +
                textoB + "</a></td></tr></table>";
    }

    private String nombreOUsuario(String nombre) {
        return nombre != null && !nombre.isBlank() ? nombre : "Usuario";
    }

    // ================================================================================
    // CUENTA / SEGURIDAD
    // ================================================================================

    public void enviarCodigoVerificacion(String toEmail, String nombre, String codigo) {
        String asunto = "Código de verificación - Tranqui App";
        String cuerpo = "<p>Tu código de verificación para activar tu cuenta en <strong>Tranqui App</strong> es:</p>" +
                cajaDestacada(codigo) +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";margin-top:15px;\">Este código es válido durante 15 minutos. Si no solicitaste este registro, podés ignorar este correo.</p>";
        String html = plantilla("✅", "Verificación de cuenta", TONO_INFO,
                "¡Hola, " + nombreOUsuario(nombre) + "!", cuerpo);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarCodigoRecuperacion(String toEmail, String nombre, String codigo) {
        String asunto = "Recuperación de contraseña - Tranqui App";
        String cuerpo = "<p>Hola " + nombreOUsuario(nombre) + ", solicitaste restablecer tu contraseña en <strong>Tranqui App</strong>. Tu código de recuperación es:</p>" +
                cajaDestacada(codigo) +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";margin-top:15px;\">El código expira en 15 minutos. Si no solicitaste este cambio, te recomendamos revisar la seguridad de tu cuenta.</p>";
        String html = plantilla("🔑", "Recuperación de contraseña", TONO_INFO,
                "Recuperación de contraseña", cuerpo);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarSolicitudCopiaDatos(String nombreUsuario, String emailUsuario, String rolUsuario) {
        String asuntoFinal = "[Ley 25.326 - Acceso a datos] Solicitud de " + rolUsuario;
        String cuerpo = "<p><strong>De:</strong> " + nombreOUsuario(nombreUsuario) + " (" + emailUsuario + ")</p>" +
                "<p><strong>Rol:</strong> " + rolUsuario + "</p>" +
                "<p>El usuario solicitó, desde \"Mi Cuenta\" &gt; \"Privacidad\", una copia de los datos personales que Tranqui App tiene registrados a su nombre.</p>";
        String html = plantilla("📄", "Solicitud Ley 25.326", TONO_INFO,
                "Solicitud de copia de datos personales", cuerpo);
        enviarCorreo("soporte@tranquisalud.com", asuntoFinal, html, emailUsuario);
    }

    // Sent from TicketService#agregarMensaje whenever an admin replies to a support ticket —
    // the creator (paciente/profesional) gets emailed instead of having to keep the app open to
    // notice, same rationale as every other "something happened while you were away" email here.
    public void enviarRespuestaTicket(String toEmail, String nombreUsuario, String asuntoTicket, String respuesta) {
        String asunto = "Respondieron tu consulta: " + asuntoTicket;
        String cuerpo = "<p>El equipo de soporte de <strong>Tranqui App</strong> respondió tu consulta \"" + asuntoTicket + "\":</p>" +
                "<div style=\"background:" + COLOR_PANEL + ";border:1px solid " + COLOR_BORDER + ";padding:15px;border-radius:8px;white-space:pre-wrap;color:" + COLOR_INK + ";margin:16px 0;\">" + respuesta + "</div>" +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";\">Podés ver la conversación completa y responder desde \"Mis Tickets\" en Tranqui App.</p>";
        String html = plantilla("💬", "Respuesta de soporte", TONO_INFO,
                "¡Hola, " + nombreOUsuario(nombreUsuario) + "!", cuerpo, "Ir a Mis Tickets", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // Sent from AdminController#resetPassword — the only place this plaintext password exists
    // outside the admin's one-time response is this email, since it's stored hashed immediately.
    public void enviarPasswordTemporal(String toEmail, String nombreUsuario, String passwordTemporal) {
        String asunto = "Tu nueva contraseña temporal - Tranqui App";
        String cuerpo = "<p>Un administrador reseteó tu contraseña en <strong>Tranqui App</strong>. Tu contraseña temporal es:</p>" +
                cajaDestacada(passwordTemporal) +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";margin-top:15px;\">Es temporal: al iniciar sesión con ella, te vamos a pedir que elijas una contraseña nueva antes de poder usar el resto de la aplicación. Si no solicitaste este cambio, contactate con nosotros.</p>";
        String html = plantilla("🔐", "Nueva contraseña temporal", TONO_ALERTA,
                "¡Hola, " + nombreOUsuario(nombreUsuario) + "!", cuerpo);
        enviarCorreo(toEmail, asunto, html);
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
        String cuerpo = "<p><strong>" + medicoNombre + "</strong> te envió tu " + tipoLower + " adjunto en este correo.</p>" +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";\">Enviado a través de Tranqui App.</p>";
        String html = plantilla("📎", "Documento adjunto", TONO_EXITO,
                "¡Hola, " + nombreOUsuario(pacienteNombre) + "!", cuerpo);

        String base64Content = extraerBase64(archivoDataUri);
        if (base64Content == null || base64Content.isBlank()) {
            log.error("enviarDocumentoAdjunto llamado sin contenido de archivo válido para {}", toEmail);
            return false;
        }
        String filename = archivoNombre != null && !archivoNombre.isBlank() ? archivoNombre : "documento";

        return enviarCorreoConAdjunto(toEmail, asunto, html, filename, base64Content);
    }

    // ================================================================================
    // TURNOS — notificaciones al profesional (nuevo / cancelado / reprogramado)
    // ================================================================================

    public void enviarNuevoTurnoProfesional(String toEmail, String nombreProfesional, String nombrePaciente,
                                             LocalDate fecha, LocalTime hora, String modalidadLabel, String tipoLabel) {
        String asunto = "Nuevo turno reservado — " + nombrePaciente;
        String cuerpo = "<p>" + nombrePaciente + " reservó un turno con vos. Estos son los detalles:</p>" +
                panelInfo(
                        "Paciente", nombrePaciente,
                        "Fecha", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null,
                        "Modalidad", modalidadLabel,
                        "Tipo de consulta", tipoLabel
                );
        String html = plantilla("🗓️", "Nuevo turno reservado", TONO_EXITO,
                "¡Tenés un nuevo turno, " + nombreOUsuario(nombreProfesional) + "!", cuerpo,
                "Ver en Tranqui App", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarTurnoCanceladoProfesional(String toEmail, String nombreProfesional, String nombrePaciente,
                                                 LocalDate fecha, LocalTime hora) {
        String asunto = "Turno cancelado — " + nombrePaciente;
        String cuerpo = "<p>El siguiente turno fue cancelado:</p>" +
                panelInfo(
                        "Paciente", nombrePaciente,
                        "Fecha", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null
                );
        String html = plantilla("❌", "Turno cancelado", TONO_PELIGRO,
                "Un turno fue cancelado, " + nombreOUsuario(nombreProfesional), cuerpo,
                "Ver mi agenda", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarTurnoModificadoProfesional(String toEmail, String nombreProfesional, String nombrePaciente,
                                                  LocalDate fechaNueva, LocalTime horaNueva) {
        String asunto = "Turno reprogramado — " + nombrePaciente;
        String cuerpo = "<p>Reprogramaste el turno de " + nombrePaciente + ". La nueva fecha y hora son:</p>" +
                panelInfo(
                        "Paciente", nombrePaciente,
                        "Nueva fecha", fechaNueva != null ? fechaNueva.format(FECHA_FMT) : null,
                        "Nueva hora", horaNueva != null ? horaNueva.format(HORA_FMT) + " hs" : null
                );
        String html = plantilla("🔄", "Turno reprogramado", TONO_ALERTA,
                "Turno reprogramado, " + nombreOUsuario(nombreProfesional), cuerpo,
                "Ver mi agenda", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // ================================================================================
    // TURNOS — confirmación de asistencia al paciente (2 días antes)
    // ================================================================================

    // Enviado por TurnoService#enviarRecordatoriosConfirmacionAsistencia, 2 días antes de la
    // fecha del turno, solo para turnos reales (nunca para documentos/recetas — esos no tienen
    // ocupaAgenda). confirmarUrl y noAsistiraUrl son enlaces públicos protegidos por token
    // (Turno.tokenConfirmacionAsistencia) que el paciente puede tocar sin necesidad de loguearse.
    public void enviarConfirmacionAsistencia(String toEmail, String nombrePaciente, String nombreProfesional,
                                              LocalDate fecha, LocalTime hora, String modalidadLabel, String tipoLabel,
                                              String direccion, String confirmarUrl, String noAsistiraUrl) {
        String asunto = "Confirmá tu turno del " + (fecha != null ? fecha.format(FECHA_FMT) : "");
        String cuerpo = "<p>Tu turno con <strong>" + nombreProfesional + "</strong> se acerca. Confirmá tu asistencia para que el profesional sepa que vas a estar, o avisanos si no vas a poder ir.</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Fecha", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null,
                        "Modalidad", modalidadLabel,
                        "Tipo de consulta", tipoLabel,
                        "Lugar", direccion
                ) +
                dosBotones("Confirmar asistencia", confirmarUrl, TONO_EXITO[2],
                        "No podré asistir", noAsistiraUrl, TONO_PELIGRO[2]) +
                "<p style=\"font-size:12px;color:" + COLOR_MUTED + ";margin-top:18px;\">Si tocás \"No podré asistir\", tu turno se cancela automáticamente siguiendo la política de cancelación habitual.</p>";
        String html = plantilla("📅", "Confirmá tu turno", TONO_ALERTA,
                "¡Hola, " + nombreOUsuario(nombrePaciente) + "!", cuerpo);
        enviarCorreo(toEmail, asunto, html);
    }

    // ================================================================================
    // SUSCRIPCIÓN — próxima a vencer / vencida / pago confirmado / cancelada
    // ================================================================================

    public void enviarSuscripcionProximaAVencer(String toEmail, String nombreProfesional, String planNombre, LocalDateTime vence) {
        String asunto = "Tu suscripción a Tranqui App vence pronto";
        String cuerpo = "<p>Tu suscripción al plan <strong>" + planNombre + "</strong> está por vencer. Renovala para no perder acceso a Tranqui App.</p>" +
                panelInfo(
                        "Plan", planNombre,
                        "Vence el", vence != null ? vence.format(FECHA_FMT) : null
                );
        String html = plantilla("⏰", "Tu suscripción vence pronto", TONO_ALERTA,
                "Renová tu suscripción, " + nombreOUsuario(nombreProfesional), cuerpo,
                "Renovar suscripción", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarSuscripcionVencida(String toEmail, String nombreProfesional, String planNombre) {
        String asunto = "Tu suscripción a Tranqui App venció";
        String cuerpo = "<p>Tu suscripción al plan <strong>" + planNombre + "</strong> venció y tu acceso a Tranqui App quedó suspendido. Reactivala para volver a usar la plataforma.</p>" +
                panelInfo("Plan", planNombre);
        String html = plantilla("🚫", "Suscripción vencida", TONO_PELIGRO,
                "Tu acceso quedó suspendido, " + nombreOUsuario(nombreProfesional), cuerpo,
                "Reactivar mi cuenta", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarSuscripcionPagoConfirmado(String toEmail, String nombreProfesional, String planNombre,
                                                 BigDecimal monto, LocalDateTime proximoCobro) {
        String asunto = "Pago confirmado — Tranqui App";
        String montoStr = monto != null ? "$ " + monto.toPlainString() : null;
        String cuerpo = "<p>Recibimos tu pago. Tu suscripción a <strong>Tranqui App</strong> sigue activa. ¡Gracias por confiar en nosotros!</p>" +
                panelInfo(
                        "Plan", planNombre,
                        "Monto", montoStr,
                        "Próximo cobro", proximoCobro != null ? proximoCobro.format(FECHA_FMT) : null
                );
        String html = plantilla("✅", "Pago confirmado", TONO_EXITO,
                "¡Gracias, " + nombreOUsuario(nombreProfesional) + "!", cuerpo,
                "Ir a Tranqui App", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarSuscripcionCancelada(String toEmail, String nombreProfesional, String planNombre, LocalDateTime accesoHasta) {
        String asunto = "Cancelaste tu suscripción — Tranqui App";
        String cuerpo = "<p>Confirmamos que cancelaste la renovación automática de tu suscripción al plan <strong>" + planNombre + "</strong>. " +
                "Vas a seguir teniendo acceso hasta que termine el período que ya pagaste.</p>" +
                panelInfo(
                        "Plan", planNombre,
                        "Acceso hasta", accesoHasta != null ? accesoHasta.format(FECHA_FMT) : null
                ) +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";\">Si te arrepentiste, podés reactivar la renovación automática desde Tranqui App antes de esa fecha.</p>";
        String html = plantilla("🔒", "Suscripción cancelada", TONO_NEUTRO,
                "Cancelaste tu suscripción, " + nombreOUsuario(nombreProfesional), cuerpo,
                "Ir a Tranqui App", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // ================================================================================
    // Transporte (Resend) — sin cambios de diseño, solo arma y despacha el HTTP request
    // ================================================================================

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
