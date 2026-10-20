# Changelog

Todos los cambios relevantes de Tranqui App. Formato basado en
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versiones
[SemVer](https://semver.org/lang/es/) (ver `docs/flujo/git.md`).

## [Sin publicar]

### Infraestructura y arquitectura

- **Maven multimódulo:** agregador raíz `tranqui-parent` con gestión centralizada de dependencias (BOM de Spring Boot 3.5.15) y Maven wrapper en la raíz.
- **Librería común (`libs/comun`):** filtro servlet para `X-Correlation-Id` con propagación a MDC y cabeceras HTTP, formato estándar de error JSON (`ErrorResponse`) y autoconfiguración Spring Boot.
- **Librería de eventos (`libs/eventos`):** sobre común `EventEnvelope<T>`, catálogo de tópicos tipado (`Topicos`), 25 records de payload para los eventos de la sección 5.4, constantes de topología RabbitMQ (`tranqui.eventos`, `tranqui.reintentos`, `tranqui.dlx`), y componente `ServiceJwt` para autenticación HMAC de llamadas internas (sección 4.2).
- **Backend y Docker:** dependencias internas agregadas en `backend/pom.xml`, `backend/Dockerfile` adaptado para compilar desde la raíz del repositorio (`-pl backend -am`), Compose local y producción actualizados, y workflows de CI/deploy adaptados al multimodulo.
- **Infraestructura local de microservicios (T2):** servicios RabbitMQ 3.13 (`rabbitmq:3.13-management-alpine`), Consul 1.19 (`hashicorp/consul:1.19`) y Redis 7 (`redis:7-alpine`) en `docker-compose.local.yml` con healthchecks, límites de memoria (256m RabbitMQ, 64m Consul, 64m Redis), configuración en `infra/rabbitmq/` y `infra/consul/`, verificación de puertos en `scripts/local.mjs` y test de contrato de topología en `libs/eventos`.
- **API Gateway (T3):** módulo `gateway` con Spring Cloud Gateway WebFlux (Spring Boot 3.5.15, Spring Cloud 2025.0.1), Java 17 y Dockerfile multicapa. Enrutamiento perimetral unificado (`/api/**`, `/ws-tranqui/**`), registro en Consul, rate limiting distribuido con Redis (límites tabla 4.3, respuesta 429 con `Retry-After`, política fail-open ante caídas de Redis), propagación de `X-Correlation-Id`, timeouts diferenciados, reintentos GET con backoff, Circuit Breakers Resilience4j con fallback 503 JSON, seguridad de endpoints `/internal/**` (bloqueo 404 a `X-Forwarded-*` y validación de `ServiceJwt`), healthcheck público y métricas Actuator en puerto interno 8082. Servicio configurado en `docker-compose.local.yml` en puerto 8080 con redirección de frontend.
- **Correcciones API Gateway (F3):** componente unificado `UserJwtVerifier` para extracción y validación criptográfica de tokens de usuario (cookie `SESSION-TOKEN` o `Authorization: Bearer`), resolución de IP real con `RealIpResolver` y lista CIDR `tranqui.gateway.trusted-proxies` para rate limiting correcto tras proxies confiables (Traefik) con descarte de `X-Forwarded-For` no confiable, eliminación de defaults hardcodeados de `jwt.secret` en código de gateway y backend monolito con fallo explícito al arrancar si falta `JWT_SECRET`, y alineación de `JWT_SECRET` en `docker-compose.local.yml`.
- **Outbox, inbox y relay a RabbitMQ (T4):** migración Flyway `V3__outbox_e_inbox.sql` con tablas `outbox` e `eventos_procesados`, índice parcial `outbox_pendientes` (`WHERE published_at IS NULL`) y trigger `trg_outbox_insert` con `pg_notify('outbox', id)`. Publicador transaccional `OutboxPublisher` con `Propagation.MANDATORY`, `EventEnvelope` con `correlationId` desde MDC (`libs/comun`) e inserción a outbox. Relay reactivo `OutboxRelay` con escucha `LISTEN outbox` (conexión JDBC dedicada `PgConnection` con reconexión), barrido de respaldo cada 30 segundos, lotes de 100 con `SELECT ... FOR UPDATE SKIP LOCKED`, publisher confirms y flag mandatory hacia exchange `tranqui.eventos` con routing key del tópico, marcado de `published_at` solo ante confirm del broker, y reintentos con backoff. Helper de idempotencia `InboxHelper` (`INSERT ... ON CONFLICT DO NOTHING`), limpieza programada `OutboxCleanerScheduler` (> 7 días outbox, > 30 días inbox), métricas Micrometer (`tranqui.outbox.pendientes`, `tranqui.outbox.antiguedad.segundos`), flag `eventos.relay.enabled` y configuración en `docker-compose.local.yml`.
- **Correcciones Outbox y Relay (F4):** inspección de `correlationData.getReturned()` en `OutboxRelay` ante mensajes sin cola enlazada (`basic.return`), marcando `published_at` sin bloquear la outbox, emitiendo warning con tópico y `eventId`, e incrementando contador Micrometer `outbox.eventos.sin_ruta` con tag `topico`; conexión JDBC dedicada para `LISTEN outbox` fuera del pool de Hikari (`PGSimpleDataSource`/`DriverManager`), evitando retener una conexión del pool de la aplicación.
- **Publicación de eventos de dominio desde el monolito (T5):** integración del publicador transaccional `OutboxPublisher` en todos los servicios y controladores del monolito cubriendo los 25 tópicos del catálogo de la sección 5.4 (`identidad.*`, `profesionales.*`, `turnos.*`, `pagos.*`, `suscripciones.*`, `calendario.*`, `chat.*`). Migración Flyway `V4__columna_version_en_entidades.sql` con columna `version` e inicialización en modelos (`Usuario`, `Turno`, `Pago`, `Subscription`, `Ticket`, `Mensaje`, `TarifaMedico`, `Disponibilidad`, `GoogleCalendarEventoExterno`) para soporte de `aggregateVersion`. Tests unitarios y de integración (`EventosPublicacionTest`, `EventosCatalogoTest`) verificando cobertura completa del catálogo, estructura de payloads sin datos sensibles ni contraseñas, y verificación local del ciclo de reserva y pago (`turnos.turno.reservado.v1` -> `pagos.pago.aprobado.v1` -> `turnos.turno.confirmado.v1`) hacia RabbitMQ.
- **Correcciones de eventos del monolito (F5):** incremento consistente de `aggregateVersion` en las 9 entidades versionadas (`Usuario`, `Turno`, `Pago`, `Subscription`, `Ticket`, `Mensaje`, `TarifaMedico`, `Disponibilidad`, `GoogleCalendarEventoExterno`) mediante callbacks del ciclo de vida JPA (`@PrePersist` inicializa en 0 y `@PreUpdate` incrementa la versión) y ejecución explícita de flush en repositorios antes de la publicación a outbox, garantizando versiones monótonamente crecientes ($n$, $n+1$) para descarte de eventos obsoletos en consumidores; limpieza de estilo eliminando verificaciones de null e inyectando `OutboxPublisher` como dependencia estándar de Spring, normalizando importaciones e incorporando test de integración `EventosAggregateVersionTest`.
- **Observabilidad: métricas y panel de administrador (T6):** infraestructura de recolección de métricas con VictoriaMetrics single-node en local (`infra/victoriametrics/scrape.yml`, retención 15d, límite 96 MB, scrape 15s de Gateway, Backend, RabbitMQ, node-exporter y cAdvisor), métricas Prometheus y percentiles Micrometer en Gateway y Backend, contador de rechazos 429 (`gateway.requests.429`), endpoints administrativos seguros en el Gateway (`GET /api/admin/salud` y `GET /api/admin/observabilidad/{recursos|trafico|latencia|eventos}?rango=1h|24h|7d` con consultas PromQL fijas y validación de rol `ADMIN`), y panel interactivo de Observabilidad en el frontend con tarjetas de estado de servicios, gráficos vectoriales SVG de recursos (VPS/contenedores/disco), tráfico (req/min, 5xx, 429), latencia (p50/p95/p99, top 10 rutas lentas) y eventos (RabbitMQ, DLQ, outbox), selector de rango 1h/24h/7d y refresco automático cada 30s.
- **Correcciones de observabilidad (F6):** `AdminAuthValidator` usa `UserJwtVerifier` (sin clave propia ni default) y la clave de `ServiceJwt` del gateway ya no tiene default fuera del perfil `local`. CORS en el gateway con `globalcors` (clave nueva `spring.cloud.gateway.server.webflux`) aplicado también a sus controladores: sin esto el navegador no podía loguearse ni abrir el panel a través del gateway en local. Actuator del monolito en el puerto de management 8082 (fuera del puerto público, sin pasar por el gate del sitio) para que VictoriaMetrics lea `/actuator/prometheus`; chequeo de Consul del gateway en `/actuator/health`; `scrape.yml` sin `evaluation_interval` (VictoriaMetrics no arrancaba) y VictoriaMetrics local con 192 MB y `-memory.allowedPercent=30` (con 96 MB lo mataba el OOM).
- **Producción con Gateway e infraestructura (T7):** `docker-compose.yml` de producción actualizado con el API Gateway (`gateway`), RabbitMQ 3.13 con topología de eventos (`tranqui.eventos`, colas lazy), Consul 1.19, Redis 7, VictoriaMetrics, node-exporter y cAdvisor; límites de memoria y CPU según presupuesto de la sección 9 (total Compose ~1,95 GB, VPS ~3,1 GB, dentro del límite de 4,8 GB), arranque escalonado por healthchecks (`depends_on: condition: service_healthy`), aislamiento perimetral sin publicación de puertos internos, transferencia de labels de Traefik de `/api` y `/ws-tranqui` al Gateway, afinación de JVM de 9.2 en el monolito y Gateway (`JAVA_TOOL_OPTIONS`), variables documentadas en `.env.template`, workflows de CI y Deploy adaptados para construir y publicar la imagen del Gateway en GHCR (`tranqui-gateway`), script de deploy en VPS actualizado para pull del Gateway y sincronización de configuraciones de `infra/`, documentación completa en `docs/entornos/produccion.md` y procedimiento de vuelta atrás inmediata.
- **Correcciones de producción (F7):** `JWT_SECRET` obligatoria y la misma para `tranqui-backend` y `gateway` (`${JWT_SECRET:?Falta JWT_SECRET en .env}`, sin fallback a `ENCRYPTION_KEY`): antes el backend de producción no la recibía y firmaba los JWT con el default público. `.env.template` con placeholder evidente y cómo generarla (`openssl rand -base64 48`), y sección "Clave JWT" en `docs/entornos/produccion.md` (cargarla invalida las sesiones abiertas). node-exporter monta `/proc`, `/sys` y `/` del host para medir el VPS y no el contenedor. VictoriaMetrics de producción con 192 MB y `-memory.allowedPercent=30` (presupuesto de Compose ~2,05 GB). Verificado que `eclipse-temurin:17-jre` trae `curl` y `wget`, así que el healthcheck del backend no cambia.
- **Spec Fase 0:** documento `docs/specs/2026-10-14-fase-0-plataforma.md` resumiendo las 7 tareas de la plataforma de microservicios y `docs/arquitectura/gateway.md` con la arquitectura del API Gateway.
- **Fase 1 · consumo de eventos (N1):** `libs/eventos` incorpora el consumo reutilizable de eventos (paquete `consumo`): `ConsumidorEventos` con ack manual y prefetch 10, un registro de inbox por handler en la misma transacción, reintentos por `<servicio>.reintento.{10s,1m,5m}` (vuelven solo a la cola del servicio), DLQ `<servicio>.dlq` con el error y los intentos en encabezados, evento mal formado directo a la DLQ, descarte opcional por `aggregateVersion` (`versiones_aplicadas`) y métrica `eventos.consumidos`. `TopologiaConsumo` declara colas y enlaces al arrancar (idempotente) y el consumo empieza con la aplicación lista, esperando a RabbitMQ con espera creciente. Autoconfiguración activada por `eventos.consumo.servicio`; `InboxHelper` pasa del monolito a la librería. Tests de integración con Testcontainers (PostgreSQL y RabbitMQ). Spec: `docs/specs/2026-10-20-fase-1-notificaciones.md`.
- **Fase 1 · catálogo de eventos para notificaciones (N2):** campos opcionales nuevos (sin cambiar de versión) en `TurnoConfirmado` (agenda o documento, tipo, dirección, Meet y origen pago/OSDE), `TurnoCancelado` (fecha, hora y resultado del reembolso), `TurnoReprogramado`, `TurnoRecordatorio`, `UsuarioActualizado` (rol y preferencia de mails), `MeetCreado` (paciente, fecha y `linkNuevo`) y `CodigoVerificacionGenerado` (nombre); los records conservan el constructor viejo. Tópicos nuevos: `turnos.asistencia.confirmada.v1`, `turnos.documento.enviado.v1`, `identidad.ticket.creado.v1`, `identidad.ticket.mensaje-recibido.v1`, `profesionales.historia-clinica.actualizada.v1` y `suscripciones.aviso.programado.v1` (31 tópicos en total). El monolito los publica por la outbox en los mismos puntos donde manda cada mail o notificación (incluye la preferencia de mails y los cambios de rol, que antes no publicaban nada; `AvisoSuscripcionPublisher` para los 10 avisos de suscripción). Test de contrato que impide contraseñas, tokens o contenido clínico en cualquier `data`.


## [v1.1.0] - 2026-10-07

### CI y deploy

- CI en cada PR a `develop` y `main`: tests del backend, typecheck, tests y build del frontend, y lint
  incremental (solo falla si el PR agrega problemas).
- Deploy automático al crear el tag `vX.Y.Z` en `main`: imágenes en GHCR, el VPS solo las baja,
  verificación del sitio y la API, y vuelta automática a la versión anterior si falla.
- Las variables de producción viven en `/srv/tranqui/.env` del VPS; ningún deploy sube un `.env`.
- Deploy y scripts por clave SSH con verificación del servidor; sin contraseñas en el repo.
  `deploy.js` queda como deploy manual de emergencia.
- Botón "Mantenimiento" en GitHub Actions (el link de administrador no se muestra en los logs:
  `npm run mantenimiento -- acceso`).

### Mail de novedades

- Al apagar el mantenimiento se manda a todos los usuarios un mail con lo que cambió (Broadcasts de
  Resend: no consume el cupo de los mails de la app). Un solo texto dividido en "Para todos",
  "Pacientes" y "Profesionales"; vista previa, prueba solo para administradores e idempotencia.
- Link de baja en cada mail (Ley 25.326) e interruptor "Novedades de Tranqui" en Mi Cuenta y en
  Configuración del profesional. Al eliminar una cuenta se borra su contacto en Resend.
- Política de Privacidad actualizada.

### Modo mantenimiento

- `npm run mantenimiento -- on ["mensaje"] | off | estado`: mientras está activo, el sitio muestra
  solo una página de mantenimiento (503), aunque el backend esté apagado. El estado vive fuera de
  `/app`, así que un deploy no lo cambia.
- Acceso de administrador con un link de un solo uso por mantenimiento, para ver el sitio real
  mientras el resto ve el aviso.

### Entorno local

- `npm run local` levanta la app completa en local (`docker-compose.local.yml`): base propia,
  backend con perfil `local`, frontend con Vite y Mailpit para ver los mails. Antes de cada
  rebuild verifica los puertos y pregunta antes de frenar lo que los ocupe. También
  `local:down`, `local:reset` y `local:logs`.
- Perfil `local` del backend: Mercado Pago, Google Calendar, QBI2 y ARCA apagados o simulados;
  mails por SMTP a Mailpit (en producción se sigue usando Resend).
- Datos de demo realistas (`LocalDemoSeeder`): 5 profesionales de distinto tipo, 8 pacientes,
  ~45 turnos, pagos, chats, documentos, notificaciones y tickets. Solo con el perfil `local`.
- Login con Google oculto en local (`VITE_GOOGLE_LOGIN=false`) y vinculación simulada de Google
  Calendar (solo con el perfil `local`).

### Arquitectura

- Plan de migración a microservicios por fases (`docs/arquitectura/microservicios.md`) y ADR 009:
  8 servicios, API Gateway con rate limiting, healthcheck y service discovery (Consul), eventos por
  push con RabbitMQ, outbox, idempotencia, presupuesto de recursos para 8 GB y 2 núcleos, y
  observabilidad en el panel de administrador. La app sale a producción con el monolito.

### Documentación y flujo de trabajo

- `AGENTS.md` y `CLAUDE.md` con las reglas para agentes de IA: SDD obligatorio antes de tocar
  código, nada de commits directos a `main`/`develop`, no tocar producción sin aprobación, sin
  secretos en el repo, sin emojis en la UI.
- Carpeta `docs/`: flujo de Git (ramas `tipo/area-descripcion`, Conventional Commits en español,
  etiquetas, SemVer), SDD con plantilla de spec, releases, hotfix, sprints, entornos, secretos,
  centro de ayuda y bugs conocidos. Se migró el contenido de `.agent/` (ADRs, etapas, diseño de
  base, especificación del MVP, guías técnicas).
- Plantilla de Pull Request.
- Repositorio: quedan solo `main` y `develop`. Se borraron las ramas viejas ya integradas y la
  rama `whatsapp-bot` (bot de WhatsApp, implementación a futuro).

## [v1.0.0] - 2026-10-07

Primera versión numerada: lo que está en producción al 2026-10-07. Resume el desarrollo de junio a
octubre de 2026.

### Pacientes

- Registro con email y contraseña (verificación por código de 6 dígitos) o con Google;
  recuperación de contraseña.
- Buscador de profesionales con filtros por profesión, especialidad, modalidad y motivo de
  consulta, con próxima disponibilidad.
- Reserva de turnos (particular u obra social, online o presencial) con bloqueo del horario por 5
  minutos mientras se paga con Mercado Pago.
- Mis Turnos: pago pendiente, link de Google Meet, dirección con mapa y cancelación con reembolso
  automático si faltan más de 48 horas.
- Confirmación de asistencia desde el mail de recordatorio, sin iniciar sesión.
- Pedido de documentos (certificados, informes) pagos sin ocupar turno.
- Chat con el profesional, Mi Cuenta (datos, obra social, contraseña, notificaciones, copia de
  datos, eliminación de cuenta) y tickets de soporte.
- Centro de ayuda con 43 preguntas frecuentes por rol y 5 videos explicativos narrados y
  subtitulados (ver `docs/centro-de-ayuda.md`).

### Profesionales

- Registro con matrícula y datos fiscales, plan sugerido según la profesión
  (psicólogos/psiquiatras).
- Perfil público, agenda por modalidad con duración e intervalo configurables, notas y pendientes.
- Honorarios y servicios: consultas, tarifas por obra social, documentos, ajuste de precios.
- Inicio con próximo turno, Meet, ficha del turno, reprogramación y cancelación, documentos
  solicitados.
- Integraciones: Mercado Pago (cobros directos, sin comisión) y Google Calendar (sincronización en
  los dos sentidos).
- Suscripciones mensuales o anuales con débito automático de Mercado Pago, cancelación de la
  renovación con acceso hasta el fin del período y pantalla para reactivar.

### Administración

- Panel de administración: verificación de profesionales, suscripciones (pagos manuales,
  cortesías, cambios de estado) y tickets de soporte.

### Cambios de octubre de 2026 incluidos en esta versión

- Centro de ayuda con videos y guías; base de datos con migraciones Flyway; mejoras en
  suscripciones y deploy (`423ab07`).
- Mercado Pago sin modo sandbox para checkouts y suscripciones en producción (`50b1792`).
- Centro de ayuda reducido a los 5 videos más usados, en formato escritorio (`0d1ded1`).
- nginx sirve los subtítulos como `text/vtt` y responde 404 a videos inexistentes (`7d3c0ff`).
- La foto de perfil se reduce a 400 px JPEG antes de guardarse: la respuesta de `/api/medicos`
  bajó de ~506 KB a ~28 KB y la landing carga en menos de 1 s (`28cb217`). La foto que ya estaba
  guardada en producción se convirtió a mano.
- Encabezado público optimizado en celular y calendario al 100% en escritorio (`7de94ae`).
- `deploy.js` compatible con Windows (`3462fde`).
- Agenda responsive y submenú de Configuración unificado (`dcc658f`).
- Se eliminó la pantalla de acceso restringido al sitio (`e08435f`).
- Arranque del backend sin `LazyInitializationException` en el reconciliador de suscripciones
  (`06642fd`).
- Se eliminó WhatsApp (Twilio) de la app; los pacientes reciben por mail las confirmaciones,
  cancelaciones, reprogramaciones y recordatorios.
