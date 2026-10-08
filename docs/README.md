# Documentación de Tranqui App

Punto de partida para personas y agentes de IA. Las reglas de trabajo están en
[`AGENTS.md`](../AGENTS.md); el historial de versiones en [`CHANGELOG.md`](../CHANGELOG.md).

## Cómo trabajamos

| Documento | Contenido |
|---|---|
| [flujo/git.md](flujo/git.md) | Ramas, nombres, commits, PRs, etiquetas y versiones |
| [flujo/sdd.md](flujo/sdd.md) | Especificación aprobada antes de tocar código |
| [flujo/releases.md](flujo/releases.md) | Pasar `develop` a producción |
| [flujo/hotfix.md](flujo/hotfix.md) | Correcciones urgentes en producción |
| [specs/](specs/README.md) | Specs de cada cambio |
| [sprints/](sprints/README.md) | División de tareas por sprint |

## Entornos

| Documento | Contenido |
|---|---|
| [entornos/local.md](entornos/local.md) | Levantar la app en local con datos y servicios simulados |
| [entornos/produccion.md](entornos/produccion.md) | Servidor, contenedores, configuración y base |
| [entornos/secretos.md](entornos/secretos.md) | Dónde vive cada secreto y cómo rotarlo |

## Producto y arquitectura

| Documento | Contenido |
|---|---|
| [producto/especificacion-v1.md](producto/especificacion-v1.md) | Especificación funcional del MVP (junio 2026) |
| [arquitectura/contexto.md](arquitectura/contexto.md) | Contexto técnico y estructura del proyecto |
| [arquitectura/base-de-datos/](arquitectura/base-de-datos/diseno.md) | Diseño original de la base (el esquema real lo define el código) |
| [adr/](adr/) | Decisiones de arquitectura (ADR 001 a 008) |
| [etapas/](etapas/) | Plan original de desarrollo por etapas (histórico) |
| [guias-tecnicas/](guias-tecnicas/) | Guías por tema: agenda, notificaciones, migraciones, Docker, Mercado Pago, auth, chat |
| [planes/](planes/) | Planes de features puntuales |

## Funcionalidades y estado

| Documento | Contenido |
|---|---|
| [centro-de-ayuda.md](centro-de-ayuda.md) | FAQ y videos explicativos |
| [novedades.md](novedades.md) | Mail de novedades al terminar un mantenimiento (Broadcasts de Resend) |
| [bugs-conocidos.md](bugs-conocidos.md) | Errores detectados pendientes de arreglar |

> Los documentos de `etapas/`, `adr/`, `producto/` y `guias-tecnicas/` se escribieron durante el
> desarrollo inicial (junio a agosto de 2026) y pueden no reflejar el código actual (por ejemplo,
> mencionan WhatsApp saliente, que se eliminó en octubre de 2026). Ante una diferencia, manda el
> código.
