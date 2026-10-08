# Spec: secretos del historial, rotación y repo público con ramas protegidas

- **Fecha:** 2026-10-07
- **Tipo:** chore
- **Sprint:** sprint-01 (tarea 6)
- **Estado:** borrador
- **Rama / PR:** `docs/docs-specs-secretos-y-limpieza` (solo la spec; la tarea no cambia código)

## Entendimiento del problema

El repo va a pasar a público. Todo el historial de git (y los commits de PRs, que GitHub conserva
aunque se borre la rama) queda visible para cualquiera. Antes hay que saber qué secretos quedaron
escritos en algún commit y dejarlos sin valor.

Revisión hecha el 2026-10-07 con `gitleaks` sobre las 216 commits de todas las ramas, más una
búsqueda dirigida de contraseñas literales (gitleaks no detecta contraseñas sueltas):

| Hallazgo | Dónde | Commits |
|---|---|---|
| Contraseña de root del VPS, escrita en `deploy.js` | `deploy.js` | `25310ab`, `29706ed`, `f757ee6`, `423ab07`, `3462fde` |
| Un JWT (token firmado de algún servicio) | `.env.template`, línea 61 | `936393a` |

Falsos positivos descartados: la contraseña `admin123` de las cuentas de demo locales
(`LocalDemoSeeder`, tests, e2e, pruebas de carga) y armado de URLs con `clientSecret`/`token` en
variables (`GoogleCalendarOAuthService`, `MercadoPagoOAuthService`, `TurnoService`).

## Solución propuesta

**Rotar, no reescribir el historial.** Reescribir con `git filter-repo` no alcanza: GitHub guarda
los commits de cada PR (`refs/pull/*`) y no se pueden borrar sin pedirlo a soporte, y cualquier
clon previo los conserva. Un secreto que estuvo en un commit se da por expuesto; lo que lo
neutraliza es cambiarlo.

1. **Contraseña de root:** la cambia el dueño (`passwd` en el VPS o desde el panel de Hostinger),
   después de comprobar que entra con su clave SSH. Luego `PasswordAuthentication no` y
   `PermitRootLogin prohibit-password` (paso 5 de la spec de GitHub Actions).
2. **JWT de `.env.template`:** el dueño identifica de qué servicio es (línea 61 de la versión del
   commit `936393a`), lo revoca o regenera en ese servicio y deja la línea de la plantilla vacía.
   El agente no puede leer `.env*` (regla del repo).
3. **Repo público:** Settings → General → Danger Zone → Change visibility.
4. **Ramas protegidas** (en un repo privado del plan gratuito no están disponibles; por eso va
   después del paso 3). Un ruleset para `main` y `develop`:
   - exigir PR para mergear, con 0 aprobaciones (el dueño es el único que mergea);
   - exigir los checks del CI: `Backend (tests)`, `Frontend (typecheck, tests y build)` y
     `Lint (solo lo que cambia el PR)`;
   - bloquear force push y borrado de la rama.
   Para `main`, además, solo squash merge desde `develop` o `hotfix/*` (ver `docs/flujo/git.md`).
5. **Repetir la revisión** con gitleaks justo antes del cambio de visibilidad, por si entró algo nuevo.

## Criterios de aceptación

- [ ] La contraseña vieja de root no sirve (probado por el dueño).
- [ ] El JWT del commit `936393a` está revocado en su servicio.
- [ ] gitleaks sin hallazgos nuevos sobre el historial completo.
- [ ] Repo público.
- [ ] Un push directo a `main` o `develop` es rechazado; un PR con el CI en rojo no se puede mergear.

## Riesgos e impacto

- **Decisión del dueño (2026-10-07): por ahora no se cambia la contraseña de root.** Mientras siga
  siendo la misma que está en el historial, el repo **no** puede pasar a público: cualquiera
  tendría acceso root al VPS. El paso 3 queda bloqueado hasta rotarla.
- Si el JWT es de un servicio en uso por producción, regenerarlo exige actualizar
  `/srv/tranqui/.env` y reiniciar el backend en el mismo momento.
- El repo público expone la estructura de la app; no expone datos ni claves si se cumplen los
  pasos 1 y 2.

## Preguntas abiertas

- ¿De qué servicio es el JWT de la línea 61 de `.env.template`? (lo responde el dueño)

## Resultado (completar al cerrar)
