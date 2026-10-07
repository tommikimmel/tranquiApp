# Plan de migración a microservicios

- **Estado:** plan aprobado en sus definiciones principales (2026-10-07). Cada fase se ejecuta con
  su propia spec SDD, aprobada antes de empezar.
- **Decisión:** [ADR 009](../adr/009-arquitectura-microservicios.md).

## 0. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Momento | La app sale a producción con el **monolito actual**. La migración se hace después, **por fases**, con el monolito atendiendo lo que todavía no se migró (patrón *strangler*). |
| Servicios | 8: identidad, profesionales, turnos, pagos, suscripciones, notificaciones, chat, calendario. |
| Tecnología | Spring Boot (JVM afinada; imágenes nativas solo si hiciera falta RAM). |
| Llamadas sincrónicas | **Todas pasan por el API Gateway**: las del frontend y las de un servicio a otro. Ningún servicio conoce la dirección de otro. |
| Eventos | **Push**: el broker empuja cada evento a los servicios suscritos; ningún servicio pregunta si hay novedades. RabbitMQ. |
| Gateway | Ruteo, rate limiting, healthcheck y service discovery (los servicios se registran con su nombre y puerto). |
| Confiabilidad | Outbox, idempotencia (inbox e `Idempotency-Key`), reintentos, DLQ, circuit breaker, sagas por coreografía. |
| Recursos | Optimizado para el VPS actual: 8 GB de RAM y **2 núcleos**. |
| Observabilidad | Uso de recursos, tráfico y latencia visibles en el panel de administrador. |

## 1. Punto de partida (medido el 2026-10-07)

| Recurso | Valor |
|---|---|
| RAM total / usada / disponible | 7,9 GB / 1,7 GB / 6,2 GB |
| CPU | 2 núcleos |
| Disco libre | 52 GB de 96 GB |
| Monolito (`tranqui-backend`) | 486 MB |
| PostgreSQL de la app | 39 MB |
| Bot de WhatsApp (4 contenedores) | ~65 MB |
| Traefik | 42 MB |

El monolito tiene ~18.000 líneas de Java. Los servicios más grandes hoy: `TurnoService` (1089),
`SubscriptionService` (861), `ResendEmailService` (752), `MedicoService` (730).

**El límite más duro es la CPU, no la RAM:** una JVM de Spring Boot consume mucha CPU al arrancar.
Arrancar 9 JVM a la vez en 2 núcleos tarda minutos, así que el arranque se escalona y las JVM se
afinan para arrancar rápido (sección 9).

## 2. Servicios

| Servicio | Responsabilidad | Código actual de origen | Base propia |
|---|---|---|---|
| **identidad** | Registro, login y emisión de JWT, verificación de mail, recuperación de contraseña, Mi Cuenta, eliminación de cuenta, tickets de soporte | `AuthController`, `AccountService`, `JwtService`, `GoogleAuthService`, `TicketService` | `identidad` |
| **profesionales** | Perfil profesional y público, buscador, verificación de matrícula, honorarios y tarifas, disponibilidad semanal, historia clínica y recetas | `MedicoController`, `MedicoService`, `DisponibilidadService`, `ConceptoService`, `ClinicalService`, `RecetaService` | `profesionales` |
| **turnos** | Reservas, bloqueo de 5 minutos, cancelación, reprogramación, asistencia, confirmación por mail, documentos pedidos | `TurnoController`, `TurnoService`, `AgendaService`, `LiberarTurnosScheduler` | `turnos` |
| **pagos** | Checkout y webhooks de Mercado Pago, reembolsos, vinculación de la cuenta de Mercado Pago del profesional | `WebhookController`, `MercadoPagoService`, `MercadoPagoOAuthService`, `PagoWebhookHandler`, `ReembolsoService` | `pagos` |
| **suscripciones** | Planes, suscripciones, débitos automáticos, reconciliación, avisos de vencimiento, facturación ARCA | `SubscriptionController`, `AdminSubscriptionController`, `SubscriptionService`, `PlanService`, `InvoiceService`, `arca/` | `suscripciones` |
| **notificaciones** | Mails (Resend) y notificaciones dentro de la app. Solo reacciona a eventos | `ResendEmailService`, `NotificacionService`, `NotificationScheduler` | `notificaciones` |
| **chat** | Mensajería en tiempo real (WebSocket) entre pacientes y profesionales | `ChatController`, `MensajeService`, config de WebSocket | `chat` |
| **calendario** | Google Calendar y Meet: OAuth, creación de eventos, sincronización y eventos externos | `GoogleCalendar*` | `calendario` |

El panel de administrador no es un servicio: el frontend llama a cada servicio (siempre a través
del gateway) con un JWT de rol `ADMIN`.

## 3. Infraestructura

| Componente | Tecnología | Rol |
|---|---|---|
| API Gateway | Spring Cloud Gateway | Único punto de entrada sincrónico: ruteo, autenticación, rate limiting, healthcheck, circuit breaker |
| Service discovery | Consul | Registro de servicios (nombre, host, puerto, healthcheck) |
| Mensajería | RabbitMQ | Eventos asincrónicos por push |
| Base de datos | PostgreSQL 15, **una instancia con una base y un usuario por servicio** | Aislamiento de datos sin pagar la RAM de 8 instancias |
| Cache del rate limiting | Redis | Contadores del token bucket |
| Métricas | VictoriaMetrics (single node) + node-exporter + cAdvisor | Observabilidad (sección 11) |
| Proxy público | Traefik (ya existe) | TLS; solo enruta hacia el gateway y el frontend |

**Por qué RabbitMQ y no Kafka:** en Kafka los consumidores hacen *pull* (consultan al broker), lo
que se pidió evitar. En RabbitMQ el broker empuja cada mensaje a los consumidores suscritos
(`basic.consume`), las colas durables guardan los eventos mientras un servicio está apagado y el
consumo de RAM es bajo.

**Por qué Consul y no Eureka:** Consul pesa ~50 MB; Eureka es otra JVM (~300 MB). Además Consul
hace los healthchecks él mismo.

### Topología de red

```
Internet ──TLS──► Traefik ──► frontend (nginx)
                         └──► gateway:8080  (/api/**, /ws/**)
                                 │  resuelve por nombre en Consul
                                 ▼
             identidad · profesionales · turnos · pagos · suscripciones · notificaciones · chat · calendario
                                 │  (red interna de Docker, sin puertos publicados)
                     PostgreSQL · RabbitMQ · Redis · Consul · VictoriaMetrics
```

- Solo el gateway y el frontend son alcanzables desde Traefik. Los servicios **no publican puertos**
  ni tienen labels de Traefik.
- Los servicios hablan entre ellos **solo a través del gateway** (sección 4.2) y por eventos.

## 4. API Gateway

### 4.1 Rutas públicas (frontend)

Se conservan las URLs actuales (`/api/...`), así el frontend no cambia al migrar. El gateway decide
a qué servicio va cada ruta:

| Prefijo | Servicio |
|---|---|
| `/api/auth/**`, `/api/tickets/**`, `/api/soporte/**` | identidad |
| `/api/medicos/**` (perfil, buscador, disponibilidad, tarifas), `/api/conceptos/**`, `/api/recetas/**`, `/api/clinical/**` | profesionales |
| `/api/medicos/{id}/turnos-disponibles`, `/api/turnos/**`, `/api/medicos/turnos/**` | turnos |
| `/api/payments/**`, `/api/medicos/mercadopago/**`, `/api/webhooks/mercadopago` | pagos |
| `/api/subscriptions/**`, `/api/admin/subscriptions/**` | suscripciones |
| `/api/notificaciones/**` | notificaciones |
| `/api/chat/**`, `/ws-tranqui/**` (WebSocket) | chat |
| `/api/medicos/google-calendar/**` | calendario |
| todo lo demás | **monolito** (mientras dure la migración) |

Durante la migración, cada fase solo mueve las rutas de su servicio; el resto sigue yendo al
monolito. **Volver atrás una fase es cambiar la ruta de vuelta al monolito.**

### 4.2 Llamadas entre servicios (internas)

Toda llamada sincrónica de un servicio a otro pasa por el gateway, con este esquema:

- **Ruta interna:** `http://gateway:8080/internal/<servicio>/...` (ejemplo:
  `/internal/profesionales/medicos/7/resumen`). El gateway la reescribe y la deriva al servicio.
- **Nunca expuesta afuera:** Traefik solo enruta `/api/**` y `/ws-tranqui/**` al gateway. Además, el
  gateway rechaza con 404 cualquier `/internal/**` que llegue con encabezados de Traefik
  (`X-Forwarded-*`).
- **Autenticación de servicio:** cada servicio firma un JWT corto (5 minutos) con su nombre como
  `sub` y el rol `SERVICE`, usando una clave interna compartida que vive solo en el `.env` del VPS.
  El gateway lo valida antes de derivar. Sin token válido: 401.
- **Contexto del usuario:** si la llamada se origina en un pedido de un usuario, se propaga su id
  en `X-User-Id` y el `X-Correlation-Id`.
- **Regla de diseño:** las llamadas internas son para **consultas**. Los cambios de estado entre
  servicios se comunican por **eventos**, no por llamadas. Así un servicio caído no bloquea a otro.

### 4.3 Rate limiting

Token bucket en Redis (`RequestRateLimiter` de Spring Cloud Gateway). Clave: id de usuario si está
autenticado; IP si no.

| Grupo de rutas | Límite |
|---|---|
| Login, registro, verificación, recuperación de contraseña | 5 por minuto por IP (ráfaga 10) |
| Crear pagos y reservas | 10 por minuto por usuario |
| Webhooks de Mercado Pago | 60 por minuto (por IP de origen) |
| Resto de `/api/**` | 120 por minuto por usuario o IP (ráfaga 60) |
| `/internal/**` | Sin límite por usuario; solo un tope global de protección (600 por minuto por servicio) |

Al superar el límite: **429** con `Retry-After`. Los rechazos se miden (sección 11). Si Redis no
responde, el gateway **deja pasar** el pedido (falla abierta) y lo registra, para no tirar la app
por un componente auxiliar.

### 4.4 Healthcheck

- Cada servicio expone `/actuator/health/liveness` y `/actuator/health/readiness` (Spring Boot
  Actuator). *Readiness* incluye la base y RabbitMQ.
- El gateway expone:
  - `/health`: público y mínimo (`UP`/`DOWN`), para Traefik y monitoreo externo.
  - `/api/admin/salud`: solo `ADMIN`; estado de cada servicio según Consul, versión e instancias.
    Lo usa el panel de administrador.

### 4.5 Service discovery

- Cada servicio usa `spring-cloud-starter-consul-discovery`. Al arrancar se registra en Consul con
  **nombre** (`turnos`), **host y puerto** (`turnos:8080` en la red de Docker), su URL de
  healthcheck y etiquetas (`version=1.4.0`).
- Se registra **recién cuando está listo** (readiness `UP`), así el gateway no le manda tráfico
  mientras arranca.
- Consul verifica el healthcheck cada 10 segundos. Un servicio que falla deja de recibir tráfico
  enseguida; si sigue caído 1 minuto, se desregistra solo. Al apagarse en orden, se desregistra.
- El gateway resuelve las rutas por nombre (`lb://turnos`) y cachea el catálogo: si Consul se cae,
  sigue usando el último conocido.

### 4.6 Resiliencia del gateway

- **Timeouts:** 2 s de conexión y 10 s de respuesta (30 s para pagos y webhooks).
- **Reintentos:** solo para `GET` idempotentes y errores de conexión, 2 intentos con espera
  creciente. Nunca se reintenta un `POST` sin `Idempotency-Key`.
- **Circuit breaker** (Resilience4j) por servicio: si más del 50 % de las últimas 20 llamadas falla,
  se abre 30 segundos y responde 503 enseguida con un mensaje claro (`{"error":"servicio_no_disponible","servicio":"turnos"}`).

## 5. Eventos

### 5.1 Topología de RabbitMQ

| Elemento | Nombre | Tipo |
|---|---|---|
| Exchange principal | `tranqui.eventos` | topic, durable |
| Exchange de reintentos | `tranqui.reintentos` | direct, durable |
| Exchange de mensajes muertos | `tranqui.dlx` | direct, durable |
| Cola de cada servicio | `<servicio>.eventos` (ej. `notificaciones.eventos`) | durable, clásica *lazy* (guarda en disco, poca RAM) |
| Colas de espera para reintentos | `<servicio>.reintento.10s`, `.1m`, `.5m` | con TTL; al vencer vuelven a la cola del servicio |
| Cola de mensajes muertos | `<servicio>.dlq` | durable, para revisar a mano |

- Cada servicio **enlaza** su cola al exchange principal con las claves que le interesan (por
  ejemplo, notificaciones con `turnos.turno.*.v1` y `pagos.#`).
- Consumo con **ack manual** y `prefetch` 10. El broker empuja; el servicio confirma (ack) recién
  cuando terminó de procesar y registró el evento en su inbox.
- **Reintentos:** si falla, el evento pasa por la cola de 10 s, después 1 min, después 5 min. Si
  sigue fallando, va a la DLQ y aparece en el panel de administrador. Un error de validación
  (evento mal formado) va directo a la DLQ, sin reintentos.
- **Publicación** con *publisher confirms* y `mandatory`: el relay de la outbox (5.4) marca un
  evento como publicado solo cuando el broker confirma que lo guardó.

### 5.2 Nombre de los tópicos

`<dominio>.<entidad>.<evento>.v<n>`, en minúsculas, verbo en participio pasado:
`turnos.turno.confirmado.v1`. El dominio es el servicio dueño de la entidad.

### 5.3 Estructura de los mensajes

Sobre común en JSON (`Content-Type: application/json`, UTF-8):

```json
{
  "eventId": "0e8a2c5e-6f6c-4b1e-9d1a-3c2b7f1e9a10",
  "eventType": "turnos.turno.confirmado",
  "version": 1,
  "occurredAt": "2026-10-07T21:30:00-03:00",
  "producer": "turnos",
  "correlationId": "c4f1…  (id del pedido que inició la cadena)",
  "causationId": "9b7d…  (eventId del evento que lo causó, si aplica)",
  "aggregateType": "turno",
  "aggregateId": "4521",
  "aggregateVersion": 3,
  "data": {
    "turnoId": 4521,
    "pacienteId": 88,
    "profesionalId": 7,
    "fecha": "2026-10-10",
    "horaInicio": "10:00",
    "modalidad": "ONLINE",
    "precio": 45000
  }
}
```

Reglas:

- `eventId` es único (UUID) y es la clave de idempotencia del consumidor.
- `aggregateVersion` crece en 1 con cada cambio de la entidad; permite descartar eventos viejos que
  lleguen tarde.
- `data` lleva lo que los consumidores necesitan para actuar **sin tener que llamar al
  productor**, pero nunca datos de salud ni información clínica, ni contraseñas o tokens.
- **Compatibilidad:** se pueden agregar campos opcionales sin cambiar la versión. Quitar o cambiar
  el significado de un campo exige una `v2`; durante una transición el productor publica las dos
  versiones.
- Los contratos (clases Java del sobre y de cada `data`) viven en `libs/eventos` y tienen tests de
  contrato.

### 5.4 Catálogo de eventos

| Tópico | Productor | Consumidores | Para qué |
|---|---|---|---|
| `identidad.usuario.registrado.v1` | identidad | notificaciones, profesionales | Mail de bienvenida; crear el perfil profesional |
| `identidad.usuario.actualizado.v1` | identidad | turnos, chat, notificaciones, pagos | Actualizar copias locales (nombre, mail, teléfono) |
| `identidad.usuario.eliminado.v1` | identidad | todos | Anonimizar datos propios (Ley 25.326) y cancelar turnos futuros |
| `identidad.codigo-verificacion.generado.v1` | identidad | notificaciones | Mail con el código |
| `identidad.ticket.respondido.v1` | identidad | notificaciones | Mail de respuesta del ticket |
| `profesionales.profesional.actualizado.v1` | profesionales | turnos, chat, pagos | Copias locales (nombre, foto, modalidades, duración de turno) |
| `profesionales.profesional.visibilidad-cambiada.v1` | profesionales | turnos | Aceptar o no reservas |
| `profesionales.tarifa.actualizada.v1` | profesionales | turnos | Precios vigentes |
| `profesionales.disponibilidad.actualizada.v1` | profesionales | turnos | Horarios reservables |
| `turnos.turno.reservado.v1` | turnos | pagos, notificaciones | Crear la preferencia de pago; aviso al profesional |
| `turnos.turno.confirmado.v1` | turnos | notificaciones, calendario, chat | Mail al paciente; evento de Google con Meet; habilitar el chat |
| `turnos.turno.cancelado.v1` | turnos | pagos, notificaciones, calendario | Reembolso según la política de 48 hs; avisos; borrar el evento |
| `turnos.turno.reprogramado.v1` | turnos | notificaciones, calendario | Avisos; mover el evento |
| `turnos.turno.expirado.v1` | turnos | pagos | Invalidar el checkout pendiente |
| `turnos.recordatorio.programado.v1` | turnos | notificaciones | Mail de confirmar asistencia (1-2 días antes) |
| `turnos.documento.solicitado.v1` | turnos | notificaciones | Aviso al profesional |
| `pagos.pago.aprobado.v1` | pagos | turnos, notificaciones | Confirmar el turno |
| `pagos.pago.rechazado.v1` | pagos | turnos | Liberar el horario |
| `pagos.reembolso.realizado.v1` | pagos | turnos, notificaciones | Marcar el reembolso; mail |
| `pagos.cuenta-mp.vinculada.v1` / `.desvinculada.v1` | pagos | profesionales | Requisito para aparecer en el buscador |
| `suscripciones.suscripcion.cambio-estado.v1` | suscripciones | profesionales, notificaciones | Visibilidad en el buscador; mails de pago, vencimiento y cancelación |
| `calendario.meet.creado.v1` | calendario | turnos, notificaciones | Guardar el link de Meet en el turno; mandárselo al paciente |
| `calendario.horario.bloqueado.v1` | calendario | turnos | Eventos personales de Google que bloquean horarios |
| `chat.mensaje.enviado.v1` | chat | notificaciones | Notificación de mensaje nuevo |

## 6. Patrones de confiabilidad

### 6.1 Outbox (publicar sin perder ni inventar eventos)

Cada servicio que publica tiene una tabla en su propia base:

```sql
CREATE TABLE outbox (
  id              BIGSERIAL PRIMARY KEY,
  event_id        UUID        NOT NULL UNIQUE,
  topic           VARCHAR(150) NOT NULL,
  aggregate_type  VARCHAR(50)  NOT NULL,
  aggregate_id    VARCHAR(64)  NOT NULL,
  payload         JSONB        NOT NULL,      -- el sobre completo
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at    TIMESTAMPTZ,
  attempts        INT          NOT NULL DEFAULT 0,
  last_error      TEXT
);
CREATE INDEX outbox_pendientes ON outbox (id) WHERE published_at IS NULL;
```

- El evento se inserta **en la misma transacción** que el cambio de negocio: si la transacción
  falla, no hay evento; si se confirma, el evento existe sí o sí.
- **Relay** (dentro de cada servicio):
  - Un trigger hace `pg_notify('outbox', …)` al insertar. El relay escucha (`LISTEN`) y publica
    **al instante**, sin esperar.
  - Como respaldo (por ejemplo, si el relay se reconectó y perdió un aviso), barre los pendientes
    cada 30 segundos.
  - Publica en orden de `id`, en lotes de hasta 100, con *publisher confirms*. Marca `published_at`
    solo con la confirmación del broker. Si falla, suma `attempts`, guarda el error y reintenta con
    espera creciente.
  - Las filas publicadas se borran a los 7 días.
- **Garantía resultante:** entrega *al menos una vez* (un evento puede llegar dos veces, nunca
  cero). Lo duplicado lo resuelve la idempotencia del consumidor.

### 6.2 Inbox (idempotencia del consumidor)

```sql
CREATE TABLE eventos_procesados (
  event_id      UUID        NOT NULL,
  consumidor    VARCHAR(80) NOT NULL,   -- nombre del handler, ej. 'mail-turno-confirmado'
  procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumidor)
);
```

- El handler inserta la fila y aplica el cambio **en la misma transacción**. Si la fila ya existía,
  el evento es un duplicado: se confirma (ack) sin hacer nada.
- Para efectos externos que no son transaccionales (mandar un mail, llamar a Mercado Pago o a
  Google), se registra antes un "intento" con el `eventId` como clave de idempotencia del proveedor
  cuando el proveedor lo soporta (Mercado Pago `X-Idempotency-Key`, Resend `Idempotency-Key`).
- Las filas se borran a los 30 días.

### 6.3 Idempotencia de la API

- Los `POST` que crean cosas importantes (reservar turno, crear pago, reembolsar, contratar
  suscripción) exigen un encabezado `Idempotency-Key` (UUID que genera el frontend por intento).
- Cada servicio guarda la clave con el hash del cuerpo y la respuesta (24 horas). Repetir la misma
  clave devuelve la misma respuesta; la misma clave con otro cuerpo devuelve 422.
- Los webhooks de Mercado Pago se deduplican por id de pago y estado.

### 6.4 Orden

RabbitMQ no garantiza orden entre reintentos. Cada consumidor guarda el último `aggregateVersion`
aplicado por entidad y descarta lo más viejo. Para entidades donde el orden importa (turnos), el
consumidor también puede pedir el estado actual por la ruta interna si detecta un salto de versión.

### 6.5 Sagas por coreografía

**Reserva y pago de un turno:**

1. turnos guarda el turno en `PENDIENTE_PAGO` con bloqueo de 5 minutos y publica
   `turnos.turno.reservado`.
2. pagos crea la preferencia de Mercado Pago. El frontend la pide por `GET /api/payments/checkout/{turnoId}`.
3. Mercado Pago avisa por webhook. pagos publica `pagos.pago.aprobado` o `pagos.pago.rechazado`.
4. turnos confirma el turno (`turnos.turno.confirmado`) o libera el horario.
5. notificaciones manda los mails; calendario crea el evento y el Meet (`calendario.meet.creado`),
   que turnos guarda en el turno.

**Compensaciones:**

- **Pago rechazado:** se libera el horario.
- **Bloqueo vencido sin pago:** turnos publica `turnos.turno.expirado` y pagos invalida el
  checkout.
- **Pago aprobado de un turno ya expirado** (por ejemplo, pagos estuvo caído): si el horario sigue
  libre, turnos lo confirma igual; si ya lo tomó otro paciente, turnos publica
  `turnos.turno.cancelado` con motivo `conflicto_de_horario` y pagos reembolsa el 100 %.

**Cancelación:** turnos calcula si corresponde reembolso (más de 48 hs, o cancela el profesional) y
lo informa en `turnos.turno.cancelado`; pagos ejecuta el reembolso y publica
`pagos.reembolso.realizado`.

### 6.6 Copias locales (read models)

Cada servicio guarda una copia mínima de los datos ajenos que lee seguido, actualizada por eventos:
turnos guarda nombre, mail y foto del profesional y del paciente; notificaciones guarda nombre y mail.
Así un servicio responde aunque el dueño del dato esté caído. Si un servicio arranca con su copia
vacía, la reconstruye con una carga inicial desde la ruta interna del dueño.

### 6.7 Otras prácticas

- **Base por servicio:** ningún servicio lee tablas de otro. Cada uno tiene su usuario de base con
  permisos solo sobre la suya.
- **Migraciones** con Flyway por servicio.
- **Correlación:** el gateway genera `X-Correlation-Id` si no viene; todos los logs y eventos lo
  llevan. Logs en JSON a stdout.
- **Apagado ordenado:** al recibir SIGTERM, cada servicio se desregistra de Consul, deja de tomar
  mensajes, termina los que está procesando (hasta 20 s) y cierra.
- **Configuración** por variables de entorno; secretos solo en el `.env` del VPS.
- **Versionado de la API pública:** las rutas actuales se mantienen; un cambio incompatible usa un
  prefijo nuevo (`/api/v2/...`).

## 7. Qué pasa si un servicio se apaga, y cuando vuelve

| Se cae | Mientras está caído | Cuando vuelve |
|---|---|---|
| notificaciones | Nadie lo nota; los eventos se acumulan en su cola | Procesa la cola sin mails duplicados (inbox) |
| calendario | Los turnos se confirman igual; los eventos de Google esperan | Crea, mueve o borra los eventos pendientes. Los turnos online quedan sin link de Meet hasta entonces; la app muestra "Lo estamos generando" |
| chat | El chat muestra "no disponible"; el resto funciona | Los clientes se reconectan solos |
| pagos | No se puede pagar (el gateway responde 503 con mensaje claro). Mercado Pago reintenta sus webhooks | Procesa los webhooks y los eventos pendientes; aplica las compensaciones de la saga si un bloqueo venció |
| turnos | No se puede reservar ni ver turnos. Los eventos de pago esperan en su cola | Confirma los turnos pagados mientras estuvo caído |
| profesionales | El buscador no funciona; reservar sí (turnos tiene su copia) | Procesa los eventos pendientes |
| identidad | Nadie nuevo puede iniciar sesión; los ya logueados siguen (el gateway valida el JWT con la clave pública, sin consultar a identidad) | Normal |
| suscripciones | No se puede contratar ni cancelar; los profesionales activos siguen visibles | Ejecuta la reconciliación pendiente |
| RabbitMQ | Los servicios siguen atendiendo; los eventos se acumulan en cada outbox | Las outbox se vacían solas; no se pierde nada |
| Consul | El gateway usa el último catálogo conocido; los servicios nuevos no se pueden registrar | Los servicios se vuelven a registrar |
| Redis | El rate limiting falla abierto (deja pasar) y se registra | Normal |
| Gateway | Toda la app queda sin API: es el único punto único de falla. Traefik muestra 502 | Se reinicia solo (`restart: always`); arranca en ~15 s |
| PostgreSQL | Toda la app queda caída | Los servicios se reconectan solos |

**Arranque de cada servicio:**

1. Espera a la base y a RabbitMQ (reintentos con espera creciente, sin fallar).
2. Aplica sus migraciones.
3. Declara su cola y sus enlaces (es idempotente).
4. Recién con *readiness* `UP` se registra en Consul y empieza a consumir eventos.

## 8. Estructura del repositorio

```
backend/                    # el monolito, hasta la fase 8
services/
  identidad/  profesionales/  turnos/  pagos/  suscripciones/  notificaciones/  chat/  calendario/
gateway/
libs/
  eventos/                  # sobre, contratos, outbox, inbox, idempotencia, JWT de servicio
  comun/                    # errores, correlación, configuración de Actuator y Consul
infra/
  rabbitmq/  consul/  victoriametrics/  postgres/   # configuración e inicialización de bases
docker-compose.yml          # producción
docker-compose.local.yml    # local (con perfiles para levantar solo algunos servicios)
```

Proyecto Maven de varios módulos con versión común. Cada servicio sigue el estilo actual del
monolito (controller, service, repository, model).

## 9. Recursos (8 GB y 2 núcleos)

### 9.1 Presupuesto de RAM

| Componente | Límite (`mem_limit`) |
|---|---|
| Sistema operativo, Docker y cache de disco mínimo | ~1 GB |
| PostgreSQL (una instancia, 9 bases incluida la del monolito durante la migración) | 512 MB |
| RabbitMQ (`vm_memory_high_watermark` 0.6 del límite) | 256 MB |
| Gateway | 288 MB |
| 8 servicios × 224 MB | ~1,8 GB |
| Redis, Consul | ~120 MB |
| VictoriaMetrics, node-exporter, cAdvisor | ~200 MB |
| Frontend (nginx) y Traefik | ~60 MB |
| Bot de WhatsApp (actual) | ~70 MB |
| Monolito (mientras dure la migración, decrece fase a fase) | 512 MB → 0 |
| **Total en el peor momento (fase 0, monolito completo)** | **~4,8 GB** |
| **Total al terminar** | **~4,3 GB** |

Margen de ~3 GB para picos y para la cache de disco de Postgres.

### 9.2 Ajustes de la JVM y de Spring Boot

- `-XX:+UseSerialGC`: el recolector más liviano; ideal para heaps chicos y 2 núcleos.
- `-XX:MaxRAMPercentage=65` con `mem_limit` del contenedor (heap de ~145 MB en 224 MB).
- `-Xss256k`, `-XX:ReservedCodeCacheSize=48m`, `-XX:MaxMetaspaceSize=96m`.
- `-XX:TieredStopAtLevel=1` en los servicios chicos (notificaciones, chat, calendario): arranque
  más rápido y menos CPU, a cambio de algo de rendimiento máximo.
- **CDS** (Class Data Sharing de Spring Boot 3.3+, `-XX:SharedArchiveFile`): arranque ~30 % más
  rápido.
- `spring.threads.virtual.enabled=true` (Java 21): miles de pedidos concurrentes sin un hilo del
  sistema por pedido. Requiere pasar los servicios a Java 21.
- Tomcat con `server.tomcat.threads.max=50` y pools de conexiones a la base chicos (Hikari 5).
- Sin JMX, sin DevTools, logs en JSON sin colores.

### 9.3 CPU

- `cpus: 0.5` por servicio en régimen normal; el gateway con `cpus: 1.0`.
- **Arranque escalonado:** el orden lo dan los `depends_on` con `condition: service_healthy`
  (infraestructura, gateway, identidad, profesionales, turnos, pagos y el resto), para no arrancar
  9 JVM a la vez.
- Si tras medir en la fase 4 el arranque o la RAM no alcanzan, los servicios más chicos
  (notificaciones, chat, calendario) pasan a imágenes nativas con GraalVM (~80 MB, arranque en
  menos de 1 s). La compilación nativa se hace en GitHub Actions, nunca en el VPS.

### 9.4 Imágenes y deploy

Compilar 9 servicios con Maven en el VPS lo deja sin memoria y sin CPU. A partir de la fase 0 las
imágenes se construyen en **GitHub Actions** y se publican en **GHCR** (registro de GitHub); el VPS
solo hace `docker compose pull` y `up`. Cada servicio se puede desplegar solo.

## 10. Fases

Cada fase tiene su spec SDD, su rama y su PR, y termina con un release (`vX.Y.0`). Al final de cada
fase el sistema completo funciona en producción.

### Fase 0: plataforma (el monolito sigue atendiendo todo)

- **Qué:** gateway delante del monolito (Traefik apunta `/api` al gateway, que deriva todo al
  monolito), Consul, RabbitMQ, Redis, `libs/eventos` y `libs/comun`, outbox en el monolito para los
  eventos de la sección 5.4, observabilidad (sección 11), imágenes en GHCR y deploy por imágenes,
  entorno local con la nueva infraestructura.
- **Salida:** todo el tráfico pasa por el gateway con rate limiting y métricas; el monolito publica
  eventos a RabbitMQ; el panel de administrador muestra recursos, tráfico y latencia.
- **Vuelta atrás:** Traefik apunta `/api` de nuevo al monolito.

### Fase 1: notificaciones

- **Por qué primero:** solo consume eventos y no tiene llamadas entrantes críticas. Prueba toda la
  cadena (outbox, RabbitMQ, inbox, reintentos, DLQ) con el menor riesgo.
- **Qué:** mails y notificaciones dentro de la app. El monolito deja de mandar mails y solo publica
  eventos.
- **Datos:** tabla `notificacion` y copia de nombre y mail de usuarios.
- **Salida:** todos los mails salen del servicio, sin duplicados, con la DLQ vacía una semana.
- **Vuelta atrás:** un flag en el monolito vuelve a activar el envío directo.

### Fase 2: chat

- **Qué:** WebSocket del chat y su historial, a través del gateway.
- **Datos:** tabla `mensaje`.
- **Salida:** el chat funciona por el gateway; reconexión automática probada con el servicio
  reiniciándose.

### Fase 3: calendario

- **Qué:** OAuth de Google Calendar, eventos y Meet, sincronización (webhooks de Google al gateway).
- **Datos:** tokens de Google (cifrados) y eventos externos, que salen de `usuario`.
- **Salida:** turnos confirmados crean su evento por `turnos.turno.confirmado`; el link de Meet
  vuelve por `calendario.meet.creado`.

### Fase 4: pagos

- **Qué:** checkout, webhooks, reembolsos, vinculación de Mercado Pago.
- **Datos:** `pago` y los tokens de Mercado Pago de cada profesional (cifrados).
- **Riesgo:** dinero real. Se prueba la saga completa con el simulador en local y en producción con
  un monto mínimo. Los webhooks se configuran en Mercado Pago apuntando al gateway.
- **Salida:** ningún pago perdido ni duplicado durante dos semanas; reconciliación contra Mercado
  Pago sin diferencias.

### Fase 5: profesionales

- **Qué:** perfiles, buscador, tarifas, disponibilidad, historia clínica y recetas.
- **Datos:** la parte profesional de `usuario`, `tarifa_medico`, `disponibilidad`, historia clínica
  y recetas.

### Fase 6: turnos

- **Qué:** reservas, agenda, cancelación, reprogramación, asistencia, documentos.
- **Datos:** `turno` y copias de profesionales y pacientes.
- **Riesgo:** es el corazón de la app. Se migra con una ventana de mantenimiento corta para mover
  los datos.

### Fase 7: suscripciones

- **Qué:** planes, suscripciones, débitos, reconciliación, facturación.
- **Datos:** `plans`, `features`, `subscriptions` y las tablas de facturación.

### Fase 8: identidad y retiro del monolito

- **Qué:** registro, login, Mi Cuenta, tickets. El gateway pasa a validar el JWT con la clave
  pública de identidad.
- **Datos:** credenciales y datos personales de `usuario`.
- **Salida:** ninguna ruta va al monolito; se apaga y se archiva su base.

### Migración de datos en cada fase

1. Se crea la base del servicio y se copian los datos del monolito con un script idempotente (se
   puede correr varias veces).
2. Mientras dura la transición, el monolito publica eventos de los cambios y el servicio nuevo los
   aplica, así su copia queda al día.
3. Se cambian las rutas del gateway al servicio nuevo, en una ventana de mantenimiento corta si la
   fase lo requiere.
4. Se compara: cantidad de registros y muestras entre ambas bases.
5. El código del monolito para ese dominio se desactiva (y se borra en la fase siguiente).

## 11. Observabilidad en el panel de administrador

Se implementa en la fase 0, así sirve también para el monolito desde el lanzamiento.

### 11.1 Qué se mide

| Qué | Cómo | Métricas |
|---|---|---|
| Recursos del VPS | node-exporter | CPU, RAM, disco, carga, red |
| Recursos por contenedor | cAdvisor | CPU y RAM de cada servicio, reinicios |
| Tráfico y latencia | Micrometer en el gateway (`spring.cloud.gateway.metrics`) y en cada servicio (`http.server.requests`) | Pedidos por minuto por ruta y servicio; latencia p50, p95 y p99; errores 4xx y 5xx; rechazos por rate limiting (429) |
| Mensajería | Plugin de Prometheus de RabbitMQ | Mensajes en cola por servicio, DLQ, tasa de publicación y consumo |
| Outbox | Métrica propia de cada servicio | Eventos pendientes de publicar y el más viejo |
| JVM | Micrometer | Heap usado, pausas de GC, hilos |

Almacenamiento: **VictoriaMetrics** en modo single node (~80 MB de RAM, compatible con Prometheus),
que recoge las métricas cada 15 segundos y las guarda 15 días. No se expone a Internet.

### 11.2 Panel de administrador

Nueva sección **"Observabilidad"** en el panel de administrador:

- **Estado de servicios:** cada servicio con su estado según Consul, versión, instancias y tiempo
  desde el último reinicio.
- **Recursos:** CPU y RAM del VPS y de cada contenedor (últimas 24 horas), disco libre.
- **Tráfico:** pedidos por minuto (total y por servicio), errores 5xx y rechazos 429.
- **Latencia:** p50, p95 y p99 por servicio y las 10 rutas más lentas.
- **Eventos:** mensajes en cola y en DLQ por servicio; eventos pendientes de publicar en las outbox.
- Selector de rango (1 hora, 24 horas, 7 días) y actualización automática cada 30 segundos.

Los datos los entrega una ruta solo para `ADMIN` del gateway (`/api/admin/observabilidad/...`) que
consulta a VictoriaMetrics con consultas fijas (el panel no manda consultas libres). Los gráficos se
dibujan con una librería liviana (uPlot, ~45 KB) o SVG propio.

### 11.3 Alertas (posterior)

Con `vmalert` (~20 MB) se pueden sumar alertas por mail o Telegram: servicio caído más de 2
minutos, DLQ con mensajes, latencia p95 alta, RAM del VPS por encima del 85 %. Queda para después de
la fase 1.

## 12. Tests

- **Unitarios** por servicio, como hoy.
- **Contratos de eventos** (`libs/eventos`): cada productor verifica que lo que publica respeta el
  contrato; cada consumidor, que procesa los ejemplos del contrato.
- **Integración** con Testcontainers (PostgreSQL y RabbitMQ): outbox, inbox, reintentos y DLQ.
- **Resiliencia:** tests que apagan un servicio o RabbitMQ en el entorno local y verifican la tabla
  de la sección 7.
- **Punta a punta:** el flujo completo de reserva y pago en el entorno local en cada PR que toque
  turnos o pagos.

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Complejidad operativa para un equipo de una persona | Migración por fases con vuelta atrás; observabilidad desde la fase 0; entorno local completo |
| CPU (2 núcleos) | Arranque escalonado, CDS, JVM afinada, nativo para los servicios chicos si hace falta |
| Consistencia eventual (un dato tarda en verse en otro servicio) | Sagas con compensaciones; la interfaz muestra estados intermedios ("Confirmando pago…") |
| El gateway como punto único de falla | `restart: always`, healthcheck, arranque rápido; se puede correr una segunda instancia si sobra RAM |
| Migración de datos de `usuario` (tabla compartida) | Scripts idempotentes, comparación de datos, ventanas de mantenimiento cortas |
| Dinero (pagos y reembolsos) | Idempotencia de punta a punta, reconciliación con Mercado Pago, fase dedicada |
