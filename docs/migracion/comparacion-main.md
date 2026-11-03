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
| paciente y profesional `GET /api/notificaciones` | sin el campo `usuario` (en `main`, el `Usuario` entero) | **igual** para el frontend: no lo lee ningún componente. Falta registrarlo como decisión |

Ninguna fila queda en **distinto** o **falta**.

## C1 a C3 — Inventario y revisión por dominio

Pendiente: el inventario de `main` (endpoints, tareas programadas, webhooks, mails, integraciones), el mapa de la
migración y la revisión de código por dominio. La comparación ejecutando cubre lo que el frontend usa; lo que no pasa
por una ruta (crons, webhooks, mails, integraciones) necesita la revisión de código.
