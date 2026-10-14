# Spec: Fase 0 — Plataforma de microservicios

- **Fecha:** 2026-10-14
- **Tipo:** chore
- **Sprint:** sprint-02
- **Estado:** aprobada
- **Rama / PR:** `chore/infra-maven-multimodulo-libs`

## Entendimiento del problema

Tranqui App opera actualmente como una aplicación monolítica Spring Boot (`backend/`). Conforme a las decisiones del [ADR 009](../adr/009-arquitectura-microservicios.md) y el [Plan de migración a microservicios](../arquitectura/microservicios.md), el sistema migrará de forma progresiva mediante el patrón *strangler* hacia 8 microservicios (`identidad`, `profesionales`, `turnos`, `pagos`, `suscripciones`, `notificaciones`, `chat`, `calendario`).

Antes de extraer el primer microservicio (Fase 1: Notificaciones), es necesario establecer los cimientos comunes de la plataforma:
1. Un proyecto Maven multimódulo con versionado unificado y librerías transversales compartidas (`libs/comun` y `libs/eventos`).
2. La infraestructura de soporte para mensajería asincrónica (RabbitMQ), registro y descubrimiento de servicios (Consul), y control de tráfico/caché de rate limiting (Redis).
3. Un API Gateway (Spring Cloud Gateway) como único punto de entrada sincrónico tanto para peticiones públicas como para llamadas internas entre servicios.
4. Confiabilidad en la publicación y consumo de eventos (patrones Outbox con Relay e Inbox/Idempotencia).
5. Observabilidad de extremo a extremo (métricas en VictoriaMetrics, correlación con `X-Correlation-Id` y panel en el frontend de administración).

## Solución propuesta

La Fase 0 se estructura y ejecuta en 7 tareas delimitadas e incrementales:

### T1 — Maven multimódulo + librerías comunes (`libs/comun` y `libs/eventos`)
- POM raíz agregador (`com.tranqui:tranqui-parent:0.0.1-SNAPSHOT`, `pom`) gestionando dependencias con el BOM de Spring Boot 3.5.15 y JJWT.
- Módulo `libs/comun`: filtro servlet de correlación (`X-Correlation-Id`, propagación a MDC y respuesta HTTP), formato estándar de error JSON (`ErrorResponse`), y autoconfiguración Spring Boot.
- Módulo `libs/eventos`: sobre común `EventEnvelope<T>` (sección 5.3), catálogo tipado de tópicos y helper de nombrado `Topicos` (sección 5.4), records de payload de negocio `data` para los 25 tópicos (sin datos clínicos ni secretos), constantes de topología RabbitMQ (sección 5.1), y componente `ServiceJwt` para autenticación HMAC de llamadas internas (sección 4.2).
- Adaptación de `backend` para compilar dentro del reactor multimódulo, actualización de `backend/Dockerfile` con contexto en raíz (`-pl backend -am`), Compose local/prod y workflows de CI/Deploy.

### T2 — Infraestructura local (RabbitMQ, Consul y Redis)
- Incorporación a `docker-compose.local.yml` de los contenedores de soporte:
  - RabbitMQ 3.13 con plugin de administración y Prometheus habilitado (`tranqui.eventos`, `tranqui.reintentos`, `tranqui.dlx`).
  - Consul para service discovery y healthchecking.
  - Redis para almacenamiento de contadores de token bucket del rate limiter.
- Verificación de puertos y perfiles de arranque opcionales.

### T3 — API Gateway
- Proyecto `gateway/` basado en Spring Cloud Gateway y Java 17.
- Ruteo público (`/api/**` y `/ws-tranqui/**` hacia el monolito mientras dura la transición).
- Ruteo interno protegido (`/internal/<servicio>/**`) validando token `ServiceJwt` con claim `rol=SERVICE`.
- Rate limiting con Redis (`RequestRateLimiter`), circuit breaker con Resilience4j y healthcheck unificado.

### T4 — Patrón Outbox + Relay
- Creación de tabla `outbox` en la base de datos de PostgreSQL con disparador de notificación (`pg_notify('outbox', ...)`).
- Componente de Relay reactivo basado en `LISTEN` y sondeo periódico de respaldo (cada 30s) que publica en RabbitMQ con *publisher confirms*.

### T5 — Eventos del monolito
- Integración en el código transaccional del monolito de la escritura a la tabla `outbox` para los eventos del catálogo 5.4 (registros de usuarios, reservas y pagos de turnos, cambios de tarifa y disponibilidad).

### T6 — Observabilidad
- Despliegue de VictoriaMetrics (single node), exportadores de métricas de host y contenedores.
- Instrumentación Micrometer en el gateway y backend para métricas de latencia (p50, p95, p99), errores 4xx/5xx y volumen de mensajes en RabbitMQ.
- Endpoint administrativo `/api/admin/observabilidad` y visualización básica en el panel de administrador.

### T7 — Deploy en producción con Gateway
- Configuración de Traefik para enrutar el tráfico de producción hacia el API Gateway.
- Construcción y publicación de imágenes en GHCR, arranque escalonado y pruebas de humo en el VPS.

## Criterios de aceptación

- [ ] `./mvnw test` desde la raíz compila y ejecuta exitosamente todos los tests de `libs/comun`, `libs/eventos` y `backend`.
- [ ] `libs/comun` inyecta automáticamente `X-Correlation-Id` en peticiones entrantes y propaga el ID en MDC y headers HTTP.
- [ ] `libs/eventos` provee el sobre `EventEnvelope` con los 11 campos estipulados y serialización Jackson correcta.
- [ ] Los 25 eventos del catálogo 5.4 cuentan con records `data` y tests unitarios de roundtrip JSON sin campos clínicos ni secretos.
- [ ] `ServiceJwt` emite y valida tokens de servicio con vencimiento a 5 minutos, rechazando tokens expirados, firmas inválidas o roles ajenos a `SERVICE`.
- [ ] `docker compose -f docker-compose.local.yml build backend` construye la imagen del backend con contexto en la raíz sin errores.
- [ ] Los workflows `.github/workflows/ci.yml` y `.github/workflows/deploy.yml` están preparados para la arquitectura multimódulo.
- [ ] Ningún test ni comportamiento existente del monolito resulta alterado o degradado.

## Riesgos e impacto

- **Compilación en CI/Deploy:** El cambio de contexto de Docker a la raíz requiere asegurar que los workflows envíen el contexto y archivo correctos.
- **Sin impacto en datos:** La tarea T1 no modifica la base de datos ni introduce migraciones destructivas.
- **Riesgo en producción:** Nulo en T1; las fases operativas subsiguientes contarán con reversibilidad directa en Traefik.

## Preguntas abiertas

- Ninguna. Las definiciones de arquitectura fueron aprobadas en el documento base `docs/arquitectura/microservicios.md`.

## Resultado (completar al cerrar)

- T1 implementada: POM agregador multimódulo raíz con Spring Boot 3.5.15 BOM, `libs/comun` con `CorrelationIdFilter` y `ErrorResponse`, `libs/eventos` con `EventEnvelope`, constantes de topología RabbitMQ, 25 tópicos y records de contrato, y `ServiceJwt`. Suites de tests de contrato y unitarios en verde. Dockerfiles y workflows de CI/deploy actualizados.
- T2 implementada: Servicios de infraestructura local en `docker-compose.local.yml`: RabbitMQ 3.13 con configuración de memoria (256 MB watermark 0.6), plugins management y prometheus, colas lazy por policy y exchanges de topología (`tranqui.eventos`, `tranqui.reintentos`, `tranqui.dlx`); Consul 1.19 como servidor dev con UI y sin ACLs; Redis 7 sin persistencia y maxmemory 64 MB. Verificación de puertos y URLs en `scripts/local.mjs`, documentación en `docs/entornos/local.md` y test de validación en `libs/eventos`.
