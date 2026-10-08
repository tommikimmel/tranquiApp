# Spec: modo mantenimiento con flag

- **Fecha:** 2026-10-07
- **Tipo:** feature
- **Sprint:** sprint-01 (tarea 4)
- **Estado:** implementada (pendiente de deploy)
- **Rama / PR:** `feature/infra-modo-mantenimiento`

## Entendimiento del problema

Hace falta poder poner el sitio en mantenimiento a voluntad: mientras el flag está prendido,
cualquier página muestra solo un aviso, y al apagarlo todo vuelve a la normalidad al instante. El
primer uso es la limpieza de la base (tarea 7), con el backend frenado, así que no puede depender
del backend. Además, `deploy.js` borra `/app` en cada deploy, y Traefik enruta `/api` directo al
backend (no pasa por el nginx del frontend).

## Solución propuesta

1. Flag como archivo en el VPS: `/srv/tranqui/mantenimiento/activo` (fuera de `/app`), montado en
   `tranqui-frontend` como solo lectura.
2. nginx revisa el flag en cada pedido: si existe, responde 503 con `Retry-After` y la página de
   mantenimiento; si no, sitio normal. Sin reinicios.
3. Página autocontenida `frontend/public/mantenimiento/index.html` con la marca, sin emojis,
   adaptada a celular. Texto: "Estamos haciendo mejoras para vos. Volvemos en unos minutos", más un
   mensaje opcional.
4. `npm run mantenimiento -- on ["mensaje"] | off | estado`, por SSH con las credenciales del deploy;
   verifica que el sitio responda 503 o 200. En la tarea 5 se suma un botón en GitHub Actions.
5. Acceso de administrador (aprobado): al prender se genera un token; el link
   `/__acceso-mantenimiento?token=...` deja una cookie con la que nginx muestra el sitio real, con
   un aviso naranja. El token se borra al apagar.

## Criterios de aceptación

- [x] Con el flag prendido, todas las rutas (páginas, JS, videos) responden 503 con la página de mantenimiento; el logo y el mensaje cargan.
- [x] El mensaje opcional se muestra; sin mensaje, el texto por defecto.
- [x] Con el flag apagado, el sitio queda como antes (incluidos los videos con `text/vtt`).
- [x] Funciona sin backend.
- [x] Un deploy no cambia el estado (la carpeta está fuera de `/app`).
- [x] Acceso de administrador: token incorrecto o ausente da 403; con el token correcto, sitio real con aviso; al apagar, el acceso viejo da 403 y la cookie vieja no muestra el aviso.
- [ ] Prueba del script `on`/`off` en producción (requiere deploy y aprobación).

## Riesgos e impacto

Cambia `frontend/nginx.conf` y `docker-compose.yml` (montaje). Necesita un deploy para quedar
activo. La API no se bloquea (para la limpieza de la base se frena el backend). El script usa las
credenciales del deploy hasta la tarea 5.

## Resultado

Verificado con un nginx real en Docker sobre el build de producción, simulando cada estado del flag
como lo hace el script. Dos correcciones durante la prueba:

- El valor de un `map` de nginx queda cacheado durante todo el pedido: la redirección interna del
  `error_page` volvía a bloquearse y se veía la página genérica de nginx. Se reemplazó por `set`/`if`.
- `absolute_redirect off`: detrás de Traefik nginx arma redirecciones con `http://`; ahora son
  relativas.
