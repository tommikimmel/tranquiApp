# Spec: Fase 3 — Servicio de calendario (Google Calendar y Meet)

- **Fecha:** 2026-10-20
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** aprobada (2026-10-20)
- **Rama / PR:** una rama y un PR a `develop` por tarea (G1 a G6), encadenadas sobre la Fase 2
- **Plan:** [microservicios.md, Fase 3](../arquitectura/microservicios.md#fase-3-calendario)

## Entendimiento del problema

La integración con Google Calendar vive en el monolito, repartida en 6 clases (`GoogleCalendarOAuthService`,
`GoogleCalendarService`, `GoogleCalendarSyncService`, `GoogleCalendarWatchService` y dos schedulers),
7 rutas en `MedicoController` (`/api/medicos/google-calendar/**`), 11 columnas de `usuario` (tokens
cifrados, sync token, canal de webhook) y la tabla `google_calendar_evento_externo`. La Fase 3 la pasa a
un servicio `calendario` con base propia, sin que el frontend ni la configuración de Google cambien.

Lo que encontré en el código y el plan no resolvía:

1. **La confirmación crea el evento en el momento.** `TurnoService` (OSDE) y `PagoWebhookHandler`
   (pago aprobado) llaman a Google dentro de la confirmación y el mail al paciente ya sale con el link de
   Meet. Con el servicio, el Meet llega después por `calendario.meet.creado` (sección 6.5): el mail de
   confirmación de un turno online sale sin link y el link llega en un segundo mail, que ya existe
   (`mailLinkVideollamada` en notificaciones, el mismo que se usa hoy cuando Google falla). Ver Preguntas.
2. **La disponibilidad lee Google en vivo.** `TurnoService` consulta los eventos del día del
   profesional (`obtenerEventosDelDia`, con caché de 60 s) para no ofrecer horarios ocupados en su
   Google. Eso queda en el monolito hasta la Fase 6: pasa a ser una consulta interna al calendario, que
   responde desde su copia sincronizada (sección 4.2: las llamadas internas son para consultas).
3. **El monolito tiene que guardar el link.** El turno guarda `telemedicinaUrl` (lo lee el paciente, los
   recordatorios y la sala). El monolito todavía no consume eventos: es el primer consumidor del
   monolito, con la librería de la Fase 1 y la tabla de inbox que ya tiene.
4. **Los canales de webhook ya registrados en Google** apuntan a
   `https://tranquisalud.com/api/medicos/google-calendar/webhook` y llevan un token firmado con la
   `ENCRYPTION_KEY`; el OAuth vuelve a `/api/medicos/google-calendar/callback`. Si el gateway deriva esas
   URLs al servicio y el servicio usa la misma clave, siguen andando sin tocar Google ni reconectar a
   nadie. Los tokens guardados también están cifrados con esa clave.
5. **Hueco de las Fases 1 y 2 (no es de calendario, pero aparece acá).** El monolito tiene dos filtros
   que los servicios no tienen: el portón de contraseña del sitio (`SiteAccessFilter`, activo si
   `SITE_ACCESS_PASSWORD` tiene valor) y el paywall de profesionales sin suscripción
   (`SubscriptionAccessFilter`, 403 `SUBSCRIPTION_REQUIRED`). Hoy un psiquiatra sin suscripción, que en
   el monolito tenía bloqueado el chat y las notificaciones, los usa igual en los servicios; y con el
   calendario pasaría lo mismo con su panel de Google. Ver Preguntas.

## Solución propuesta

### G1 — Portones en el gateway
- Filtro global del gateway con el portón del sitio (misma cookie `SITE-ACCESS`, mismas excepciones:
  webhooks, callbacks, health, `/internal`) y el paywall para el rol `PSIQUIATRA` con las mismas
  excepciones que hoy. Solo actúa sobre las rutas que van a servicios; las del monolito siguen con sus
  filtros, así no se chequea dos veces.
- El estado de la suscripción se consulta al monolito (`/internal/monolito/suscripciones/{id}/acceso`)
  con caché de 60 s. Si el monolito no responde, deja pasar: el paywall es comercial, no de seguridad, y
  un monolito caído no debería cortar el chat. En la Fase 7 la consulta pasa al servicio de suscripciones.

### G2 — Catálogo y monolito
- **Catálogo:** campos opcionales nuevos, sin cambiar de versión: `horaFin` en `TurnoConfirmadoData` y
  `horaFinNueva` en `TurnoReprogramadoData` (el evento de Google necesita el fin del turno).
- **Flag `calendario.externo`** (`CALENDARIO_EXTERNO`, `true` en los compose): con `true` el monolito
  no llama a Google al confirmar, cancelar ni reprogramar, apaga sus tres schedulers de Google y deja de
  publicar `calendario.*`. Con `false` (vuelta atrás) todo sigue como hoy.
- **Consumo de `calendario.meet.creado.v1`:** el monolito guarda `telemedicinaUrl` (y el id del evento)
  en el turno, idempotente y con el control de versión de la Fase 1.
- **Disponibilidad:** los horarios ocupados en Google se piden por
  `/internal/calendario/profesionales/{id}/ocupado?fecha=...` a través del gateway, con la misma caché de
  60 s; si el calendario no responde, se ofrece la agenda sin ese filtro (igual que hoy cuando Google
  falla). El monolito también responde esa ruta con sus datos, para la vuelta atrás.
- **Rutas internas para la carga inicial:** cuentas de Google conectadas (tokens tal cual están, cifrados,
  más sync token y canal de webhook), eventos externos, y `/turnos-confirmados` suma `horaFin`,
  modalidad, si ocupa agenda, id del evento y link.

### G3 — Servicio `services/calendario`
- Spring Boot 3.5 / Java 17, base propia `calendario`, Flyway, Consul, métricas, outbox y consumo de
  `libs/eventos`, 288 MB con la JVM de la Fase 2.
- **Datos:** `cuenta_google` (tokens cifrados, vencimiento, calendario, sync token, canal de webhook),
  `evento_externo` (la caché de hoy), `turno_agendado` (copia de los turnos confirmados futuros con su
  evento de Google y su link) y `participante` (nombre del paciente para el título del evento, como el chat).
- **Mismas rutas y formato:** `GET /status`, `GET /connect`, `POST /connect-simulado`, `POST /disconnect`,
  `GET /eventos`, `GET /callback` y `POST /webhook` bajo `/api/medicos/google-calendar`, con los mismos
  roles, redirecciones y JSON.
- **Eventos que consume:** `turnos.turno.confirmado` (crea el evento y el Meet), `cancelado` (lo borra),
  `reprogramado` (lo mueve) y los de identidad (nombre del paciente; al eliminarse la cuenta de un
  profesional, corta el canal y borra sus tokens).
- **Eventos que publica:** `calendario.meet.creado.v1` (con `linkNuevo`, que dispara el mail del link) y
  `calendario.horario.bloqueado.v1` (como hoy; sin consumidores hasta la Fase 6).
- **Lo mismo que hoy:** sincronización incremental cada 5 minutos y por webhook, renovación diaria de
  canales, reintento cada 5 minutos de los turnos que quedaron sin evento, borrado de los eventos de la
  app al desvincular. En local (`google.calendar.enabled=false`) genera el Meet simulado como hoy.
- **Clave de cifrado:** la misma `ENCRYPTION_KEY` del monolito (ver Preguntas).
- **Carga inicial** desde las rutas internas del monolito, conservando los tokens cifrados.

### G4 — Gateway
- `/api/medicos/google-calendar/**` y `/internal/calendario/**` van al destino de
  `TRANQUI_SERVICES_CALENDARIO_URI` (`lb://calendario` en el compose), antes de la regla general de
  `/api/medicos/**`, que sigue en el monolito. El callback y el webhook pasan sin sesión de usuario.
- Vuelta atrás: sacar la variable y poner `CALENDARIO_EXTERNO=false` en el monolito.

### G5 — Infraestructura
- `db-servicios` crea la base `calendario`; servicio en los dos compose (288 MB, después del chat);
  variables `CALENDARIO_DB_PASSWORD` y las de Google (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
  `GOOGLE_CALENDAR_ENABLED`, `GOOGLE_CALENDAR_WATCH_ENABLED`) pasan al servicio; imagen en CI y deploy;
  métricas; docs. Presupuesto de Compose: 2.688 + 288 = 2.976 MB. **Producción se prepara pero no se
  despliega.**
- Script de vuelta atrás `scripts/fase3-devolver-calendario.mjs`: copia al monolito las cuentas de
  Google (tokens que el servicio renovó, conexiones y desconexiones nuevas) y los ids de evento de los
  turnos; idempotente y con `--prueba`, como el de la Fase 2.

### G6 — Verificación de punta a punta en local
Conexión simulada, turno online pagado (evento y Meet por eventos, link en el turno y mail del link),
turno presencial, cancelación y reprogramación, disponibilidad con un horario ocupado en Google,
desconexión, el calendario caído (la reserva y la confirmación siguen; el link llega al volver),
el paywall y el portón, y la vuelta atrás. Google real se prueba al final, cuando haya credenciales
de prueba (decisión 4).

## Criterios de aceptación

- [ ] Las 7 rutas de `/api/medicos/google-calendar` responden igual desde el servicio; el frontend no cambia.
- [ ] Un turno online confirmado (pago u OSDE) termina con su evento en Google y el link en el turno y
      en el mail del paciente, sin duplicados.
- [ ] Cancelar o reprogramar borra o mueve el evento; desvincular borra los de la app.
- [ ] La disponibilidad no ofrece horarios ocupados en el Google del profesional, y sale igual si el
      calendario está caído.
- [ ] Con el calendario caído, confirmar un turno no falla; al volver, crea el evento y manda el link.
- [ ] Las cuentas ya conectadas siguen conectadas después de la carga inicial, sin volver a autorizar,
      y los canales de webhook existentes siguen llegando.
- [ ] Ningún evento lleva tokens de Google.
- [ ] Con la vuelta atrás (variable del gateway + flag), el calendario funciona como hoy.
- [ ] Un psiquiatra sin suscripción recibe 403 `SUBSCRIPTION_REQUIRED` en las rutas de los
      servicios, y el portón del sitio también las cubre.
- [ ] `./mvnw test`, tests del frontend y lint sin errores nuevos.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **Tokens de Google:** se copian cifrados, nunca se descifran para moverlos, y no salen en eventos ni
  en logs. La ruta interna que los devuelve solo acepta JWT de servicio.
- **Cambio de comportamiento:** los turnos online con Google conectado reciben dos mails en vez de uno
  (pregunta 1), y la disponibilidad lee la copia del calendario (al día por webhook o, como mucho, cada
  5 minutos) en vez de Google con 60 s de caché.
- **Corte:** el flag del monolito y la ruta del gateway van juntos; si solo cambia uno, el evento se
  crea dos veces o ninguna. El deploy de la fase lo tiene que hacer en un solo paso.
- **Memoria:** otra JVM de 288 MB; el total de Compose queda en ~2,98 GB.

## Decisiones (respuestas del 2026-10-20)

1. **Mail de confirmación de turnos online:** dos mails. La confirmación sale sin link (avisando que
   llega en un mail aparte) y el link llega en el mail que ya existe, cuando el calendario crea el Meet.
2. **Paywall y portón en los servicios:** se suma G1 a esta fase; arregla también chat y notificaciones.
3. **Clave de cifrado:** el servicio usa la misma `ENCRYPTION_KEY` del monolito (los tokens guardados y
   los tokens de los canales de webhook ya registrados en Google están hechos con ella). Separar una
   clave propia queda para una tarea aparte: volver a cifrar los tokens dentro del servicio y renovar
   los canales (se renuevan solos cada 6 días).
4. **Google real en local:** no hay credenciales de prueba todavía. La verificación usa el modo
   simulado y tests con la API de Google simulada; Google real se prueba al final, cuando el usuario
   cree un cliente OAuth de prueba.
5. Un PR a `develop` por tarea, Java 17, sin deploy (como en las fases anteriores).

## Resultado (completar al cerrar)

### G1 — portones en el gateway
- `PortonesGlobalFilter` decide por el destino de la ruta: si va al monolito no hace nada (ahí actúan
  sus filtros); así, en la vuelta atrás de una fase el gateway deja de aplicarlos sin tocar nada.
- El JWT de usuario trae el mail como `sub` y el rol en `rol`, no el id: la ruta interna nueva es
  `/internal/monolito/suscripciones/acceso?email=` (un mail que no existe responde `permitido: true`;
  la autenticación la resuelve el servicio).
- **Encontrado al revisar el borde:** la regla de Traefik de producción solo derivaba `/api` y
  `/ws-tranqui`, así que el WebSocket `/ws-notificaciones` de la Fase 1 habría ido al frontend. Suma
  `/ws-notificaciones` (en local no se veía porque Vite deriva todo al gateway).

### G2 — catálogo y monolito
- **Disponibilidad:** `HorariosOcupadosCliente` decide con el mismo flag: con `calendario.externo` pide al
  calendario por el gateway; sin el flag lee Google como antes. Por eso el monolito no necesita responder
  `/internal/calendario/...` para la vuelta atrás (la spec lo proponía): en la vuelta atrás el flag va en
  `false` y no se hace la consulta. Los intervalos traen "todo el día" y los eventos "disponible" de
  Google ya no llegan.
- **Mail de confirmación:** se deja el texto actual para los turnos online sin link ("El link de la
  videollamada va a estar en Mis Turnos"). Prometer "un mail aparte" sería falso para los profesionales
  sin Google conectado, que nunca generan el segundo mail.
- **Consumo en el monolito:** se prende solo con `EVENTOS_CONSUMO_SERVICIO=monolito`; una propiedad vacía
  en `application.yml` activaría el consumidor sin nombre de cola. El inbox usa la tabla
  `eventos_procesados` que el monolito ya tenía (misma estructura que la librería).
- **Hallazgo (producción, sin tocar):** el monolito no lee la `ENCRYPTION_KEY` del `.env`. Sus cuatro usos
  (`EncryptionUtil`, el `state` del OAuth de Google y de Mercado Pago, y el token de los canales de Google)
  leen `security.encryption.key`, que ningún `application.yml` mapea, así que en producción usan la clave de
  respaldo escrita en el código, pública porque el repo es público. Afecta los tokens de Mercado Pago y de
  Google guardados. Arreglarlo exige volver a cifrar esos tokens en la base de producción: queda para
  una decisión del usuario, junto con el hotfix del `JWT_SECRET`. Mientras tanto, el servicio de calendario
  resuelve la clave exactamente igual que el monolito (decisión 3), así lee los tokens existentes y el día
  que se corrija cambia en los dos a la vez.
