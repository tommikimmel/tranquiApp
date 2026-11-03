# Inventario de `main` y mapa a la migración (generado)

Generado con `node scripts/comparacion/inventario.mjs`. No editar a mano: el análisis va en
[comparacion-main.md](comparacion-main.md).

## Endpoints de main (112): 112 encontrados, 0 faltan, 0 sin regla del gateway

| Endpoint | En main | Regla del gateway | Módulo en la migración | Estado |
|---|---|---|---|---|
| `GET /api/admin/users` | AdminController.getUsers | identidad | services/identidad | encontrado |
| `PUT /api/admin/users/{id}/rol` | AdminController.updateRol | identidad | services/identidad | encontrado |
| `POST /api/admin/users/{id}/verify` | AdminController.verifyProfessional | identidad | services/identidad | encontrado |
| `POST /api/admin/users/{id}/reject` | AdminController.rejectProfessional | identidad | services/identidad | encontrado |
| `POST /api/admin/users/{id}/reset-password` | AdminController.resetPassword | identidad | services/identidad | encontrado |
| `GET /api/admin/tickets` | AdminController.listarTickets | identidad | services/identidad | encontrado |
| `PUT /api/admin/tickets/{id}/estado` | AdminController.cambiarEstadoTicket | identidad | services/identidad | encontrado |
| `GET /api/admin/subscriptions/overview` | AdminSubscriptionController.getOverview | suscripciones | services/pagos | encontrado |
| `GET /api/admin/subscriptions/list` | AdminSubscriptionController.getAllSubscriptions | suscripciones | services/pagos | encontrado |
| `POST /api/admin/subscriptions/manual-payment` | AdminSubscriptionController.registerManualPayment | suscripciones | services/pagos | encontrado |
| `GET /api/admin/subscriptions/invoices` | AdminSubscriptionController.getAllInvoices | suscripciones | services/pagos | encontrado |
| `POST /api/admin/subscriptions/invoices/{id}/credit-note` | AdminSubscriptionController.emitirNotaDeCredito | suscripciones | services/pagos | encontrado |
| `GET /api/admin/subscriptions/plans` | AdminSubscriptionController.getAllPlans | suscripciones | services/pagos | encontrado |
| `PUT /api/admin/subscriptions/plans/{id}` | AdminSubscriptionController.updatePlan | suscripciones | services/pagos | encontrado |
| `GET /api/admin/subscriptions/events` | AdminSubscriptionController.getAllSubscriptionEvents | suscripciones | services/pagos | encontrado |
| `GET /api/admin/subscriptions/{id}/events` | AdminSubscriptionController.getSubscriptionEvents | suscripciones | services/pagos | encontrado |
| `PATCH /api/admin/subscriptions/{id}/status` | AdminSubscriptionController.updateSubscriptionStatus | suscripciones | services/pagos | encontrado |
| `POST /api/admin/subscriptions/reconciliation/run` | AdminSubscriptionController.triggerReconciliation | suscripciones | services/pagos | encontrado |
| `POST /api/auth/register` | AuthController.register | auth-ratelimited | services/identidad | encontrado |
| `POST /api/auth/verify-email` | AuthController.verifyEmail | auth-ratelimited | services/identidad | encontrado |
| `POST /api/auth/resend-code` | AuthController.resendCode | auth-ratelimited | services/identidad | encontrado |
| `POST /api/auth/forgot-password` | AuthController.forgotPassword | auth-ratelimited | services/identidad | encontrado |
| `POST /api/auth/reset-password` | AuthController.resetPassword | auth-ratelimited | services/identidad | encontrado |
| `POST /api/auth/login` | AuthController.login | auth-ratelimited | services/identidad | encontrado |
| `POST /api/auth/google` | AuthController.loginWithGoogle | identidad | services/identidad | encontrado |
| `POST /api/auth/complete-profile` | AuthController.completeProfile | identidad | services/identidad | encontrado |
| `GET /api/auth/me` | AuthController.getMe | identidad | services/identidad | encontrado |
| `POST /api/auth/aceptar-terminos` | AuthController.aceptarTerminos | identidad | services/identidad | encontrado |
| `GET /api/auth/mi-cuenta` | AuthController.obtenerMiCuenta | identidad | services/identidad | encontrado |
| `PUT /api/auth/mi-cuenta` | AuthController.actualizarMiCuenta | identidad | services/identidad | encontrado |
| `PUT /api/auth/mi-cuenta/notificaciones` | AuthController.actualizarPreferenciasNotificacion | identidad | services/identidad | encontrado |
| `GET /api/auth/mi-cuenta/novedades` | AuthController.obtenerSuscripcionNovedades | identidad | services/identidad | encontrado |
| `PUT /api/auth/mi-cuenta/novedades` | AuthController.actualizarSuscripcionNovedades | identidad | services/identidad | encontrado |
| `POST /api/auth/mi-cuenta/password` | AuthController.cambiarPassword | identidad | services/identidad | encontrado |
| `POST /api/auth/set-new-password` | AuthController.setNewPassword | identidad | services/identidad | encontrado |
| `POST /api/auth/mi-cuenta/eliminar` | AuthController.eliminarCuenta | identidad | services/identidad | encontrado |
| `POST /api/auth/logout` | AuthController.logout | identidad | services/identidad | encontrado |
| `GET /api/chat/historial/{destinatarioId}` | ChatController.obtenerHistorial | chat | services/chat | encontrado |
| `GET /api/chat/tiene-no-leidos` | ChatController.tieneNoLeidos | chat | services/chat | encontrado |
| `GET /api/chat/canales` | ChatController.obtenerCanales | chat | services/chat | encontrado |
| `GET /api/chat/canales/visitadores` | ChatController.obtenerCanalesVisitadores | chat | services/chat | encontrado |
| `GET /api/notificaciones` | ClinicalController.obtenerNotificaciones | notificaciones | services/notificaciones | encontrado |
| `POST /api/notificaciones/marcar-leidas` | ClinicalController.marcarLeidas | notificaciones | services/notificaciones | encontrado |
| `GET /api/pacientes/atendidos` | ClinicalController.obtenerPacientesAtendidos | clinica | services/profesionales | encontrado |
| `GET /api/pacientes/{pacienteId}/seguimientos` | ClinicalController.obtenerSeguimientos | clinica | services/profesionales | encontrado |
| `POST /api/pacientes/{pacienteId}/seguimientos` | ClinicalController.crearSeguimiento | clinica | services/profesionales | encontrado |
| `GET /api/pacientes/{pacienteId}/informes` | ClinicalController.obtenerInformes | clinica | services/profesionales | encontrado |
| `POST /api/pacientes/{pacienteId}/informes` | ClinicalController.crearInforme | clinica | services/profesionales | encontrado |
| `GET /api/pacientes/me/turnos` | ClinicalController.obtenerMisTurnos | turnos | services/turnos | encontrado |
| `GET /api/pacientes/me/seguimientos` | ClinicalController.obtenerMisSeguimientos | clinica | services/profesionales | encontrado |
| `POST /api/pacientes/me/seguimientos` | ClinicalController.crearMiSeguimiento | clinica | services/profesionales | encontrado |
| `GET /api/pacientes/me/informes` | ClinicalController.obtenerMisInformes | clinica | services/profesionales | encontrado |
| `PUT /api/pacientes/{pacienteId}` | ClinicalController.actualizarPaciente | clinica | services/profesionales | encontrado |
| `DELETE /api/pacientes/{pacienteId}/informes/{informeId}` | ClinicalController.eliminarInforme | clinica | services/profesionales | encontrado |
| `PUT /api/pacientes/{pacienteId}/informes/{informeId}` | ClinicalController.editarInforme | clinica | services/profesionales | encontrado |
| `DELETE /api/pacientes/{pacienteId}/seguimientos/{seguimientoId}` | ClinicalController.eliminarSeguimiento | clinica | services/profesionales | encontrado |
| `POST /api/conceptos/solicitar` | ConceptoController.solicitarDocumento | clinica | services/profesionales | encontrado |
| `GET /api/conceptos/pendientes` | ConceptoController.obtenerDocumentosPendientes | clinica | services/profesionales | encontrado |
| `POST /api/conceptos/{id}/emitir` | ConceptoController.emitirDocumento | clinica | services/profesionales | encontrado |
| `GET /api/health` | HealthController.health | monolito-fallback | backend | encontrado |
| `GET /api/medicos` | MedicoController.obtenerMedicos | profesionales | services/profesionales | encontrado |
| `GET /api/medicos/perfil` | MedicoController.obtenerPerfil | profesionales | services/profesionales | encontrado |
| `PUT /api/medicos/perfil` | MedicoController.actualizarPerfil | profesionales | services/profesionales | encontrado |
| `GET /api/medicos/stats` | MedicoController.obtenerStats | turnos-stats | services/turnos | encontrado |
| `PUT /api/medicos/disponibilidad-config` | MedicoController.actualizarConfigAgenda | profesionales | services/profesionales | encontrado |
| `GET /api/medicos/disponibilidad` | MedicoController.obtenerDisponibilidad | profesionales | services/profesionales | encontrado |
| `PUT /api/medicos/disponibilidad` | MedicoController.actualizarDisponibilidad | profesionales | services/profesionales | encontrado |
| `GET /api/medicos/mercadopago/status` | MedicoController.obtenerEstadoMercadoPago | medicos-mercadopago | services/pagos | encontrado |
| `GET /api/medicos/mercadopago/connect` | MedicoController.obtenerUrlConexionMercadoPago | medicos-mercadopago | services/pagos | encontrado |
| `POST /api/medicos/mercadopago/connect-simulado` | MedicoController.simularConexionMercadoPago | medicos-mercadopago | services/pagos | encontrado |
| `POST /api/medicos/mercadopago/disconnect` | MedicoController.desvincularMercadoPago | medicos-mercadopago | services/pagos | encontrado |
| `GET /api/medicos/mercadopago/callback` | MedicoController.callbackMercadoPago | medicos-mercadopago | services/pagos | encontrado |
| `GET /api/medicos/google-calendar/status` | MedicoController.obtenerEstadoGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `POST /api/medicos/google-calendar/connect-simulado` | MedicoController.simularConexionGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `GET /api/medicos/google-calendar/connect` | MedicoController.obtenerUrlConexionGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `POST /api/medicos/google-calendar/disconnect` | MedicoController.desvincularGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `POST /api/medicos/google-calendar/webhook` | MedicoController.recibirNotificacionGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `GET /api/medicos/google-calendar/eventos` | MedicoController.obtenerEventosExternosGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `GET /api/medicos/google-calendar/callback` | MedicoController.callbackGoogleCalendar | medicos-google-calendar | services/calendario | encontrado |
| `POST /api/internal/novedades` | NovedadesInternoController.enviar | monolito-fallback | backend | encontrado |
| `GET /api/internal/novedades/{id}` | NovedadesInternoController.estado | monolito-fallback | backend | encontrado |
| `POST /api/recetas/enviar` | RecetaController.enviarReceta | clinica | services/profesionales | encontrado |
| `GET /api/recetas/me` | RecetaController.obtenerMisRecetas | clinica | services/profesionales | encontrado |
| `GET /api/recetas/diagnosticos` | RecetaController.buscarDiagnosticos | clinica | services/profesionales | encontrado |
| `GET /api/recetas/medicamentos` | RecetaController.buscarMedicamentos | clinica | services/profesionales | encontrado |
| `GET /api/recetas/financiadores` | RecetaController.buscarFinanciadores | clinica | services/profesionales | encontrado |
| `GET /api/recetas/{id}` | RecetaController.obtenerRecetaPorId | clinica | services/profesionales | encontrado |
| `GET /api/site-access/estado` | SiteAccessController.estado | identidad | services/identidad | encontrado |
| `POST /api/site-access/verificar` | SiteAccessController.verificar | identidad | services/identidad | encontrado |
| `POST /api/soporte/solicitud-datos` | SoporteController.solicitarCopiaDatos | identidad | services/identidad | encontrado |
| `GET /api/subscriptions/plans` | SubscriptionController.getActivePlans | suscripciones | services/pagos | encontrado |
| `GET /api/subscriptions/my-subscription` | SubscriptionController.getMySubscription | suscripciones | services/pagos | encontrado |
| `POST /api/subscriptions/checkout` | SubscriptionController.iniciarCheckout | subscriptions-checkout | services/pagos | encontrado |
| `POST /api/subscriptions/cancel` | SubscriptionController.cancelarMiSuscripcion | suscripciones | services/pagos | encontrado |
| `POST /api/tickets` | TicketController.crearTicket | identidad | services/identidad | encontrado |
| `GET /api/tickets/mios` | TicketController.misTickets | identidad | services/identidad | encontrado |
| `GET /api/tickets/{id}` | TicketController.obtenerTicket | identidad | services/identidad | encontrado |
| `POST /api/tickets/{id}/mensajes` | TicketController.agregarMensaje | identidad | services/identidad | encontrado |
| `GET /api/medicos/{medicoId}/turnos-disponibles` | TurnoController.obtenerTurnosDisponibles | turnos-disponibles | services/turnos | encontrado |
| `GET /api/medicos/turnos-disponibles-conteo` | TurnoController.obtenerConteosDisponibilidad | turnos-conteo | services/turnos | encontrado |
| `POST /api/turnos/reservar` | TurnoController.reservarTurno | turnos-reservar | services/turnos | encontrado |
| `GET /api/medicos/turnos/hoy` | TurnoController.obtenerTurnosDeHoy | medicos-turnos | services/turnos | encontrado |
| `GET /api/medicos/turnos` | TurnoController.obtenerTodosTurnos | medicos-turnos | services/turnos | encontrado |
| `POST /api/turnos/{turnoId}/cancelar` | TurnoController.cancelarTurno | turnos | services/turnos | encontrado |
| `POST /api/turnos/{turnoId}/abandonar-pago` | TurnoController.abandonarReservaPendiente | turnos | services/turnos | encontrado |
| `PUT /api/turnos/{turnoId}/asistencia` | TurnoController.actualizarAsistencia | turnos | services/turnos | encontrado |
| `POST /api/turnos/{turnoId}/documento-enviado` | TurnoController.marcarDocumentoEnviado | turnos | services/turnos | encontrado |
| `PUT /api/turnos/{turnoId}/reprogramar` | TurnoController.reprogramarTurno | turnos | services/turnos | encontrado |
| `GET /api/turnos/{turnoId}/confirmar-asistencia` | TurnoController.confirmarAsistencia | turnos | services/turnos | encontrado |
| `GET /api/turnos/{turnoId}/no-asistira` | TurnoController.noAsistira | turnos | services/turnos | encontrado |
| `POST /api/payments/webhook` | WebhookController.receiveWebhook | pagos-webhooks | services/pagos | encontrado |
| `POST /api/payments/verificar` | WebhookController.verificarPago | pagos | services/pagos | encontrado |

## Tareas programadas y de arranque de main (12)

| Módulo | Tipo | Método | Cuándo |
|---|---|---|---|
| backend | arranque | DataInitializer.run |  |
| backend | arranque | FotoUrlCleanupRunner.run |  |
| backend | arranque | LocalDemoSeeder.run |  |
| backend | arranque | RecetaFlagBackfillRunner.run |  |
| backend | arranque | TarifaAgendaBackfillRunner.run |  |
| backend | programada | GoogleCalendarPollingScheduler.sincronizarCalendariosConectados | fixedRate = 300000, initialDelay = 10000 |
| backend | programada | GoogleCalendarWatchScheduler.renovarCanalesPorVencer | cron = "0 0 4 * * ?", zone = "America/Argentina/Cordoba" |
| backend | programada | LiberarTurnosScheduler.liberarTurnosExpirados | fixedRate = 60000 |
| backend | programada | NotificationScheduler.autoEliminarNotificacionesVencidas | cron = "0 0 * * * ?" |
| backend | programada | NotificationScheduler.enviarRecordatoriosConfirmacionAsistencia | cron = "0 0 9 * * ?", zone = "America/Argentina/Cordoba" |
| backend | programada | SubscriptionReconciliationScheduler.runDailyReconciliation | cron = "0 0 4 * * ?" |
| backend | programada | SubscriptionReconciliationScheduler.runHourlyAlertsCheck | cron = "0 0 * * * ?" |

## Tareas programadas y de arranque de la migración (44)

| Módulo | Tipo | Método | Cuándo |
|---|---|---|---|
| backend | arranque | DataInitializer.run |  |
| backend | arranque | FotoUrlCleanupRunner.run |  |
| backend | arranque | LocalDemoSeeder.run |  |
| backend | arranque | RecetaFlagBackfillRunner.run |  |
| backend | arranque | TarifaAgendaBackfillRunner.run |  |
| backend | programada | OutboxCleanerScheduler.limpiarEventos | cron = "${eventos.cleaner.cron:0 0 3 * * ?}" |
| backend | programada | OutboxRelay.barridoDeRespaldo | fixedDelayString = "${eventos.relay.backup-poll-ms:30000}", initialDelay = 10000 |
| backend | arranque | OutboxRelay.start | ApplicationReadyEvent |
| backend | programada | GoogleCalendarPollingScheduler.sincronizarCalendariosConectados | fixedRate = 300000, initialDelay = 10000 |
| backend | programada | GoogleCalendarWatchScheduler.renovarCanalesPorVencer | cron = "0 0 4 * * ?", zone = "America/Argentina/Cordoba" |
| backend | programada | LiberarTurnosScheduler.liberarTurnosExpirados | fixedRate = 60000 |
| backend | programada | NotificationScheduler.autoEliminarNotificacionesVencidas | cron = "0 0 * * * ?" |
| backend | programada | NotificationScheduler.enviarRecordatoriosConfirmacionAsistencia | cron = "0 0 9 * * ?", zone = "America/Argentina/Cordoba" |
| backend | programada | ResincronizacionProfesionales.resincronizar | initialDelayString = "${profesionales.resincronizacion-demora-inicial-ms:60000}", fixedDelayString = "#{${profesionales.resincronizacion-minutos:15} * 60000}" |
| backend | programada | ResincronizacionSuscripciones.resincronizar | initialDelayString = "${suscripciones.resincronizacion-demora-inicial-ms:60000}", fixedDelayString = "#{${suscripciones.resincronizacion-minutos:15} * 60000}" |
| backend | arranque | ResincronizacionSuscripciones.resincronizar | ApplicationReadyEvent |
| backend | programada | ResincronizacionTurnos.resincronizar | initialDelayString = "${turnos.resincronizacion-demora-inicial-ms:60000}", fixedDelayString = "#{${turnos.resincronizacion-minutos:15} * 60000}" |
| backend | programada | SubscriptionReconciliationScheduler.runDailyReconciliation | cron = "0 0 4 * * ?" |
| backend | programada | SubscriptionReconciliationScheduler.runHourlyAlertsCheck | cron = "0 0 * * * ?" |
| services/calendario | programada | Programador.sincronizarCuentas | fixedRate = 300_000, initialDelay = 30_000 |
| services/calendario | programada | Programador.renovarCanales | cron = "0 0 4 * * ?", zone = "America/Argentina/Cordoba" |
| services/calendario | programada | Programador.borrarTurnosPasados | cron = "0 30 3 * * ?", zone = "America/Argentina/Cordoba" |
| services/calendario | arranque | CargaInicial.alArrancar | ApplicationReadyEvent |
| services/chat | programada | ManejadoresCopias.limpiarTurnosPasados | cron = "0 10 0 * * ?", zone = "America/Argentina/Buenos_Aires" |
| services/chat | arranque | CargaInicial.alArrancar | ApplicationReadyEvent |
| services/identidad | arranque | DataInitializer.run |  |
| services/identidad | arranque | CargaInicialIdentidad.alArrancar | ApplicationReadyEvent |
| services/notificaciones | arranque | CargaInicialContactos.alArrancar | ApplicationReadyEvent |
| services/notificaciones | programada | LimpiezaProgramada.notificacionesViejas | cron = "0 0 * * * ?" |
| services/notificaciones | programada | LimpiezaProgramada.inboxViejo | cron = "0 30 4 * * ?", zone = "America/Argentina/Buenos_Aires" |
| services/pagos | arranque | CargaInicial.alArrancar | ApplicationReadyEvent |
| services/pagos | arranque | CargaInicialSuscripciones.alArrancar | ApplicationReadyEvent |
| services/pagos | programada | Copias.resincronizarSuscriptores | initialDelayString = "#{${pagos.suscripciones.resincronizacion-suscriptores-minutos:15} * 60000}", fixedDelayString = "#{${pagos.suscripciones.resincronizacion-suscriptores-minutos:15} * 60000}" |
| services/pagos | programada | SubscriptionReconciliationScheduler.runDailyReconciliation | cron = "0 0 4 * * ?" |
| services/pagos | programada | SubscriptionReconciliationScheduler.runHourlyAlertsCheck | cron = "0 0 * * * ?" |
| services/pagos | arranque | SubscriptionReconciliationScheduler.runHourlyAlertsCheck | ApplicationReadyEvent |
| services/profesionales | arranque | CargaInicial.alArrancar | ApplicationReadyEvent |
| services/profesionales | arranque | CargaInicialClinica.alArrancar | ApplicationReadyEvent |
| services/profesionales | programada | Copias.resincronizarTurnos | initialDelayString = "#{${profesionales.clinica.resincronizacion-turnos-minutos:15} * 60000}", fixedDelayString = "#{${profesionales.clinica.resincronizacion-turnos-minutos:15} * 60000}" |
| services/profesionales | programada | SincronizacionMonolito.sincronizar | initialDelayString = "#{${profesionales.sincronizacion-segundos:60} * 1000}", fixedDelayString = "#{${profesionales.sincronizacion-segundos:60} * 1000}" |
| services/turnos | arranque | CargaInicial.alArrancar | ApplicationReadyEvent |
| services/turnos | programada | Copias.resincronizarProfesionales | initialDelayString = "#{${turnos.resincronizacion-profesionales-minutos:15} * 60000}", fixedDelayString = "#{${turnos.resincronizacion-profesionales-minutos:15} * 60000}" |
| services/turnos | programada | Programadas.vencerReservas | fixedRate = 60_000 |
| services/turnos | programada | Programadas.recordatorios | cron = "0 0 9 * * ?", zone = "America/Argentina/Cordoba" |

## Mails de main (servicio.método ← quién lo llama)

- `mailSender.createMimeMessage` ← ResendEmailService
- `mailSender.send` ← ResendEmailService
- `resendEmailService.enviarAlertaAdmin` ← SubscriptionReconciliationScheduler
- `resendEmailService.enviarAvisoPagoFallidoSuscripcion` ← SubscriptionService
- `resendEmailService.enviarAvisoSuspensionSuscripcion` ← AdminSubscriptionController
- `resendEmailService.enviarAvisoSuspensionSuscripcion` ← SubscriptionReconciliationScheduler
- `resendEmailService.enviarAvisoVencimientoManualSuscripcion` ← SubscriptionReconciliationScheduler
- `resendEmailService.enviarCodigoRecuperacion` ← AuthController
- `resendEmailService.enviarCodigoVerificacion` ← AuthController
- `resendEmailService.enviarConfirmacionAsistencia` ← TurnoService
- `resendEmailService.enviarConfirmacionCancelacionSuscripcion` ← AdminSubscriptionController
- `resendEmailService.enviarConfirmacionCancelacionSuscripcion` ← SubscriptionService
- `resendEmailService.enviarConfirmacionPagoSuscripcion` ← SubscriptionService
- `resendEmailService.enviarDocumentoAdjunto` ← TurnoService
- `resendEmailService.enviarDocumentoPagadoPaciente` ← PagoWebhookHandler
- `resendEmailService.enviarLinkVideollamadaPaciente` ← GoogleCalendarSyncService
- `resendEmailService.enviarNuevoTurnoProfesional` ← PagoWebhookHandler
- `resendEmailService.enviarPasswordTemporal` ← AdminController
- `resendEmailService.enviarPreavisoRenovacionSuscripcion` ← SubscriptionReconciliationScheduler
- `resendEmailService.enviarRecetaEmitidaPaciente` ← RecetaService
- `resendEmailService.enviarRespuestaTicket` ← TicketService
- `resendEmailService.enviarSolicitudCopiaDatos` ← SoporteController
- `resendEmailService.enviarTurnoCanceladoPaciente` ← TurnoService
- `resendEmailService.enviarTurnoCanceladoProfesional` ← TurnoService
- `resendEmailService.enviarTurnoConfirmadoPaciente` ← PagoWebhookHandler
- `resendEmailService.enviarTurnoConfirmadoPaciente` ← TurnoService
- `resendEmailService.enviarTurnoModificadoProfesional` ← TurnoService
- `resendEmailService.enviarTurnoReprogramadoPaciente` ← TurnoService

## Integraciones externas (hosts https)

| Host | main | Migración |
|---|---|---|
| accounts.google.com | GoogleCalendarOAuthService | backend: GoogleCalendarOAuthService, services/calendario: TokensGoogle |
| api.mercadopago.com | MercadoPagoOAuthService, MercadoPagoService | backend: MercadoPagoOAuthService, backend: MercadoPagoService, services/pagos: MercadoPagoAdminApiSdk, services/pagos: MercadoPagoApiSdk |
| api.resend.com | ResendAudienciaClient, ResendEmailService | backend: ResendAudienciaClient, backend: ResendEmailService, services/identidad: MailNovedades, services/identidad: ResendAudienciaClient, services/notificaciones: ResendEmailService |
| auth.mercadopago.com.ar | MercadoPagoOAuthService | backend: MercadoPagoOAuthService, services/pagos: Vinculacion |
| images.unsplash.com | DataInitializer, LocalDemoSeeder | backend: DataInitializer, backend: LocalDemoSeeder |
| meet.google.com | GoogleCalendarService, LocalDemoSeeder | backend: GoogleCalendarService, backend: LocalDemoSeeder, services/calendario: AgendaTurnos |
| oauth2.googleapis.com | GoogleCalendarOAuthService | backend: GoogleCalendarOAuthService, services/calendario: TokensGoogle |
| servicios1.afip.gov.ar | ArcaConfig | backend: ArcaConfig, services/pagos: ArcaConfig |
| wsaa.afip.gov.ar | ArcaConfig | backend: ArcaConfig, services/pagos: ArcaConfig |
| wsaahomo.afip.gov.ar | ArcaConfig | backend: ArcaConfig, services/pagos: ArcaConfig |
| wswhomo.afip.gov.ar | ArcaConfig | backend: ArcaConfig, services/pagos: ArcaConfig |
| www.afip.gob.ar | ArcaInvoicePdfService | backend: ArcaInvoicePdfService, services/pagos: ArcaInvoicePdfService |
| www.googleapis.com | GoogleCalendarOAuthService | backend: GoogleCalendarOAuthService, services/calendario: TokensGoogle |
| www.mercadopago.com.ar | MercadoPagoService, TurnoService | backend: MercadoPagoService, backend: TurnoService, services/pagos: Checkout |

## Llamadas del frontend de main (95) y si la migración las sigue haciendo

| Llamada | Método y ruta | En el frontend migrado |
|---|---|---|
| getSiteAccessStatus | `GET /api/site-access/estado` | igual |
| verificarSiteAccess | `POST /api/site-access/verificar` | igual |
| getMedicos | `GET /api/medicos` | igual |
| getTurnosDisponibles | `GET /api/medicos/{}/turnos-disponibles` | igual |
| getConteosDisponibilidad | `GET /api/medicos/turnos-disponibles-conteo` | igual |
| reservarTurno | `POST /api/turnos/reservar` | igual |
| getFinanciadores | `GET /api/recetas/financiadores` | igual |
| solicitarCopiaDatos | `POST /api/soporte/solicitud-datos` | igual |
| crearTicket | `POST /api/tickets` | igual |
| getMisTickets | `GET /api/tickets/mios` | igual |
| getTicket | `GET /api/tickets/{}` | igual |
| responderTicket | `POST /api/tickets/{}/mensajes` | igual |
| loginGoogle | `POST /api/auth/google` | igual |
| login | `POST /api/auth/login` | igual |
| register | `POST /api/auth/register` | igual |
| verifyEmail | `POST /api/auth/verify-email` | igual |
| resendCode | `POST /api/auth/resend-code` | igual |
| forgotPassword | `POST /api/auth/forgot-password` | igual |
| resetPassword | `POST /api/auth/reset-password` | igual |
| getMe | `GET /api/auth/me` | igual |
| logout | `POST /api/auth/logout` | igual |
| aceptarTerminos | `POST /api/auth/aceptar-terminos` | igual |
| getMiCuenta | `GET /api/auth/mi-cuenta` | igual |
| actualizarMiCuenta | `PUT /api/auth/mi-cuenta` | igual |
| actualizarPreferenciasNotificacion | `PUT /api/auth/mi-cuenta/notificaciones` | igual |
| obtenerSuscripcionNovedades | `GET /api/auth/mi-cuenta/novedades` | igual |
| actualizarSuscripcionNovedades | `PUT /api/auth/mi-cuenta/novedades` | igual |
| cambiarPassword | `POST /api/auth/mi-cuenta/password` | igual |
| eliminarCuenta | `POST /api/auth/mi-cuenta/eliminar` | igual |
| completeProfile | `POST /api/auth/complete-profile` | igual |
| getAdminUsers | `GET /api/admin/users` | igual |
| updateUserRol | `PUT /api/admin/users/{}/rol` | igual |
| verifyProfessional | `POST /api/admin/users/{}/verify` | igual |
| rejectProfessional | `POST /api/admin/users/{}/reject` | igual |
| resetUserPassword | `POST /api/admin/users/{}/reset-password` | igual |
| getAdminTickets | `GET /api/admin/tickets` | igual |
| cambiarEstadoTicket | `PUT /api/admin/tickets/{}/estado` | igual |
| setNewPassword | `POST /api/auth/set-new-password` | igual |
| getPerfil | `GET /api/medicos/perfil` | igual |
| getStats | `GET /api/medicos/stats` | igual |
| actualizarPerfil | `PUT /api/medicos/perfil` | igual |
| getDisponibilidad | `GET /api/medicos/disponibilidad` | igual |
| actualizarDisponibilidad | `PUT /api/medicos/disponibilidad` | igual |
| actualizarConfigAgenda | `PUT /api/medicos/disponibilidad-config` | igual |
| getMercadoPagoStatus | `GET /api/medicos/mercadopago/status` | igual |
| getMercadoPagoConnectUrl | `GET /api/medicos/mercadopago/connect` | igual |
| simularConexionMercadoPago | `POST /api/medicos/mercadopago/connect-simulado` | igual |
| desconectarMercadoPago | `POST /api/medicos/mercadopago/disconnect` | igual |
| getGoogleCalendarStatus | `GET /api/medicos/google-calendar/status` | igual |
| getGoogleCalendarConnectUrl | `GET /api/medicos/google-calendar/connect` | igual |
| simularConexionGoogleCalendar | `POST /api/medicos/google-calendar/connect-simulado` | igual |
| desconectarGoogleCalendar | `POST /api/medicos/google-calendar/disconnect` | igual |
| getEventosExternosGoogleCalendar | `GET /api/medicos/google-calendar/eventos` | igual |
| getTurnosHoy | `GET /api/medicos/turnos/hoy` | igual |
| getTurnos | `GET /api/medicos/turnos` | igual |
| enviarReceta | `POST /api/recetas/enviar` | igual |
| getMisRecetas | `GET /api/recetas/me` | igual |
| getRecetaPorId | `GET /api/recetas/{}` | igual |
| buscarMedicamentos | `GET /api/recetas/medicamentos` | igual |
| getChatHistorial | `GET /api/chat/historial/{}` | igual |
| getChatCanales | `GET /api/chat/canales` | igual |
| getTieneNoLeidos | `GET /api/chat/tiene-no-leidos` | igual |
| getChatCanalesVisitadores | `GET /api/chat/canales/visitadores` | igual |
| getNotificaciones | `GET /api/notificaciones` | igual |
| marcarNotificacionesLeidas | `POST /api/notificaciones/marcar-leidas` | igual |
| getPacientesAtendidos | `GET /api/pacientes/atendidos` | igual |
| actualizarPaciente | `PUT /api/pacientes/{}` | igual |
| getSeguimientos | `GET /api/pacientes/{}/seguimientos` | igual |
| crearSeguimiento | `POST /api/pacientes/{}/seguimientos` | igual |
| eliminarSeguimiento | `DELETE /api/pacientes/{}/seguimientos/{}` | igual |
| getInformes | `GET /api/pacientes/{}/informes` | igual |
| crearInforme | `POST /api/pacientes/{}/informes` | igual |
| editarInforme | `PUT /api/pacientes/{}/informes/{}` | igual |
| eliminarInforme | `DELETE /api/pacientes/{}/informes/{}` | igual |
| getMisTurnos | `GET /api/pacientes/me/turnos` | igual |
| getMisSeguimientos | `GET /api/pacientes/me/seguimientos` | igual |
| crearMiSeguimiento | `POST /api/pacientes/me/seguimientos` | igual |
| getMisInformes | `GET /api/pacientes/me/informes` | igual |
| cancelarTurno | `POST /api/turnos/{}/cancelar` | igual |
| abandonarReservaPendiente | `POST /api/turnos/{}/abandonar-pago` | igual |
| actualizarAsistencia | `PUT /api/turnos/{}/asistencia` | igual |
| reprogramarTurno | `PUT /api/turnos/{}/reprogramar` | igual |
| marcarDocumentoEnviado | `POST /api/turnos/{}/documento-enviado` | igual |
| verificarPagoTurno | `GET /api/payments/verificar` | igual |
| getSubscriptionPlans | `GET /api/subscriptions/plans` | igual |
| getMySubscription | `GET /api/subscriptions/my-subscription` | igual |
| iniciarCheckoutSuscripcion | `POST /api/subscriptions/checkout` | igual |
| cancelMySubscription | `POST /api/subscriptions/cancel` | igual |
| getAdminSubscriptionOverview | `GET /api/admin/subscriptions/overview` | igual |
| getAdminSubscriptionsList | `GET /api/admin/subscriptions/list` | igual |
| getAdminSubscriptionPlans | `GET /api/admin/subscriptions/plans` | igual |
| registerAdminManualPayment | `POST /api/admin/subscriptions/manual-payment` | igual |
| ejecutarReconciliacionAdmin | `POST /api/admin/subscriptions/reconciliation/run` | igual |
| updateSubscriptionPlan | `PUT /api/admin/subscriptions/plans/{}` | igual |
| updateSubscriptionStatus | `PATCH /api/admin/subscriptions/{}/status` | igual |
