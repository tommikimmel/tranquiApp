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

**Automático con el tag.** Al crear el tag `vX.Y.Z` sobre `main` (desde GitHub: Releases, "Draft a
new release", tag nuevo `vX.Y.Z` con target `main`), GitHub Actions (`.github/workflows/deploy.yml`):

1. Verifica que el tag esté en `main`.
2. Corre los tests (backend y frontend).
3. Construye las imágenes y las publica en GHCR (`ghcr.io/tommikimmel/tranqui-backend:vX.Y.Z` y
   `tranqui-frontend:vX.Y.Z`).
4. Por SSH, el VPS baja las imágenes y recrea los contenedores (`scripts/vps/deploy-remoto.sh`), sin
   compilar nada.
5. Verifica que respondan `https://tranquisalud.com` y `/api/health`. Si no, **vuelve sola a la
   versión anterior** y el workflow queda en rojo.

La versión desplegada queda en `/srv/tranqui/version-actual` y el historial en
`/srv/tranqui/deploys.log`.

**Manual de emergencia** (solo si Actions no está disponible), desde tu máquina con `.env.vps`
configurado (ver `docs/entornos/secretos.md`):

| Comando | Qué hace |
|---|---|
| `node deploy.js vX.Y.Z` | Mismo camino que Actions con una versión ya publicada en GHCR |
| `node deploy.js --compilar` | Compila en el VPS el commit actual (le saca CPU a producción mientras compila) |

## Si algo sale mal

El deploy vuelve solo a la versión anterior si la nueva no responde. Para volver a mano a una
versión ya publicada: `node deploy.js vX.Y.Z` con la versión anterior. Si la versión nueva incluía
una migración de base de datos, evaluar antes si la versión anterior es compatible con el esquema
nuevo (Flyway no revierte migraciones solo).
