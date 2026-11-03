# Spec: Fase 1 — Servicio de notificaciones

- **Fecha:** 2026-10-20
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** implementada y verificada en local (2026-10-20); sin deploy
- **Rama / PR:** una rama y un PR a `develop` por tarea (N1 a N7)
- **Plan:** [microservicios.md, Fase 1](../arquitectura/microservicios.md#fase-1-notificaciones)

## Entendimiento del problema

Hoy el monolito manda los mails (`ResendEmailService`, 848 líneas, 26 tipos de mail llamados desde 13
clases) y crea las notificaciones dentro de la app (`NotificacionService`, 16 llamadas desde 4
servicios), que empuja por WebSocket a `/topic/notificaciones/{usuarioId}` sobre el endpoint
`/ws-tranqui`, el mismo que usa el chat.

La Fase 1 saca todo eso a un servicio `notificaciones` que **solo reacciona a eventos**. Lo que
encontré al leer el código y que el plan no resolvía:

1. **Los eventos llevan ids, los mails necesitan datos.** `TurnoCanceladoData` tiene `pacienteId` y
   `profesionalId`, pero el mail necesita nombre, mail, fecha, hora y modalidad. Hace falta (a) una
   copia local de contactos en el servicio y (b) campos opcionales nuevos en algunos `data` (el plan
   lo permite sin cambiar de versión).
2. **Hay mails y notificaciones sin evento en el catálogo:** asistencia confirmada, documento
   enviado al paciente, receta emitida, ticket creado/respondido a admins, avisos de suscripción con
   montos, contraseña temporal, solicitud de copia de datos, documento adjunto, alerta al admin y
   novedades.
3. **Algunos no deberían viajar en un evento:** la contraseña temporal (regla 5.3: nada de
   contraseñas en eventos), el documento adjunto (es un archivo, puede ser clínico) y las novedades
   (mail masivo atado al flujo de mantenimiento).
4. **Preferencia del usuario:** `Usuario.notificacionesEmailHabilitadas` decide si se manda el mail.
   El servicio tiene que conocerla (va en la copia de contactos).
5. **No existe infraestructura de consumo:** T2 declaró solo los 3 exchanges; no hay colas por
   servicio, colas de reintento, DLQ, consumidor con ack manual ni inbox reutilizable (el
   `InboxHelper` vive dentro del monolito).
6. **WebSocket compartido:** las notificaciones en vivo usan `/ws-tranqui`, que en la Fase 2 pasa al
   servicio de chat.

## Solución propuesta

### N1 — Consumo de eventos reutilizable (`libs/eventos`)
- `ConsumidorEventos`: listener con ack manual y `prefetch` 10, inbox (`eventos_procesados`) en la
  misma transacción que el handler, descarte por `aggregateVersion` viejo, reintentos 10 s → 1 min →
  5 min por colas con TTL y DLQ al final; evento mal formado va directo a la DLQ (sección 5.1).
- Declaración idempotente al arrancar de `<servicio>.eventos`, `<servicio>.reintento.{10s,1m,5m}`,
  `<servicio>.dlq` y sus enlaces, a partir de la lista de tópicos que el servicio declara.
- `InboxHelper` y la migración del inbox pasan del monolito a la librería (el monolito la usa igual).
- Tests de integración con Testcontainers (PostgreSQL y RabbitMQ): duplicado, reintento, DLQ,
  versión vieja.

### N2 — Catálogo de eventos ampliado (`libs/eventos` + monolito)
- **Campos opcionales nuevos** (sin cambiar de versión) en `TurnoReservado`, `TurnoConfirmado`,
  `TurnoCancelado`, `TurnoReprogramado`, `TurnoRecordatorio`, `SuscripcionCambioEstado`,
  `UsuarioActualizado` (preferencia de mail) y `MeetCreado`: lo que cada mail muestra (fecha, hora,
  modalidad, monto, plan, link de Meet).
- **Tópicos nuevos:** `turnos.asistencia.confirmada.v1`, `turnos.documento.enviado.v1`,
  `profesionales.receta.emitida.v1`, `identidad.ticket.creado.v1`,
  `suscripciones.aviso.programado.v1` (preaviso de renovación, pago fallido, vencimiento manual y
  suspensión, con un `tipo`). Ninguno lleva datos clínicos: "tenés una receta nueva", no la receta.
- El monolito los publica por la outbox en los mismos puntos donde hoy manda el mail.

### N3 — Servicio `services/notificaciones`
- Spring Boot 3.5 / Java 17, mismo estilo que el monolito. Base propia `notificaciones` (usuario de
  base propio) en la misma instancia de PostgreSQL; Flyway; registro en Consul; métricas para
  VictoriaMetrics; JVM afinada (sección 9.2, `-XX:TieredStopAtLevel=1`), `mem_limit` 224 MB.
- **Copia de contactos** (`contacto`: id, mail, nombre, apellido, rol, mail habilitado), alimentada
  por `identidad.usuario.registrado/actualizado/eliminado`. Si arranca vacía, hace la carga inicial
  desde una ruta interna nueva del monolito (`/internal/monolito/contactos`, paginada, con
  `ServiceJwt`, a través del gateway).
- **Mails:** las plantillas de `ResendEmailService` se mudan al servicio (mismo HTML). Envío con
  Resend usando `Idempotency-Key` = `eventId` + plantilla; en local sigue yendo a Mailpit.
- **Notificaciones en la app:** tabla `notificacion`, rutas `GET /api/notificaciones` y
  `POST /api/notificaciones/marcar-leidas` (mismas URLs que hoy), limpieza de las de más de 24 h.
- **En vivo:** endpoint STOMP propio `/ws-notificaciones` con el mismo control de JWT que el
  monolito; el frontend se suscribe ahí a `/topic/notificaciones/{id}`. El chat sigue en `/ws-tranqui`.

### N4 — Monolito: dejar de mandar
- Flag `notificaciones.envio-directo` (default `false`): en `false` el monolito solo publica eventos;
  en `true` vuelve a mandar mails y crear notificaciones como hoy (vuelta atrás del plan).
- Quedan en el monolito, **con envío directo, a propósito**: contraseña temporal, solicitud de copia
  de datos, documento adjunto, alerta al admin y novedades (ver Preguntas, punto 1). Se mudan con su
  dominio en fases siguientes.
- `NotificationScheduler`: el recordatorio de las 9 publica `turnos.recordatorio.programado.v1`; la
  limpieza pasa al servicio.

### N5 — Gateway y frontend
- Gateway: `/api/notificaciones/**` y `/ws-notificaciones/**` → `lb://notificaciones`, ruta interna
  `/internal/monolito/**` → monolito. Vuelta atrás: la ruta vuelve al monolito.
- Frontend (`App.tsx`, `LandingPage.tsx`): suscripción de notificaciones en `/ws-notificaciones`.

### N6 — Infraestructura local y datos
- `infra/postgres/`: script de creación de bases y usuarios por servicio (idempotente).
- `docker-compose.local.yml`: servicio `notificaciones` con healthcheck y arranque escalonado;
  `scrape.yml` lo incluye. **Producción (`docker-compose.yml`) se prepara pero no se despliega.**
- Script idempotente que copia contactos y notificaciones vigentes del monolito al servicio, con
  comparación de cantidades.

### N7 — Verificación de punta a punta en local
Reserva + pago + cancelación + reprogramación + ticket + receta en el navegador, mirando Mailpit y
la campanita; servicio apagado y vuelto a prender; DLQ forzada.

## Criterios de aceptación

- [ ] Cada mail que hoy sale por evento sale **una sola vez** desde el servicio (Mailpit), con el
      mismo contenido que antes, y no sale desde el monolito.
- [ ] Un usuario con mails deshabilitados no recibe mails (sí notificaciones en la app).
- [ ] La campanita recibe en vivo por `/ws-notificaciones` y lista/marca leídas por el gateway.
- [ ] Con el servicio apagado, los eventos se acumulan en `notificaciones.eventos`; al prenderlo se
      procesan sin duplicados.
- [ ] Un handler que falla pasa por 10 s, 1 min y 5 min y termina en `notificaciones.dlq`, visible
      en el panel de Observabilidad; un evento mal formado va directo a la DLQ.
- [ ] Con `notificaciones.envio-directo=true` y la ruta al monolito, todo vuelve a funcionar como hoy.
- [ ] Ningún evento lleva contraseñas, tokens ni datos clínicos (test de contrato).
- [ ] `./mvnw test` y los tests del frontend en verde; el servicio aparece en Consul y en el panel.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada; el compose de producción queda preparado.
- **Mails perdidos o duplicados** si el inbox o el `Idempotency-Key` fallan: cubierto por tests de
  integración y por la verificación con el servicio reiniciándose.
- **Consistencia eventual:** un usuario recién registrado puede tardar en estar en la copia; el
  handler reintenta (por eso los reintentos) en vez de descartar.
- **Memoria local:** suma una JVM de 224 MB y la base nueva.

## Decisiones (respuestas del 2026-10-20)

1. Los 5 mails sin evento (contraseña temporal, solicitud de copia de datos, documento adjunto,
   alerta al admin y novedades) quedan con envío directo en el monolito hasta la fase de su dominio.
2. Los servicios nuevos usan Java 17, igual que el resto; el paso a Java 21 se hace después, para todo junto.
3. Un PR a `develop` por tarea. Sin deploy ni release: la Fase 1 se verifica en local.

## Resultado (completar al cerrar)

### N1 — consumo de eventos (PR #19)
Implementado como en la spec. Consumidor, topología, reintentos, DLQ, inbox y descarte por versión
en `libs/eventos` (paquete `consumo`), con tests de integración contra PostgreSQL y RabbitMQ reales.

### N2 — catálogo ampliado
Desvíos respecto de la spec, encontrados al cablear cada punto del monolito:

- **Receta emitida queda con envío directo** (no se crea `profesionales.receta.emitida.v1`): el mail
  lleva el link al PDF de la receta y el número de recetario, que son datos de salud (sección 5.3).
  Se suma a los envíos directos que quedan en el monolito. Hoy no sale nunca: QBI2 está deshabilitado.
- **Enlaces de "confirmar asistencia":** llevan un token de un solo uso, que no viaja en
  `turnos.recordatorio.programado.v1`. Notificaciones los pide por una ruta interna de consulta del
  monolito (`/internal/monolito/turnos/{id}/enlaces-asistencia`, en N4).
- **Dos tópicos más de los previstos:** `identidad.ticket.mensaje-recibido.v1` (el usuario responde
  en su ticket; aviso a los admins) y `profesionales.historia-clinica.actualizada.v1` (avisos en la
  app de seguimientos e informes, sin contenido clínico).
- **`origen` en `TurnoConfirmado`:** el pago aprobado y la validación de OSDE mandan avisos
  distintos (OSDE: solo el mail al paciente, sin monto); el campo permite replicarlo exacto.
- **Eventos que antes no se publicaban:** el cambio de la preferencia de mails y los cambios de rol
  desde el panel ahora publican `identidad.usuario.actualizado.v1`, porque la copia de contactos de
  notificaciones los necesita.

### N3 — servicio de notificaciones
- Módulo `services/notificaciones`. Un handler (consumidor) por cada mail o aviso: si uno falla, el
  reintento no repite los que ya salieron (el inbox los reconoce por consumidor).
- **Mejora en `libs/eventos` encontrada al probar:** el consumidor corría los handlers de un evento
  en secuencia y cortaba en el primero que fallaba, así que un mail caído demoraba 10 s los demás
  avisos. Ahora corre todos y recién después decide si reintenta.
- **Avisos en vivo que siguen en el monolito:** "nuevo mensaje" del chat y la aprobación de un pago
  de concepto no se guardan como notificación; solo se empujan por `/ws-tranqui`. El frontend
  mantiene esa suscripción y suma `/ws-notificaciones` (N5).
- El listado `GET /api/notificaciones` conserva los roles de hoy (paciente y profesional): los
  administradores reciben sus avisos de tickets solo en vivo, como en el monolito.

### N4 — el monolito deja de mandar
- El flag se chequea en un solo lugar: dentro de `ResendEmailService` (los 18 mails con evento) y en
  `NotificacionService.crearNotificacion`. Los llamadores no cambian: con el flag en `false` la
  llamada no hace nada y el evento, que ya se publica desde N2, lo toma el servicio.
- Rutas internas nuevas del monolito: `/internal/monolito/contactos` y
  `/internal/monolito/turnos/{id}/enlaces-asistencia`, con JWT de servicio (segunda barrera detrás
  del gateway) y fuera del gate de contraseña del sitio.
- **Vuelta atrás:** `NOTIFICACIONES_ENVIO_DIRECTO=true` en el monolito y la ruta del gateway de
  vuelta al monolito (N5).

### N5 — gateway y frontend
- La URI del servicio sale de `TRANQUI_SERVICES_NOTIFICACIONES_URI`; por defecto el gateway sigue
  derivando al monolito y el compose la pone en `lb://notificaciones`. Vuelta atrás: sacar la variable.
- El frontend se conecta a los dos endpoints con el mismo canal, porque los avisos de chat y de
  pagos de concepto siguen saliendo del monolito por `/ws-tranqui` (ver N3).

### N6 — infraestructura local y datos
- **Bases por servicio:** en vez de un script de inicialización de PostgreSQL (que solo corre con
  un volumen nuevo), un contenedor de una sola ejecución `db-servicios`, idempotente, que corre en
  cada arranque. Así funciona con el volumen de producción que ya existe.
- **Copia de datos:** la carga inicial del servicio copia los contactos y las notificaciones de las
  últimas 24 h por la ruta interna; no hace falta correr un script a mano. La comparación de
  cantidades es `scripts/fase1-comparar-notificaciones.mjs` (solo lee).
- `docker-compose.yml` queda preparado; no se desplegó nada.

### N7 — verificación de punta a punta en local (2026-10-20)
Stack completo (`docker compose -p tranqui-local`), frontend en :5173 por el gateway en :8080.

| Prueba | Resultado |
|---|---|
| Arranque | El servicio arranca en ~22 s, declara su cola con 17 tópicos, carga 27 contactos por la ruta interna y queda `passing` en Consul |
| Reserva y pago simulado | Un mail al profesional y uno al paciente, mandados por el servicio (clave `eventId:consumidor`); el monolito no mandó ninguno. Aviso en la campanita de los dos |
| Servicio caído | Reprogramación y cancelación con el servicio apagado: 2 eventos esperan en `notificaciones.eventos`, ningún mail. Al prenderlo, cada mail sale una sola vez |
| En vivo | Con el panel de la profesional abierto, la reserva llega por `/ws-notificaciones` sin recargar: se abre la alerta de nuevo paciente y la campanita lo muestra |
| Ticket | Aviso a los dos administradores desde el servicio; el monolito no creó notificaciones |
| Registro | Un solo mail con el código de verificación; el usuario nuevo entra a la copia de contactos |
| Evento mal formado | Directo a `notificaciones.dlq` con el error y 0 intentos; el panel de Observabilidad la muestra |
| Vuelta atrás | Con `NOTIFICACIONES_ENVIO_DIRECTO=true` y la ruta al monolito, los mails vuelven a salir del monolito |

**Problemas encontrados y corregidos en N7:**
- **Consul se quedaba sin memoria** (60 de 64 MB, CPU al 375 %, sin responder) al registrar el gateway
  y el servicio, y su healthcheck lo marcaba `unhealthy` antes de terminar de arrancar: 128 MB y 30 s
  de gracia en los dos compose.
- **La DLQ no se veía en el panel** (bug de la Fase 0): el plugin de Prometheus de RabbitMQ sumaba
  todas las colas en una serie sin la etiqueta `queue`. Se activa
  `prometheus.return_per_object_metrics` en `infra/rabbitmq/rabbitmq.conf`.
- **La vuelta atrás duplicaba mails** si el servicio seguía prendido: el procedimiento ahora incluye
  frenarlo y, al volver al servicio, vaciar la cola con los eventos que ya atendió el monolito.
- **El script de comparación contaba mal las notificaciones**: las fechas se guardan en hora de
  Argentina sin zona y `now()` de la base es UTC.

**No probado en vivo:** el mail de confirmación de asistencia (lo dispara el scheduler de las 9);
están probados por separado el handler, los textos y la ruta interna de enlaces.

**Memoria medida en local (a observar antes de producción):** después de la prueba, el servicio usa
~207 de sus 224 MB (sin reinicios ni OOM) y Consul ~93 de 128 MB. El servicio entra en el límite de
la sección 9.1, pero con poco margen: si en producción se acerca más, las opciones son bajar
`MaxRAMPercentage`, sumar CDS (sección 9.2) o subir el límite, que cambia el presupuesto de las 8
fases.
