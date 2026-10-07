# Flujo de Git

Reemplaza a las normas anteriores de `.agent/Etapas/00_workflow_desarrollo.md` (eliminado el
2026-10-07). Ya no se usan ramas `release/*` ni `bugfix/*`.

## 1. Ramas permanentes

| Rama | Contiene | Recibe cambios de |
|---|---|---|
| `main` | Lo que está en producción. Cada merge es una versión nueva. | PR desde `develop` (release) o desde `hotfix/*` |
| `develop` | El desarrollo en curso del sprint. | PR desde ramas de trabajo; merge de `main` después de un hotfix |

Nadie commitea directo a `main` ni a `develop`: todo entra por Pull Request con el CI en verde.
El único que aprueba y mergea es el dueño del repositorio.

## 2. Ramas de trabajo

Una rama por cambio. Sale de `develop` (salvo los hotfix, que salen de `main`).

**Formato:** `tipo/area-descripcion-corta`

- Todo en minúsculas, palabras separadas por guiones, sin tildes ni ñ.
- La descripción dice *qué* cambia, en 2 a 5 palabras.

| Tipo | Para qué | Ejemplo |
|---|---|---|
| `feature` | Funcionalidad nueva | `feature/front-centro-de-ayuda` |
| `fix` | Corrección de un error en `develop` | `fix/back-turnos-hora-actual` |
| `style` | Restyling o cambios visuales sin lógica nueva | `style/front-header-mobile` |
| `refactor` | Reestructurar código sin cambiar el comportamiento | `refactor/back-servicio-turnos` |
| `perf` | Mejora de rendimiento | `perf/front-foto-perfil` |
| `docs` | Solo documentación | `docs/docs-guias-ia-y-flujo` |
| `test` | Solo tests | `test/back-reembolsos` |
| `chore` | Dependencias, build, configuración, CI | `chore/infra-github-actions` |
| `hotfix` | Corrección urgente en producción (sale de `main`) | `hotfix/back-webhook-mp` |

| Área | Alcance |
|---|---|
| `front` | `frontend/` |
| `back` | `backend/` |
| `db` | Migraciones o datos |
| `infra` | Docker, nginx, deploy, CI |
| `docs` | Documentación |
| `full` | Cambio que toca frontend y backend a la vez |

## 3. Commits

Formato [Conventional Commits](https://www.conventionalcommits.org/es/), en español:

```
tipo(alcance): descripción en minúsculas, en presente o infinitivo

Cuerpo opcional: el porqué del cambio, no el qué. Líneas de hasta ~100 caracteres.
```

- `tipo`: `feat`, `fix`, `style`, `refactor`, `perf`, `docs`, `test`, `chore`.
- `alcance`: el módulo o pantalla (`ayuda`, `checkout`, `turnos`, `nginx`, `perfil`, `deploy`…).
- Un commit = una unidad lógica que compila y pasa tests.

Ejemplos reales del repo:

```
feat(ayuda): reducir el centro de ayuda a los 5 videos más usados, en formato escritorio
fix(nginx): servir los subtítulos .vtt como text/vtt y los videos sin fallback a index.html
perf(perfil): reducir la foto de perfil a 400 px JPEG antes de guardarla
```

## 4. Pull Requests

- **Título:** igual que un commit (`tipo(alcance): descripción`).
- **Descripción:** completar la plantilla (`.github/pull_request_template.md`): problema, solución,
  link a la spec, cómo probarlo y checklist.
- **Destino:** `develop` (o `main` si es hotfix o release).
- **Merge:** *squash and merge* para ramas de trabajo (un commit limpio por cambio en `develop`);
  *merge commit* para el release `develop → main`, así queda claro qué entró en cada versión.
- Borrar la rama después del merge.

## 5. Etiquetas de GitHub (labels)

Cada PR lleva al menos una etiqueta de cada grupo:

| Grupo | Etiquetas |
|---|---|
| Tipo | `tipo:feature`, `tipo:fix`, `tipo:style`, `tipo:refactor`, `tipo:perf`, `tipo:docs`, `tipo:test`, `tipo:chore`, `tipo:hotfix` |
| Área | `area:frontend`, `area:backend`, `area:db`, `area:infra`, `area:docs` |
| Sprint | `sprint-01`, `sprint-02`, … |

## 6. Versiones

[SemVer](https://semver.org/lang/es/): `vMAYOR.MENOR.PARCHE`, como tag de git sobre `main`.

| Sube | Cuando el release incluye |
|---|---|
| MAYOR (`v2.0.0`) | Un cambio que rompe compatibilidad (datos, API pública o flujos que obligan a los usuarios a cambiar algo) |
| MENOR (`v1.1.0`) | Al menos una `feature` |
| PARCHE (`v1.0.1`) | Solo `fix`, `style`, `perf`, `refactor` o un hotfix |

La versión de partida es **`v1.0.0`**: lo que está en producción el 2026-10-07.
Cada versión tiene su entrada en [`CHANGELOG.md`](../../CHANGELOG.md).
