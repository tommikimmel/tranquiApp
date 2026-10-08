# Spec: limpieza de la base de producción

- **Fecha:** 2026-10-07
- **Tipo:** chore
- **Sprint:** sprint-01 (tarea 7)
- **Estado:** borrador
- **Rama / PR:** `docs/docs-specs-secretos-y-limpieza` (spec); el script va en una rama aparte una vez aprobada

## Entendimiento del problema

La base de producción tiene cuentas, turnos, pagos y suscripciones de prueba creados durante el
desarrollo. Antes de abrir la app al público hay que dejarla vacía salvo el administrador y el
catálogo de planes. Decisión del dueño: **sin backup previo**.

Lo que no se puede olvidar: los profesionales de prueba que pagaron la suscripción tienen un
débito automático (preapproval) en Mercado Pago, cobrado por la cuenta de la plataforma
(`MP_ADMIN_ACCESS_TOKEN`). Borrar la fila de `subscriptions` **no** frena el débito: hay que
cancelarlo en Mercado Pago antes (mismo motivo que `MercadoPagoService.cancelarSuscripcionPreapproval`).

Tablas (esquema actual):

- **Se conservan:** `plans`, `features`, `plan_features`, `flyway_schema_history` y, de `usuario`,
  solo `admin@tranqui.com`.
- **Se vacían:** `disponibilidad`, `google_calendar_evento_externo`, `informe_clinico`,
  `invoice_sequences`, `invoices`, `mensaje`, `notificacion`, `novedades_envio`, `pago`, `receta`,
  `seguimiento_diario`, `solicitud_documento`, `subscription_events`, `subscription_payments`,
  `subscriptions`, `tarifa_medico`, `ticket`, `ticket_mensaje`, `turno` y cualquier otra tabla
  que exista en producción y no esté en la lista de conservadas (por ejemplo, restos del bot de
  WhatsApp).

## Solución propuesta

Un script `scripts/vps/limpieza-produccion.sh`, que corre en el VPS, con dos modos:

- **`inventario`** (solo lectura): cantidad de filas por tabla, usuarios que quedarían, y la
  lista de suscripciones con `mp_preapproval_id` que no estén canceladas, consultando su estado
  real en Mercado Pago (`GET /preapproval/{id}`).
- **`ejecutar`**: pide escribir `BORRAR` para seguir y hace, en orden:
  1. Cancela en Mercado Pago cada preapproval no cancelado (`PUT /preapproval/{id}` con
     `status: cancelled`). El token se lee del contenedor del backend y nunca se imprime. Si
     alguno falla, se detiene sin borrar nada.
  2. Para el backend (`docker compose -p app stop tranqui-backend`), así nada escribe en el medio.
  3. En **una sola transacción**: `TRUNCATE ... RESTART IDENTITY CASCADE` de todas las tablas que
     no están en la lista de conservadas, y `DELETE FROM usuario WHERE email <> 'admin@tranqui.com'`.
     Antes de confirmar, comprueba que quede exactamente un usuario y que `plans` siga con filas;
     si no, `ROLLBACK`.
  4. Levanta el backend y espera `/api/health`.

Ventana: con el modo mantenimiento prendido y **sin archivo de novedades** (no queda nadie a
quien mandarle el mail). Requiere que `v1.1.0` ya esté desplegada, porque el modo mantenimiento
llega con esa versión.

Alternativa descartada: borrar desde el panel de administración, cuenta por cuenta. Lento, deja
tablas sin tocar y no cancela los débitos de Mercado Pago de forma ordenada.

## Criterios de aceptación

- [ ] El inventario se revisa con el dueño antes de ejecutar.
- [ ] Ningún preapproval de prueba queda activo en Mercado Pago.
- [ ] Solo queda `admin@tranqui.com` en `usuario`; `plans`, `features` y `plan_features` sin cambios.
- [ ] El admin entra y ve el panel vacío; un registro nuevo de paciente y de profesional funciona.

## Riesgos e impacto

- **Irreversible y sin backup** (decisión del dueño). La transacción única evita quedar a mitad
  de camino, pero lo confirmado no se recupera.
- Los canales de Google Calendar de profesionales borrados siguen avisando a nuestro webhook
  hasta que vencen (unos días); el webhook ignora canales desconocidos.
- Los contactos de Resend de usuarios de prueba (si se llegaron a sincronizar) quedan en Resend;
  se borran a mano desde su panel.

## Preguntas abiertas

- Ninguna: el alcance quedó definido por el dueño el 2026-10-07.

## Resultado (completar al cerrar)
