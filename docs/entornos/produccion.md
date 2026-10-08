# Producción

- **Dominio:** https://tranquisalud.com
- **Servidor:** un VPS con Docker. Los datos de acceso no se documentan acá (ver
  [secretos.md](secretos.md)).
- **Proxy y TLS:** Traefik, compartido con otros servicios del VPS. Enruta `/api` y `/ws-tranqui`
  al backend y el resto al frontend.

## Contenedores

| Contenedor | Qué es |
|---|---|
| `tranqui-frontend` | nginx que sirve el build de React (`frontend/nginx.conf`) |
| `tranqui-backend` | Spring Boot |
| `tranqui-db` | PostgreSQL 15 con volumen persistente |
| `tranqui-bot`, `tranqui-bot-worker`, `wsp-db`, `wsp-redis` | Bot de WhatsApp (proyecto aparte, fuera de este repositorio; implementación a futuro) |

## Configuración

- Variables de producción en **`/srv/tranqui/.env`** del VPS (nombres en `.env.template`). Se edita
  por SSH; ningún deploy la sube ni la pisa. El deploy se cancela si tiene `SEED_TEST_ACCOUNTS=true`.
- Estructura en el VPS:

  | Ruta | Qué es |
  |---|---|
  | `/srv/tranqui/.env` | Variables de producción |
  | `/srv/tranqui/docker-compose.yml` | Copiado por cada deploy |
  | `/srv/tranqui/deploy-remoto.sh` | Script de deploy (copiado por cada deploy) |
  | `/srv/tranqui/version-actual`, `deploys.log` | Versión desplegada e historial |
  | `/srv/tranqui/mantenimiento/` | Flag y archivos del modo mantenimiento |

- **Proyecto de Docker `app`:** todos los comandos usan `docker compose -p app`. De ese nombre depende
  el volumen de la base (`app_pgdata`); cambiarlo crearía una base nueva y vacía.
- Perfil de Spring: `prod` por defecto (`SPRING_PROFILES_ACTIVE`).
- Mercado Pago en modo real (no sandbox): cobros y débitos son reales.
- Recetas (QBI2) y facturación (ARCA) deshabilitadas.

## Base de datos

- Esquema: Hibernate `ddl-auto: update` crea lo nuevo; Flyway aplica las migraciones de
  `backend/src/main/resources/db/migration` (la base existente se tomó como versión 1).
- **Nadie toca la base de producción sin aprobación explícita del dueño para esa acción.**

## Deploy

Ver [docs/flujo/releases.md](../flujo/releases.md).

## Modo mantenimiento

Mientras está activo, cualquier página de tranquisalud.com muestra solo un aviso de mantenimiento
(estado 503 con `Retry-After`, para que los buscadores no lo indexen). Funciona aunque el backend
esté apagado y no requiere reiniciar nada.

| Comando (desde la raíz del repo) | Qué hace |
|---|---|
| `npm run mantenimiento -- on` | Prende el aviso con el texto por defecto |
| `npm run mantenimiento -- on "Volvemos a las 22 hs"` | Prende el aviso con un mensaje extra |
| `npm run mantenimiento -- off` | Vuelve el sitio a la normalidad |
| `npm run mantenimiento -- estado` | Muestra si está activo |

**Acceso de administrador:** al prender, el comando devuelve un link
`https://tranquisalud.com/__acceso-mantenimiento?token=...`. Abriéndolo, ese navegador queda con una
cookie que le deja ver el sitio real (con un aviso naranja abajo a la izquierda) mientras el resto
ve el aviso. El token es nuevo en cada mantenimiento y se borra al apagarlo. No lo compartas.

**Cómo funciona:**

- La carpeta `/srv/tranqui/mantenimiento` del VPS está montada en `tranqui-frontend` (solo
  lectura). Está fuera de `/app`, así que un deploy no la borra ni cambia el estado.
- Mientras está activo contiene `activo` (el flag), `info.json` (el mensaje) y `token.conf` (el
  token del acceso de administrador). nginx (`frontend/nginx.conf`) revisa el flag en cada pedido.
- La página es `frontend/public/mantenimiento/index.html`, autocontenida (no depende de React ni
  del backend).
- Solo cubre el sitio: la API (`/api`, que Traefik enruta directo al backend) sigue respondiendo.
  Para una tarea de base de datos, además de prender el mantenimiento hay que frenar el backend.
- El script se conecta por SSH con las mismas credenciales que el deploy (ver `secretos.md`).

**Mail de novedades:** al prender con `--novedades archivo.md`, al apagar se manda a todos los
usuarios un mail con lo que cambió (Broadcasts de Resend). Ver [docs/novedades.md](../novedades.md).

**Regla:** ningún agente de IA prende ni apaga el mantenimiento sin aprobación explícita del dueño
para esa acción puntual.
