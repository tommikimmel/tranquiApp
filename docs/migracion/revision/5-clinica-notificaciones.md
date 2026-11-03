# C3 · Revisión 5: historia clínica, recetas, notificaciones y mails

- **main:** `f11-main` (7d06fb0), backend en `backend/src/main/java/com/tranqui/app` (abreviado `M/`).
- **Migración:** el worktree `f11-c4` desapareció en medio de la revisión. Seguí leyendo la misma rama con
  `git show cf418b5:<ruta>` (`fix/full-fase-11-alerta-admins` = `test/full-fase-11-c3-revision`, que ya tiene el
  arreglo de la alerta a admins). Abreviaturas: `PROF/` = `services/profesionales/src/main/java/com/tranqui/profesionales`,
  `NOTIF/` = `services/notificaciones/src/main/java/com/tranqui/notificaciones`.
- **Gateway:** `/api/pacientes/**`, `/api/recetas/**` y `/api/conceptos/**` van a la regla `clinica`
  (`TRANQUI_SERVICES_CLINICA_URI=lb://profesionales`). `/api/pacientes/me/turnos` lo agarra antes la regla `turnos`.
  `/api/notificaciones/**` y `/ws-notificaciones/**` van a `lb://notificaciones`, y `/ws-tranqui/**` va al chat
  (`gateway/src/main/resources/application.yml:151, 383-418, 484-487, 575-578`).
- Lo que ya se sabía y no se reporta: `GET /api/notificaciones` sin el campo `usuario`, y la alerta a admins (ya arreglada).

## 1. Historia clínica y recetas

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `GET /api/pacientes/atendidos` | `M/controller/ClinicalController.java:55`, `M/service/ClinicalService.java:66-122` | `PROF/clinica/HistoriaClinicaController.java:40`, `HistoriaClinicaService.java:153-211` | igual | Mismo armado: turnos CONFIRMADO/PENDIENTE_VALIDACION más chat (solo rol PACIENTE), prioridad alta si hay un turno confirmado en los próximos 3 días, `ultimaVisita`, `sinTurno` y no leídos. Mismos campos en `PacienteDto` (`PROF/clinica/Dtos.java:36-59`). Diferencia en un caso borde: un paciente sin copia en `personas` se saltea (`HistoriaClinicaService.java:166-177`), ver hallazgo 5. |
| `GET /api/pacientes/{id}/seguimientos` (profesional) | `ClinicalController.java:66`, `ClinicalService.java:171-175` | `HistoriaClinicaController.java:51`, `HistoriaClinicaService.java:213-222` | igual | Solo lo que cargó ese profesional, del más nuevo al más viejo. El JSON tiene los mismos campos que la entidad: id, fecha, estadoAnimo, sintomas, notas, fechaCreacion, estado, motivo, hashIntegridad y nombreMedico. |
| `POST /api/pacientes/{id}/seguimientos` | `ClinicalController.java:77`, `ClinicalService.java:178-206` | `HistoriaClinicaController.java:62`, `HistoriaClinicaService.java:224-245` | cambia a propósito | Ahora exige que el paciente esté "atendido": si no, 403 "El paciente no está entre los que atendés." ([fase-7, decisión 2](../../docs/specs/2026-10-25-fase-7-historia-clinica.md)). El hash, el estado VIGENTE y la fecha por defecto quedan igual. La notificación sale por el evento `profesionales.historia-clinica.actualizada` con el mismo texto. |
| `GET /api/pacientes/{id}/informes` (profesional) | `ClinicalController.java:91`, `ClinicalService.java:209-213` | `HistoriaClinicaController.java:76`, `HistoriaClinicaService.java:247-257` | igual | Mismos campos, informeOriginalId incluido. |
| `POST /api/pacientes/{id}/informes` | `ClinicalController.java:102`, `ClinicalService.java:216-254` | `HistoriaClinicaController.java:87`, `HistoriaClinicaService.java:259-281` | cambia a propósito | Exige "atendido" (fase-7, decisión 2). Lo demás es igual: el hash, nombreArchivo y las dos notificaciones (al profesional y al paciente), que salen por el evento. |
| `GET /api/pacientes/me/seguimientos` | `ClinicalController.java:133`, `ClinicalService.java:257-261` | `HistoriaClinicaController.java:101`, `HistoriaClinicaService.java:284-293` | igual | Todos los seguimientos del paciente, de todos sus profesionales, con nombreMedico. |
| `POST /api/pacientes/me/seguimientos` | `ClinicalController.java:143`, `ClinicalService.java:264-294`: lo asigna siempre a `paula@tranqui.com` (el JSON no trae profesional por el `@JsonIgnore`) | `HistoriaClinicaService.java:296-303`: **siempre** responde 400 "El seguimiento tiene que ser para un profesional que te atiende." | cambia a propósito | fase-7, decisión 2 ("se rechaza en vez de asignarlo a uno fijo"). Como el pedido no tiene forma de nombrar un profesional, la ruta queda inutilizable. El frontend no la usa (solo `api.crearMiSeguimiento`, sin llamadas). Ver hallazgo 4. |
| `GET /api/pacientes/me/informes` | `ClinicalController.java:154`, `ClinicalService.java:297-301` | `HistoriaClinicaController.java:121`, `HistoriaClinicaService.java:305-316` | igual | El paciente ve todos sus informes, también los ANULADO y los anexos, como en main. |
| `GET /api/pacientes/me/turnos` | `ClinicalController.java:123` | regla `turnos` del gateway (`application.yml:151`) | igual | Queda fuera de este dominio: el ruteo es correcto porque la regla de turnos va antes que la de clínica. |
| `PUT /api/pacientes/{id}` (editar ficha) | `ClinicalController.java:164`, `ClinicalService.java:310-448` | `HistoriaClinicaController.java:132`, `HistoriaClinicaService.java:318-341` → `PUT /internal/identidad/pacientes/{id}` (`services/identidad/.../interno/FichaPaciente.java:46-156`) | cambia a propósito | Exige "atendido" (decisión 2) y guarda en identidad por una ruta interna: si identidad no responde, 503 "No se pudo actualizar el paciente en este momento…" (decisión 3). Las validaciones y los mensajes son iguales: los errores van todos juntos, con el mismo regex de afiliado y el mismo chequeo de teléfono. El +54, el "borrar datos cargados" sin credencial y la obra social con el plan también son iguales. El 400 de identidad llega al usuario con el mismo texto. La respuesta usa `ultimaVisita="Hoy"` y los no leídos, como main. Publica `identidad.usuario.actualizado`. |
| `DELETE /api/pacientes/{p}/informes/{id}?motivo=` (anular) | `ClinicalController.java:178`, `ClinicalService.java:454-463` | `HistoriaClinicaController.java:146`, `HistoriaClinicaService.java:343-355` | igual | Es un soft-delete: queda ANULADO con el motivo (por defecto "No especificado"), solo puede hacerlo el autor y si no es el autor da 403 con el mismo texto. La fila nunca se borra. |
| `PUT /api/pacientes/{p}/informes/{id}` (corrección por anexo) | `ClinicalController.java:194`, `ClinicalService.java:470-501` | `HistoriaClinicaController.java:160`, `HistoriaClinicaService.java:357-383` | igual | El original queda VIGENTE_CORREGIDO y se crea una fila ANEXO_CORRECCION con informeOriginalId y motivo. Si el informe está anulado, 409 "No se puede corregir un informe anulado". Devuelve el anexo. |
| `DELETE /api/pacientes/{p}/seguimientos/{id}` | `ClinicalController.java:215`, `ClinicalService.java:504-513` | `HistoriaClinicaController.java:173`, `HistoriaClinicaService.java:385-397` | igual | Soft-delete con motivo; solo el autor. |
| Integridad: hash SHA-256 por asiento, nada se borra | `ClinicalService.java:50-63` | `HistoriaClinicaService.java:85-98`; `PROF/datos/Informes.java:102-109`, `Seguimientos.java:98-105` (solo hay `UPDATE estado/motivo`, nunca `DELETE`) | igual | Las partes del hash y el separador `|` son los mismos. Al anular o corregir se reescribe el mismo hash original. |
| Avisos en la campanita de historia clínica (4) | `ClinicalService.java:198-203, 239-251, 286-291` | `NOTIF/avisos/ManejadoresClinicos.java:12-31` | igual | "Seguimiento Diario cargado", "Informe clínico creado", "Nuevo Informe Clínico disponible" y "Seguimiento cargado por paciente", con el mismo texto y el mismo tipo. El evento no trae contenido clínico. |
| `POST /api/recetas/enviar` | `M/controller/RecetaController.java:25`, `M/service/RecetaService.java:50-371` | `PROF/recetas/RecetaController.java:32`, `RecetaService.java:67-464` | cambia a propósito | Exige "atendido" (decisión 2). El gate del plan usa la copia `puedeRecetar` y da el mismo mensaje (409). Se mantienen las mismas validaciones de datos faltantes (texto incluido), el payload a QBI2 (paciente, médico, matrícula, **sello** con los mismos fallbacks y lugar de atención), los mensajes de error de QBI2 recortados a 240 y los 422. "Marcar receta fuera de turno como enviada" pasa por el evento `profesionales.receta.emitida`, que consume turnos. El mail va por una ruta interna (decisión 4), ver hallazgo 1. |
| `GET /api/recetas/me` | `RecetaController.java:33`, `RecetaService.java:374-384` | `RecetaController.java:43`, `RecetaService.java:466-488` | igual | Al paciente le muestra las recetas donde es el paciente; al profesional, las que emitió él. Mismos campos en `RecetaResponseDto`. |
| `GET /api/recetas/{id}` | `RecetaController.java:63`, `RecetaService.java:387-397` | `RecetaController.java:83`, `RecetaService.java:490-500` | igual | Solo la ven el paciente o el médico de esa receta; para el resto, 403 "No tiene permiso para ver esta receta". |
| `GET /api/recetas/diagnosticos`, `/medicamentos` | `RecetaController.java:40, 47` | `RecetaController.java:52, 63` | igual | Solo el profesional. Mismos parámetros (`texto`, `pagina=1`). |
| `GET /api/recetas/financiadores` (pública) | `RecetaController.java:58` | `RecetaController.java:78`; `PROF/seguridad/FiltroAutenticacionUsuario.java:24, 67` | igual | Sigue sin sesión, para el checkout de invitados. |
| Anular receta en QBI2 | `Qbi2RecipeClient.anularReceta` existe pero **ninguna ruta lo usa** | Lo mismo: el cliente está, la ruta no | igual | En main no hay endpoint de anulación, así que no se pierde nada. |
| Sello (3 líneas) | Validación en `M/service/MedicoService.java:233-249` y uso en `RecetaService.java:189-201` | `PROF/perfil/Perfiles.java:112-120` y `RecetaService.java:236-247` | igual | Los mismos topes (40/40/25), los mismos mensajes y los mismos fallbacks en la receta. |
| `POST /api/conceptos/solicitar` | `M/controller/ConceptoController.java:33`, `M/service/ConceptoService.java:19-47`: devuelve el `checkoutUrl` real de Mercado Pago | `PROF/clinica/ConceptoController.java:22`, `ConceptoService.java:23-48`: devuelve `checkoutUrl=""` y publica `concepto.solicitado` | distinto | Ver hallazgo 2. El frontend no usa `/api/conceptos`. |
| `GET /api/conceptos/pendientes` | `ConceptoController.java:45`: la entidad completa, con `paciente` y `medico` como `Usuario` entero | `ConceptoService.java:50-69`: `UsuarioResumen` (id, nombre, apellido, email, teléfono, dni) | distinto | Es una mejora de privacidad que no está en la spec. Ver hallazgo 3. |
| `POST /api/conceptos/{id}/emitir` | `ConceptoService.java:50-70`: para un concepto ajeno tira `SecurityException` (500) | `ConceptoService.java:71-98`: 403 | cambia a propósito | Fase 7, "Hecho en H2" ("emitir un concepto ajeno responde 403 en vez de 500"). |
| Certificados e informes adjuntos: `POST /api/turnos/{id}/documento-enviado` | `M/service/TurnoService.java:815-867` | `services/turnos/.../turnos/Gestion.java:127-162` → `POST /internal/notificaciones/documentos` (`NOTIF/interno/DocumentosController.java:34-49`) | igual | Mismas validaciones y mensajes: dueño, no ocupa agenda, pagado, no es receta, archivo obligatorio, 8 MB y paciente con mail. Si falla el mail, el error es el mismo ("No se pudo enviar el mail con el documento…") y no se marca. El adjunto no se guarda. La notificación "Documento enviado" sale por evento con el mismo texto. |

## 2. Notificaciones y mails

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `GET /api/notificaciones` | `ClinicalController.java:34`, `M/service/NotificacionService.java:71-76` | `NOTIF/notificacion/NotificacionController.java:32-44`, `NotificacionService.java:65-69` | igual | Solo PACIENTE y PSIQUIATRA, las últimas 24 h, de la más nueva a la más vieja, con id, titulo, mensaje, leido, tipo y fechaCreacion. Si no existe el contacto, la migración devuelve `[]` y main 404: es un caso irreal. |
| `POST /api/notificaciones/marcar-leidas` | `ClinicalController.java:43`, `NotificacionService.java:79-83` | `NotificacionController.java:46-55` | igual | Marca todas las del usuario. |
| Aviso en vivo de las notificaciones guardadas | Por `/ws-tranqui`, `NotificacionService.java:46-62` (payload id, pacienteNombre, tipo, fecha, titulo, mensaje) | Por `/ws-notificaciones`, `NOTIF/notificacion/NotificacionService.java:28-45, 91-100`, solo después del commit | cambia a propósito | Endpoint propio (fase-1, "En vivo"). El payload es el mismo y el frontend se suscribe a los dos endpoints (`frontend/src/api/api.ts:16`). |
| Seguridad del WebSocket | `M/config/WebSocketChannelInterceptor.java:40-80`: JWT por cookie o Bearer, y solo `/topic/notificaciones/{su id}` | `NOTIF/seguridad/WebSocketConfig.java:76-109` | igual | Mismas reglas. Además rechaza destinos que no sean de notificaciones, pero ese endpoint no tiene otros. |
| `/ws-tranqui`: aviso "NUEVO_MENSAJE" del chat | `M/controller/ChatController.java:55-66` | `services/chat/.../mensaje/ChatController.java:81-91` | igual | El mismo payload a `/topic/notificaciones/{destinatario}`. |
| Aviso en vivo de un concepto aprobado (sin guardar) | `M/service/PagoWebhookHandler.java:62-72` | `NOTIF/avisos/ManejadoresPagos.java:12-26` | igual | El mismo payload al profesional. |
| Limpieza de notificaciones de más de 24 h | `M/service/NotificationScheduler.java:21-29` (cada hora) | `NOTIF/notificacion/LimpiezaProgramada.java:21-25` (cada hora) | igual | Además se limpia el inbox de eventos a los 30 días, algo nuevo de la plataforma. |
| Preferencia de mails ("Mi Cuenta") | `Usuario.notificacionesEmailHabilitadas`, se lee en el momento | Copia en `Contacto.emailHabilitado` (notificaciones) y `Persona.emailHabilitado` (profesionales), que se actualiza con `identidad.usuario.actualizado` (`services/identidad/.../cuenta/AccountService.java:86-97`) | igual | Se respeta en los mismos mails (`NOTIF/avisos/Avisos.java:38-40`). La copia es eventual: un mail que sale segundos después del cambio podría usar el valor viejo. |
| Fallo de un mail | Se loguea y no se reintenta | `EnvioMailException`: el evento se reintenta con `Idempotency-Key` = eventId:consumidor (`Avisos.java:33-35`) | cambia a propósito | fase-1, línea 71. |
| Plantilla común, transporte (Resend o SMTP/Mailpit) y remitente | `M/service/ResendEmailService.java:82-118, 720-848` | `NOTIF/mail/ResendEmailService.java` | igual | `plantilla(...)` es idéntica (lo comparé con un diff). Mismo `from` y `reply_to`. |

### Los 23 mails de `ResendEmailService` de main

Comparé las plantillas método por método con espacios normalizados: 20 son idénticas, y las 3 diferencias están en
la tabla. El disparo lo revisé del lado de notificaciones (handler, destinatario y preferencia). La condición
exacta de cada productor (turnos, pagos, identidad) le toca a los revisores de esos dominios.

| # | Mail | Quién lo dispara en main → a quién | Migración | Estado | Detalle |
|---|---|---|---|---|---|
| 1 | `enviarCodigoVerificacion` | `AuthController:212,270`, al registrarse o reenviar el código → al usuario | `identidad.codigo-verificacion.generado` → `NOTIF/avisos/ManejadoresIdentidad.java:11-27` | igual | Mismo texto. No depende de la preferencia, como en main. |
| 2 | `enviarCodigoRecuperacion` | `AuthController:289` → al usuario | El mismo evento con tipo PASSWORD_RESET (`ManejadoresIdentidad.java:21-22`) | igual | |
| 3 | `enviarSolicitudCopiaDatos` | `SoporteController:39` → a soporte, con reply-to al usuario | `identidad.soporte.solicitud-datos` → `ManejadoresIdentidad.java:97-117` | igual | La dirección de soporte ahora es configurable (`app.soporte-email`) y por defecto es la misma. |
| 4 | `enviarRespuestaTicket` | `TicketService:164-173` → al creador del ticket | `identidad.ticket.respondido` → `ManejadoresIdentidad.java:39-48` | igual | |
| 5 | `enviarPasswordTemporal` | `AdminController:143` → al usuario | `identidad.password-temporal.generada` → `ManejadoresIdentidad.java:71-95` | igual | Valida el evento y no loguea la contraseña. |
| 6 | `enviarDocumentoAdjunto` | `TurnoService:850` → al paciente (sin mirar la preferencia) | Turnos `Gestion.java:153-158` → `DocumentosController.java:34-49` | igual | El mismo texto y el mismo adjunto. Antes devolvía `false`; ahora responde 400/502 y turnos lo traduce al mismo error. |
| 7 | `enviarNuevoTurnoProfesional` | `PagoWebhookHandler:151` → al profesional (solo turnos, no documentos) | `turnos.turno.confirmado` (origen pago) → `ManejadoresTurnos.java:68-84` | igual | |
| 8 | `enviarTurnoCanceladoProfesional` | `TurnoService:768` → al profesional, si ocupa agenda | `turnos.turno.cancelado` → `ManejadoresTurnos.java:126-142` | igual | |
| 9 | `enviarTurnoModificadoProfesional` | `TurnoService:936` → al profesional | `turnos.turno.reprogramado` → `ManejadoresTurnos.java:167-180` | igual | |
| 10 | `enviarConfirmacionAsistencia` | Recordatorio `TurnoService:975-997` → al paciente, si acepta mails | `turnos.recordatorio.programado` → `ManejadoresTurnos.java:200-216` | igual | La etiqueta de modalidad la arma turnos (`Publicador.java:80`). |
| 11 | `enviarAlertaAdmin` | `SubscriptionReconciliationScheduler:50-66` → a cada ADMIN | `pagos.alerta-admin` → `ManejadoresPagos.java:65-78` | igual | Ahora escapa el HTML del título y del mensaje (llegan de otro servicio). Ya estaba arreglado en esta rama. |
| 12 | `enviarTurnoConfirmadoPaciente` | `PagoWebhookHandler:175`, OSDE `TurnoService:488` → al paciente, si acepta mails | `ManejadoresTurnos.java:86-109` | igual | En OSDE va sin precio, como main. |
| 13 | `enviarDocumentoPagadoPaciente` | `PagoWebhookHandler:170` → al paciente, si acepta mails | `ManejadoresTurnos.java:100-102` | igual | |
| 14 | `enviarTurnoCanceladoPaciente` | `TurnoService:783` → al paciente, si acepta mails | `ManejadoresTurnos.java:144-163` | igual | Suma el caso `EN_PROCESO` (el reembolso se pidió y se avisa cuando se acredite). Es de la Fase 4, porque los reembolsos ahora son asíncronos. |
| 15 | `enviarTurnoReprogramadoPaciente` | `TurnoService:952` → al paciente, si acepta mails | `ManejadoresTurnos.java:182-196` | igual | |
| 16 | `enviarLinkVideollamadaPaciente` | `GoogleCalendarSyncService:207-216`, si hay link nuevo → al paciente, si acepta mails | `calendario.meet.creado` (`linkNuevo`) → `ManejadoresTurnos.java:247-264` | igual | |
| 17 | `enviarRecetaEmitidaPaciente` | `RecetaService:356-368` → al paciente, si acepta mails | `PROF/recetas/RecetaService.java:448-461` → `POST /internal/notificaciones/recetas` (`DocumentosController.java:21-23, 55-70`) | **distinto** | Cuando la receta no trae link, el mail no sale. Ver hallazgo 1. |
| 18 | `enviarPreavisoRenovacionSuscripcion` | `SubscriptionReconciliationScheduler:118` → al profesional | `suscripciones.aviso.programado` → `ManejadoresSuscripciones.java:11-42` | igual | No depende de la preferencia, como main. |
| 19 | `enviarConfirmacionPagoSuscripcion` | `SubscriptionService:453,613` | El mismo evento (PAGO_CONFIRMADO) | igual | |
| 20 | `enviarAvisoPagoFallidoSuscripcion` | `SubscriptionService:628` | El mismo evento (PAGO_FALLIDO) | igual | |
| 21 | `enviarConfirmacionCancelacionSuscripcion` | `SubscriptionService:668,762`, `AdminSubscriptionController:298` | El mismo evento (CANCELACION_CONFIRMADA) | igual | |
| 22 | `enviarAvisoSuspensionSuscripcion` | `SubscriptionReconciliationScheduler:164,228`, `AdminSubscriptionController:311` | El mismo evento (SUSPENSION) | igual | |
| 23 | `enviarAvisoVencimientoManualSuscripcion` | `SubscriptionReconciliationScheduler:259` | El mismo evento (VENCIMIENTO_MANUAL) | igual | |
| — | Novedades (`htmlNovedades` / `enviarHtml`) | `NovedadesService` | Lo manda identidad (`MailNovedades`) | igual | No está en el servicio de notificaciones a propósito (fase-1, punto 3). Le toca al revisor de identidad. |
| — | `enviarReembolsoRealizadoPaciente` / `enviarReembolsoPendientePaciente` | No existen | `ManejadoresPagos.java:32-63` | cambia a propósito | Son mails nuevos de la Fase 4 (reembolso asíncrono) y respetan la preferencia. |

**Totales por estado (sin contar las filas "—" informativas):** igual 48 (18 de clínica y recetas, 8 de
notificaciones y 22 mails) · cambia a propósito 8 · distinto 3 · falta 0. Las dos filas "—": novedades está igual
(lo manda identidad) y los mails de reembolso son nuevos a propósito.

## Hallazgos (distinto / falta / a verificar)

1. **Distinto: el mail de la receta no sale cuando QBI2 no devuelve el link del PDF.**
   - **main:** `M/service/RecetaService.java:356-368` lo manda igual y la plantilla cambia el botón a "Ver en Tranqui App" (`M/service/ResendEmailService.java:513-515`).
   - **Migración:** `NOTIF/interno/DocumentosController.java:21-23` exige `@NotBlank @Pattern("https://\\S+")` en `pdfUrl`, así que responde 400 y profesionales solo loguea el error (`PROF/recetas/RecetaService.java:458-460`). La plantilla de la migración sí acepta un link nulo (`NOTIF/mail/ResendEmailService.java:707+`, y el test `PlantillasYTransporteTest.java:259` lo prueba), pero la ruta lo bloquea antes. En local, con QBI2 simulado (`s3Link(null)`), el mail no sale nunca.
   - **Impacto:** el paciente no se entera por mail de que tiene una receta emitida.
   - **Arreglo:** que `pdfUrl` sea opcional y, si viene, que tenga que ser `https://`. Por ejemplo `@Pattern(regexp = "https://\\S+")` sin `@NotBlank`. Sumar un test de la ruta con `pdfUrl` nulo.
2. **Distinto: `POST /api/conceptos/solicitar` ya no le da al paciente el link de pago.**
   - **main:** devuelve el `checkoutUrl` de Mercado Pago en la misma respuesta (`M/service/ConceptoService.java:37-47`).
   - **Migración:** responde `checkoutUrl: ""` (`PROF/clinica/ConceptoService.java:47`), y después del corte de la Fase 7 nadie guarda ni expone el link. La Fase 4 lo guardaba en el monolito al recibir `pagos.checkout.creado`, pero profesionales solo consume `pagos.concepto.aprobado` (`PROF/interno/Manejadores.java:178`).
   - **Impacto:** hoy ninguno, porque el frontend no llama a `/api/conceptos`. Si alguien la usa, el documento pago no se puede pagar.
   - **Arreglo:** o se retira la API de conceptos y se documenta como sin uso, o profesionales consume `pagos.checkout.creado` para las solicitudes y expone el link.
3. **Distinto, menor: el JSON de `/api/conceptos/pendientes` y `/emitir` cambió.**
   - **main:** devuelve el `Usuario` entero del paciente y del médico, con hash de contraseña y tokens (`M/model/SolicitudDocumento.java:27-33`).
   - **Migración:** devuelve un resumen (`PROF/clinica/Dtos.java:116-137`).
   - **Impacto:** ninguno (no hay uso en el frontend). Es una mejora de privacidad que no está en ninguna spec.
   - **Arreglo:** anotarlo en `docs/migracion/comparacion-main.md` como cambio a propósito, como se hizo con el historial del chat de la Fase 2.
4. **A verificar: `POST /api/pacientes/me/seguimientos` responde siempre 400.**
   - **Por qué:** la decisión 2 de la fase-7 pide rechazar el seguimiento "sin profesional", pero el pedido no tiene forma de indicar uno, así que la ruta quedó inutilizable (`PROF/clinica/HistoriaClinicaService.java:296-303`). En main tampoco servía: todo iba a `paula@tranqui.com`. El frontend no la llama.
   - **Impacto:** ninguno visible.
   - **Arreglo propuesto:** confirmar que se quiere así. Si es así, documentarlo y sacar `api.crearMiSeguimiento` del frontend. Si no, aceptar un `medicoId` validado con "atendido".
5. **A verificar: "Pacientes atendidos" saltea a los pacientes que todavía no están en la copia `personas`.**
   - **Por qué:** `PROF/clinica/HistoriaClinicaService.java:162-177` descarta los ids sin `Persona`. En main el paciente sale siempre, porque se lee de la misma base. Con la copia al día no pasa (la carga inicial trae a todos los usuarios y `usuario.registrado/actualizado` la refresca), pero un evento atrasado o fallido haría desaparecer al paciente de la lista hasta la resincronización (`SincronizacionMonolito`, cada 60 s).
   - **Arreglo:** que la conciliación de copias mida cuántos ids de `turno_atendido` no tienen `Persona`, o loguear un warning cuando se descarta uno.
