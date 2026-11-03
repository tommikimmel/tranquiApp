# C3 — Frontend: `main` vs migración

Diff: `git diff origin/main test/full-fase-11-c3-revision -- frontend/src` (sin tests ni `e2e/`). Cambiaron 9 archivos
(2 nuevos de tipos/utilidades, 1 componente nuevo). El resto del frontend es idéntico byte a byte; las 95 llamadas de
`api.ts` de `main` siguen iguales (ver `docs/migracion/comparacion-main.md`, C1/C2) y el golden master de la API no deja
ningún campo leído por el frontend sin mandar.

| Archivo / flujo | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `api/api.ts` `getBackendOrigin` (prod, https) | `https://${host}` | igual, salvo que haya `VITE_API_URL` | **igual** | `VITE_API_URL` solo se define en `docker-compose.local.yml:600`; el build de producción no la tiene (api.ts:3-9) |
| `api/api.ts` `getBackendOrigin` (dev, http) | `http://host:8081` (monolito) | `http://host:8080` (gateway) | **cambia a propósito** | Entorno local: el frontend entra por el gateway (spec Fase 1, línea 194; Fase 2, línea 142). Afecta API, WebSockets y el webhook simulado |
| Avisos en vivo, profesional (`App.tsx:284-355`) | 1 STOMP a `/ws-tranqui`, suscripto a `/topic/notificaciones/{id}` | 2 STOMP (`/ws-tranqui` y `/ws-notificaciones`) con el mismo handler (`ENDPOINTS_AVISOS_EN_VIVO`, api.ts:16) | **cambia a propósito** | Spec Fase 1, N3/N5 (líneas 161-164 y 181-182): notificaciones guardadas por `/ws-notificaciones`, "nuevo mensaje" del chat por `/ws-tranqui` |
| Avisos en vivo, paciente (`LandingPage.tsx:895-939`) | idem, URL armada a mano con 8081 | idem dos endpoints, URL por `getBackendOrigin` | **cambia a propósito** | Misma decisión. Ver hallazgo 2 (posible aviso duplicado) |
| Reconexión de WebSockets (`App.tsx`, `LandingPage.tsx`, `useChat.ts:78-81`) | `webSocketFactory` devolvía siempre el mismo `SockJS`: tras un corte nunca reconectaba | crea un `SockJS` nuevo en cada intento | **cambia a propósito** (arreglo) | Mejora: el chat y la campanita vuelven solos tras reiniciar un servicio. En `main` hacía falta recargar |
| Chat (`useChat.ts`) endpoint y rutas | `/ws-tranqui`, `/api/chat` | igual | **igual** | Spec Fase 2, línea 64 ("el frontend no cambia") |
| Reserva y checkout (`CheckoutFlow.tsx:1741-1768`) | `POST /api/turnos/reservar` devuelve `checkoutUrl` y redirige | si viene `checkoutPendiente` sin link, hace polling a `GET /api/payments/checkout/{turnoId}` (cada 1 s, 20 veces, `esperarCheckout.ts`) y después sigue igual (mock → simulador; real → redirect) | **cambia a propósito** | Spec Fase 4, P4 (líneas 209-215). Backend: `Reservas.java:220` siempre manda `turnoId` y `checkoutPendiente=true`; `PagosController.java:124` responde `estado/checkoutUrl/error` |
| Error de Mercado Pago en la reserva | 409 con el mensaje, el turno no queda creado | el turno se crea; el error llega por `estado=ERROR` y se muestra igual en la pantalla de selección; el horario queda tomado hasta que venza el bloqueo de 5 min | **cambia a propósito** | Spec Fase 4, P4 ("a diferencia de antes, el turno ya existe…"). Ver hallazgo 1 sobre el impacto visible |
| Mensajes nuevos de error del checkout (`esperarCheckout.ts:10,35`) | — | "No pudimos generar el link de pago…", "La reserva venció antes de que pudiéramos generar el link de pago…" | **cambia a propósito** | Spec Fase 4, P4; spec Fase 10 (reserva vencida) |
| Estado `PAGADO` en el polling (`esperarCheckout.ts:27-36`) | — | no lo trata: sigue esperando y termina en el mensaje genérico de error | **a verificar** | Ver hallazgo 3 |
| Webhook simulado "Simular pago" (`CheckoutFlow.tsx:1886-1888`) | `POST {host:8081}/api/payments/webhook` | `POST {getBackendOrigin()}/api/payments/webhook` (gateway → pagos) | **igual** | Mismo método, ruta, headers y cuerpo; solo cambia cómo arma el origen |
| Mis turnos / pagar después | `checkoutUrl` del turno | igual (el link llega al turno por `pagos.checkout.creado`) | **igual** | Spec Fase 4, P4 |
| Admin: menú "Observabilidad" (`AdminDashboard.tsx:448, 631-636, 763-764`) | no existe | nueva sección en la barra lateral | **cambia a propósito** | Spec Fase 0, T6 (línea 53). Las otras secciones (por defecto sigue siendo `subscriptions`) no cambian |
| Admin: `AdminObservabilidadView.tsx` (nuevo, 822 líneas) + `types/observabilidad.ts` | no existe | `GET /api/admin/salud` y `GET /api/admin/observabilidad/{recursos,trafico,latencia,eventos}?rango=` con refresco automático | **cambia a propósito** | Los atiende el gateway (`AdminSaludController`, `AdminObservabilidadController`). Cada pedido tiene `.catch(() => null)`: si falla, el panel queda vacío sin romper el resto. `onLogout` se recibe pero no se usa (línea 215) |
| `api.ts` nuevos: `obtenerCheckout`, `getAdminSalud`, `getAdminObservabilidad` | — | agregados | **cambia a propósito** | Fases 4 y 0. No se quitó ni se cambió ninguna llamada existente |
| Login, registro, Mi Cuenta, panel del profesional (fuera de los avisos), historia clínica, tickets, suscripciones, paywall | — | sin cambios en el código | **igual** | Cubiertos además por la suite de punta a punta de C4 en los dos stacks |

## Hallazgos (distinto / falta / a verificar)

1. **A verificar — error de Mercado Pago deja el horario tomado.** En `main` (`TurnoService.java:433-446`), si el
   profesional no vinculó Mercado Pago y la simulación está apagada, la reserva responde 409 y no se crea el turno: el
   paciente puede elegir otro horario o reintentar al instante. En la migración (`Reservas.java:209-221` +
   `CheckoutFlow.tsx:1744-1746`) el turno se crea igual, el paciente ve el mensaje de `estado=ERROR` y el horario queda
   bloqueado 5 minutos (para él y para otros). Está decidido en la spec de la Fase 4 (P4), así que no es regresión, pero
   **impacto**: si el paciente vuelve a intentar el mismo horario dentro de esos 5 min, choca con su propia reserva
   colgada. **Arreglo propuesto**: confirmar en el stack local qué devuelve un segundo intento; si choca, que pagos
   publique la cancelación del turno cuando el cobro queda en `ERROR` (o que turnos libere el turno al recibirlo).
2. **A verificar — aviso de concepto aprobado por dos endpoints.** Los dos clientes STOMP comparten handler. El
   monolito migrado sigue empujando el aprobado de concepto por `/ws-tranqui` (`PagoWebhookHandler.java:119`) y el
   servicio de notificaciones lo empuja por `/ws-notificaciones` (spec Fase 2, C2, línea 41). Si los dos caminos corren
   a la vez, el profesional ve la alerta dos veces. Con pagos externo el webhook del monolito no debería procesarse, y
   `/ws-tranqui` ya va al servicio de chat (gateway `application.yml:578`), así que lo esperable es un solo aviso.
   **Impacto** bajo (alerta duplicada). **Arreglo propuesto**: confirmarlo en el stack local aprobando un concepto con
   el panel abierto; si se duplica, deduplicar por `id` en el handler o sacar el empuje del monolito.
3. **A verificar — `esperarCheckout` no trata `PAGADO`.** Si el cobro ya figura pagado (por ejemplo, un webhook que
   llega antes que el polling, o un reintento sobre una reserva ya cobrada), el polling sigue 20 s y termina mostrando
   "No pudimos generar el link de pago" aunque el pago esté hecho (`esperarCheckout.ts:27-38`). **Impacto**: caso raro,
   mensaje engañoso. **Arreglo propuesto**: tratar `PAGADO` como éxito (pasar a `confirmed`) en `CheckoutFlow`.

No hay filas en **distinto** ni en **falta**: todo lo que `main` hacía en el frontend sigue en la migración.
