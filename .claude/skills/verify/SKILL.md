---
name: verify
description: How to build/launch/drive TranquiApp locally to verify a change.
---

# Verifying TranquiApp changes

## Launch

1. Start Docker Desktop if not running, then bring up the stack (it's usually already
   provisioned from a previous run — this just starts the containers):
   ```
   docker compose up -d tranqui-db
   ```
   (this actually starts the whole existing `tranqui-db`/`tranqui-backend`/`tranqui-frontend`
   stack if the containers already exist from a prior `docker compose up --build`; it does NOT
   rebuild images, so backend/Java changes won't be picked up this way — only a full
   `docker compose up --build -d` rebuilds).
2. For frontend-only changes (React/TSX/CSS), run the Vite dev server instead of relying on the
   `tranqui-frontend` container (which serves a stale prebuilt image): `cd frontend && npm run
   dev` → http://localhost:5173. It talks to the backend at `http://localhost:8081/api`
   (`frontend/src/api/api.ts`), which the docker-compose backend container already serves.
3. For backend/Java changes, the docker `tranqui-backend` container won't have them unless
   rebuilt (`docker compose up --build -d tranqui-backend`, slow) — no fast local Maven path has
   been verified yet.

## Login (seed users — see `backend/.../config/DataInitializer.java`)

All seed accounts use password `admin123`:
- `medico.verificado@gmail.com` — fully verified professional, has seed availability/agenda data.
- `medico.sinverificar@gmail.com` — unverified professional.
- `paciente.completo@gmail.com` — patient with complete profile.
- `paciente.sindatos@gmail.com` — patient with no data.
- `admin@tranqui.com` — admin.

## Gotchas

- Switching accounts mid-session: clear cookies/localStorage (`page.context().clearCookies()` +
  `localStorage.clear()`) then navigate to `/login`, rather than just navigating to `/login`
  while already authenticated (it auto-redirects back).
- Direct navigation to a sub-route like `/panel/agenda` can bounce back to `/panel` — click
  through the sidebar nav instead of `page.goto()`-ing the sub-route directly.
- Playwright screenshots/snapshots from the MCP server save relative to the *parent* of the repo
  dir (e.g. `...\TranquiApp\foo.png`, one level above `...\TranquiApp\tranquiApp\`), not the repo
  root and not the invoking tool's cwd — `Glob` for the filename if `Read` can't find it.
- `/api/medicos/disponibilidad-config`, `/api/medicos/google-calendar/status`, and
  `/api/chat/tiene-no-leidos` were observed returning 403 for `medico.verificado@gmail.com` even
  right after a fresh login, on the containers as of 2026-07-17 — looked like a pre-existing
  backend permissions issue unrelated to frontend changes, not something caused by editing
  `App.tsx`/`LandingPage.tsx`. Re-check whether this is still true before assuming a save failure
  is your change's fault.
- The `.mp-connect-banner` (Vinculaciones / home connect banners) layout breaks at viewport
  widths around ~1000–1100px (just under the 1100px sidebar breakpoint in `dashboard.css`): the
  fixed-width icon + action button leave almost no room for the text column, wrapping it to one
  word per line. Pre-existing, not caused by the SVG logo swap. Worth a real fix.
