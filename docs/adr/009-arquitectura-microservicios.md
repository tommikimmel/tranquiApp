# ADR 009: Migración del backend a microservicios

- **Fecha:** 2026-10-07
- **Estado:** aceptada
- **Plan detallado:** [docs/arquitectura/microservicios.md](../arquitectura/microservicios.md)

## Contexto

El backend es un monolito Spring Boot de ~18.000 líneas que concentra autenticación, profesionales,
turnos, pagos, suscripciones, notificaciones, chat y Google Calendar. Corre en un VPS de 8 GB de RAM
y 2 núcleos, compartido con Traefik y un bot de WhatsApp. El dueño del proyecto decidió separar el
backend en microservicios para poder evolucionar y escalar cada dominio por separado.

## Decisión

- La app sale a producción con el monolito; la migración se hace después, **por fases** (patrón
  *strangler*), con vuelta atrás en cada una.
- 8 servicios: identidad, profesionales, turnos, pagos, suscripciones, notificaciones, chat y
  calendario. Spring Boot con la JVM afinada.
- **Toda llamada sincrónica pasa por un API Gateway** (Spring Cloud Gateway), tanto desde el
  frontend como entre servicios, para no exponer la estructura interna. El gateway hace ruteo,
  rate limiting (Redis), healthcheck y resuelve los servicios por nombre en **Consul**, donde cada
  servicio se registra con su nombre y puerto.
- **Eventos asincrónicos por push con RabbitMQ** (se descartó Kafka porque sus consumidores hacen
  pull). Sobre JSON común versionado y tópicos `<dominio>.<entidad>.<evento>.v<n>`.
- **Outbox** en cada productor, **inbox** e `Idempotency-Key` para idempotencia, reintentos con
  espera creciente y DLQ, circuit breaker en el gateway y sagas por coreografía.
- Una instancia de PostgreSQL con **una base por servicio**.
- Observabilidad (VictoriaMetrics, node-exporter, cAdvisor y Micrometer) visible en el panel de
  administrador desde la primera fase.
- Imágenes construidas en GitHub Actions y publicadas en GHCR; el VPS no compila.

## Consecuencias

- Más piezas que operar y consistencia eventual entre servicios; se compensa con observabilidad,
  entorno local completo y fases con vuelta atrás.
- Presupuesto de ~4,3 a 4,8 GB de RAM; la CPU (2 núcleos) es el límite principal y obliga a
  escalonar el arranque y afinar las JVM.
- El frontend no cambia de URLs: el gateway conserva las rutas `/api/...` actuales.
