package com.tranqui.notificaciones.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
import java.util.Locale;
import java.util.Map;

/**
 * Plantillas y envío de los mails, mudados desde el ResendEmailService del monolito con el mismo
 * HTML. Quedan los mails que salen por eventos y, desde la Fase 6, el documento adjunto (lo pide el
 * servicio de turnos por una ruta interna: el archivo no viaja en eventos). Los demás de envío directo
 * (contraseña temporal, copia de datos, receta, alerta al admin y novedades) siguen en el monolito.
 */
@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // "resend" (producción) o "smtp" (entorno local con Mailpit, ver application-local.yml).
    @Value("${mail.transport:resend}")
    private String transport;

    // Solo existe cuando spring.mail.host está configurado (perfil local); en producción no hay bean.
    @Autowired
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-AR"));

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

    // Sin emojis ni íconos en el badge: regla de producto (nada de emojis) y los clientes de
    // correo (Gmail, Outlook) bloquean SVG, así que el badge es solo texto con color.
    private String plantilla(String badgeTexto, String[] tono, String titulo, String cuerpoHtml) {
        return plantilla(badgeTexto, tono, titulo, cuerpoHtml, null, null);
    }

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
        String html = plantilla("Verificación de cuenta", TONO_INFO,
                "¡Hola, " + nombreOUsuario(nombre) + "!", cuerpo);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarCodigoRecuperacion(String toEmail, String nombre, String codigo) {
        String asunto = "Recuperación de contraseña - Tranqui App";
        String cuerpo = "<p>Hola " + nombreOUsuario(nombre) + ", solicitaste restablecer tu contraseña en <strong>Tranqui App</strong>. Tu código de recuperación es:</p>" +
                cajaDestacada(codigo) +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";margin-top:15px;\">El código expira en 15 minutos. Si no solicitaste este cambio, te recomendamos revisar la seguridad de tu cuenta.</p>";
        String html = plantilla("Recuperación de contraseña", TONO_INFO,
                "Recuperación de contraseña", cuerpo);
        enviarCorreo(toEmail, asunto, html);
    }

    // Sent from TicketService#agregarMensaje whenever an admin replies to a support ticket —
    // the creator (paciente/profesional) gets emailed instead of having to keep the app open to
    // notice, same rationale as every other "something happened while you were away" email here.
    public void enviarRespuestaTicket(String toEmail, String nombreUsuario, String asuntoTicket, String respuesta) {
        String asunto = "Respondieron tu consulta: " + asuntoTicket;
        String cuerpo = "<p>El equipo de soporte de <strong>Tranqui App</strong> respondió tu consulta \"" + asuntoTicket + "\":</p>" +
                "<div style=\"background:" + COLOR_PANEL + ";border:1px solid " + COLOR_BORDER + ";padding:15px;border-radius:8px;white-space:pre-wrap;color:" + COLOR_INK + ";margin:16px 0;\">" + respuesta + "</div>" +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";\">Podés ver la conversación completa y responder desde \"Mis Tickets\" en Tranqui App.</p>";
        String html = plantilla("Respuesta de soporte", TONO_INFO,
                "¡Hola, " + nombreOUsuario(nombreUsuario) + "!", cuerpo, "Ir a Mis Tickets", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
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
        String html = plantilla("Nuevo turno reservado", TONO_EXITO,
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
        String html = plantilla("Turno cancelado", TONO_PELIGRO,
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
        String html = plantilla("Turno reprogramado", TONO_ALERTA,
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
        String html = plantilla("Confirmá tu turno", TONO_ALERTA,
                "¡Hola, " + nombreOUsuario(nombrePaciente) + "!", cuerpo);
        enviarCorreo(toEmail, asunto, html);
    }

    // ================================================================================
    // ALERTAS INTERNAS — al equipo de administración (SubscriptionReconciliationScheduler)
    // ================================================================================

    // ================================================================================
    // TURNOS — notificaciones al paciente (confirmado / cancelado / reprogramado / receta)
    // ================================================================================

    private static final String POLITICA_CANCELACION_HTML =
            "<p style=\"font-size:12px;color:" + COLOR_MUTED + ";margin-top:18px;\">Podés cancelar desde Mis Turnos " +
            "hasta 48 horas antes del turno y se te reembolsa el total. Con menos de 48 horas de anticipación, el " +
            "turno se cancela pero no corresponde reembolso.</p>";

    // EN_PROCESO (Fase 4): lo pide el servicio de pagos después de la cancelación y avisa con otro mail.
    public enum ResultadoReembolso { REEMBOLSADO, SIN_REEMBOLSO, SIN_PAGO, PENDIENTE, EN_PROCESO }

    // meetUrl puede venir vacío para un turno online si Google Calendar todavía no generó el link
    // real (ver GoogleCalendarSyncService#exportarTurnosPendientesAGoogleCalendar): en ese caso
    // se avisa que el link aparece en Mis Turnos en vez de mandar uno inexistente.
    public void enviarTurnoConfirmadoPaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                               LocalDate fecha, LocalTime hora, boolean esOnline, String tipoLabel,
                                               String direccion, String meetUrl, BigDecimal monto) {
        String asunto = "Turno confirmado con " + nombreProfesional + " — " + (fecha != null ? fecha.format(FECHA_FMT) : "");
        String lugar = esOnline
                ? (meetUrl != null && !meetUrl.isBlank()
                    ? "<a href=\"" + meetUrl + "\" style=\"color:" + TONO_EXITO[2] + ";\">Unirse a Google Meet</a>"
                    : "El link de la videollamada va a estar en Mis Turnos")
                : direccion;
        String cuerpo = "<p>Tu pago fue aprobado y tu turno quedó confirmado. Estos son los detalles:</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Fecha", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null,
                        "Modalidad", esOnline ? "Online (videollamada)" : "Presencial",
                        "Tipo de consulta", tipoLabel,
                        esOnline ? "Videollamada" : "Lugar", lugar,
                        "Monto abonado", monto != null ? "$ " + String.format(Locale.GERMANY, "%,.0f", monto) : null
                ) +
                (esOnline
                        ? "<p>El link se activa 10 minutos antes. Buscá un lugar tranquilo y con buena conexión.</p>"
                        : "<p>Te recomendamos llegar 10 minutos antes.</p>") +
                POLITICA_CANCELACION_HTML;
        String html = plantilla("Turno confirmado", TONO_EXITO,
                "¡Listo, " + nombreOUsuario(nombrePaciente) + "! Tu turno está confirmado", cuerpo,
                "Ver mis turnos", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // Para servicios que no ocupan agenda (receta, certificado, informe): no hay fecha/hora, el
    // profesional lo prepara y se lo entrega al paciente.
    public void enviarDocumentoPagadoPaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                               String tipoDocumentoLabel, BigDecimal monto) {
        String asunto = "Pago confirmado — " + tipoDocumentoLabel;
        String cuerpo = "<p>Recibimos tu pago. " + nombreProfesional + " ya tiene tu pedido y te lo va a enviar en cuanto lo prepare.</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Pedido", tipoDocumentoLabel,
                        "Monto abonado", monto != null ? "$ " + String.format(Locale.GERMANY, "%,.0f", monto) : null
                );
        String html = plantilla("Pago confirmado", TONO_EXITO,
                "¡Gracias, " + nombreOUsuario(nombrePaciente) + "!", cuerpo,
                "Ir a Tranqui App", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    /**
     * Fase 4: el segundo mail de una cancelación con reembolso. Con el servicio de pagos la cancelación
     * sale enseguida ("te avisamos cuando se acredite") y este confirma que Mercado Pago lo hizo.
     */
    public void enviarReembolsoRealizadoPaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                                 LocalDate fecha, LocalTime hora, BigDecimal monto) {
        String asunto = "Reembolso realizado" + (fecha != null ? " — turno del " + fecha.format(FECHA_FMT) : "");
        String cuerpo = "<p>Mercado Pago ya procesó el reembolso de tu turno cancelado. Se devuelve al mismo medio "
                + "de pago y la acreditación depende de los plazos de Mercado Pago.</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Fecha del turno", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null,
                        "Monto reembolsado", monto != null ? "$ " + String.format(Locale.GERMANY, "%,.0f", monto) : null
                );
        String html = plantilla("Reembolso realizado", TONO_EXITO,
                "Te devolvimos el pago, " + nombreOUsuario(nombrePaciente), cuerpo,
                "Ir a Tranqui App", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    /** Fase 4: el reembolso que correspondía no se pudo hacer automáticamente (mismo texto que PENDIENTE). */
    public void enviarReembolsoPendientePaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                                 LocalDate fecha, LocalTime hora) {
        String asunto = "Tu reembolso está en revisión" + (fecha != null ? " — turno del " + fecha.format(FECHA_FMT) : "");
        String cuerpo = "<p>Te corresponde el reembolso del total abonado, pero no pudimos procesarlo automáticamente. "
                + "Ya estamos revisándolo: si en 5 días hábiles no lo ves acreditado, escribinos desde Soporte.</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Fecha del turno", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null
                );
        String html = plantilla("Reembolso en revisión", TONO_ALERTA,
                "Estamos revisando tu reembolso, " + nombreOUsuario(nombrePaciente), cuerpo,
                "Ir a Tranqui App", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // fecha/hora en null para documentos (no ocupan agenda).
    public void enviarTurnoCanceladoPaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                              LocalDate fecha, LocalTime hora, boolean canceladoPorProfesional,
                                              ResultadoReembolso reembolso) {
        String asunto = "Turno cancelado" + (fecha != null ? " — " + fecha.format(FECHA_FMT) : "");
        String motivo = canceladoPorProfesional
                ? "<p>" + nombreProfesional + " tuvo que cancelar tu turno. Te pedimos disculpas por el inconveniente.</p>"
                : "<p>Cancelaste tu turno con " + nombreProfesional + ".</p>";
        String detalleReembolso = switch (reembolso) {
            case REEMBOLSADO -> "Se reembolsa el total abonado al mismo medio de pago. La acreditación depende de los plazos de Mercado Pago.";
            case SIN_REEMBOLSO -> "La cancelación se hizo con menos de 48 horas de anticipación, así que según la política de cancelación no corresponde reembolso.";
            case PENDIENTE -> "Te corresponde el reembolso del total abonado, pero no pudimos procesarlo automáticamente. Ya estamos revisándolo: si en 5 días hábiles no lo ves acreditado, escribinos desde Soporte.";
            case EN_PROCESO -> "Te corresponde el reembolso del total abonado. Ya lo pedimos a Mercado Pago y te avisamos por mail cuando se acredite.";
            case SIN_PAGO -> null;
        };
        String cuerpo = motivo +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Fecha", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null
                ) +
                (detalleReembolso != null ? "<p>" + detalleReembolso + "</p>" : "") +
                "<p>Podés reservar un nuevo turno cuando quieras.</p>";
        String html = plantilla("Turno cancelado", TONO_PELIGRO,
                "Tu turno fue cancelado, " + nombreOUsuario(nombrePaciente), cuerpo,
                "Reservar otro turno", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    public void enviarTurnoReprogramadoPaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                                 LocalDate fechaNueva, LocalTime horaNueva, boolean esOnline,
                                                 String direccion, String meetUrl) {
        String asunto = "Tu turno fue reprogramado — " + (fechaNueva != null ? fechaNueva.format(FECHA_FMT) : "");
        String lugar = esOnline
                ? (meetUrl != null && !meetUrl.isBlank()
                    ? "<a href=\"" + meetUrl + "\" style=\"color:" + TONO_ALERTA[2] + ";\">Unirse a Google Meet</a>"
                    : "El link de la videollamada va a estar en Mis Turnos")
                : direccion;
        String cuerpo = "<p>" + nombreProfesional + " reprogramó tu turno. La nueva fecha y hora son:</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Nueva fecha", fechaNueva != null ? fechaNueva.format(FECHA_FMT) : null,
                        "Nueva hora", horaNueva != null ? horaNueva.format(HORA_FMT) + " hs" : null,
                        "Modalidad", esOnline ? "Online (videollamada)" : "Presencial",
                        esOnline ? "Videollamada" : "Lugar", lugar
                ) +
                "<p>Si el nuevo horario no te queda bien, escribile a tu profesional por el chat de Tranqui App.</p>" +
                POLITICA_CANCELACION_HTML;
        String html = plantilla("Turno reprogramado", TONO_ALERTA,
                "Tu turno cambió de horario, " + nombreOUsuario(nombrePaciente), cuerpo,
                "Ver mis turnos", frontendUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // Enviado cuando el link real de Meet se genera después de confirmado el turno (Google
    // Calendar no estaba disponible en ese momento o el profesional lo conectó más tarde).
    public void enviarLinkVideollamadaPaciente(String toEmail, String nombrePaciente, String nombreProfesional,
                                                LocalDate fecha, LocalTime hora, String meetUrl) {
        String asunto = "Ya tenés el link de tu sesión online — " + (fecha != null ? fecha.format(FECHA_FMT) : "");
        String cuerpo = "<p>Ya está listo el link de la videollamada de tu turno con " + nombreProfesional + ".</p>" +
                panelInfo(
                        "Profesional", nombreProfesional,
                        "Fecha", fecha != null ? fecha.format(FECHA_FMT) : null,
                        "Hora", hora != null ? hora.format(HORA_FMT) + " hs" : null
                ) +
                "<p>El link se activa 10 minutos antes. También lo tenés en Mis Turnos.</p>";
        String html = plantilla("Link de videollamada", TONO_INFO,
                "Tu sesión online está lista, " + nombreOUsuario(nombrePaciente), cuerpo,
                "Unirse a Google Meet", meetUrl);
        enviarCorreo(toEmail, asunto, html);
    }

    // ================================================================================
    // SUSCRIPCIÓN — preaviso de renovación / pago confirmado / pago fallido / cancelada /
    // suspendida / vencimiento de alta manual
    // ================================================================================

    public void enviarPreavisoRenovacionSuscripcion(String toEmail, String nombre, String nombrePlan, BigDecimal monto, LocalDateTime fechaCobro, String billingCycle) {
        String asunto = "Recordatorio: tu suscripción se renovará en 3 días - Tranqui App";
        String fechaStr = fechaCobro != null ? fechaCobro.format(DATE_FORMATTER) : "en los próximos 3 días";
        String cicloStr = "annual".equalsIgnoreCase(billingCycle) ? "anual" : "mensual";
        String montoStr = monto != null ? String.format(Locale.GERMANY, "%,.2f", monto) : "0,00";

        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 24px; max-width: 550px; border: 1px solid #e2e8f0; border-radius: 12px; color: #1e293b;\">" +
                        "<h2 style=\"color: #009ee3; margin-top: 0;\">Tranqui App</h2>" +
                        "<h3 style=\"color: #0f172a; margin-top: 0;\">Hola, %s</h3>" +
                        "<p style=\"font-size: 15px; line-height: 1.5;\">Te recordamos que en <strong>3 días</strong> (el <strong>%s</strong>) se renovará automáticamente tu suscripción al <strong>Plan %s (%s)</strong>.</p>" +
                        "<div style=\"background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; margin: 20px 0;\">" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px;\"><strong>Monto a debitar:</strong> $%s ARS</p>" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px;\"><strong>Fecha de débito:</strong> %s</p>" +
                        "  <p style=\"margin: 0; font-size: 14px;\"><strong>Medio de pago:</strong> Débito automático (Mercado Pago)</p>" +
                        "</div>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Si deseás continuar con el servicio, no necesitás realizar ninguna acción. El cobro se procesará automáticamente y mantendrás todas tus funcionalidades y la visibilidad de tu agenda sin interrupciones.</p>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Si querés cambiar de plan o cancelar la renovación automática antes de que se acredite el pago, podés gestionarlo ingresando a tu cuenta en Tranqui App antes del %s. En caso de cancelar, no se realizará ningún nuevo débito y mantendrás el acceso completo hasta esa fecha.</p>" +
                        "<div style=\"text-align: center; margin: 25px 0;\">" +
                        "  <a href=\"%s/panel\" style=\"background-color: #009ee3; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; display: inline-block;\">Gestionar mi suscripción</a>" +
                        "</div>" +
                        "<p style=\"font-size: 12px; color: #64748b; margin-top: 25px; border-top: 1px solid #e2e8f0; padding-top: 15px;\">Este es un mensaje automático de Tranqui App. Si tenés dudas o necesitás asistencia, respondé a este correo o escribinos desde la sección de Soporte.</p>" +
                        "</div>",
                nombre != null ? nombre : "Profesional",
                fechaStr,
                nombrePlan != null ? nombrePlan : "Profesional",
                cicloStr,
                montoStr,
                fechaStr,
                fechaStr,
                frontendUrl
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarConfirmacionPagoSuscripcion(String toEmail, String nombre, String nombrePlan, BigDecimal monto, LocalDateTime periodoInicio, LocalDateTime periodoFin, String comprobanteRef) {
        String asunto = "¡Pago confirmado! Tu suscripción a Tranqui App está activa";
        String inicioStr = periodoInicio != null ? periodoInicio.format(DATE_FORMATTER) : "hoy";
        String finStr = periodoFin != null ? periodoFin.format(DATE_FORMATTER) : "próximo período";
        String montoStr = monto != null ? String.format(Locale.GERMANY, "%,.2f", monto) : "0,00";

        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 24px; max-width: 550px; border: 1px solid #e2e8f0; border-radius: 12px; color: #1e293b;\">" +
                        "<h2 style=\"color: #10b981; margin-top: 0;\">¡Pago recibido con éxito!</h2>" +
                        "<p style=\"font-size: 15px; line-height: 1.5;\">Hola <strong>%s</strong>, confirmamos que se procesó correctamente el pago de tu suscripción en <strong>Tranqui App</strong>.</p>" +
                        "<div style=\"background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 16px; margin: 20px 0;\">" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px;\"><strong>Plan:</strong> %s</p>" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px;\"><strong>Monto abonado:</strong> $%s ARS</p>" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px;\"><strong>Período cubierto:</strong> del %s al %s</p>" +
                        "  <p style=\"margin: 0; font-size: 14px;\"><strong>Referencia:</strong> %s</p>" +
                        "</div>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Tu cuenta se encuentra activa y tu perfil continúa visible en el buscador público para que tus pacientes puedan agendar turnos.</p>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Tu Factura C electrónica oficial ante ARCA ya fue emitida y se encuentra disponible para descargar en formato PDF desde la pestaña <strong>Mi Suscripción y Facturas ARCA</strong> en tu cuenta.</p>" +
                        "<div style=\"text-align: center; margin: 25px 0;\">" +
                        "  <a href=\"%s/panel\" style=\"background-color: #10b981; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; display: inline-block;\">Ir a mi panel</a>" +
                        "</div>" +
                        "<p style=\"font-size: 12px; color: #64748b; margin-top: 25px; border-top: 1px solid #e2e8f0; padding-top: 15px;\">Gracias por ser parte de la comunidad de profesionales de Tranqui App.</p>" +
                        "</div>",
                nombre != null ? nombre : "Profesional",
                nombrePlan != null ? nombrePlan : "Profesional",
                montoStr,
                inicioStr,
                finStr,
                comprobanteRef != null ? comprobanteRef : "Pago Mercado Pago",
                frontendUrl
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarAvisoPagoFallidoSuscripcion(String toEmail, String nombre, String nombrePlan, BigDecimal monto, LocalDateTime fechaVencimientoGracia) {
        String asunto = "Problema con el cobro de tu suscripción - Período de gracia activado";
        String graciaStr = fechaVencimientoGracia != null ? fechaVencimientoGracia.format(DATE_FORMATTER) : "en 7 días";
        String montoStr = monto != null ? String.format(Locale.GERMANY, "%,.2f", monto) : "0,00";

        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 24px; max-width: 550px; border: 1px solid #fecaca; border-radius: 12px; color: #1e293b;\">" +
                        "<h2 style=\"color: #ef4444; margin-top: 0;\">No pudimos procesar tu pago</h2>" +
                        "<p style=\"font-size: 15px; line-height: 1.5;\">Hola <strong>%s</strong>, te informamos que Mercado Pago no pudo debitar el cobro automático correspondiente a tu <strong>Plan %s</strong> ($%s ARS).</p>" +
                        "<div style=\"background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; padding: 16px; margin: 20px 0;\">" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px; color: #92400e;\"><strong>Período de gracia de 7 días activo</strong></p>" +
                        "  <p style=\"margin: 0; font-size: 13px; color: #78350f; line-height: 1.4;\">Para que tus pacientes no sufran interrupciones en su atención médica, tu acceso a Tranqui App y la visibilidad de tu agenda continuarán habilitados hasta el <strong>%s</strong>.</p>" +
                        "</div>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Por favor ingresá a tu panel para actualizar tu medio de pago o reintentar la suscripción. Si el pago no se acredita antes del %s, el acceso a las funciones profesionales quedará temporalmente suspendido.</p>" +
                        "<div style=\"text-align: center; margin: 25px 0;\">" +
                        "  <a href=\"%s/panel\" style=\"background-color: #ef4444; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; display: inline-block;\">Regularizar medio de pago</a>" +
                        "</div>" +
                        "<p style=\"font-size: 12px; color: #64748b; margin-top: 25px; border-top: 1px solid #e2e8f0; padding-top: 15px;\">Si ya regularizaste el pago en las últimas horas, podés desestimar este mensaje.</p>" +
                        "</div>",
                nombre != null ? nombre : "Profesional",
                nombrePlan != null ? nombrePlan : "Profesional",
                montoStr,
                graciaStr,
                graciaStr,
                frontendUrl
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarConfirmacionCancelacionSuscripcion(String toEmail, String nombre, String nombrePlan, LocalDateTime accesoHasta) {
        String asunto = "Suscripción cancelada - Tranqui App";
        String accesoHastaStr = accesoHasta != null ? accesoHasta.format(DATE_FORMATTER) : "el fin del período abonado";

        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 24px; max-width: 550px; border: 1px solid #e2e8f0; border-radius: 12px; color: #1e293b;\">" +
                        "<h2 style=\"color: #0f172a; margin-top: 0;\">Confirmación de cancelación</h2>" +
                        "<p style=\"font-size: 15px; line-height: 1.5;\">Hola <strong>%s</strong>, confirmamos que cancelaste la renovación automática de tu suscripción al <strong>Plan %s</strong> en Tranqui App.</p>" +
                        "<div style=\"background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; margin: 20px 0;\">" +
                        "  <p style=\"margin: 0 0 8px 0; font-size: 14px;\"><strong>Renovación automática:</strong> Cancelada en Mercado Pago (no se realizarán nuevos cobros).</p>" +
                        "  <p style=\"margin: 0; font-size: 14px;\"><strong>Acceso garantizado hasta:</strong> %s</p>" +
                        "</div>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Tal como establece nuestra política, <strong>mantendrás acceso total a tu agenda, pacientes y funcionalidades hasta el %s</strong>, fecha en la que concluye el período que ya tenías abonado.</p>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Pasada esa fecha, tu perfil se pausará del buscador público. Podés reactivar tu plan cuando lo desees con un solo clic desde tu cuenta.</p>" +
                        "<div style=\"text-align: center; margin: 25px 0;\">" +
                        "  <a href=\"%s/panel\" style=\"background-color: #009ee3; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; display: inline-block;\">Ir a mi cuenta</a>" +
                        "</div>" +
                        "<p style=\"font-size: 12px; color: #64748b; margin-top: 25px; border-top: 1px solid #e2e8f0; padding-top: 15px;\">Lamentamos verte partir. Si tenés algún comentario o sugerencia sobre cómo podemos mejorar, no dudes en escribirnos.</p>" +
                        "</div>",
                nombre != null ? nombre : "Profesional",
                nombrePlan != null ? nombrePlan : "Profesional",
                accesoHastaStr,
                accesoHastaStr,
                frontendUrl
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarAvisoSuspensionSuscripcion(String toEmail, String nombre, String nombrePlan) {
        String asunto = "Suscripción suspendida - Tranqui App";

        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 24px; max-width: 550px; border: 1px solid #e2e8f0; border-radius: 12px; color: #1e293b;\">" +
                        "<h2 style=\"color: #64748b; margin-top: 0;\">Tu suscripción ha finalizado</h2>" +
                        "<p style=\"font-size: 15px; line-height: 1.5;\">Hola <strong>%s</strong>, te informamos que ha finalizado el período de acceso a tu suscripción del <strong>Plan %s</strong> en Tranqui App.</p>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">A partir de este momento tu perfil ha dejado de estar visible en el buscador público para nuevos turnos y el acceso a las funciones profesionales ha quedado restringido.</p>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Tu historial clínico, pacientes registrados e información profesional continúan resguardados de forma segura. Podés reactivar tu suscripción en cualquier momento para retomar tu atención.</p>" +
                        "<div style=\"text-align: center; margin: 25px 0;\">" +
                        "  <a href=\"%s/panel\" style=\"background-color: #009ee3; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; display: inline-block;\">Reactivar mi suscripción</a>" +
                        "</div>" +
                        "<p style=\"font-size: 12px; color: #64748b; margin-top: 25px; border-top: 1px solid #e2e8f0; padding-top: 15px;\">Si creés que se trata de un error, por favor contactate con nosotros a soporte@tranquisalud.com.</p>" +
                        "</div>",
                nombre != null ? nombre : "Profesional",
                nombrePlan != null ? nombrePlan : "Profesional",
                frontendUrl
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    public void enviarAvisoVencimientoManualSuscripcion(String toEmail, String nombre, String nombrePlan, LocalDateTime fechaVencimiento, int diasRestantes) {
        String asunto = String.format("Tu suscripción a Tranqui App vence en %d días", diasRestantes);
        String vencimientoStr = fechaVencimiento != null ? fechaVencimiento.format(DATE_FORMATTER) : "próximamente";

        String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 24px; max-width: 550px; border: 1px solid #e2e8f0; border-radius: 12px; color: #1e293b;\">" +
                        "<h2 style=\"color: #009ee3; margin-top: 0;\">Aviso de vencimiento de suscripción</h2>" +
                        "<p style=\"font-size: 15px; line-height: 1.5;\">Hola <strong>%s</strong>, te recordamos que tu período abonado del <strong>Plan %s</strong> vence el próximo <strong>%s</strong> (%d días restantes).</p>" +
                        "<p style=\"font-size: 14px; line-height: 1.5;\">Para continuar usando la plataforma sin interrupciones ni perder la visibilidad de tu agenda, podés renovar tu suscripción adhiriéndote al débito automático con Mercado Pago o realizando una nueva transferencia bancaria.</p>" +
                        "<div style=\"text-align: center; margin: 25px 0;\">" +
                        "  <a href=\"%s/panel\" style=\"background-color: #009ee3; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; display: inline-block;\">Renovar mi plan</a>" +
                        "</div>" +
                        "<p style=\"font-size: 12px; color: #64748b; margin-top: 25px; border-top: 1px solid #e2e8f0; padding-top: 15px;\">Equipo de Tranqui App.</p>" +
                        "</div>",
                nombre != null ? nombre : "Profesional",
                nombrePlan != null ? nombrePlan : "Profesional",
                vencimientoStr,
                diasRestantes,
                frontendUrl
        );

        enviarCorreo(toEmail, asunto, htmlContent);
    }

    // ================================================================================
    // Transporte (Resend o SMTP local). A diferencia del monolito, un envío fallido tira
    // EnvioMailException: el evento se reintenta (10 s, 1 min, 5 min) y después va a la DLQ.
    // Cada envío lleva la clave de idempotencia del evento (ClaveIdempotencia), así un
    // reintento no le manda el mismo mail dos veces al destinatario (Resend la recuerda 24 h).
    // ================================================================================

    // ================================================================================
    // DOCUMENTO ADJUNTO (Fase 6) — el certificado, informe o documento que el profesional le manda al
    // paciente. Lo pide turnos en el momento (DocumentosController); el archivo no se guarda.
    // ================================================================================

    public void enviarDocumentoAdjunto(String toEmail, String pacienteNombre, String medicoNombre, String tipoDocumento,
                                       String archivoDataUri, String archivoNombre) {
        String tipoLower = tipoDocumento != null ? tipoDocumento.toLowerCase() : "documento";
        String asunto = "Tu " + tipoLower + " de " + medicoNombre;
        String cuerpo = "<p><strong>" + medicoNombre + "</strong> te envió tu " + tipoLower + " adjunto en este correo.</p>" +
                "<p style=\"font-size:13px;color:" + COLOR_MUTED + ";\">Enviado a través de Tranqui App.</p>";
        String html = plantilla("Documento adjunto", TONO_EXITO,
                "¡Hola, " + nombreOUsuario(pacienteNombre) + "!", cuerpo);
        String base64 = extraerBase64(archivoDataUri);
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("El documento no tiene contenido.");
        }
        String nombre = archivoNombre != null && !archivoNombre.isBlank() ? archivoNombre : "documento";
        enviarCorreo(toEmail, asunto, html, nombre, base64);
    }

    private static String extraerBase64(String dataUri) {
        if (dataUri == null) return null;
        int coma = dataUri.indexOf(',');
        return coma >= 0 ? dataUri.substring(coma + 1) : dataUri;
    }

    private void enviarCorreo(String toEmail, String asunto, String htmlBody) {
        enviarCorreo(toEmail, asunto, htmlBody, null, null);
    }

    private void enviarCorreo(String toEmail, String asunto, String htmlBody, String adjuntoNombre, String adjuntoBase64) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Mail '{}' sin destinatario: no se envía", asunto);
            return;
        }
        String clave = ClaveIdempotencia.actual();
        if (usarSmtp()) {
            enviarPorSmtp(toEmail, asunto, htmlBody, clave, adjuntoNombre, adjuntoBase64);
            return;
        }
        if (apiKey == null || apiKey.trim().isEmpty() || !apiKey.startsWith("re_")) {
            log.info("==================================================================");
            log.info("[RESEND MOCK/DEV MODE] No hay API Key de Resend configurada.");
            log.info("Destinatario: {}", toEmail);
            log.info("Asunto: {}", asunto);
            log.info("Contenido enviado (Simulación). Colocá RESEND_API_KEY en tu .env para envíos reales.");
            log.info("==================================================================");
            return;
        }

        HttpResponse<String> response;
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("from", String.format("Tranqui App <%s>", fromEmail));
            payloadMap.put("to", List.of(toEmail));
            payloadMap.put("subject", asunto);
            payloadMap.put("html", htmlBody);
            if (adjuntoNombre != null && adjuntoBase64 != null) {
                payloadMap.put("attachments", List.of(Map.of("filename", adjuntoNombre, "content", adjuntoBase64)));
            }

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payloadMap)));
            if (clave != null) {
                request.header("Idempotency-Key", clave);
            }
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EnvioMailException("Envío interrumpido a " + toEmail, e);
        } catch (Exception e) {
            throw new EnvioMailException("No se pudo contactar a Resend para " + toEmail, e);
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Correo enviado exitosamente mediante Resend a {}. Status: {}", toEmail, response.statusCode());
        } else {
            throw new EnvioMailException("Resend respondió " + response.statusCode() + " para " + toEmail
                    + ": " + response.body());
        }
    }

    // ================================================================================
    // Transporte SMTP — solo entorno local (Mailpit). Mismo HTML que se envía por Resend.
    // ================================================================================

    private boolean usarSmtp() {
        return "smtp".equalsIgnoreCase(transport);
    }

    private void enviarPorSmtp(String toEmail, String asunto, String htmlBody, String clave, String adjuntoNombre,
                               String adjuntoBase64) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new EnvioMailException("mail.transport=smtp pero no hay spring.mail.host configurado");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            boolean conAdjunto = adjuntoNombre != null && adjuntoBase64 != null;
            MimeMessageHelper helper = new MimeMessageHelper(message, conAdjunto, "UTF-8");
            helper.setFrom(String.format("Tranqui App <%s>", fromEmail));
            helper.setTo(toEmail);
            helper.setSubject(asunto);
            helper.setText(htmlBody, true);
            if (conAdjunto) {
                helper.addAttachment(adjuntoNombre, new org.springframework.core.io.ByteArrayResource(
                        java.util.Base64.getDecoder().decode(adjuntoBase64)));
            }
            if (clave != null) {
                // Para rastrear en Mailpit de qué evento salió cada mail.
                message.setHeader("X-Tranqui-Idempotency-Key", clave);
            }
            mailSender.send(message);
            log.info("Correo enviado por SMTP a {} ({})", toEmail, asunto);
        } catch (Exception e) {
            throw new EnvioMailException("No se pudo enviar por SMTP a " + toEmail, e);
        }
    }
}
