# Secretos

**Ningún secreto se guarda en el repositorio**: ni en el código, ni en la documentación, ni en
`docker-compose.yml`. Esto incluye contraseñas, claves de API, tokens, claves SSH y claves de
cifrado. `.env` está en `.gitignore`; `.env.template` lista los nombres con valores vacíos.

## Dónde vive cada secreto (esquema acordado)

| Secreto | Dónde vive | Quién lo cambia y cómo |
|---|---|---|
| Clave SSH privada para el deploy | Secreto de GitHub Actions `VPS_SSH_KEY` | El dueño, en GitHub: Settings → Secrets and variables → Actions (se puede reemplazar, no leer) |
| Host y usuario del VPS | Secretos `VPS_HOST` y `VPS_USER` | Igual que el anterior |
| Variables de producción (base, Mercado Pago, Resend, cifrado, Google) | Archivo `.env` **solo en el VPS** | El dueño, por SSH, editando ese archivo; después redeploy |
| `NOVEDADES_TOKEN` (endpoint interno del mail de novedades) | Archivo `.env` del VPS | El dueño, por SSH; generarlo con `openssl rand -hex 32` |
| Contraseña de root del VPS | Solo en el gestor de contraseñas del dueño | El dueño. No se usa en el CI ni en scripts |

## Estado actual y pendientes (2026-10-07)

- `deploy.js` todavía tiene la contraseña del VPS escrita como valor por defecto, y figura en el
  historial de git. **Hay que cambiarla** y pasar el deploy a clave SSH (sprint 01, tarea 5). Que
  se borre del archivo no alcanza: queda en el historial.
- `docker-compose.yml` define un valor por defecto para `ENCRYPTION_KEY`. Producción tiene que
  usar una clave propia definida en el `.env` del VPS; hay que verificarlo antes de hacer público
  el repositorio (sprint 01, tarea 6).
- Antes de hacer público el repositorio se revisa todo el historial con un escáner de secretos
  (gitleaks) y se rota cualquier secreto que aparezca.

## Si un secreto se filtra

1. Rotarlo de inmediato en el servicio que corresponda.
2. Actualizarlo donde vive (tabla de arriba) y redesplegar.
3. No alcanza con borrarlo del repositorio: el historial de git lo conserva.
