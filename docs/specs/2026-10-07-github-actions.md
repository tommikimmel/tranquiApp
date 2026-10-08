# Spec: GitHub Actions, deploy por clave SSH y secretos fuera del repo

- **Fecha:** 2026-10-07
- **Tipo:** chore
- **Sprint:** sprint-01 (tarea 5)
- **Estado:** implementada en código; pasos en producción pendientes de aprobación
- **Rama / PR:** `chore/infra-github-actions` (apilada sobre #5)

## Entendimiento del problema

El deploy era `node deploy.js` desde la máquina del dueño: contraseña de root escrita en el archivo
(y en el historial), el `.env` local subido al servidor, imágenes compiladas en el VPS (2 núcleos)
con producción atendiendo, y sin CI que frene un PR con tests rotos. En producción, el proyecto de
Docker se llama `app` (carpeta `/app`) y la base vive en el volumen `app_pgdata`; el login SSH por
contraseña está habilitado.

## Decisiones (aprobadas)

Imágenes en GHCR, públicas; deploy disparado por el tag `vX.Y.Z`; `deploy.js` se mantiene como
deploy manual de emergencia.

## Solución

- `ci.yml`: backend (`mvnw test`), frontend (`tsc`, `vitest`, `build`) y lint incremental
  (`scripts/ci/lint-cambios.mjs`) en cada PR a `develop`/`main`; reutilizable por el deploy.
- `deploy.yml`: tag en `main` → tests → imágenes en GHCR → SSH con clave y `known_hosts` → copia
  `docker-compose.yml` y `scripts/vps/deploy-remoto.sh` a `/srv/tranqui` → pull, `up -d` con
  `-p app`, verificación y vuelta atrás automática.
- `deploy-remoto.sh`: cancela si falta `/srv/tranqui/.env`, si `SEED_TEST_ACCOUNTS=true` o si no
  existe el volumen `app_pgdata`.
- `mantenimiento.yml`: botón manual (on/off/estado/prueba de novedades); no imprime el link de
  administrador (logs públicos); comando `acceso` para obtenerlo desde la máquina del dueño.
- `mantenimiento.mjs` y `deploy.js`: credenciales desde variables de entorno o `.env.vps`, clave SSH
  y verificación de la huella del servidor; sin contraseñas.
- `docker-compose.yml`: `image: ghcr.io/tommikimmel/tranqui-*:${TRANQUI_VERSION}` (conserva `build`).

## Criterios de aceptación

- [x] Workflows válidos (`actionlint` sin errores).
- [x] El lint incremental marca solo problemas nuevos (probado contra `origin/develop`).
- [x] Con `-p app` el volumen resuelve a `app_pgdata` (verificado con `docker compose config`).
- [x] Sin contraseñas en `deploy.js` ni en los scripts.
- [ ] Un PR con un test roto queda en rojo (se verifica con el primer PR que corra el CI).
- [ ] El tag `v1.1.0` despliega por imágenes y la app queda andando con la misma base.
- [ ] Un deploy cuya verificación falla vuelve a la versión anterior.
- [ ] Botón de mantenimiento funcionando desde GitHub.
- [ ] Login SSH por contraseña deshabilitado, con las claves probadas.

## Pasos en producción (cada uno con aprobación)

1. Agregar las claves públicas (Actions y dueño) a `/root/.ssh/authorized_keys`.
2. Crear `/srv/tranqui` y copiar `/app/.env` a `/srv/tranqui/.env` (permisos 600), verificando que
   tenga `DB_PASSWORD` y `ENCRYPTION_KEY`.
3. Cargar los secretos en GitHub y crear el entorno `produccion`.
4. Primer deploy (`v1.1.0`) al hacer el release; después, imágenes de GHCR públicas.
5. El dueño cambia la contraseña de root; después, `PasswordAuthentication no`.
