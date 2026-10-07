# Plan: Sistema de Tickets + Panel de Administrador ampliado

**Estado:** Propuesto (para revisión del usuario, no implementado)
**Fecha:** 2026-08-12

---

## 1. Resumen

Agregar un sistema de tickets de soporte (pacientes y profesionales generan tickets, el administrador los resuelve vía chat) y ampliar el panel de administrador con un sidebar por secciones, gestión de turnos en nombre de pacientes, reseteo de contraseñas, y acceso completo a los datos de profesionales y pacientes.

## 2. Qué ya existe hoy (punto de partida real)

Esto cambia el alcance del trabajo respecto a partir de cero:

| Pieza | Estado actual |
|---|---|
| Formulario de "queja" (`ComplaintModal.tsx` → `SoporteController`) | Existe, pero **solo envía un email** a soporte@tranquisalud.com. No persiste nada en base de datos, no tiene estado, no es consultable desde la app. No sirve como base de datos para tickets — hay que construir la persistencia desde cero. |
| Rol `ADMIN` | Ya existe en el enum `Rol`, con auth JWT + `@PreAuthorize("hasRole('ADMIN'))` funcionando. |
| Panel de administrador (`AdminDashboard.tsx` + `AdminController`) | Ya existe y funciona: 2 tabs (Pendientes de verificación / Todos los usuarios), tabla de usuarios con búsqueda y filtro por rol, modal de detalle de profesional, cambio de rol. **No tiene sidebar seccionado** (son tabs simples arriba del contenido), **no tiene** gestión de turnos, **no tiene** reset de contraseña. |
| Chat en tiempo real | Ya existe infraestructura STOMP completa (`WebSocketConfig`, `/ws-tranqui`, patrón `Mensaje`/`MensajeService`/`ChatController`) para el chat médico↔paciente. Reusable como *mecanismo de transporte*, pero el modelo (`Mensaje` es 1-a-1 por par de usuarios) no encaja con "un hilo de chat por ticket" — hace falta una entidad nueva para los mensajes del ticket. |
| Creación de turno (`/api/turnos/reservar`) | Endpoint público, sin auth, que ya hace "find or create" de paciente por email. Un admin podría technically pegarle igual, pero hoy el turno queda `PENDIENTE_PAGO` y dispara Mercado Pago — hay que decidir si un turno creado por admin salta ese paso. |
| Reset de contraseña por admin | **No existe.** Solo hay flujo self-service (código por email) y cambio de password con password actual. Hay que crearlo. |
| Notificaciones in-app | `NotificacionService` ya soporta crear notificación + push WebSocket por usuario (bandeja efímera de 24hs). Reusable para avisar "nuevo ticket" / "nueva respuesta". |

## 3. Alcance de este plan

**Incluye:**
- Modelo de datos y backend completo para tickets (creación, mensajes, cambio de estado, listado filtrado).
- Reemplazo del `ComplaintModal` actual por creación real de ticket + una vista "Mis tickets" para que el paciente/profesional vea respuestas y responda.
- Sidebar seccionado en el panel de admin.
- Vista de Tickets en el panel de admin, con filtros por estado y por tipo de creador, y chat de resolución.
- Alta de turno en nombre de un paciente, desde el panel de admin.
- Reseteo de contraseña de cualquier usuario, desde el panel de admin.
- Ficha completa de usuario (profesional o paciente) accesible desde el panel de admin.

**No incluye (fuera de este plan, a definir después si hace falta):**
- Auditoría/logging formal de acciones sensibles del admin (lo recomiendo igual, ver §8).
- Múltiples admins asignados a un mismo ticket / reparto de carga entre admins.
- Notificación por email de tickets (solo in-app por defecto; queda como opción, ver §8).

## 4. Modelo de datos nuevo

### `Ticket`
| Campo | Tipo | Notas |
|---|---|---|
| `id` | Long | PK |
| `creador` | `Usuario` (FK) | Paciente o profesional que abrió el ticket |
| `tipoCreador` | enum `PACIENTE` \| `PROFESIONAL` | Denormalizado desde `creador.rol` al crear — permite filtrar sin joins y no cambia si el rol del usuario cambia después (mismo patrón que `Turno.ocupaAgenda` respecto a `TarifaMedico.requiereAgenda`) |
| `asunto` | String | |
| `estado` | enum `PENDIENTE` \| `ACTIVO` \| `RESUELTO` | `PENDIENTE` = recién creado, sin respuesta de admin. `ACTIVO` = admin ya respondió, conversación abierta. `RESUELTO` = cerrado |
| `adminAsignado` | `Usuario` (FK, nullable) | Opcional — qué admin lo está atendiendo, si se decide asignar |
| `fechaCreacion` / `fechaActualizacion` / `fechaResolucion` | timestamps | |

### `TicketMensaje`
| Campo | Tipo | Notas |
|---|---|---|
| `id` | Long | PK |
| `ticket` | `Ticket` (FK) | |
| `autor` | `Usuario` (FK) | El creador del ticket o un admin |
| `contenido` | TEXT | |
| `fechaEnvio` | timestamp | |
| `leidoPorAdmin` / `leidoPorUsuario` | boolean | Para badges de "no leído" a cada lado |

Migraciones nuevas: tabla `ticket` y `ticket_mensaje`, con índices por `estado`, `tipo_creador`, y `ticket_id` en mensajes (mismo patrón de índices que ya usa `Turno`).

## 5. Backend

### Controlador de tickets (lado paciente/profesional) — `/api/tickets`
- `POST /api/tickets` — crea ticket + primer mensaje. Roles `PACIENTE`, `PSIQUIATRA`.
- `GET /api/tickets/mios` — lista los tickets propios (con estado y último mensaje).
- `GET /api/tickets/{id}` — detalle + historial de mensajes. Autorización: dueño del ticket o `ADMIN`.
- `POST /api/tickets/{id}/mensajes` — agrega mensaje al hilo. Autorización: dueño o `ADMIN`.

### Extensión del panel admin — `/api/admin`
- `GET /api/admin/tickets?estado=&tipoCreador=` — listado filtrable (reusa los mismos endpoints de detalle/mensajes de arriba, ya autorizados para `ADMIN`).
- `PUT /api/admin/tickets/{id}/estado` — cambiar estado (pendiente/activo/resuelto).
- `POST /api/admin/turnos` — crear turno en nombre de un paciente. Ver decisión pendiente en §8 sobre si salta el pago.
- `POST /api/admin/users/{id}/reset-password` — genera (o recibe) una password nueva, la hashea con el mismo `BCryptPasswordEncoder` ya usado, y la guarda. Ver decisión pendiente en §8 sobre el flujo exacto.
- `GET /api/admin/users/{id}` — ficha completa (si el listado actual no alcanza): turnos, recetas, pagos, historia clínica según corresponda al rol.

### Chat de tickets — transporte
Reusar `WebSocketConfig`/`/ws-tranqui` ya existente. Nuevo mapeo STOMP `@MessageMapping("/tickets.enviar")` que persiste en `TicketMensaje` y hace `convertAndSend` a un topic `/topic/tickets/{ticketId}` (en vez del patrón 1-a-1 por usuario que usa `ChatController` hoy).

### Notificaciones
Reusar `NotificacionService.crearNotificacion`:
- Nuevo ticket → notificar a admins.
- Nueva respuesta de admin → notificar al creador del ticket.
- Nueva respuesta del creador → notificar al admin asignado (o a todos los admins si no hay asignación).

## 6. Frontend

### Panel de administrador — sidebar seccionado
El `Sidebar.tsx` actual del médico es una lista plana con un solo título de sección — no hay hoy ningún ejemplo de sidebar *multi-sección* en el repo, así que esto es un componente nuevo (`AdminSidebar.tsx`), no una extensión del existente. Estructura propuesta:

- **General**: Resumen (stats actuales de `AdminDashboard`)
- **Soporte**: Tickets (con sub-filtros estado / paciente-profesional)
- **Gestión**: Usuarios (tabla actual, pacientes + profesionales), Turnos (crear/ver), Verificaciones (tab "Pendientes" actual)

`AdminDashboard.tsx` pasa de "2 tabs" a actuar como router interno de estas secciones (mismo rol que hoy cumple `App.tsx` con `NavSection` para el médico).

### Componentes nuevos
- `AdminTicketsView.tsx` — listado + panel de chat al seleccionar un ticket.
- `AdminTurnoForm.tsx` — modal para crear turno eligiendo médico + paciente + fecha/hora/servicio (mismo shape que `ReservaTurnoDto`, reusando `api.reservarTurno` o un nuevo `api.crearTurnoAdmin`).
- Ampliación del modal de detalle de usuario ya existente para incluir botón "Resetear contraseña" y, si hace falta, una vista de ficha completa equivalente a `ProfessionalDetailPanel` pero para pacientes (turnos, recetas, pagos).
- `MyTicketsView.tsx` — nueva vista para que paciente/profesional vean sus tickets y respondan. Reemplaza el flujo actual de `ComplaintModal` (que hoy es "enviar y listo, sin poder ver respuesta en la app").

## 7. Fases de implementación sugeridas

1. **Backend — modelo y CRUD de tickets**: entidades, migraciones, repos, service, controller, tests.
2. **Frontend — lado paciente/profesional**: reemplazar `ComplaintModal` por creación real de ticket + `MyTicketsView`.
3. **Frontend — panel admin**: sidebar seccionado + vista de Tickets con chat.
4. **Panel admin — turnos y contraseñas**: alta de turno para paciente, reset de password, ficha completa de usuario.
5. **Tiempo real**: WebSocket para el chat de tickets + notificaciones in-app.
6. *(Opcional, recomendado)* Auditoría de acciones sensibles del admin — ver §8.

## 8. Decisiones que te faltan tomar antes de implementar

- **Turno creado por admin**: ¿queda `CONFIRMADO` directo (salteando Mercado Pago) o sigue el flujo normal de pago pendiente?
- **Reset de contraseña**: ¿el admin escribe una password temporal a mano, o el sistema genera una y se la muestra en pantalla / se la manda por email al usuario?
- **Asignación de tickets**: ¿cualquier admin puede responder cualquier ticket, o se asignan a un admin específico?
- **`ComplaintModal` actual**: ¿se reemplaza 100% por el sistema de tickets, o convive como un canal alternativo?
- **Acceso del admin a datos clínicos**: pediste acceso a "absolutamente todos los datos" de profesional y paciente — dado que esto incluye historia clínica psiquiátrica (dato sensible bajo Ley 25.326, que ya tienen documentada en la política de privacidad del sitio), te recomiendo que quede con algún registro de auditoría de qué admin vio qué ficha y cuándo, aunque el acceso en sí no tenga restricciones. Es una recomendación, no un bloqueo — decidilo vos.
