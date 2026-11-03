package com.tranqui.app.eventos;

import com.tranqui.eventos.Topicos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class EventosCatalogoTest {

    /**
     * Mapeo exhaustivo de cada tópico del catálogo a sus productores identificados en el monolito.
     */
    private static final Map<String, List<String>> PRODUCTORES_POR_TOPICO = Map.ofEntries(
            Map.entry(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, List.of("AuthController#register", "GoogleAuthService#getOrCreateUsuario")),
            Map.entry(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, List.of("AccountService#updateProfile", "AccountService#actualizarPreferenciasNotificacion", "AdminController#updateRol", "AdminController#verifyProfessional")),
            Map.entry(Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, List.of("AccountService#deleteAccount")),
            Map.entry(Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1, List.of("AuthController#register", "AuthController#resendCode", "AuthController#forgotPassword")),
            Map.entry(Topicos.IDENTIDAD_TICKET_RESPONDIDO_V1, List.of("TicketService#agregarMensaje")),
            Map.entry(Topicos.IDENTIDAD_TICKET_CREADO_V1, List.of("TicketService#crearTicket")),
            Map.entry(Topicos.IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1, List.of("TicketService#agregarMensaje")),
            Map.entry(Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1, List.of("AdminController#verifyProfessional", "AdminController#rejectProfessional")),
            Map.entry(Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1, List.of("MedicoService#actualizarPerfil", "MedicoService#actualizarConfigAgenda", "servicio de profesionales (Fase 5)")),
            Map.entry(Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1, List.of("AdminController#verifyProfessional", "AdminController#rejectProfessional")),
            Map.entry(Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V1, List.of("MedicoService#actualizarPerfil")),
            Map.entry(Topicos.PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V1, List.of("DisponibilidadService#guardarDisponibilidades", "DisponibilidadService#inicializarDisponibilidadesPorDefecto")),
            Map.entry(Topicos.PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1, List.of("ClinicalService#guardarSeguimiento", "ClinicalService#guardarInforme", "ClinicalService#guardarSeguimientoPaciente")),
            Map.entry(Topicos.TURNOS_TURNO_RESERVADO_V1, List.of("TurnoService#reservarTurno")),
            Map.entry(Topicos.TURNOS_TURNO_CONFIRMADO_V1, List.of("PagoWebhookHandler#procesarAprobacionTurno", "TurnoService#confirmarTurnoOsde")),
            Map.entry(Topicos.TURNOS_TURNO_CANCELADO_V1, List.of("TurnoService#cancelarTurno")),
            Map.entry(Topicos.TURNOS_TURNO_REPROGRAMADO_V1, List.of("TurnoService#reprogramarTurno")),
            Map.entry(Topicos.TURNOS_TURNO_EXPIRADO_V1, List.of("LiberarTurnosScheduler#liberarTurnosExpirados")),
            Map.entry(Topicos.TURNOS_RECORDATORIO_PROGRAMADO_V1, List.of("TurnoService#enviarRecordatoriosConfirmacionAsistencia")),
            Map.entry(Topicos.TURNOS_DOCUMENTO_SOLICITADO_V1, List.of("TurnoService#reservarTurno", "PagoWebhookHandler#procesarAprobacionTurno")),
            Map.entry(Topicos.TURNOS_DOCUMENTO_ENVIADO_V1, List.of("TurnoService#marcarDocumentoEnviado", "TurnoService#marcarRecetasEnviadasParaPaciente")),
            Map.entry(Topicos.TURNOS_ASISTENCIA_CONFIRMADA_V1, List.of("TurnoService#confirmarAsistenciaPaciente")),
            Map.entry(Topicos.PAGOS_PAGO_APROBADO_V1, List.of("PagoWebhookHandler#procesarAprobacionTurno")),
            Map.entry(Topicos.PAGOS_PAGO_RECHAZADO_V1, List.of("PagoWebhookHandler#procesarRechazoTurno")),
            Map.entry(Topicos.PAGOS_REEMBOLSO_REALIZADO_V1, List.of("ReembolsoService#procesarReembolso")),
            Map.entry(Topicos.PAGOS_CUENTA_MP_VINCULADA_V1, List.of("MercadoPagoOAuthService#procesarCallback", "MercadoPagoOAuthService#simularConexionDev")),
            Map.entry(Topicos.PAGOS_CUENTA_MP_DESVINCULADA_V1, List.of("MercadoPagoOAuthService#desvincular")),
            Map.entry(Topicos.PAGOS_CONCEPTO_APROBADO_V1, List.of("PagoWebhookHandler#procesarAprobacionConcepto", "servicio de pagos")),
            Map.entry(Topicos.PAGOS_CHECKOUT_CREADO_V1, List.of("servicio de pagos (Fase 4)")),
            Map.entry(Topicos.PAGOS_REEMBOLSO_FALLIDO_V1, List.of("servicio de pagos (Fase 4)")),
            Map.entry(Topicos.PROFESIONALES_CONCEPTO_SOLICITADO_V1, List.of("ConceptoService#crearSolicitud")),
            Map.entry(Topicos.PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2, List.of("servicio de profesionales (Fase 5)")),
            Map.entry(Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V2, List.of("servicio de profesionales (Fase 5)")),
            Map.entry(Topicos.PROFESIONALES_RECETA_EMITIDA_V1, List.of("RecetaService#emitirReceta")),
            Map.entry(Topicos.TURNOS_TURNO_ACTUALIZADO_V1, List.of("servicio de turnos (Fase 6)")),
            Map.entry(Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V1, List.of("SubscriptionService#logEvent (deprecado)")),
            Map.entry(Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2, List.of("SubscriptionService#logEvent", "servicio de pagos (Fase 8)")),
            Map.entry(Topicos.SUSCRIPCIONES_AVISO_PROGRAMADO_V1, List.of("SubscriptionService#registerManualPayment", "SubscriptionService#activarPagoSuscripcion", "SubscriptionService#processMercadoPagoPreapprovalWebhook", "SubscriptionService#cancelarSuscripcionPorProfesional", "SubscriptionReconciliationScheduler", "AdminSubscriptionController#updateSubscriptionStatus")),
            Map.entry(Topicos.CALENDARIO_MEET_CREADO_V1, List.of("PagoWebhookHandler#procesarAprobacionTurno", "TurnoService#confirmarTurnoOsde", "GoogleCalendarSyncService#exportarTurnosPendientesAGoogleCalendar")),
            Map.entry(Topicos.CALENDARIO_HORARIO_BLOQUEADO_V1, List.of("GoogleCalendarSyncService#aplicarCambio")),
            Map.entry(Topicos.CHAT_MENSAJE_ENVIADO_V1, List.of("MensajeService#guardarMensaje"))
    );

    @Test
    @DisplayName("Todos los tópicos del catálogo deben tener un productor asignado o estar en la lista documentada")
    void todosLosTopicosTienenProductorAsignado() {
        List<String> todosLosTopicos = Topicos.TODOS_LOS_TOPICOS;
        assertThat(todosLosTopicos).isNotEmpty();
        assertThat(todosLosTopicos).hasSize(41);

        for (String topico : todosLosTopicos) {
            assertThat(Topicos.esValido(topico))
                    .withFailMessage("El tópico no cumple con el formato convención: %s", topico)
                    .isTrue();

            assertThat(PRODUCTORES_POR_TOPICO)
                    .withFailMessage("El tópico '%s' no tiene productor mapeado ni documentación de no aplica", topico)
                    .containsKey(topico);

            List<String> productores = PRODUCTORES_POR_TOPICO.get(topico);
            assertThat(productores)
                    .withFailMessage("El tópico '%s' tiene una lista vacía de productores", topico)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("El mapa de productores no debe contener tópicos ajenos a la lista oficial")
    void mapaDeProductoresCoincideExactamenteConCatalogo() {
        assertThat(PRODUCTORES_POR_TOPICO.keySet())
                .containsExactlyInAnyOrderElementsOf(Topicos.TODOS_LOS_TOPICOS);
    }
}
