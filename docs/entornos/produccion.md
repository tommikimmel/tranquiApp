# Producción

- **Dominio:** https://tranquisalud.com
- **Servidor:** un VPS con Docker (8 GB de RAM, 2 núcleos). Los datos de acceso no se documentan acá (ver
  [secretos.md](secretos.md)).
- **Proxy y TLS:** Traefik, compartido con otros servicios del VPS. A partir de la Fase 0 de microservicios,
  enruta `/api`, `/ws-tranqui` y `/ws-notificaciones` hacia el **API Gateway** (`gateway:8080`) y el resto al frontend (`tranqui-frontend:80`).
- **Aislamiento interno:** el monolito (`tranqui-backend`) y los servicios de soporte no publican puertos en el
  host ni tienen labels de Traefik; viven exclusivamente en la red interna de Docker (`tranqui-network`).

## Contenedores y recursos (Fase 0)

Todos los contenedores tienen límites de memoria (`mem_limit`) y CPU (`cpus`) fijados conforme a la sección 9
del plan de arquitectura de microservicios (`docs/arquitectura/microservicios.md`):

| Contenedor | Servicio Compose | Qué es / Rol | `mem_limit` | `cpus` |
|---|---|---|---|---|
| `tranqui-frontend` | `tranqui-frontend` | nginx que sirve el build de React (`frontend/nginx.conf`) | 64 MB | 0.5 |
| `tranqui-gateway` | `gateway` | Spring Cloud Gateway (ruteo, auth de servicio, rate limiting, métricas) | 288 MB | 1.0 |
| `tranqui-backend` | `tranqui-backend` | Monolito Spring Boot (JVM afinada, transactional outbox + relay) | 512 MB | 0.5 |
| `tranqui-db` | `tranqui-db` | PostgreSQL 15 con volumen persistente (`app_pgdata`) | 512 MB | 0.5 |
| `tranqui-rabbitmq` | `rabbitmq` | RabbitMQ 3.13 con topología de eventos (`tranqui.eventos`, colas lazy) | 256 MB | 0.5 |
| `tranqui-consul` | `consul` | Consul 1.19 (service discovery y catálogo de salud) | 128 MB | 0.5 |
| `tranqui-redis` | `redis` | Redis 7 (almacenamiento de token bucket para rate limiting) | 64 MB | 0.5 |
| `tranqui-victoriametrics` | `victoriametrics` | VictoriaMetrics single-node (retención 15d de métricas Prometheus, cachés al 30%) | 192 MB | 0.5 |
| `tranqui-cadvisor` | `cadvisor` | cAdvisor v0.49.1 (métricas de uso de CPU/RAM por contenedor) | 64 MB | 0.3 |
| `tranqui-node-exporter` | `node-exporter` | Prometheus Node Exporter v1.8.2 (métricas del host VPS) | 32 MB | 0.2 |
| `tranqui-notificaciones` | `notificaciones` | Servicio de notificaciones (Fase 1): mails y avisos en la app a partir de eventos, base propia `notificaciones` | 288 MB | 0.5 |
| `tranqui-chat` | `chat` | Servicio de chat (Fase 2): mensajería en vivo por `/ws-tranqui` e historial, base propia `chat` | 288 MB | 0.5 |
| `tranqui-calendario` | `calendario` | Servicio de calendario (Fase 3): Google Calendar y Meet, eventos de los turnos y horarios ocupados, base propia `calendario` | 288 MB | 0.5 |
| `tranqui-db-servicios` | `db-servicios` | Contenedor de una sola ejecución: crea la base y el usuario de cada servicio (`infra/postgres/crear-bases-servicios.sh`) y termina | 32 MB | 0.2 |
| `tranqui-bot`, `tranqui-bot-worker`, `wsp-db`, `wsp-redis` | — | Bot de WhatsApp (proyecto aparte, fuera de este repositorio) | ~70 MB | — |

### Presupuesto de memoria (RAM)

- **Total suma `mem_limit` Docker Compose (Fase 3):** `64 + 288 + 512 + 512 + 256 + 128 + 64 + 192 + 64 + 32 + 288 + 288 + 288 = 2.976 MB` (~2,98 GB; `db-servicios` corre unos segundos al arrancar y no suma).
- **Traefik y bot de WhatsApp:** ~112 MB.
- **Sistema operativo y buffers mínimos:** ~1 GB.
- **Consumo proyectado total en VPS:** **~4,1 GB**, ampliamente dentro del techo presupuestado de **~4,8 GB**
  de la sección 9.1 para la Fase 0, reservando ~3 GB de margen libre para picos y caché de disco de PostgreSQL.

### Afinación de JVM (backend y gateway)

Tanto el backend monolítico como el gateway configuran flags estrictos de JVM (`JAVA_TOOL_OPTIONS` y Dockerfiles)
según la sección 9.2:
`-XX:+UseSerialGC -XX:MaxRAMPercentage=65 -Xss256k -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=96m -Duser.timezone=America/Argentina/Buenos_Aires`.

### Afinación de JVM (servicios chicos: notificaciones, chat y calendario)

`-XX:+UseSerialGC -XX:MinRAMPercentage=30 -XX:MaxRAMPercentage=30 -Xss256k -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=128m -XX:TieredStopAtLevel=1`,
con `mem_limit` de 288 MB. Medido en la Fase 2 (con Native Memory Tracking): lo que no es heap (clases
~94 MB, tabla de símbolos ~23, código ~15, CDS de la JDK ~10, hilos) suma ~165-175 MB fijos, y con
SerialGC el heap crece hasta su máximo (~86 MB; el vivo usa ~55). El proceso queda en ~245 MB. Con
224 MB llegaba al tope del contenedor y vivía del swap; con 96 MB de metaspace (usa ~92) quedaba al
borde de un `OutOfMemoryError`. Con menos de ~250 MB la JVM toma `MinRAMPercentage` (50 % por
defecto) e ignora `MaxRAMPercentage`, por eso se fijan los dos.

## Configuración y variables de entorno

- Variables de producción en **`/srv/tranqui/.env`** del VPS (nombres y valores de ejemplo en `.env.template`). Se edita
  por SSH; ningún deploy la sube ni la pisa. El deploy se cancela si tiene `SEED_TEST_ACCOUNTS=true`.
- Estructura en el VPS:

  | Ruta | Qué es |
  |---|---|
  | `/srv/tranqui/.env` | Variables de producción (secretos reales, nunca en git) |
  | `/srv/tranqui/docker-compose.yml` | Copiado por cada deploy |
  | `/srv/tranqui/deploy-remoto.sh` | Script de deploy (copiado por cada deploy) |
  | `/srv/tranqui/infra/` | Configuraciones de Consul, RabbitMQ y VictoriaMetrics |
  | `/srv/tranqui/version-actual`, `deploys.log` | Versión desplegada e historial |
  | `/srv/tranqui/mantenimiento/` | Flag y archivos del modo mantenimiento |

- **Proyecto de Docker `app`:** todos los comandos usan `docker compose -p app`. De ese nombre depende
  el volumen de la base (`app_pgdata`); cambiarlo crearía una base nueva y vacía.

### Variables nuevas a cargar en `/srv/tranqui/.env`

Al incorporar la Fase 0, deben agregarse estas variables al archivo `/srv/tranqui/.env`:

```bash
# Clave HMAC-SHA256 para firma y validación de tokens entre servicios (mínimo 32 caracteres)
SERVICE_JWT_KEY=clave-secreta-de-servicio-de-al-menos-32-caracteres

# Clave de firma de los JWT de usuario (backend y gateway). Ver "Clave JWT".
JWT_SECRET=cambiar-por-una-clave-aleatoria-de-al-menos-32-bytes

# Credenciales de RabbitMQ para publicación y consumo de eventos
RABBITMQ_USER=tranqui
RABBITMQ_PASSWORD=password-aleatoria-fuerte-generada-para-rabbitmq
RABBITMQ_VHOST=/

# URL del monolito para derivación interna desde el API Gateway
TRANQUI_SERVICES_MONOLITO_URI=http://tranqui-backend:8080

# Habilitación del relay transaccional de outbox hacia RabbitMQ
EVENTOS_RELAY_ENABLED=true
EVENTOS_RELAY_BACKUP_POLL_MS=30000
```

### Clave JWT

`JWT_SECRET` firma los tokens de sesión de los usuarios. La usan el backend (que los firma) y el
gateway (que los valida), así que tiene que ser **la misma variable para los dos**. Es obligatoria:
si falta en `/srv/tranqui/.env`, `docker compose` corta con `Falta JWT_SECRET en .env` y no levanta nada.

- **Generarla** en el VPS y pegarla en `/srv/tranqui/.env` (nunca en el repo):
  ```bash
  openssl rand -base64 48
  ```
- **Al cargarla por primera vez se invalidan todas las sesiones abiertas:** los tokens firmados con
  la clave anterior dejan de valer y todos los usuarios tienen que volver a iniciar sesión. Conviene
  hacerlo en un horario de poco uso. Lo mismo pasa cada vez que se rota.
- No reutilizar `ENCRYPTION_KEY` ni `SERVICE_JWT_KEY`: cada clave tiene un uso distinto.

## Orden de primer arranque (Arranque escalonado)

Para evitar picos excesivos de CPU al arrancar múltiples servicios sobre los 2 núcleos del VPS, `docker-compose.yml`
define dependencias escalonadas con `condition: service_healthy`:

1. **Infraestructura base:** arrancan `tranqui-db`, `rabbitmq`, `consul`, `redis`, `victoriametrics`, `cadvisor` y `node-exporter`.
2. **Monolito (`tranqui-backend`):** espera a que `tranqui-db` y `rabbitmq` pasen sus healthchecks (`service_healthy`).
3. **API Gateway (`gateway`):** espera a que `tranqui-backend`, `consul`, `redis` y `victoriametrics` pasen sus healthchecks (`service_healthy`).
4. **Frontend (`tranqui-frontend`):** arranca recién cuando `gateway` está saludable.

### Pasos para el primer arranque manual en el VPS

1. Cargar las variables nuevas en `/srv/tranqui/.env`, incluida `JWT_SECRET` generada con
   `openssl rand -base64 48` (ver [Clave JWT](#clave-jwt): cierra las sesiones abiertas).
2. Asegurar que `/srv/tranqui/infra` esté sincronizado.
3. Descargar las imágenes publicadas:
   ```bash
   cd /srv/tranqui
   TRANQUI_VERSION=vX.Y.Z docker compose -p app --env-file /srv/tranqui/.env pull tranqui-backend tranqui-frontend gateway
   ```
4. Levantar los contenedores:
   ```bash
   TRANQUI_VERSION=vX.Y.Z docker compose -p app --env-file /srv/tranqui/.env up -d --remove-orphans
   ```
5. Verificar estado y salud:
   ```bash
   docker compose -p app ps
   curl -fsS https://tranquisalud.com/api/health
   ```

## Fase 3: servicio de calendario

Google Calendar y Meet (`/api/medicos/google-calendar`, incluidos el callback del OAuth y el webhook de
Google, que no cambian de URL) viven en el contenedor `calendario`. Crea, mueve y borra los eventos de
los turnos a partir de los eventos de turnos, y el Meet vuelve al monolito por `calendario.meet.creado`
(el monolito lo consume con `EVENTOS_CONSUMO_SERVICIO=monolito`). La disponibilidad de turnos le pide
los horarios ocupados por `/internal/calendario/...`; si no responde, la agenda sale sin ese filtro.

**Variables en `/srv/tranqui/.env`:** `CALENDARIO_DB_PASSWORD` (`openssl rand -hex 24`). Las de Google
(`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_CALENDAR_ENABLED`, `GOOGLE_CALENDAR_WATCH_ENABLED`) y
`APP_PUBLIC_URL`/`FRONTEND_URL` son las mismas que ya usa el monolito. La consola de Google no cambia.

**Clave de cifrado:** el servicio lee los tokens de Google que guardó el monolito, así que resuelve la
clave igual que él (`security.encryption.key`). Hoy el monolito no recibe `ENCRYPTION_KEY` y usa el
valor de respaldo del código: ver el hallazgo en `docs/specs/2026-10-20-fase-3-calendario.md` antes de
tocar esa clave (hay que cambiarla en los dos a la vez y volver a cifrar los tokens).

**Primer arranque:** copia del monolito los usuarios, las cuentas de Google (tokens cifrados, sin
descifrarlos: nadie vuelve a autorizar), la caché de eventos externos y los turnos confirmados con su
evento. La primera pasada (a los 30 s, con Google habilitado) hace una sincronización completa.

**El corte va en un solo paso:** `CALENDARIO_EXTERNO=true` en el monolito y la ruta del gateway al
servicio. Si cambia uno solo, el evento de Google se crea dos veces o ninguna.

**Vuelta atrás de la Fase 3** (sin desconectar a nadie):

1. En `/srv/tranqui/.env`: `CALENDARIO_EXTERNO=false` y
   `TRANQUI_SERVICES_CALENDARIO_URI=http://tranqui-backend:8080`.
2. `docker compose -p app stop calendario` y `docker compose -p app up -d tranqui-backend gateway`.
3. Devolver al monolito las cuentas de Google y los eventos de los turnos (idempotente; `--prueba`
   solo cuenta):
   ```bash
   node scripts/fase3-devolver-calendario.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```

**Volver al servicio después:** borrar la base del calendario para que el primer arranque vuelva a
copiar todo: `docker compose -p app exec tranqui-db psql -U <DB_USER> -d tranqui_app -c "DROP DATABASE calendario"`,
volver las dos variables a su valor y `docker compose -p app up -d`.

## Fase 2: servicio de chat

El chat (`/api/chat`, el WebSocket `/ws-tranqui`) vive en el contenedor `chat`. La lista de pacientes del
profesional (monolito) le pide los no leídos por `/internal/chat/...`; si el chat no responde, sale sin
ellos.

**Variable nueva en `/srv/tranqui/.env`:** `CHAT_DB_PASSWORD` (`openssl rand -hex 24`). `db-servicios`
crea la base `chat` en el arranque.

**Primer arranque:** el servicio copia del monolito los usuarios, los turnos confirmados y todos los
mensajes, conservando los ids. Hasta que termina, los chats viejos pueden no aparecer.

**Vuelta atrás de la Fase 2** (sin perder conversaciones):

1. En `/srv/tranqui/.env`: `TRANQUI_SERVICES_CHAT_URI=http://tranqui-backend:8080`.
2. `docker compose -p app stop chat` y `docker compose -p app up -d gateway`.
3. Devolver al monolito los mensajes escritos en el servicio (idempotente; `--prueba` solo cuenta):
   ```bash
   node scripts/fase2-devolver-mensajes.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```

**Volver al servicio después:** borrar la base del chat para que el primer arranque vuelva a copiar
todo (incluidos los mensajes escritos en el monolito durante la vuelta atrás):
`docker compose -p app exec tranqui-db psql -U <DB_USER> -d tranqui_app -c "DROP DATABASE chat"`, sacar la
variable del `.env` y `docker compose -p app up -d`.

## Fase 1: servicio de notificaciones

Los mails y las notificaciones de la app (la campanita) salen del contenedor `notificaciones`, a
partir de los eventos que publica el monolito. El monolito solo manda directo los mails sin evento:
contraseña temporal, copia de datos, documento adjunto, receta, alerta al admin y novedades.

**Variables nuevas en `/srv/tranqui/.env`:**

- `NOTIFICACIONES_DB_PASSWORD`: contraseña del usuario `notificaciones` de PostgreSQL. Generarla con
  `openssl rand -hex 24`. El contenedor `db-servicios` crea la base y el usuario en cada arranque
  (es idempotente, no toca `app_pgdata` más que para eso).
- `RESEND_API_KEY` ahora también la usa el servicio (es la misma).

**Primer arranque:** el servicio arranca después del gateway, copia los contactos y las
notificaciones de las últimas 24 h del monolito por la ruta interna `/internal/monolito/**`, y
empieza a consumir `notificaciones.eventos`. Para comparar las dos bases (solo lee):

```bash
node scripts/fase1-comparar-notificaciones.mjs --proyecto app --contenedor tranqui-db   --usuario <DB_USER> --base tranqui_app
```

**Vuelta atrás de la Fase 1** (sin tocar datos):

1. En `/srv/tranqui/.env`: `NOTIFICACIONES_ENVIO_DIRECTO=true` y
   `TRANQUI_SERVICES_NOTIFICACIONES_URI=http://tranqui-backend:8080`.
2. Frenar el servicio **antes** de aplicar, o los mails salen dos veces (del monolito y del servicio):
   `docker compose -p app stop notificaciones`.
3. Aplicar: `docker compose -p app up -d tranqui-backend gateway`. El monolito vuelve a mandar los
   mails y a crear las notificaciones; los eventos se acumulan en `notificaciones.eventos`.

**Volver al servicio después de una vuelta atrás:** esos eventos acumulados ya los atendió el
monolito. Vaciar la cola antes de prenderlo (panel de RabbitMQ, cola `notificaciones.eventos`,
"Purge"), volver las dos variables a su valor y `docker compose -p app up -d`.

## VUELTA ATRÁS (Rollback a Monolito directo)

Si el API Gateway o algún servicio de infraestructura auxiliar presenta problemas en producción, la arquitectura
permite **volver atrás al monolito directo de forma inmediata** sin apagar la base de datos ni redeployar imágenes:

1. Editar `/srv/tranqui/docker-compose.yml` en el VPS.
2. Quitar o comentar los labels de Traefik en el servicio `gateway`:
   ```yaml
   # labels:
   #   - "traefik.enable=true"
   #   - "traefik.http.routers.tranqui-gateway.entrypoints=websecure"
   #   - "traefik.http.routers.tranqui-gateway.rule=Host(`tranquisalud.com`) && (PathPrefix(`/api`) || PathPrefix(`/ws-tranqui`) || PathPrefix(`/ws-notificaciones`))"
   #   - "traefik.http.routers.tranqui-gateway.tls.certresolver=letsencrypt"
   #   - "traefik.http.services.tranqui-gateway.loadbalancer.server.port=8080"
   ```
3. Restaurar los labels de Traefik en el servicio `tranqui-backend`:
   ```yaml
   labels:
     - "traefik.enable=true"
     - "traefik.http.routers.tranqui-backend.entrypoints=websecure"
     - "traefik.http.routers.tranqui-backend.rule=Host(`tranquisalud.com`) && (PathPrefix(`/api`) || PathPrefix(`/ws-tranqui`))"
     - "traefik.http.routers.tranqui-backend.tls.certresolver=letsencrypt"
     - "traefik.http.services.tranqui-backend.loadbalancer.server.port=8080"
   ```
4. Aplicar el cambio de configuración:
   ```bash
   docker compose -p app --env-file /srv/tranqui/.env up -d tranqui-backend gateway
   ```
5. Traefik detecta la reconfiguración dinámicamente en segundos y vuelve a enviar todo el tráfico de `/api` y `/ws-tranqui` directamente al monolito.

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
- Solo cubre el sitio: la API (`/api`, que Traefik enruta al gateway/backend) sigue respondiendo.
  Para una tarea de base de datos, además de prender el mantenimiento hay que frenar el backend.
- El script se conecta por SSH con las mismas credenciales que el deploy (ver `secretos.md`).

**Mail de novedades:** al prender con `--novedades archivo.md`, al apagar se manda a todos los
usuarios un mail con lo que cambió (Broadcasts de Resend). Ver [docs/novedades.md](../novedades.md).

**Regla:** ningún agente de IA prende ni apaga el mantenimiento sin aprobación explícita del dueño
para esa acción puntual.
