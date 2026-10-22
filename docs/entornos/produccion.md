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

### Clave de cifrado

`ENCRYPTION_KEY` cifra los tokens de Mercado Pago y de Google guardados en la base (AES-256-GCM, formato
`v2:`) y firma el `state` del OAuth y los canales de Google. Es obligatoria, de al menos 32 bytes, y el
backend no arranca si falta o si tiene un valor publicado en el repo (el de `.env.template` o el viejo).

Hasta la v1.1.0 el backend **no la usaba**: leía `security.encryption.key`, que nada mapeaba, y cifraba
con una clave escrita en el código, pública porque el repo es público.

- **Primer deploy con la corrección:**
  1. Generar una clave nueva en el VPS y pegarla en `/srv/tranqui/.env` (nunca en el repo):
     `openssl rand -base64 48`. No reutilizar la de antes ni `JWT_SECRET`.
  2. Desplegar. Al arrancar, `RecifradoTokensLegados` pasa a la clave nueva los tokens viejos y deja
     en el log cuántos pasó (`Cifrado de tokens: N valor(es)...`). Corre en cada arranque y no hace
     nada cuando ya no quedan.
  3. Los profesionales siguen conectados a Mercado Pago y a Google sin volver a autorizar. Los canales
     de Google firmados antes valen hasta que la renovación diaria los vuelve a registrar.
- **Volver a una imagen anterior:** la v1.1.0 no lee el formato nuevo. Antes de bajar la imagen,
  devolver los valores originales desde `cifrado_legado_respaldo` (con aprobación, como todo lo que
  toca la base), una vez por columna:
  ```sql
  UPDATE usuario u SET mp_access_token_encrypted = r.valor
  FROM cifrado_legado_respaldo r
  WHERE r.usuario_id = u.id AND r.columna = 'mp_access_token_encrypted';
  -- igual con mp_refresh_token_encrypted, google_access_token_encrypted y google_refresh_token_encrypted
  ```
  Un token que se renovó después del deploy no tiene respaldo (sigue en `v2:`) y ese profesional tiene
  que volver a conectar la cuenta. El respaldo se borra solo a los 14 días, porque está cifrado con la
  clave pública.
- Rotarla más adelante no tiene un proceso automático: los tokens en `v2:` solo se leen con la clave con
  la que se cifraron.

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
