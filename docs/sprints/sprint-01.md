# Sprint 01 — Preparar la salida a producción

- **Inicio:** 2026-10-07
- **Cierre:** a definir
- **Versión a publicar:** v1.1.0 (la versión de partida, lo que estaba en producción el 2026-10-07, es v1.0.0)

Plan acordado con el dueño del proyecto el 2026-10-07. Los pasos que tocan producción (5 a 7)
requieren aprobación explícita antes de ejecutarse.

## Tareas

| # | Tarea | Tipo | Rama | Estado |
|---|---|---|---|---|
| 0 | Eliminar la carpeta `investigacion/` (fuera del repo) | chore | — | hecha |
| 1 | Alinear `develop` con `main` y borrar las ramas viejas | chore | — | hecha (también se borró `whatsapp-bot`, con respaldo local en un bundle fuera del repo) |
| 2 | Documentación: `AGENTS.md`, `CLAUDE.md`, `docs/`, `CHANGELOG.md`, plantilla de PR | docs | `docs/docs-guias-ia-y-flujo` | hecha (PR #1) |
| 3 | Entorno local de pruebas: perfil `local`, datos de demo realistas, mails en Mailpit, sin login de Google | feature | `feature/full-entorno-local` | hecha, PR #2, v1.1.0 ([spec](../specs/2026-10-07-entorno-local.md)) |
| 4 | Modo mantenimiento con flag (página única mientras está activo) | feature | `feature/infra-modo-mantenimiento` | hecha, PR #3, v1.1.0 ([spec](../specs/2026-10-07-modo-mantenimiento.md)) |
| 4b | Mail de novedades al terminar el mantenimiento (Broadcasts de Resend) | feature | `feature/full-mail-novedades` | hecha, PR #5, v1.1.0 ([spec](../specs/2026-10-07-mail-novedades.md)) |
| 5 | GitHub Actions (CI en PRs, deploy al taggear `main`) y secretos fuera del repo, deploy por clave SSH | chore | `chore/infra-github-actions` | hecha, PR #6, v1.1.0 ([spec](../specs/2026-10-07-github-actions.md)); primer deploy automático el 2026-10-07 |
| 6 | Revisar secretos en el historial (gitleaks), rotar los expuestos y hacer público el repo con ramas protegidas | chore | — | hecha el 2026-10-08 ([spec](../specs/2026-10-07-secretos-y-repo-publico.md)); falta rotar el token de QBI2 |
| 7 | Limpieza de la base de producción: borrar todo menos el admin y el catálogo de planes, cancelando antes los débitos de Mercado Pago de los profesionales de prueba | chore | `chore/infra-limpieza-produccion` | hecha el 2026-10-08, PR #10 ([spec](../specs/2026-10-07-limpieza-produccion.md)) |

## Después de este sprint

La migración a microservicios empieza después del lanzamiento, en sprints propios: una fase por
sprint según `docs/arquitectura/microservicios.md` (fase 0: plataforma y observabilidad).

## Decisiones tomadas

- Se trabaja en local; no hay entorno remoto de staging.
- En local se simulan Mercado Pago, Google Calendar y los mails. El login con Google no se usa en
  entornos de prueba (se ingresa con email y contraseña). Recetas (QBI2) y facturación (ARCA) no
  se implementan por ahora.
- Datos de demo: profesionales de distinto tipo, turnos variados, chats y actividad como si se
  usara la app de verdad.
- Ramas permanentes solo `main` y `develop`; PR obligatorio; el dueño es el único que aprueba.
- Versionado SemVer, empezando en v1.0.0.
- Limpieza de producción: se borra todo menos el usuario admin y las tablas de planes
  (`plans`, `features`, `plan_features`). Sin backup previo, por decisión del dueño.
- La ventana de mantenimiento se activa y desactiva con un flag.
