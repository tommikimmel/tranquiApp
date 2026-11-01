# C3 — Pagos, reembolsos, suscripciones y facturación: `main` contra la migración

- **main:** `f11-main` (`backend/src/main/java/com/tranqui/app`, abreviado `app/`).
- **Migración:** el worktree `f11-c4` desapareció a mitad de la revisión; el resto se leyó (solo lectura, `git show`)
  de la rama `test/full-fase-11-c3-revision`, que contiene c4 y el arreglo de la alerta a admins. Abreviaturas:
  `pagos/` = `services/pagos/src/main/java/com/tranqui/pagos`, `gw/` = `gateway/src/main/java/com/tranqui/gateway`.
- **Specs citadas:** F4 = `docs/specs/2026-10-22-fase-4-pagos.md`, F6 = `...-fase-6-turnos.md`,
  F8 = `docs/specs/2026-10-26-fase-8-suscripciones.md`.
- No se reporta (ya conocido): la alerta por mail a los admins y el paywall con `lb://pagos`.

## 1. Pagos y reembolsos

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| Firma del webhook `POST /api/payments/webhook` | `app/service/MercadoPagoWebhookValidator.java:41-82` | `pagos/mp/ValidadorFirma.java` (`esValida`) | **igual** | Mismo HMAC-SHA256 (`id:…;request-id:…;ts:…;`), mismo bypass `test-signature` con `payment.simulation.enabled`, misma laxitud con `mercadopago.enabled=false`, 400 "Firma inválida". |
| Respuesta del webhook si falla el proceso | Siempre 200 (`WebhookController.java:229-233`) | 500 para que MP reintente (`pagos/web/PagosController.java:61-77`) | **cambia a propósito** | F4, P2 "Webhook". Efecto lateral: un `data.id` no numérico ahora da 500 y MP reintenta (ver hallazgo 13). |
| Webhook `payment` de turno/documento aprobado | `WebhookController.java:151-228` → `PagoWebhookHandler` | `pagos/cobros/NotificacionesMercadoPago.java` (rama `payment`) → `Aprobaciones.aplicar` | **igual** | Token del profesional por `user_id`, referencia numérica = turno, `doc-<id>` = documento. Idempotencia: main por estado del turno/solicitud; migración por `transaction_id` único (`datos/Pagos.java`, `ON CONFLICT DO NOTHING`). |
| Webhook `payment` rechazado/cancelado | Se ignora (solo procesa `approved`, `WebhookController.java:216`) | Registra el rechazo y publica `pagos.pago.rechazado` (`cobros/Aprobaciones.java`) | **cambia a propósito** | F4 P1 ("`pagos.pago.rechazado` libera el horario") y criterio de aceptación. Rechazos de documentos no se informan, igual que main. |
| Webhook `payment` de la plataforma (`sub-`) | Prueba primero el token admin para cualquier `payment` (`WebhookController.java:169-185`) | Solo si el `user_id` no es de un profesional vinculado (`NotificacionesMercadoPago`, rama `cuenta.isEmpty()`) | **igual** (con matiz) | Mismo resultado en el caso normal. Caso borde en hallazgo 14 (a verificar). |
| Simulador local (cuerpo con `external_reference`) | `WebhookController.java:208-213` (siempre aprueba) | `NotificacionesMercadoPago` (rama `external_reference`) | **igual** | Además acepta `sub-*` simulado para suscripciones (en main rompía con `NumberFormatException`). |
| `/api/webhooks/mercadopago` | No existe handler (404) | Ruta del gateway `pagos-webhooks` a pagos, pero pagos tampoco lo mapea (404) | **igual** | Ninguno de los dos lo atiende; solo existe en la regla del gateway. |
| `POST /api/payments/verificar` | `WebhookController.java:254-293` | `pagos/web/PagosController.java:84-117` | **igual** | Mismos params, 400 "Referencia externa inválida.", 404, 409 "No se pudo verificar el pago." / "El pago no corresponde a este turno.", búsqueda por referencia (prefiere `approved`). "Ya confirmado" se mira por pago aprobado en vez de por estado del turno. |
| `GET /api/payments/checkout/{referencia}` | No existe (el link venía en la respuesta de reservar) | `PagosController.java:124-132` (`estado`, `checkoutUrl`, `error`) | **cambia a propósito** | F4 decisión 1 y P4. Público, como la reserva. |
| Preferencia del turno (título, monto, back URLs, `notification_url`, `auto_return`, token del profesional) | `MercadoPagoService.crearPreferenciaPago` | `pagos/cobros/Checkout.java` + `mp/MercadoPagoApiSdk.crearPreferencia`; título de `turnos/.../Etiquetas.tituloCobro` | **cambia a propósito** | Contenido igual ("Consulta Psiquiátrica - <nombre>", `health`, ARS); ahora se crea por `turnos.turno.reservado` (F4 decisión 1). Mock `mock-preference-id` igual. |
| Profesional sin Mercado Pago al cobrar | La reserva/solicitud tiraba 409 con "El profesional todavía no vinculó…" | El cobro queda `ERROR` con el mismo texto y lo informa `GET /checkout/{ref}` | **cambia a propósito** | F4 P2 "Checkout que falla" (la reserva sigue respondiendo 409 desde turnos, F4 P1). |
| `POST /api/conceptos/solicitar` | `ConceptoController.java:33-43`: crea y devuelve `checkoutUrl` | `services/profesionales/.../clinica/ConceptoService.crearSolicitud`: devuelve `checkoutUrl: ""`, pagos crea la preferencia por `profesionales.concepto.solicitado` | **cambia a propósito** | F4 P1 y decisión 4 (el frontend no usa esta API). Título del ítem igual (`<tipo> - <nombre>`). Rol PACIENTE igual. |
| `GET /api/conceptos/pendientes` | Devuelve la entidad `SolicitudDocumento` con `paciente`/`medico` como `Usuario` completos (`ConceptoController.java:45-55`) | Devuelve `SolicitudResponse` con `UsuarioResumen` (id, nombre, apellido, email, teléfono, dni) | **distinto** | Cambia la forma del JSON (main exponía el usuario entero). El frontend no lo usa (F4 P1). Ver hallazgo 12. |
| `POST /api/conceptos/{id}/emitir` | `ConceptoService.emitirDocumento` (`SecurityException` sin handler → 500) | `ConceptoService.emitirDocumento` (`ProhibidoException`) | **igual** | Mismas reglas (dueño, "El documento no ha sido abonado"); el no-dueño ahora recibe un error prolijo en vez de 500. |
| Aprobación de un documento pago | `PagoWebhookHandler.procesarAprobacionConcepto` (APROBADO, `transactionId`, WebSocket `/topic/notificaciones/{medicoId}`) | `pagos.concepto.aprobado` → profesionales (`interno/Manejadores.java:178`) y notificaciones en vivo (`avisos/ManejadoresPagos.java:28`) | **igual** | Por eventos (F4 P1). Verificado en F4 P6 "Documento pago". |
| Aprobación de un turno pago (confirmar, Meet, notificaciones, mails) | `PagoWebhookHandler.procesarAprobacionTurno` | `pagos.pago.aprobado` → turnos `turnos/Cobros.pagoAprobado` → calendario/notificaciones | **igual** | Por eventos; F4 P6 "Reserva y pago" (tres mails y el Meet). |
| Pago aprobado de un turno vencido con el horario tomado | Se confirma igual (solo chequea `CONFIRMADO`) | Se cancela y se reembolsa (`turnos/Cobros.java`, `horarioTomado`) | **cambia a propósito** | F6 decisión 4. Con el horario libre se confirma como en main. |
| Bloqueo de 5 min vencido | El turno se cancela; el pago tardío se procesa | Además el cobro pasa a `VENCIDO` (`Checkout.vencer`); el pago tardío se procesa | **cambia a propósito** | F4 P2 / P6 "Bloqueo vencido". |
| Política de reembolso (48 h; si cancela el profesional, siempre) | `ReembolsoService.java:254-262`, `TurnoService.java:716-741` | `turnos/Gestion.correspondeReembolso` + `cancelar` (resultado `EN_PROCESO`/`SIN_REEMBOLSO`/`SIN_PAGO`) | **igual** | Misma regla y mismos casos. |
| Reembolso en Mercado Pago | `ReembolsoService.java:264-299` (token descifrado tal cual, sin renovar) | `pagos/cobros/ReembolsosTurnos.java` (`accessTokenValido`, renueva si vence) | **igual** | Mismo `PaymentRefundClient` con el token del profesional; uno por turno (`reembolso.turno_id` único). Mejora: renueva el token. |
| Reembolso sin token (profesional desvinculado) | No llama a MP y lo da por reembolsado | `FALLIDO` + `pagos.reembolso.fallido` + mail "Tu reembolso está en revisión" | **cambia a propósito** | F4 P2 "Reembolso sin token en producción". |
| Reembolso que MP rechaza | Mail de cancelación con `PENDIENTE` | `FALLIDO` + mail "en revisión" | **cambia a propósito** | F4 decisión 3 y P1 (`pagos.reembolso.fallido`). |
| Mails de cancelación con reembolso | Uno solo, que dice si se reembolsó | Dos: la cancelación ("te avisamos cuando se acredite") y "Reembolso realizado" | **cambia a propósito** | F4 decisión 3. |
| Vinculación MP: `status`, `connect`, `connect-simulado`, `disconnect`, `callback` | `MedicoController.java:128-200`, `MercadoPagoOAuthService` | `pagos/web/MercadoPagoController.java`, `mp/Vinculacion.java` | **igual** | Mismo JSON (`connected`, `mpUserId`, `mercadopagoEnabled`), rol PSIQUIATRA, 403 de la simulación con MP real, mismas redirecciones `?mp=…`, mismo `state` firmado, mismo mock (`TEST-dev-mock-*`, `999999999`). |
| Renovación de tokens de MP | Margen 300 s; conserva refresh y `user_id` si no vienen | Margen 300 s; `COALESCE` en `datos/CuentasMp.guardar` | **igual** | — |
| Split / comisión de la plataforma | No hay (`marketplace_fee` ausente; 100 % a la cuenta del profesional) | No hay | **igual** | — |
| Paywall sobre `/api/medicos/mercadopago/**` (callback exento) | `SubscriptionAccessFilter.java:46-56` | `gw/portones/PortonesGlobalFilter.java:63-77` | **igual** | Mismas exenciones; la regla de acceso en sí difiere (ver dominio 2, hallazgo 1). |

## 2. Suscripciones y facturación

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `GET /api/subscriptions/plans` (público) | `SubscriptionController.java:39-58` | `pagos/suscripciones/SubscriptionController.java:61-79` | **igual** | Mismos campos (`isActive` incluido), solo activos, sin sesión. |
| `GET /api/subscriptions/my-subscription` | `SubscriptionController.java:63-112` (PSIQUIATRA, ADMIN, PACIENTE; `null` sin fila) | `SubscriptionController.java:84-139` | **igual** | Mismo DTO y fechas ISO. Agrega el rol `PROFESIONAL`, que no existe (inocuo). |
| `POST /api/subscriptions/checkout` | `SubscriptionService.iniciarCheckout` (`:64-141`) | `SuscripcionService.iniciarCheckout` | **igual** (salvo error) | Mismo 400 "planId es obligatorio", 422 con los mismos mensajes (429 de MP, sin precio anual), ciclo `annual`/`monthly`, cancela el preapproval previo, crea la fila si falta. Plan o profesional inexistente: main 404, migración 500 (hallazgo 7). |
| Preapproval de MP (payer, `back_url`, `reason`, `external_reference sub-<id>`, frecuencia, `start_date` +10 min, token admin) | `MercadoPagoService.crearSuscripcionPreapproval` | `pagos/mp/MercadoPagoAdminApiSdk.crearPreapproval` | **igual** | — |
| Modo simulado de suscripciones | Solo se simula la creación del preapproval (`MOCK-PRE-<id>`) | Toda la API admin simulada (`mp/MercadoPagoAdminApiSimulada.java:19-80`) | **distinto** | Hallazgo 8 (a verificar). |
| `POST /api/subscriptions/cancel` | `SubscriptionService.cancelarSuscripcionPorProfesional` (`:705-773`) | `SuscripcionService.cancelarSuscripcionPorProfesional` | **igual** | Idempotente, 422 si MP no confirma, ACTIVE si el período sigue vigente, mismo mensaje y mail (por evento). |
| Webhook `subscription_preapproval` | `WebhookController.java:88-108` + `processMercadoPagoPreapprovalWebhook` | `NotificacionesMercadoPago` + `SuscripcionService.processMercadoPagoPreapprovalWebhook` | **igual** | authorized→ACTIVE, paused→PAST_DUE, cancelled→CANCELLED (ACTIVE si hay período), activa el período si no había, mail de cancelación. |
| Webhook `subscription_authorized_payment` | `WebhookController.java:110-148` | `NotificacionesMercadoPago` | **igual** | Consulta `/authorized_payments/{id}`, deduplica por `payment.id`, mismos defaults. |
| Webhook `payment` con `sub-<id>` | `processMercadoPagoPaymentByExternalReference` | Ídem | **igual** | Período +1/+12 meses, gracia +7 días, `MP-<id>` como clave, factura tras commit, mails de pago confirmado/fallido. |
| Webhooks de suscripción con `PAGOS_SUSCRIPCIONES=false` | Los procesa el monolito | Pagos los reenvía al monolito | **cambia a propósito** | F4 decisión 2, F8 S2b/S3 (vuelta atrás). |
| Regla de acceso del paywall en rutas que van a servicios | `isAccessAllowed` en tiempo real: sin fila → 403; ACTIVE con período vencido → 403 (`SubscriptionService.java:200-220`) | `/internal/suscripciones/acceso` (`SuscripcionesInternoController.java:80-106`): sin fila → pasa; ACTIVE con `graceUntil` futuro → pasa | **distinto** | Hallazgo 1. |
| Paywall: caché de 60 s y "si falla, deja pasar" | No hay (en proceso) | `gw/portones/AccesoSuscripcionCliente.java:238-260` | **cambia a propósito** | Diseño de la Fase 3 (G1), recogido en F8 "Entendimiento" 1. |
| Paywall: exenciones, roles y respuesta 403 `SUBSCRIPTION_REQUIRED` | `SubscriptionAccessFilter.java:46-103` | `PortonesGlobalFilter.java:47-117` | **igual** | Mismo cuerpo; solo PSIQUIATRA; `OPTIONS` y anónimos pasan. |
| Paywall en las rutas que siguen en el monolito y buscador/"puede recetar" | Lee `subscriptions` directo | Copia `suscripcion_acceso` alimentada por `cambio-estado.v2` + resincronización cada 15 min (`backend/.../service/AccesoSuscripciones.java:55-71`) | **cambia a propósito** | F8 decisión 2. La copia no se entera de renovaciones sin cambio de estado: hallazgo 6. |
| Alta de la suscripción al registrarse un profesional | `AuthController.java:187-200` (PENDING_VERIFICATION, plan por profesión) | `pagos/suscripciones/ManejadoresSuscripciones.suscripcionAlta` (por `identidad.usuario.registrado`) | **igual** | Mismo criterio y plan. |
| Verificación y rechazo de matrícula | `AdminController.java:73-121` | `ManejadoresSuscripciones.suscripcionVerificacion` (por `verificacion-resuelta.v1`) | **cambia a propósito** | F8 decisión 3. Transiciones iguales; el actor del evento de auditoría pasa del mail del admin a `"admin"` (hallazgo 9). |
| Gracia de 14 días a verificados al arrancar (`@PostConstruct`) | `SubscriptionService.java:236-312` (corre en cada arranque) | No se porta | **cambia a propósito** | Javadoc de `SuscripcionService` y F8 S1/§4 (las tareas de arranque no corren dos veces; los datos vienen de la carga inicial). Efecto: un profesional verificado sin período ya no recibe 14 días en el próximo reinicio. |
| Siembra de planes y features (`@PostConstruct`) | `PlanService` | No se porta (carga inicial) | **cambia a propósito** | Ídem. |
| `GET /api/admin/subscriptions/overview` | `getAdminOverviewStats` (`:819-860`) | `SuscripcionService.getAdminOverviewStats` | **igual** | Mismas claves en español, topes y alerta del 80 %. |
| `GET /api/admin/subscriptions/list` | `AdminSubscriptionController.java:60-104` | `AdminSubscriptionController.java:103-150` | **igual** | "-" si no hay profesional, igual. |
| `GET /api/admin/subscriptions/plans` | `:202-221` | `:204-223` | **igual** | Incluye inactivos. |
| `PUT /api/admin/subscriptions/plans/{id}` | `PlanService.updatePlan` (`:210-235`) | `AdminSubscriptionController.java:228-262` | **igual** (detalle menor) | Mismos campos y respuesta (todos los campos del plan). Un `name` en blanco: main lo ignora, la migración lo guarda vacío (hallazgo 10). |
| `POST /api/admin/subscriptions/manual-payment` | `AdminSubscriptionController.java:109-137`, `registerManualPayment` (`:344-468`) | `AdminSubscriptionController.java:155-199`, `SuscripcionService.registerManualPayment` | **distinto** | Idempotencia, período, gracia +7, factura y mail iguales. Difiere: admin = id 1 (hallazgo 2), falta el control de CUIT con ARCA prendido (hallazgo 4), método sin validar (hallazgo 5). JSON de respuesta igual. |
| `GET /events` y `GET /{id}/events` | Entidad `SubscriptionEvent` (`payloadJson` como `details`) | `SubscriptionEventDto` con `details` | **igual** | Mismo orden (más nuevo primero). |
| `PATCH /api/admin/subscriptions/{id}/status` | `:265-333` (`SubscriptionStatus.valueOf` → 400 si no existe) | `:297-361` (`toUpperCase`, sin validar) | **distinto** | Cancelación del preapproval, mails de cancelación/suspensión y respuesta `{id,status}` iguales. Hallazgo 3. |
| `POST /api/admin/subscriptions/reconciliation/run` | `:338-342` (siempre corre) | `:366-371` | **igual** (detalle menor) | Si `PAGOS_SUSCRIPCIONES=false` o falta la carga inicial no corre pero responde "ejecutada correctamente" (hallazgo 11). |
| `GET /api/admin/subscriptions/invoices` | `:142-168` | `:376-401` | **igual** | Mismo DTO y nombre del tipo. |
| `POST /api/admin/subscriptions/invoices/{id}/credit-note` | `:173-188` | `:406-422` | **igual** | Mismos errores ("…no está habilitada", solo ISSUED, solo tipo 11) y respuesta con todos los campos de la factura. El cuerpo ahora es opcional. |
| Factura C con CAE (numeración, PDF, estados, apagada) | `InvoiceService.generateInvoiceForPayment` + `arca/*` | `pagos/facturacion/InvoiceService` + `Arca*` | **igual** | Traslado tal cual (F8 decisión 4): `diff` sin cambios de lógica (solo Lombok → código). Misma config `arca.*` y defaults. Numeración con `FOR UPDATE` (`datos/SecuenciasFactura.proximoNumero`). |
| Nota de crédito C | `InvoiceService.emitirNotaDeCredito` | Ídem | **igual** | Anula la original (VOIDED) si sale el CAE. |
| Factura después del commit | `emitirFacturaTrasCommit` (`:482-502`) | `SuscripcionService.emitirFacturaTrasCommit` + `FacturacionSuscripcionesArca` | **igual** | — |
| Reconciliación diaria (04:00): preaviso, canceladas vencidas, pagos sin factura, facturas fallidas, suspensión pasada la gracia, vencimiento de manuales (7/3/1 días), tope del monotributo | `SubscriptionReconciliationScheduler.java:72-84` y checks | `pagos/suscripciones/SubscriptionReconciliationScheduler.java:72-90` y checks | **igual** | Mismas consultas (comparadas una por una con el JPQL) y mismas transiciones. Solo corre con `PAGOS_SUSCRIPCIONES=true` y la carga inicial lista (F8 S1/§4). |
| Revisión horaria + al arrancar | `:91-102` | `:94-107` | **igual** | Ídem. |
| Alerta a los admins (facturas FAILED, tope) | `notificarAdmins` | `pagos.alerta.admin.v1` | **igual** | Arreglado en esta rama; no se reporta. |
| Avisos a profesionales (preaviso, pago confirmado/fallido, cancelación, suspensión, vencimiento manual) | `ResendEmailService` directo | `suscripciones.aviso.programado.v1` → `services/notificaciones/.../avisos/ManejadoresSuscripciones.java` | **cambia a propósito** | F8 "Mails" (salen por evento). Mismos argumentos mapeados por tipo; tampoco dependen de la preferencia de mails. |

## Conteo

- Pagos y reembolsos: 27 filas — igual 15, cambia a propósito 11, distinto 1.
- Suscripciones y facturación: 35 filas — igual 24 (4 con detalle menor), cambia a propósito 7, distinto 4.
- Total: 62 filas — **igual 39, cambia a propósito 18, distinto 5, falta 0**. Hallazgos: 11 distintos y 3 a verificar
  (varios salen de filas "igual" con detalle menor).

## Hallazgos (distinto / falta / a verificar)

1. **Distinto (alto): el paywall de los servicios deja pasar más que main.** `pagos/.../SuscripcionesInternoController.java:89-104`
   (rama c3): (a) un PSIQUIATRA sin fila de suscripción (o que todavía no está en la copia `suscriptor`) recibe
   `permitido=true`; en main `isAccessAllowed` devuelve `false` (`app/service/SubscriptionService.java:207-208`);
   (b) una suscripción `ACTIVE` con `currentPeriodEnd` vencido pero `graceUntil` futuro pasa 7 días más; en main no
   (`SubscriptionService.java:212-219`, el comentario de la copia del monolito lo dice: "la gracia no otorga acceso").
   Tampoco coincide con la copia del monolito (`backend/.../AccesoSuscripciones.java:40-71`), así que un mismo
   profesional puede estar bloqueado en las rutas del monolito y habilitado en las de los servicios.
   **Impacto:** un profesional con el pago vencido (manual o débito que no entró) sigue usando turnos, agenda,
   chat, etc. hasta 7 días sin pagar; uno sin fila, indefinidamente. Ninguna spec lo decide.
   **Arreglo:** que `/acceso` use solo `isAccessAllowed` para PSIQUIATRA conocido (sin la condición de gracia), y
   `false` si es PSIQUIATRA sin fila; dejar `true` solo para mails desconocidos (el servicio decide). Test con los
   dos casos.
2. **Distinto (medio): el pago manual queda registrado a nombre del usuario 1.** `AdminSubscriptionController.java:158`
   y `:413` buscan al admin en la copia `suscriptor`, que solo tiene profesionales
   (`services/identidad/.../InternoIdentidadController.java`, `esSuscriptor`), y caen en `orElse(1L)`. Queda
   `registered_by_admin_id = 1` y el evento `MANUAL_PAYMENT_ACTIVATED` con actor `"1"`; en main es el id real del
   admin (`app/controller/AdminSubscriptionController.java:114-131`).
   **Impacto:** la auditoría de pagos manuales (Ley 25.326, como dice main) atribuye todos los pagos a otra persona.
   **Arreglo:** usar el `uid` del JWT (`UsuarioJwt.Usuario.uid()`, que el filtro ya cruza con la copia de participantes)
   o buscar en `Participantes`; si no aparece, 403 en vez de un id inventado.
3. **Distinto (medio): `PATCH /{id}/status` acepta cualquier estado.** `AdminSubscriptionController.java:314`
   guarda `newStatusStr.toUpperCase()` sin validar (la columna es `VARCHAR`, sin `CHECK`); main usaba
   `SubscriptionStatus.valueOf` (`AdminSubscriptionController.java:275`) → 400.
   **Impacto:** un error de tipeo del admin deja la suscripción en un estado inexistente (sin acceso, fuera de todas
   las consultas de la reconciliación) y publica un `cambio-estado.v2` con ese valor.
   **Arreglo:** validar contra los 9 estados de main y responder 400 si no está.
4. **Distinto (bajo hoy, alto con ARCA): falta el control de CUIT en el pago manual.** main corta con "No se puede
   emitir factura: el profesional no tiene CUIT/CUIL cargado" si `emitInvoice`, ARCA prendida y no es cortesía
   (`app/service/SubscriptionService.java:376-380`); la migración no (`SuscripcionService.java:297-300`).
   **Impacto:** con `ARCA_ENABLED=true` el pago se registra y la factura falla después en silencio (solo log y la
   reintenta el cron).
   **Arreglo:** portar el chequeo con `prof.taxIdEfectivo()` y `arcaConfig.isEnabled()`.
5. **Distinto (bajo): el método del pago manual no se valida.** `SuscripcionService.java:307` guarda cualquier texto
   en `billing_source` y `method`; main lo mapeaba a `BillingSource` con `MANUAL_TRANSFER` por defecto
   (`SubscriptionService.java:390-395`).
   **Impacto:** un valor raro saca la suscripción del aviso de vencimiento de manuales (que filtra
   `MANUAL_CASH`/`MANUAL_TRANSFER`).
   **Arreglo:** mismo mapeo que main.
6. **Distinto (bajo-medio): la copia del acceso no se entera de renovaciones.** `SuscripcionService.logEvent` publica
   `cambio-estado.v2` solo si cambia el estado (`SuscripcionService.java:549`). No publica en: renovación por MP
   ACTIVE→ACTIVE (`activarPagoSuscripcion`), pago manual sobre una suscripción ACTIVE, ni alta nueva por pago manual
   (estado previo `null`). La copia del monolito evalúa `periodoHasta` en tiempo real
   (`backend/.../AccesoSuscripciones.java:55-71`).
   **Impacto:** cuando vence el período anterior, el profesional que ya renovó puede quedar hasta 15 min
   (resincronización) bloqueado en las rutas del monolito y oculto del buscador / sin poder recetar. En main el
   cambio es inmediato.
   **Arreglo:** publicar v2 también cuando cambian período, gracia o plan (o siempre en `activarPagoSuscripcion` y
   `registerManualPayment`).
7. **Distinto (bajo): checkout con plan o profesional inexistente da 500.** `SuscripcionService.java:82-84` tira
   `IllegalArgumentException` y pagos no tiene `@ControllerAdvice`; main tiraba `EntityNotFoundException` → 404
   (`SubscriptionService.java:65-68`, `GlobalExceptionHandler.java:21-23`).
   **Arreglo:** `ResponseStatusException(NOT_FOUND, …)` con los mismos textos.
8. **A verificar (bajo en local, alto si se prende en producción): la simulación cubre toda la API admin.**
   Con `PAYMENT_SIMULATION_ENABLED=true` se activa `MercadoPagoAdminApiSimulada` en vez del SDK: cancelar un preapproval
   no llama a MP, y `obtenerPagoAdmin` devuelve "aprobado, `sub-<paymentId>`, $149.500"
   (`MercadoPagoAdminApiSimulada.java:51`), así que cualquier webhook `payment` de un `user_id` no vinculado activa la
   suscripción con id = paymentId. En main la simulación solo tocaba la creación del preapproval y el resto iba a MP
   (main permite simular con MP real, `MercadoPagoWebhookValidator.java:42-49`).
   **Por qué a verificar:** el compose de producción lo deja en `false`; no encontré una spec que lo decida.
   **Arreglo:** simular solo `crearPreapproval` (como main), o exigir además `mercadopago.enabled=false`.
9. **Distinto (bajo): la verificación/rechazo pierde quién lo hizo.** `ManejadoresSuscripciones.java:166` y `:179`
   registran el actor `"admin"`; main guardaba el mail del admin (`AdminController.java:90`, `:116`).
   **Arreglo:** sumar el admin a `VerificacionResueltaData` (campo opcional).
10. **Distinto (bajo): `PUT /plans/{id}` con `name` en blanco lo guarda vacío.** main lo ignoraba
    (`PlanService.java:223`); la migración hace `trim()` y lo guarda (`AdminSubscriptionController.java:245-246`).
    **Arreglo:** ignorar nombre en blanco.
11. **Distinto (bajo): `reconciliation/run` responde éxito sin correr** si `PAGOS_SUSCRIPCIONES=false` o la carga
    inicial no terminó (`SubscriptionReconciliationScheduler.java:74-77`). Solo pasa con una configuración inconsistente.
    **Arreglo:** responder 409 con el motivo.
12. **A verificar (bajo, para el revisor de profesionales): `GET /api/conceptos/pendientes` cambió de forma.** main
    serializaba `SolicitudDocumento` con los `Usuario` completos; la migración devuelve `UsuarioResumen`. El frontend
    no usa esta API (F4 P1), y la migración expone menos datos (mejor). Confirmar que nadie más la consume.
13. **Distinto (bajo): un webhook `payment` con `data.id` no numérico da 500.** `NotificacionesMercadoPago.java:141`
    tira `NumberFormatException` → 500 → MP reintenta el mismo aviso; main respondía 200.
    **Arreglo:** tratarlo como `IGNORADO`.
14. **A verificar (bajo): cómo se reconoce un cobro de suscripción en el webhook `payment`.** main probaba primero el
    token admin con cualquier `payment` (`WebhookController.java:169-185`); la migración solo si el `user_id` no es de
    un profesional vinculado. Si la cuenta de MP de la plataforma también estuviera vinculada como profesional (por
    ejemplo, para pruebas), el débito `sub-*` iría a `Aprobaciones`, que lo descarta por referencia no numérica, y la
    suscripción no se activaría. **Por qué a verificar:** depende de que esa vinculación exista en producción.
    **Arreglo:** si el pago leído con el token del profesional trae `sub-*`, derivarlo a suscripciones.

## Notas (sin hallazgo, iguales en los dos lados)

- El precio de `/api/conceptos/solicitar` lo manda el cliente en main y en la migración.
- Un pago de suscripción con estado distinto de `approved` (por ejemplo `pending`) pasa la suscripción a PAST_DUE y
  manda el mail de pago fallido en los dos.
- La factura se emite en `afterCommit` con `@Transactional` REQUIRED en los dos; con ARCA apagada no hay efecto.
