# Releases: de `develop` a producción

Pasar `develop` a `main` **es** publicar una versión nueva de la app y requiere redeploy.

## Checklist de release

1. **Cerrar el sprint:** todo lo que va en la versión está mergeado en `develop` y lo que no se
   terminó queda anotado para el sprint siguiente (`docs/sprints/`).
2. **Probar `develop` en local** con el entorno de pruebas (`docs/entornos/local.md`): flujos
   principales de paciente (registro, reserva y pago, cancelación) y de profesional (agenda,
   honorarios, configuración, suscripción).
3. **Definir la versión** según `docs/flujo/git.md` (sección 6).
4. **Actualizar `CHANGELOG.md`:** mover lo de "Sin publicar" a una sección `## [vX.Y.Z] - AAAA-MM-DD`.
   Ese commit va en una rama `chore/docs-release-vX.Y.Z` con PR a `develop`.
5. **Revisar migraciones:** si hay archivos nuevos en `db/migration`, leerlos y confirmar que son
   seguros para los datos de producción.
6. **Abrir el PR `develop → main`** con título `release: vX.Y.Z` y el changelog de la versión en
   la descripción.
7. **Mergear** (merge commit, no squash) y **crear el tag** `vX.Y.Z` sobre `main`.
8. **Deploy a producción** (ver abajo).
9. **Verificar producción:** el sitio responde, `/api/medicos` responde, login y reserva funcionan.

## Deploy

- **Hoy (2026-10-07):** manual, con `node deploy.js` desde la raíz del repo en `main` y con el
  árbol limpio. Empaqueta lo commiteado, lo sube al VPS y reconstruye los contenedores.
- **Planificado:** GitHub Actions despliega automáticamente al crear el tag `v*` en `main`, por
  SSH con clave. Cuando esté, este documento se actualiza.

## Si algo sale mal

Volver a la versión anterior: checkout del tag anterior y redeploy. Si la versión nueva incluía
una migración de base de datos, evaluar antes si la versión anterior es compatible con el esquema
nuevo (Flyway no revierte migraciones solo).
