package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.AsistenciaConfirmadaData;
import com.tranqui.eventos.data.DocumentoEnviadoData;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.TurnoRecordatorioData;
import com.tranqui.eventos.data.TurnoReprogramadoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.interno.ClienteInterno;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

import static com.tranqui.notificaciones.avisos.Avisos.hora;

/**
 * Avisos de turnos. Cada mail y cada notificación es un handler (consumidor) aparte: si uno falla
 * y el evento se reintenta, los que ya salieron no se repiten. Textos y condiciones copiados del
 * monolito (PagoWebhookHandler y TurnoService); ver orca-specs-fases/fase1-mapa-handlers.md.
 */
@Configuration
public class ManejadoresTurnos {

    private static boolean esOrigenPago(TurnoConfirmadoData d) {
        return d.origen() == null || TurnoConfirmadoData.ORIGEN_PAGO.equals(d.origen());
    }

    private static boolean esOnline(String modalidad) {
        return "ONLINE".equals(modalidad);
    }

    // ---------------------------------------------------------------- turno confirmado

    @Bean
    ManejadorEvento<TurnoConfirmadoData> notifTurnoConfirmadoProfesional(Avisos avisos) {
        return ManejadorEvento.de("notif-turno-confirmado-profesional", Topicos.TURNOS_TURNO_CONFIRMADO_V1,
                TurnoConfirmadoData.class, e -> {
                    TurnoConfirmadoData d = e.data();
                    if (!esOrigenPago(d)) {
                        return;
                    }
                    Contacto medico = avisos.contacto(d.profesionalId());
                    Contacto paciente = avisos.contacto(d.pacienteId());
                    if (d.esDocumento()) {
                        avisos.notificar(medico, "Nuevo Documento Pendiente", "El paciente " + paciente.getNombre()
                                + " pagó " + d.tipoDocumentoLabel()
                                + ". Encontralo en \"Documentos solicitados\" en Inicio.", "DOCUMENTO_PENDIENTE");
                    } else {
                        avisos.notificar(medico, "Nuevo Turno Reservado", "El paciente " + paciente.getNombre()
                                + " ha reservado un turno para el día " + d.fecha()
                                + " a las " + d.horaInicio() + "hs.", "TURNO_RESERVADO");
                    }
                });
    }

    @Bean
    ManejadorEvento<TurnoConfirmadoData> notifTurnoConfirmadoPaciente(Avisos avisos) {
        return ManejadorEvento.de("notif-turno-confirmado-paciente", Topicos.TURNOS_TURNO_CONFIRMADO_V1,
                TurnoConfirmadoData.class, e -> {
                    TurnoConfirmadoData d = e.data();
                    if (!esOrigenPago(d)) {
                        return;
                    }
                    Contacto medico = avisos.contacto(d.profesionalId());
                    Contacto paciente = avisos.contacto(d.pacienteId());
                    if (d.esDocumento()) {
                        avisos.notificar(paciente, "Pago confirmado", "Tu pago por " + d.tipoDocumentoLabel()
                                + " con el profesional " + medico.getNombre()
                                + " fue confirmado. Te va a llegar en cuanto el profesional lo prepare.", "TURNO_CONFIRMADO");
                    } else {
                        avisos.notificar(paciente, "Turno confirmado", "Tu turno con el profesional " + medico.getNombre()
                                + " para el día " + d.fecha() + " a las " + d.horaInicio() + "hs ha sido confirmado.",
                                "TURNO_CONFIRMADO");
                    }
                });
    }

    @Bean
    ManejadorEvento<TurnoConfirmadoData> mailTurnoConfirmadoProfesional(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-turno-confirmado-profesional";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_TURNO_CONFIRMADO_V1, TurnoConfirmadoData.class, e -> {
            TurnoConfirmadoData d = e.data();
            if (!esOrigenPago(d) || d.esDocumento()) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (medico.getEmail() == null) {
                return;
            }
            avisos.mail(e, consumidor, () -> mails.enviarNuevoTurnoProfesional(medico.getEmail(), medico.getNombre(),
                    paciente.getNombre(), d.fecha(), hora(d.horaInicio()), d.modalidad(), d.tipoLabel()));
        });
    }

    @Bean
    ManejadorEvento<TurnoConfirmadoData> mailTurnoConfirmadoPaciente(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-turno-confirmado-paciente";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_TURNO_CONFIRMADO_V1, TurnoConfirmadoData.class, e -> {
            TurnoConfirmadoData d = e.data();
            boolean osde = TurnoConfirmadoData.ORIGEN_OSDE.equals(d.origen());
            if (osde && d.esDocumento()) {
                return;
            }
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            if (d.esDocumento()) {
                avisos.mail(e, consumidor, () -> mails.enviarDocumentoPagadoPaciente(paciente.getEmail(),
                        paciente.getNombre(), medico.getNombre(), d.tipoDocumentoLabel(), d.precio()));
            } else {
                avisos.mail(e, consumidor, () -> mails.enviarTurnoConfirmadoPaciente(paciente.getEmail(),
                        paciente.getNombre(), medico.getNombre(), d.fecha(), hora(d.horaInicio()),
                        esOnline(d.modalidad()), d.tipoLabel(), d.direccion(), d.meetUrl(), osde ? null : d.precio()));
            }
        });
    }

    // ---------------------------------------------------------------- turno cancelado

    @Bean
    ManejadorEvento<TurnoCanceladoData> notifTurnoCancelado(Avisos avisos) {
        return ManejadorEvento.de("notif-turno-cancelado", Topicos.TURNOS_TURNO_CANCELADO_V1, TurnoCanceladoData.class, e -> {
            TurnoCanceladoData d = e.data();
            Contacto medico = avisos.contacto(d.profesionalId());
            Contacto paciente = avisos.contacto(d.pacienteId());
            avisos.notificar(medico, "Turno cancelado", "El turno del " + d.fecha() + " a las " + d.horaInicio()
                    + " hs con el paciente " + paciente.getNombre() + " ha sido cancelado.", "TURNO_CANCELADO");
            avisos.notificar(paciente, "Turno cancelado", "El turno del " + d.fecha() + " a las " + d.horaInicio()
                    + " hs con el profesional " + medico.getNombre() + " ha sido cancelado.", "TURNO_CANCELADO");
        });
    }

    @Bean
    ManejadorEvento<TurnoCanceladoData> mailTurnoCanceladoProfesional(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-turno-cancelado-profesional";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_TURNO_CANCELADO_V1, TurnoCanceladoData.class, e -> {
            TurnoCanceladoData d = e.data();
            if (Boolean.FALSE.equals(d.ocupaAgenda())) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (medico.getEmail() == null) {
                return;
            }
            avisos.mail(e, consumidor, () -> mails.enviarTurnoCanceladoProfesional(medico.getEmail(), medico.getNombre(),
                    paciente.getNombre(), d.fecha(), hora(d.horaInicio())));
        });
    }

    @Bean
    ManejadorEvento<TurnoCanceladoData> mailTurnoCanceladoPaciente(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-turno-cancelado-paciente";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_TURNO_CANCELADO_V1, TurnoCanceladoData.class, e -> {
            TurnoCanceladoData d = e.data();
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            boolean ocupaAgenda = !Boolean.FALSE.equals(d.ocupaAgenda());
            ResendEmailService.ResultadoReembolso reembolso = d.resultadoReembolso() != null
                    ? ResendEmailService.ResultadoReembolso.valueOf(d.resultadoReembolso())
                    : (d.correspondeReembolso() ? ResendEmailService.ResultadoReembolso.REEMBOLSADO
                    : ResendEmailService.ResultadoReembolso.SIN_PAGO);
            avisos.mail(e, consumidor, () -> mails.enviarTurnoCanceladoPaciente(paciente.getEmail(), paciente.getNombre(),
                    medico.getNombre(), ocupaAgenda ? d.fecha() : null, ocupaAgenda ? hora(d.horaInicio()) : null,
                    "PROFESIONAL".equals(d.canceladoPor()), reembolso));
        });
    }

    // ---------------------------------------------------------------- turno reprogramado

    @Bean
    ManejadorEvento<TurnoReprogramadoData> mailTurnoReprogramadoProfesional(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-turno-reprogramado-profesional";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_TURNO_REPROGRAMADO_V1, TurnoReprogramadoData.class, e -> {
            TurnoReprogramadoData d = e.data();
            Contacto medico = avisos.contacto(d.profesionalId());
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (medico.getEmail() == null) {
                return;
            }
            avisos.mail(e, consumidor, () -> mails.enviarTurnoModificadoProfesional(medico.getEmail(), medico.getNombre(),
                    paciente.getNombre(), d.fechaNueva(), hora(d.horaInicioNueva())));
        });
    }

    @Bean
    ManejadorEvento<TurnoReprogramadoData> mailTurnoReprogramadoPaciente(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-turno-reprogramado-paciente";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_TURNO_REPROGRAMADO_V1, TurnoReprogramadoData.class, e -> {
            TurnoReprogramadoData d = e.data();
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            avisos.mail(e, consumidor, () -> mails.enviarTurnoReprogramadoPaciente(paciente.getEmail(),
                    paciente.getNombre(), medico.getNombre(), d.fechaNueva(), hora(d.horaInicioNueva()),
                    esOnline(d.modalidad()), d.direccion(), d.meetUrl()));
        });
    }

    // ---------------------------------------------------------------- recordatorio y asistencia

    @Bean
    ManejadorEvento<TurnoRecordatorioData> mailRecordatorioAsistencia(Avisos avisos, ResendEmailService mails,
                                                                      ClienteInterno interno) {
        String consumidor = "mail-recordatorio-asistencia";
        return ManejadorEvento.de(consumidor, Topicos.TURNOS_RECORDATORIO_PROGRAMADO_V1, TurnoRecordatorioData.class, e -> {
            TurnoRecordatorioData d = e.data();
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            ClienteInterno.EnlacesAsistencia enlaces = interno.enlacesAsistencia(d.turnoId());
            avisos.mail(e, consumidor, () -> mails.enviarConfirmacionAsistencia(paciente.getEmail(), paciente.getNombre(),
                    medico.getNombre(), d.fecha(), hora(d.horaInicio()), d.modalidad(), d.tipoLabel(), d.direccion(),
                    enlaces.confirmarUrl(), enlaces.noAsistiraUrl()));
        });
    }

    @Bean
    ManejadorEvento<AsistenciaConfirmadaData> notifAsistenciaConfirmada(Avisos avisos) {
        return ManejadorEvento.de("notif-asistencia-confirmada", Topicos.TURNOS_ASISTENCIA_CONFIRMADA_V1,
                AsistenciaConfirmadaData.class, e -> {
                    AsistenciaConfirmadaData d = e.data();
                    Contacto medico = avisos.contacto(d.profesionalId());
                    Contacto paciente = avisos.contacto(d.pacienteId());
                    avisos.notificar(medico, "Paciente confirmó su asistencia", paciente.getNombre()
                            + " confirmó que va a asistir al turno del " + d.fecha() + " a las " + d.horaInicio() + " hs.",
                            "TURNO_ASISTENCIA_CONFIRMADA");
                });
    }

    // ---------------------------------------------------------------- documentos y Meet

    @Bean
    ManejadorEvento<DocumentoEnviadoData> notifDocumentoEnviado(Avisos avisos) {
        return ManejadorEvento.de("notif-documento-enviado", Topicos.TURNOS_DOCUMENTO_ENVIADO_V1,
                DocumentoEnviadoData.class, e -> {
                    DocumentoEnviadoData d = e.data();
                    Contacto paciente = avisos.contacto(d.pacienteId());
                    String mensaje = "RECETA".equals(d.origen())
                            ? "Tu receta fuera de turno ya fue generada."
                            : "Tu " + (d.tipoLabel() != null ? d.tipoLabel().toLowerCase(Locale.ROOT) : "documento")
                            + " te llegó por mail.";
                    avisos.notificar(paciente, "Documento enviado", mensaje, "DOCUMENTO_ENVIADO");
                });
    }

    @Bean
    ManejadorEvento<MeetCreadoData> mailLinkVideollamada(Avisos avisos, ResendEmailService mails) {
        String consumidor = "mail-link-videollamada";
        return ManejadorEvento.de(consumidor, Topicos.CALENDARIO_MEET_CREADO_V1, MeetCreadoData.class, e -> {
            MeetCreadoData d = e.data();
            // Solo cuando el paciente recibió la confirmación sin este link (GoogleCalendarSyncService).
            if (!Boolean.TRUE.equals(d.linkNuevo()) || d.pacienteId() == null) {
                return;
            }
            Contacto paciente = avisos.contacto(d.pacienteId());
            if (!avisos.aceptaMails(paciente)) {
                return;
            }
            Contacto medico = avisos.contacto(d.profesionalId());
            avisos.mail(e, consumidor, () -> mails.enviarLinkVideollamadaPaciente(paciente.getEmail(),
                    paciente.getNombre(), medico.getNombre(), d.fecha(), hora(d.horaInicio()), d.meetUrl()));
        });
    }
}
