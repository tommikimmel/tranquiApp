# AGENTS.md — Reglas de trabajo para agentes de IA

Este archivo es la fuente de verdad para cualquier agente de IA (Claude, Codex, Cursor, etc.) que
trabaje en **Tranqui App**. `CLAUDE.md` apunta acá. Si algo de este archivo choca con una
instrucción vieja en otro documento, gana este archivo.

La documentación completa está en [`docs/`](docs/README.md).

---

## 1. Reglas obligatorias

1. **SDD antes de tocar código.** Nunca modifiques código, base de datos ni infraestructura sin
   antes mostrarle al usuario una spec con tu entendimiento del problema, la solución propuesta y
   los criterios de aceptación, y recibir su aprobación explícita. Ver
   [docs/flujo/sdd.md](docs/flujo/sdd.md).
2. **Nunca commitees directo a `main` ni a `develop`.** Todo cambio va en su propia rama y entra
   por Pull Request. Ver [docs/flujo/git.md](docs/flujo/git.md).
3. **Nunca toques producción sin aprobación explícita para esa acción puntual**: ni la base de
   datos, ni el VPS, ni un deploy, ni el flag de mantenimiento. Una aprobación anterior no cuenta
   para la siguiente.
4. **Nunca guardes secretos en el repositorio** (contraseñas, claves de API, tokens, claves SSH).
   Ver [docs/entornos/secretos.md](docs/entornos/secretos.md).
5. **Nada de emojis en la interfaz.** Usá íconos SVG (`frontend/src/components/Icon.tsx`).
6. **Tests antes de abrir un PR.** El cambio tiene que venir con tests del comportamiento nuevo o
   corregido, y la suite completa tiene que pasar.
7. **Cambios destructivos de base de datos solo por migración Flyway** (ver sección 5).
8. **Idioma:** documentación, commits, PRs y textos de la interfaz en español. La interfaz usa
   voseo rioplatense ("Tocá", "Elegí"). Nombres de código (variables, clases) siguen el estilo del
   archivo que estés tocando.
9. **Si algo no está claro, preguntá.** No inventes requisitos ni elijas por el usuario en
   decisiones de producto.

---

## 2. Flujo de trabajo resumido

```
develop ──► rama tipo/area-descripcion ──► PR a develop ──► (fin de sprint) PR develop → main ──► tag vX.Y.Z ──► deploy
main ──► hotfix/area-descripcion ──► PR a main ──► tag vX.Y.Z+1 ──► deploy ──► merge main → develop
```

- Ramas permanentes: `main` (producción) y `develop` (desarrollo).
- Nombres de rama, commits, etiquetas y versiones: [docs/flujo/git.md](docs/flujo/git.md).
- Release a producción: [docs/flujo/releases.md](docs/flujo/releases.md).
- Hotfix: [docs/flujo/hotfix.md](docs/flujo/hotfix.md).
- Las tareas se dividen por sprint en [docs/sprints/](docs/sprints/README.md) (no es Scrum formal,
  solo una forma de agrupar el trabajo).
- El único que aprueba y mergea PRs es el dueño del repositorio.

---

## 3. Stack

| Capa | Tecnología |
|---|---|
| Frontend | React 19 + Vite + TypeScript (`frontend/`) |
| Backend | Spring Boot 3.5, Java 17 (`backend/`) |
| Base de datos | PostgreSQL 15, esquema por Hibernate `ddl-auto: update` + Flyway |
| Infraestructura | Docker Compose en un VPS, detrás de Traefik (TLS) |
| Integraciones | Mercado Pago (cobros y suscripciones), Google Calendar/Meet, Resend (mails), QBI2 (recetas, deshabilitado), ARCA (facturación, deshabilitado) |

Contexto técnico ampliado: [docs/arquitectura/contexto.md](docs/arquitectura/contexto.md).

---

## 4. Comandos

### Frontend (`cd frontend`)

| Qué | Comando |
|---|---|
| Servidor de desarrollo | `npm run dev` |
| Tests | `npm test` |
| Lint | `npm run lint` |
| Typecheck + build | `npm run build` (o `npx tsc -b` solo typecheck) |

### Backend (`cd backend`)

| Qué | Comando |
|---|---|
| Tests | `./mvnw test` |
| Empaquetar | `./mvnw -DskipTests package` |

### Entorno local completo (desde la raíz)

| Qué | Comando |
|---|---|
| Levantar todo (verifica puertos y pregunta antes de frenar algo) | `npm run local` |
| Logs del backend | `npm run local:logs` |
| Frenar | `npm run local:down` |
| Borrar la base local y volver a sembrar | `npm run local:reset` |

App en `localhost:5173`, mails en `localhost:8025`, cuentas de demo con contraseña `admin123`.
Detalle en [docs/entornos/local.md](docs/entornos/local.md). Nunca frenes procesos ni contenedores
ajenos sin preguntarle al usuario.

---

## 5. Convenciones técnicas

- **Base de datos:** Hibernate (`ddl-auto: update`) crea tablas y columnas nuevas. Todo cambio
  destructivo (borrar o renombrar columnas, migrar datos) va como migración Flyway en
  `backend/src/main/resources/db/migration/V<n>__descripcion.sql`.
- **Integraciones externas** (Mercado Pago, Google, mails): siempre detrás de un flag de
  configuración y simulables en local. En tests se mockean.
- **Fotos de perfil:** se guardan como data URL; siempre pasan por
  `frontend/src/utils/profilePhoto.ts` (400 px, JPEG) antes de guardarse.
- **Centro de ayuda:** preguntas en `frontend/src/constants/helpFaq.ts`, videos y subtítulos en
  `frontend/public/videos/`. Ver [docs/centro-de-ayuda.md](docs/centro-de-ayuda.md).
- **Errores conocidos** pendientes de arreglar: [docs/bugs-conocidos.md](docs/bugs-conocidos.md).

---

## 6. Al terminar un cambio

1. Tests, lint y typecheck en verde.
2. Verificación en el navegador si el cambio es visible (ver `docs/entornos/local.md`).
3. Spec actualizada con lo que realmente se hizo (`docs/specs/`).
4. Entrada en la sección "Sin publicar" de [`CHANGELOG.md`](CHANGELOG.md).
5. Si cerraste un bug de `docs/bugs-conocidos.md`, sacalo de ahí.
6. Commit con el formato de `docs/flujo/git.md`, push de la rama y PR a `develop` con la plantilla.
