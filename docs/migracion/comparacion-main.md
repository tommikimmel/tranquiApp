# Comparación con `main` (Fase 11)

Spec: [2026-10-28-fase-11-comparacion-con-main.md](../specs/2026-10-28-fase-11-comparacion-con-main.md). `main` es el
monolito en producción (`7d06fb0`); la migración, la punta de la cadena de PR a `develop` (Fase 10, T4).

Estados: **igual**, **cambia a propósito** (con su decisión), **distinto** o **falta**.

## C4 — Comparación ejecutando (2026-10-30)

Los dos stacks se levantan de a uno: `main` en el proyecto `tranqui-main` (monolito en `127.0.0.1:18081`, porque el
8081 lo usa otro stack) y la migración en `tranqui-local` (gateway en :8080). Los dos con los datos de demo de
`LocalDemoSeeder`.

### Suite de punta a punta

```bash
# main
E2E_URL=http://localhost:5173 E2E_API_URL=http://localhost:18081 \
  E2E_REDIRIGIR_API="http://localhost:8081=http://localhost:18081" npx playwright test
# migración
E2E_RESILIENCIA=1 E2E_COMPOSE="docker compose -p tranqui-local -f $(pwd)/../docker-compose.local.yml" npx playwright test
```

| Recorrido | `main` | Migración |
|---|---|---|
| Buscador, reserva con pago simulado, agenda de la profesional, cancelación con reembolso, Mi Cuenta | pasa | pasa |
| Registro con código, matrícula, tickets, paywall, historia clínica, perfil público | pasa | pasa |
| Resiliencia (servicio caído, eventos pendientes, DLQ vacía) | no aplica | pasa |

Ninguna funcionalidad que pasa en `main` falla en la migración. El paywall pasa en los dos **después** del arreglo de
T3 (#90): antes, en la migración, la profesional vencida entraba a su panel.

### Golden master de la API

`scripts/comparacion/golden-master.mjs` manda las 52 lecturas que hace el frontend de `main`, con cada rol de demo, y
compara código HTTP y forma del JSON (campos y tipos, no valores). **44 iguales, 8 con diferencias:**

| Pedido | Diferencia | Estado |
|---|---|---|
| anónimo `GET /api/auth/me` | 403 en `main`, 401 en la migración | **igual** para el frontend: los dos caen en el `catch` de `getMe` y quedan sin sesión |
| paciente `GET /api/medicos/perfil`, `GET /api/admin/users`; admin `GET /api/medicos/perfil` | el 403 viene sin cuerpo (en `main`, el JSON de error de Spring) | **igual** para el frontend: `sanitizeErrorMessage` muestra el mismo mensaje genérico en los dos casos |
| profesional `GET /api/chat/historial/{id}` | `remitente` y `destinatario` solo con `id` (en `main`, el `Usuario` entero con el hash de la contraseña y los tokens) | **cambia a propósito**: [Fase 2, C3](../specs/2026-10-20-fase-2-chat.md); el frontend lee solo `remitente.id` y `destinatario.id` |
| admin `GET /api/admin/users` | sin el hash, los códigos, los tokens cifrados ni los campos que el panel no usa | **cambia a propósito**: [Fase 9, decisión 3](../specs/2026-10-28-fase-9-identidad.md); `AdminDashboard` no lee ninguno de los campos que ya no vienen |
| paciente y profesional `GET /api/notificaciones` | sin el campo `usuario` (en `main`, el `Usuario` entero) | **cambia a propósito**: [decisión 1 de esta comparación](#decisiones-de-la-comparación); el frontend no lo lee |

Ninguna fila queda en **distinto** o **falta**.

## C1 y C2 — Inventario de `main` y mapa a la migración (2026-11-01)

`scripts/comparacion/inventario.mjs` saca de cada revisión los endpoints, las tareas programadas y de arranque, los
mails, las integraciones externas y las llamadas de `api.ts`. Para cada endpoint de `main` resuelve la regla del
gateway (la primera que coincide, en orden) y el módulo destino según las `TRANQUI_SERVICES_*_URI` del compose local.
El detalle generado está en [inventario.md](inventario.md).

```bash
node scripts/comparacion/inventario.mjs inventario <repo-main> main.json
node scripts/comparacion/inventario.mjs inventario . migracion.json
node scripts/comparacion/inventario.mjs mapa main.json migracion.json . docs/migracion/inventario.md
```

| Qué | `main` | Resultado | Estado |
|---|---|---|---|
| Endpoints | 112 | Los 112 tienen regla del gateway y un endpoint con el mismo método y ruta en el módulo destino | **igual** (la forma de las respuestas, en C4) |
| Llamadas de `api.ts` | 95 leídas (más `getSubscriptionEvents`, que arma la ruta con un ternario) | Las 95 siguen iguales en el frontend migrado | **igual** |
| Tareas programadas y de arranque | 12 | Cada una sigue en el monolito (con su interruptor de la fase) o tiene su par en un servicio: vencimiento de reservas y recordatorios en turnos, Google Calendar en calendario, limpieza de notificaciones en notificaciones, reconciliación de suscripciones en pagos | **igual**, salvo la fila siguiente |
| Mails | 23 tipos | 22 salen de notificaciones por evento o por ruta interna | **igual** |
| Mail de alerta a los administradores (`enviarAlertaAdmin`) | Facturas con error y facturación cerca del tope del monotributo, en la reconciliación diaria | Con `SUSCRIPCIONES_EXTERNO` la reconciliación la hace pagos, que **solo lo escribe en el log**; la del monolito no corre | **arreglado** (C5, suscripciones): pagos publica `pagos.alerta.admin.v1` en los dos casos y notificaciones le manda el mail de alerta a cada administrador, con el mismo título y texto que el monolito |
| Integraciones externas | 14 hosts | Cada host de `main` sigue usado en la migración (Mercado Pago en pagos, Google en calendario, Resend en notificaciones e identidad, ARCA en pagos) | **igual** |

El inventario es por expresiones regulares: no ve la lógica dentro de los métodos. Lo que falta revisar es C3: por
dominio, la lógica de cada endpoint (validaciones, mensajes de error, efectos), con la tabla de arriba como índice.

## Decisiones de la comparación

1. **`GET /api/notificaciones` sin el campo `usuario` (2026-11-01).** En `main`, cada notificación de la campanita
   traía en `usuario` la cuenta dueña completa: email, DNI, teléfono, dirección, el hash de la contraseña, los códigos
   de verificación y los tokens cifrados de Google y Mercado Pago. En la migración (`services/notificaciones`,
   `NotificacionController.NotificacionDto`) cada notificación trae solo sus datos: id, título, mensaje, si está leída,
   tipo y fecha. Ningún componente del frontend lee `usuario`, así que en pantalla no cambia nada; se deja así porque
   deja de mandar al navegador datos sensibles que no hacían falta.
