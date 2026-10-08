# Secretos

**Ningún secreto se guarda en el repositorio**: ni en el código, ni en la documentación, ni en
`docker-compose.yml`. Esto incluye contraseñas, claves de API, tokens, claves SSH y claves de
cifrado. `.env` está en `.gitignore`; `.env.template` lista los nombres con valores vacíos.

## Dónde vive cada secreto

| Secreto | Dónde vive | Cómo se cambia |
|---|---|---|
| Clave SSH de GitHub Actions (privada) | Secreto `VPS_SSH_KEY` de GitHub (Settings → Secrets and variables → Actions) | Generar un par nuevo (`ssh-keygen -t ed25519`), agregar la pública a `/root/.ssh/authorized_keys` del VPS, reemplazar el secreto y borrar la pública vieja |
| Datos del VPS para Actions | Secretos `VPS_HOST`, `VPS_USER`, `VPS_KNOWN_HOSTS` (línea de `ssh-keyscan`) y `VPS_HOST_FINGERPRINT` (`SHA256:...`) | Reemplazarlos en GitHub si cambia el servidor |
| Tu clave SSH personal (privada) | Solo en tu máquina (`~/.ssh/tranqui_vps`) | Igual que la de Actions, con tu propia clave |
| Configuración local de los scripts | `.env.vps` en la raíz del repo (ignorado por git): `VPS_HOST`, `VPS_USER`, `VPS_PRIVATE_KEY_PATH`, `VPS_HOST_FINGERPRINT` | Editando el archivo |
| Variables de producción (base, cifrado, Mercado Pago, Resend, Google, `NOVEDADES_TOKEN`) | `/srv/tranqui/.env` en el VPS | Por SSH, editando el archivo; después un deploy (o `docker compose -p app --env-file /srv/tranqui/.env -f /srv/tranqui/docker-compose.yml up -d`) |
| Contraseña de root del VPS | Solo en el gestor de contraseñas del dueño | La cambia el dueño (`passwd` en el VPS). No la usa ningún script ni el CI |
| Token de GHCR para publicar imágenes | No hace falta: Actions usa el `GITHUB_TOKEN` automático | — |

Las imágenes de GHCR (`tranqui-backend`, `tranqui-frontend`) son públicas: no contienen secretos.

### Ejemplo de `.env.vps`

```
VPS_HOST=<ip o dominio del VPS>
VPS_USER=root
VPS_PRIVATE_KEY_PATH=~/.ssh/tranqui_vps
VPS_HOST_FINGERPRINT=SHA256:<huella del servidor>
```

## Estado (2026-10-07)

- La contraseña de root estuvo escrita en `deploy.js` y figura en el historial de git: **hay que
  cambiarla**. Desde la tarea 5 ningún script la usa.
- Con las claves funcionando, se apaga el login SSH por contraseña (`PasswordAuthentication no`).
- `docker-compose.yml` ya no tiene valores por defecto para `ENCRYPTION_KEY` ni `DB_PASSWORD`: tienen
  que estar en el `.env`.
- Antes de hacer público el repositorio se revisa todo el historial con un escáner de secretos
  (gitleaks) y se rota cualquier secreto que aparezca.

## Si un secreto se filtra

1. Rotarlo de inmediato en el servicio que corresponda.
2. Actualizarlo donde vive (tabla de arriba) y redesplegar.
3. No alcanza con borrarlo del repositorio: el historial de git lo conserva.
