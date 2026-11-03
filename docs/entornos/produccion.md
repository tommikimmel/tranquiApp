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
| `tranqui-pagos` | `pagos` | Servicio de pagos (Fase 4 y Fase 8): Mercado Pago de turnos y documentos (Fase 4), suscripciones, planes y facturación ARCA (Fase 8), base propia `pagos` | 288 MB | 0.5 |
| `tranqui-profesionales` | `profesionales` | Servicio de profesionales (Fase 5 y Fase 7): perfil, buscador, tarifas, disponibilidad, historia clínica y recetas (Fase 7), base propia `profesionales` | 288 MB | 0.5 |
| `tranqui-turnos` | `turnos` | Servicio de turnos (Fase 6): reservas, agenda, cancelación, reprogramación, asistencia y documentos, base propia `turnos` | 288 MB | 0.5 |
| `tranqui-identidad` | `identidad` | Servicio de identidad (Fase 9): registro, login y JWT RS256, Mi Cuenta, soporte, administración de usuarios, portón del sitio y novedades, base propia `identidad`. Publica `127.0.0.1:8084` en el VPS solo para el script de novedades | 288 MB | 0.5 |
| `tranqui-db-servicios` | `db-servicios` | Contenedor de una sola ejecución: crea la base y el usuario de cada servicio (`infra/postgres/crear-bases-servicios.sh`) y termina | 32 MB | 0.2 |
| `tranqui-bot`, `tranqui-bot-worker`, `wsp-db`, `wsp-redis` | — | Bot de WhatsApp (proyecto aparte, fuera de este repositorio) | ~70 MB | — |

### Presupuesto de memoria (RAM)

- **Total suma `mem_limit` Docker Compose (Fase 6, Fase 7 y Fase 8):** `64 + 288 + 512 + 512 + 256 + 128 + 64 + 192 + 64 + 32 + 288 + 288 + 288 + 288 + 288 + 288 = 3.840 MB` (~3,84 GB; `db-servicios` corre unos segundos al arrancar y no suma). Las Fases 7 y 8 no agregan ningún contenedor (historia clínica en `profesionales`, suscripciones en `pagos`, decisión 1), por lo que la suma de límites se mantiene idéntica en 3.840 MB y ~5,0 GB proyectados. El consumo y trabajo de facturación (SOAP de ARCA y PDF) se mide en la verificación de punta a punta.
- **Fase 9:** el contenedor `identidad` suma 288 MB mientras el monolito siga prendido (no se apaga en esta fase):
  **4.128 MB** de límites y **~5,3 GB** proyectados. Se mide en la verificación de punta a punta; el apagado del
  monolito (una fase posterior) libera sus 512 MB.
- **Traefik y bot de WhatsApp:** ~112 MB.
- **Sistema operativo y buffers mínimos:** ~1 GB.
- **Consumo proyectado total en VPS:** **~5,0 GB**, por encima del techo de **~4,8 GB** de la sección 9.1, con
  ~3 GB de margen libre en el VPS de 8 GB para picos y caché de disco de PostgreSQL. Decisión de las Fases 6, 7 y 8: se
  acepta y se mide el consumo real en el VPS antes del deploy (los servicios usan bastante menos que su límite:
  pagos ~175 MB de 288). Si hace falta, se baja el monolito a 384 MB o los servicios chicos a 256 MB.

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

## Fase 10: validación de los eventos

Cada servicio y el monolito validan el data de los eventos contra las reglas de su record (ids, mails, montos,
estados), al publicar y al consumir: nunca se confía en lo que manda otro servicio. `EVENTOS_VALIDACION_MODO`:

- `loguear` (por defecto en el compose de producción, la primera semana después del deploy): un evento inválido se
  publica y se procesa igual, con un aviso en el log que nombra el tópico, el campo y la regla (nunca el valor).
- `rechazar` (después de revisar los logs de esa semana): un evento inválido no se publica y, si llega, va a la DLQ
  del servicio sin tocar ninguna copia. En local y en los tests ya es así.

Para revisar la semana: `docker compose -p app logs --since 168h | grep "data inválido"`. Si no aparece nada (o lo que
aparece se corrigió), poner `EVENTOS_VALIDACION_MODO=rechazar` en `/srv/tranqui/.env` y reiniciar el monolito y los
servicios.

## Fase 9: identidad en su propio servicio

La cuenta y la sesión (`/api/auth/**`, incluida Mi Cuenta), los tickets (`/api/tickets/**`, `/api/admin/tickets/**`),
soporte (`/api/soporte/**`), la administración de usuarios (`/api/admin/users/**`), el portón del sitio
(`/api/site-access/**`) y las novedades viven en el contenedor `identidad`, sobre su base propia `identidad`. El monolito
**no se apaga** en esta fase: queda como vuelta atrás.

- **Requisito:** la Fase 8 tiene que estar cortada antes (el paywall del gateway pasa a preguntarle a pagos).
- **JWT RS256:** solo identidad firma los tokens de usuario (`JWT_PRIVATE_KEY`); el gateway, los servicios y el
  monolito los validan con la clave pública (`JWT_PUBLIC_KEY`). Durante siete días después del corte se aceptan también
  los tokens HS256 que emitía el monolito (`JWT_HS256_ACEPTADO=true`), así nadie pierde la sesión.
- **Fuentes de datos:** con `TRANQUI_FUENTE_IDENTIDAD=identidad`, pagos, profesionales y turnos leen los datos de usuario de
  `/internal/identidad/...` y lo que no es de identidad de su dueño (Mercado Pago y la suscripción de pagos, los turnos de
  turnos, la agenda de profesionales).
- **Novedades:** `scripts/mantenimiento.mjs` las manda a `127.0.0.1:8084/internal/identidad/novedades` cuando el `.env`
  tiene `IDENTIDAD_EXTERNO=true`; si no, al monolito, que en el compose de microservicios no publica puerto: **antes del
  corte de la Fase 9 las novedades no salen** (como hoy desde la Fase 0).

**Claves RSA** (una vez, en el VPS; nunca se versionan ni salen del servidor):
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt-privada.pem
echo "JWT_PRIVATE_KEY=$(openssl pkcs8 -topk8 -nocrypt -in /tmp/jwt-privada.pem -outform DER | base64 -w0)"
echo "JWT_PUBLIC_KEY=$(openssl rsa -in /tmp/jwt-privada.pem -pubout -outform DER | base64 -w0)"
shred -u /tmp/jwt-privada.pem
```
Las dos líneas van al `/srv/tranqui/.env` (base64 de una línea: PKCS#8 la privada, X.509 la pública). También hace
falta `IDENTIDAD_DB_PASSWORD` (`openssl rand -hex 24`) antes del primer `up`, para que `db-servicios` cree la base.

**Primer arranque / carga inicial:** con `IDENTIDAD_CARGA=true`, el servicio copia del monolito `usuario` (solo las 57
columnas de identidad), `ticket`, `ticket_mensaje` y `novedades_envio`, con los mismos ids, hashes y versiones,
únicamente si las cuatro tablas están vacías. Antes de insertar valida cada fila (ids y mails sin repetir, roles, fechas,
claves del contrato): si algo no cumple, aborta sin insertar nada y lo dice en el log con los ids.

**El corte va con la app en mantenimiento** (hay contraseñas y sesiones):

1. `npm run mantenimiento -- on "Volvemos en unos minutos"`.
2. Cargar `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` y `IDENTIDAD_DB_PASSWORD` en `/srv/tranqui/.env` (con `JWT_HS256_ACEPTADO=true`,
   que es el valor por defecto) y reiniciar gateway, servicios y monolito para que acepten los dos formatos de token:
   ```bash
   docker compose -p app up -d gateway notificaciones chat calendario pagos profesionales turnos tranqui-backend
   ```
3. En `/srv/tranqui/.env`: `IDENTIDAD_EXTERNO=true` y reiniciar el monolito:
   ```bash
   docker compose -p app up -d tranqui-backend
   ```
   Desde acá el monolito no escribe identidad: sus rutas de cuenta, soporte y administración de usuarios responden 409,
   igual que sus escrituras internas de pacientes y de la identidad del profesional.
4. En `/srv/tranqui/.env`: `IDENTIDAD_CARGA=true` y levantar identidad:
   ```bash
   docker compose -p app up -d identidad
   ```
   Esperar que esté sano y el log "Carga inicial de identidad completada: N usuarios, ...". Si la carga aborta, los logs
   de `identidad` dicen qué ids no cumplen: corregirlos en el monolito (con aprobación) o volver atrás.
5. En `/srv/tranqui/.env`: `TRANQUI_FUENTE_IDENTIDAD=identidad` y reiniciar pagos, profesionales y turnos:
   ```bash
   docker compose -p app up -d pagos profesionales turnos
   ```
6. En `/srv/tranqui/.env`: `TRANQUI_PAYWALL_FUENTE=pagos` y `TRANQUI_SERVICES_IDENTIDAD_URI=lb://identidad`, y reiniciar el
   gateway:
   ```bash
   docker compose -p app up -d gateway
   ```
7. Conciliar y, sin diferencias, mantenimiento off:
   ```bash
   node scripts/fase9-conciliar-identidad.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   npm run mantenimiento -- off
   ```

**Siete días después del corte:** `JWT_HS256_ACEPTADO=false` en `/srv/tranqui/.env` y reiniciar gateway, servicios, identidad y
monolito. Desde ahí solo valen los tokens RS256 (los HS256 ya vencieron: duran 7 días).

**Vuelta atrás de la Fase 9** (en mantenimiento):

1. `npm run mantenimiento -- on "Mantenimiento por actualización"`.
2. En `/srv/tranqui/.env`: `IDENTIDAD_CARGA=false` y bajar identidad:
   ```bash
   docker compose -p app stop identidad
   ```
3. Devolver al monolito lo creado o cambiado en identidad (aborta sin tocar nada si un mail pertenece a otro usuario en el
   monolito):
   ```bash
   node scripts/fase9-devolver-identidad.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
4. En `/srv/tranqui/.env`: sacar `TRANQUI_FUENTE_IDENTIDAD`, `TRANQUI_PAYWALL_FUENTE` y `TRANQUI_SERVICES_IDENTIDAD_URI`, poner
   `IDENTIDAD_EXTERNO=false` y reiniciar monolito, servicios y gateway:
   ```bash
   docker compose -p app up -d tranqui-backend pagos profesionales turnos gateway
   ```
   Las sesiones RS256 abiertas siguen valiendo: el monolito valida con `JWT_PUBLIC_KEY`. Los tokens nuevos vuelven a ser
   HS256, así que `JWT_HS256_ACEPTADO` tiene que quedar en `true`.
5. Conciliar:
   ```bash
   node scripts/fase9-conciliar-identidad.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
6. `npm run mantenimiento -- off`.

**Volver a cortar después de una vuelta atrás:**
La carga inicial de identidad solo corre con las cuatro tablas vacías: después de haber devuelto y conciliado sin
diferencias, y con la app en mantenimiento, vaciarlas en la base `identidad` y repetir el corte:
```bash
docker compose -p app exec tranqui-db psql -U <DB_USER> -d identidad -c "TRUNCATE TABLE ticket_mensaje, ticket, perfil_registro, usuario, novedades_envio RESTART IDENTITY CASCADE;"
```

## Fase 8: suscripciones, planes y facturación en el servicio de pagos

Las suscripciones (`/api/subscriptions/**`), los planes, las features, la administración de suscripciones
(`/api/admin/subscriptions/**`), los pagos de suscripciones y la facturación con ARCA viven en el contenedor
`pagos`, sobre su base propia `pagos`.

- **Sin contenedor nuevo:** las suscripciones y la facturación se alojan en el servicio de pagos (decisión 1).
  No suma memoria al presupuesto del VPS (se mantiene el límite de 288 MB). El impacto del trabajo de facturación
  (SOAP de ARCA y PDFs) se evalúa en la verificación de punta a punta (riesgo de la spec).
- **Facturación con ARCA:** la integración se migró tal cual y sigue apagada (`ARCA_ENABLED=false`), ya que
  todavía no está definida en producción (ver [docs/cambios-a-futuro.md](../cambios-a-futuro.md), punto 4).
- **Mercado Pago de plataforma:** usa las credenciales de la cuenta administradora (`MP_ADMIN_ACCESS_TOKEN` y
  `MP_ADMIN_PUBLIC_KEY`), tomadas del `.env` igual que en el monolito.
- **Acceso a suscripciones y recetas:** el monolito mantiene una copia local en `suscripcion_acceso` alimentada
  por el evento `suscripciones.suscripcion.cambio-estado.v2` y una resincronización periódica cada 15 minutos
  contra `/internal/suscripciones/estado`. Con esto, el portón del gateway y el servicio de profesionales siguen
  consultando al monolito sin cambios en esta fase.

**Variables en `/srv/tranqui/.env`:** `SUSCRIPCIONES_EXTERNO=true` en el monolito, `PAGOS_SUSCRIPCIONES=true` en
el servicio de pagos y `TRANQUI_SERVICES_SUSCRIPCIONES_URI=lb://pagos` en el gateway. Las credenciales de
`MP_ADMIN_*` y `ARCA_*` ya existían en el `.env`.

**Primer arranque / carga inicial:** con `PAGOS_SUSCRIPCIONES=true`, el servicio copia del monolito las ocho
tablas (`plans`, `features`, `plan_features`, `subscriptions`, `subscription_events`, `subscription_payments`,
`invoices` e `invoice_sequences`) con sus mismos ids y numeración correlativa, únicamente si las ocho tablas están
vacías. Antes de insertar nada, verifica unicidad por profesional: si existen dos suscripciones para el mismo
`professional_id`, la carga aborta sin insertar nada.

**El corte va con la app en mantenimiento** (hay plata y numeración de comprobantes fiscales):

1. `npm run mantenimiento -- on "Volvemos en unos minutos"`.
2. En `/srv/tranqui/.env`: `SUSCRIPCIONES_EXTERNO=true` y reiniciar SOLO el monolito:
   ```bash
   docker compose -p app up -d tranqui-backend
   ```
   Desde acá el monolito no escribe suscripciones: sus rutas responden 409, no corren sus crons, y los webhooks
   de suscripción que le reenvía pagos reciben 409. Pagos responde 500 y Mercado Pago los reintenta más tarde,
   así que no se pierden. Su copia de acceso queda vacía hasta el paso 5; no importa porque está en mantenimiento.
3. En `/srv/tranqui/.env`: `PAGOS_SUSCRIPCIONES=true` y reiniciar pagos:
   ```bash
   docker compose -p app up -d pagos
   ```
4. Esperar que `pagos` esté sano y el log "Carga inicial de suscripciones completada"
   > **Si la carga aborta por duplicados:** revisar los logs de `pagos` para identificar el `profesionalId` y
   > los ids de suscripción involucrados. Para resolverlo hay que volver `SUSCRIPCIONES_EXTERNO=false` mientras
   > se corrige en el monolito (o corregirlo por SQL en el monolito con aprobación), y repetir desde el paso 2.
5. Reiniciar el monolito otra vez para que resincronice su copia del acceso ya con el servicio listo
   (se usa `restart` porque `up -d` no reinicia el contenedor si su configuración no cambió en el `.env`):
   ```bash
   docker compose -p app restart tranqui-backend
   ```
   Esperar en sus logs:
   "Resincronización de suscripciones completada: N profesionales actualizados."
   con `N` mayor que cero si hay suscripciones. Sin esto, al salir de mantenimiento el portón del gateway y el
   buscador verían a todos los profesionales sin acceso hasta la resincronización automática (hasta 15 minutos).
6. En `/srv/tranqui/.env`: `TRANQUI_SERVICES_SUSCRIPCIONES_URI=lb://pagos` y reiniciar el gateway:
   ```bash
   docker compose -p app up -d gateway
   ```
7. Conciliar y, sin diferencias, mantenimiento off:
   ```bash
   node scripts/fase8-conciliar-suscripciones.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   npm run mantenimiento -- off
   ```
   Igual que con turnos y clínica, la conciliación se corre justo después del corte y tras una vuelta atrás.

**Vuelta atrás de la Fase 8:**

1. `npm run mantenimiento -- on "Mantenimiento por actualización"`.
2. En `/srv/tranqui/.env`: `PAGOS_SUSCRIPCIONES=false` y reiniciar pagos:
   ```bash
   docker compose -p app up -d pagos
   ```
   El servicio deja de procesar y vuelve a reenviar los webhooks de suscripción al monolito. El monolito todavía
   tiene `SUSCRIPCIONES_EXTERNO=true`, así que responde 409 y Mercado Pago reintenta después: nada se pierde.
3. Correr `fase8-devolver-suscripciones.mjs` para devolver al monolito todas las suscripciones, eventos, pagos,
   facturas y secuencias creados o modificados en el servicio, con validación estricta de conflictos (aborta si
   difiere algún campo inmutable como el profesional, monto de un pago o número/CAE de una factura emitida) y
   ajuste de secuencias:
   ```bash
   node scripts/fase8-devolver-suscripciones.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
4. En `/srv/tranqui/.env`: `SUSCRIPCIONES_EXTERNO=false`, sacar `TRANQUI_SERVICES_SUSCRIPCIONES_URI` (o apuntarla
   a `http://tranqui-backend:8080`) y reiniciar monolito y gateway:
   ```bash
   docker compose -p app up -d tranqui-backend gateway
   ```
5. Conciliar:
   ```bash
   node scripts/fase8-conciliar-suscripciones.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
6. `npm run mantenimiento -- off`.

**Volver a cortar después de una vuelta atrás:**
Como la carga inicial de suscripciones solo corre con las ocho tablas vacías, antes de volver a cortar hay que vaciar
en la base `pagos` las ocho tablas (después de haber devuelto y conciliado sin diferencias). La copia `suscriptor`
**no se vacía** (es una copia de identidad que se mantiene sola).
> **Advertencia:** ejecutar este vaciado (`TRUNCATE`) únicamente si la conciliación anterior finalizó sin
> diferencias y con la app en mantenimiento (`npm run mantenimiento -- on`):
```bash
docker compose -p app exec tranqui-db psql -U <DB_USER> -d pagos -c "TRUNCATE TABLE plans, features, plan_features, subscriptions, subscription_events, subscription_payments, invoices, invoice_sequences RESTART IDENTITY CASCADE;"
```
Luego repetir los pasos del corte.

## Fase 7: historia clínica y recetas en el servicio de profesionales

Los seguimientos diarios (`/api/pacientes/**/seguimientos`), los informes clínicos (`/api/pacientes/**/informes`),
la lista de pacientes atendidos (`/api/pacientes/atendidos`), la ficha del paciente (`/api/pacientes/{id}`), las
recetas electrónicas (`/api/recetas/**` con QBI2/Innovamed) y las solicitudes de conceptos (`/api/conceptos/**`)
viven en el contenedor `profesionales`, sobre su base propia `profesionales`.

- **Sin contenedor nuevo:** la historia clínica y recetas se alojan en el servicio de profesionales (decisión 1).
  No suma memoria al presupuesto del VPS (se mantiene el límite de 288 MB).
- **Pacientes y ficha:** los datos personales siguen siendo de identidad (monolito). La edición de la ficha
  (`PUT /api/pacientes/{id}`) se delega al monolito por `/internal/monolito/pacientes/{id}`; si el monolito no
  responde, la edición falla con error y no se persiste nada.
- **Reglas de acceso (datos de salud):** cargar seguimientos e informes, ver y editar la ficha del paciente, y
  emitir recetas exigen que el profesional atienda al paciente (un turno confirmado o pendiente de validación con
  él, o un chat previo). El seguimiento cargado por un paciente sin profesional se rechaza (400) en vez de
  asignarse a un usuario fijo.
- **Mail de la receta:** lo envía notificaciones por la ruta interna `POST /internal/notificaciones/recetas`
  (el monolito atiende la misma URL en la vuelta atrás).
- **Recetas electrónicas (QBI2):** el servicio toma las mismas variables `QBI2_RECIPE_*` que el monolito. En
  local funciona simulado.

**Variables en `/srv/tranqui/.env`:** `CLINICA_EXTERNO=true` en el monolito, `PROFESIONALES_CLINICA=true` en
el servicio de profesionales y `TRANQUI_SERVICES_CLINICA_URI=lb://profesionales` en el gateway. Las credenciales
de la base de datos de profesionales (`PROFESIONALES_DB_PASSWORD`) y QBI2 son las que ya existían.

**Primer arranque / carga inicial:** con `PROFESIONALES_CLINICA=true`, el servicio copia del monolito los
seguimientos diarios, informes clínicos, recetas y solicitudes de conceptos con sus mismos ids, estados y
hashes de integridad, y posiciona las secuencias después del máximo id. Recién después atiende las rutas clínicas
y consume eventos de recetas.

**El corte va con la app en mantenimiento** (la historia clínica no puede perder un asiento ni duplicar información):

1. `npm run mantenimiento -- on "Volvemos en unos minutos"`.
2. En `/srv/tranqui/.env`: agregar o configurar:
   - `CLINICA_EXTERNO=true`
   - `PROFESIONALES_CLINICA=true`
   - `TRANQUI_SERVICES_CLINICA_URI=lb://profesionales`
3. Reiniciar los contenedores en el orden adecuado para asegurar que el monolito exponga las rutas internas de
   carga inicial antes de que profesionales arranque a copiar:
   ```bash
   docker compose -p app up -d tranqui-backend
   docker compose -p app up -d profesionales
   docker compose -p app up -d gateway
   ```
   (El monolito queda respondiendo las rutas internas `/internal/monolito/clinica/**` para la carga inicial aunque tenga `CLINICA_EXTERNO=true`, ya que `ClinicaExternoFilter` solo filtra `/api/**`. El modo mantenimiento no corta la comunicación interna entre contenedores).
4. Esperar que `profesionales` esté sano y en sus logs figure:
   `Carga inicial clínica completada` (o `Carga inicial clínica:`).
5. Conciliar las cuatro tablas y, si da sin diferencias, desactivar mantenimiento:
   ```bash
   node scripts/fase7-conciliar-clinica.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   npm run mantenimiento -- off
   ```
   A diferencia de otras fases, la conciliación no se corre a diario después del corte: el monolito ya no recibe asientos nuevos, por lo que la conciliación diaria daría diferencias esperables. La conciliación sirve justo después del corte y después de una vuelta atrás.

**Vuelta atrás de la Fase 7:**

1. `npm run mantenimiento -- on "Mantenimiento por actualización"`.
2. Devolver al monolito todos los seguimientos, informes, recetas y solicitudes creados o modificados en el
   servicio, con validación estricta de inalterabilidad (si difiere el contenido o hash de un asiento existente,
   o si algún paciente o médico no existe en el monolito, aborta con error sin tocar datos) y ajuste de secuencias:
   ```bash
   node scripts/fase7-devolver-clinica.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
3. En `/srv/tranqui/.env`: `CLINICA_EXTERNO=false` y sacar `TRANQUI_SERVICES_CLINICA_URI` (o apuntarla a
   `http://tranqui-backend:8080`). Con la vuelta atrás, lo que deja de mandar tráfico clínico al servicio es el
   gateway (sacar `TRANQUI_SERVICES_CLINICA_URI`) y `CLINICA_EXTERNO=false`. `PROFESIONALES_CLINICA` no apaga
   rutas ni sincronizaciones del servicio, solo habilita la carga inicial clínica (que además solo corre con las
   cuatro tablas vacías); se define `PROFESIONALES_CLINICA=false` (o se saca del `.env`) para que, si el servicio
   se reinicia, no intente ninguna carga.
4. Reiniciar monolito, profesionales y gateway:
   ```bash
   docker compose -p app up -d tranqui-backend gateway profesionales
   ```
5. Conciliar (`node scripts/fase7-conciliar-clinica.mjs ...`).
6. `npm run mantenimiento -- off`.

**Volver a cortar después de una vuelta atrás:**
Como la carga inicial clínica solo corre con las cuatro tablas vacías, antes de volver a cortar hay que vaciar en
la base `profesionales` las tablas `seguimiento_diario`, `informe_clinico`, `receta` y `solicitud_documento`
(después de haber devuelto y conciliado).
> **Advertencia:** ejecutar este vaciado únicamente si la conciliación anterior finalizó sin diferencias. Si no se
> vacían, el servicio arrancaría con los datos viejos y no volvería a cargar los asientos del monolito:
```bash
docker compose -p app exec tranqui-db psql -U <DB_USER> -d profesionales -c "TRUNCATE TABLE seguimiento_diario, informe_clinico, receta, solicitud_documento;"
```
Luego repetir los pasos del corte.

**Compatibilidad con vuelta atrás de la Fase 6 (turnos al monolito):**
Si los turnos vuelven al monolito (`TURNOS_EXTERNO=false`), la Fase 7 sigue funcionando plenamente: el servicio
de profesionales resincroniza periódicamente los turnos atendidos cada 15 minutos consultando `/internal/monolito/turnos`
(ruta que el monolito atiende en su vuelta atrás), y el monolito marca los turnos de recetas fuera de turno por
evento (`profesionales.receta.emitida.v1`).

## Fase 6: servicio de turnos

Las reservas, la agenda, la cancelación, la reprogramación, la asistencia y los documentos viven en el
contenedor `turnos`, en las mismas URLs: `/api/turnos/**`, los horarios libres y el conteo de la home,
`/api/medicos/turnos/**` y "Mis turnos" del paciente (`/api/pacientes/me/turnos`). Los links de los mails
(confirmar asistencia, "no voy a ir") siguen funcionando: el servicio tiene los mismos ids y tokens.

- **Pacientes:** siguen siendo del monolito (identidad). La reserva le pide "buscar o crear paciente"; si el
  monolito no responde, la reserva falla con un error y no queda nada.
- **Copia del monolito:** las estadísticas del panel, la historia clínica y las recetas siguen leyendo `turno`
  y su pago. Se actualizan con `turnos.turno.actualizado` y una resincronización cada 15 minutos
  (`TURNOS_RESINCRONIZACION_MINUTOS`).
- **Documento adjunto:** lo manda notificaciones (`/internal/notificaciones/documentos`); el archivo no se
  guarda en ningún lado.
- **Pago de un turno vencido:** si el horario ya lo tomó otro paciente, el turno queda cancelado y pagos
  reembolsa el pago completo (el paciente recibe los mails de cancelación y reembolso).

**Variables en `/srv/tranqui/.env`:** `TURNOS_DB_PASSWORD` (`openssl rand -hex 24`). `MERCADOPAGO_ENABLED`,
`PAYMENT_SIMULATION_ENABLED` y `APP_PUBLIC_URL` son las mismas que ya usa el monolito.

**Primer arranque:** copia del monolito los usuarios, la agenda de los profesionales y todos los turnos con sus
ids, versiones y los tokens de los mails, y pone la secuencia después del último. Recién después consume
eventos, vence bloqueos y manda recordatorios.

**El corte va con la app en mantenimiento** (pocos minutos, sin reservas a medio pagar que se pierdan):

1. `npm run mantenimiento -- on "Volvemos en unos minutos"`.
2. Esperar 5 minutos (que venzan los bloqueos de pago en curso).
3. En `/srv/tranqui/.env`: `TURNOS_EXTERNO=true` (o sacarla) y `TRANQUI_SERVICES_TURNOS_URI` sin definir (usa
   `lb://turnos`).
4. `docker compose -p app up -d turnos tranqui-backend gateway` y esperar que `turnos` esté sano (en sus logs:
   "Carga inicial de turnos: ...").
5. Conciliar y, si da sin diferencias, `npm run mantenimiento -- off`:
   ```bash
   node scripts/fase6-conciliar-turnos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
6. La conciliación todos los días durante dos semanas.

**Vuelta atrás de la Fase 6:**

1. `npm run mantenimiento -- on`.
2. En `/srv/tranqui/.env`: `TURNOS_EXTERNO=false` y `TRANQUI_SERVICES_TURNOS_URI=http://tranqui-backend:8080`.
3. `docker compose -p app stop turnos` y `docker compose -p app up -d tranqui-backend gateway`.
4. Devolver al monolito los turnos, sus pagos y los tokens de los mails, con la versión por encima de la del
   servicio y la secuencia ajustada (idempotente; `--prueba` solo cuenta):
   ```bash
   node scripts/fase6-devolver-turnos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
5. `npm run mantenimiento -- off`.

**Volver al servicio después:** borrar la base de turnos para que el primer arranque vuelva a copiar todo:
`docker compose -p app exec tranqui-db psql -U <DB_USER> -d tranqui_app -c "DROP DATABASE turnos"`, y repetir el
corte.

## Fase 5: servicio de profesionales

El buscador (`GET /api/medicos`), "Mi perfil" (`/api/medicos/perfil`) y la agenda (`/api/medicos/disponibilidad`
y `/api/medicos/disponibilidad-config`) viven en el contenedor `profesionales`, en las mismas URLs. Las
estadísticas del panel, la historia clínica, las recetas y los documentos de la API de conceptos **siguen en
el monolito**, igual que la verificación del administrador.

- **Identidad:** nombre, apellido, teléfono, sexo, fecha de nacimiento, CUIL, documento y foto siguen siendo
  del monolito. Al guardar el perfil, el servicio manda esa parte al monolito antes que el resto; si el
  monolito no responde, el profesional ve un error y no se guarda nada.
- **Suscripción, verificación y Mercado Pago:** el servicio los copia del monolito cada minuto
  (`PROFESIONALES_SINCRONIZACION_SEGUNDOS`) y enseguida con los eventos de identidad y de verificación. Un
  cambio de suscripción tarda hasta un minuto en reflejarse en el buscador.
- **Copia del monolito:** turnos, recetas, facturación y los mails siguen leyendo el perfil, la
  disponibilidad y las tarifas de sus tablas. Se actualizan con los eventos del servicio y con una
  resincronización cada 15 minutos (`PROFESIONALES_RESINCRONIZACION_MINUTOS`). Con el servicio caído, la
  agenda y la reserva siguen andando; lo que no se puede es editar el perfil ni ver el buscador.

**Variables en `/srv/tranqui/.env`:** `PROFESIONALES_DB_PASSWORD` (`openssl rand -hex 24`). `MERCADOPAGO_ENABLED`
es la misma que ya usa el monolito.

**Primer arranque:** copia del monolito los profesionales con su perfil, foto, disponibilidad y tarifas, y lo
que es del monolito (identidad, verificación, suscripción, Mercado Pago). La versión de cada profesional
arranca en la de su usuario. Recién después empieza a consumir eventos.

**El corte va en un solo paso:** `PROFESIONALES_EXTERNO=true` en el monolito y la ruta del gateway al servicio.
Con el flag, el monolito responde 409 si alguna de esas rutas le llega igual. Después, la conciliación todos
los días durante dos semanas (una diferencia de menos de 15 minutos puede ser la resincronización que no
pasó todavía):
```bash
node scripts/fase5-conciliar-profesionales.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
```

**Vuelta atrás de la Fase 5:**

1. En `/srv/tranqui/.env`: `PROFESIONALES_EXTERNO=false` y
   `TRANQUI_SERVICES_PROFESIONALES_URI=http://tranqui-backend:8080`.
2. `docker compose -p app stop profesionales` y `docker compose -p app up -d tranqui-backend gateway`.
3. Devolver al monolito el perfil, la disponibilidad y las tarifas, y dejar la versión de cada usuario por
   encima de la del servicio (idempotente; `--prueba` solo cuenta):
   ```bash
   node scripts/fase5-devolver-profesionales.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```

**Volver al servicio después:** borrar la base de profesionales para que el primer arranque vuelva a copiar
todo: `docker compose -p app exec tranqui-db psql -U <DB_USER> -d tranqui_app -c "DROP DATABASE profesionales"`,
volver las dos variables a su valor y `docker compose -p app up -d`.

## Fase 4: servicio de pagos

Mercado Pago de los turnos y de los documentos pagos vive en el contenedor `pagos`: `/api/payments`
(el webhook, "verificar" y el link del checkout) y `/api/medicos/mercadopago` (incluido el callback del
OAuth), en las mismas URLs públicas. Crea la preferencia a partir de la reserva (la reserva responde sin
link y el frontend lo pide aparte), confirma por `pagos.pago.aprobado` y reembolsa a partir de la
cancelación (el paciente recibe el mail de cancelación y otro cuando Mercado Pago confirma el reembolso).
Las **suscripciones siguen en el monolito** (Fase 8): Mercado Pago avisa todo a la misma URL y el servicio
le reenvía al monolito, por `/internal/monolito/pagos/notificacion-suscripcion`, lo que es de
suscripciones. En el panel de Mercado Pago no cambia nada.

**Variables en `/srv/tranqui/.env`:** `PAGOS_DB_PASSWORD` (`openssl rand -hex 24`). Las de Mercado Pago
(`MERCADOPAGO_ENABLED`, `MERCADOPAGO_CLIENT_ID`, `MERCADOPAGO_CLIENT_SECRET`, `MERCADOPAGO_WEBHOOK_SECRET`,
`PAYMENT_SIMULATION_ENABLED`) y `APP_PUBLIC_URL`/`FRONTEND_URL` son las mismas que ya usa el monolito.
Necesita la misma `ENCRYPTION_KEY` y el hotfix de la clave de cifrado en el monolito, como el calendario.

**Primer arranque:** copia del monolito los usuarios, las cuentas de Mercado Pago (tokens cifrados, sin
descifrarlos: nadie vuelve a vincular), los pagos, los documentos de la API de conceptos y los turnos que
están esperando el pago. Recién después empieza a consumir eventos.

**El corte va en un solo paso**, en una ventana sin checkouts en curso (duran 5 minutos):
`PAGOS_EXTERNO=true` en el monolito y la ruta del gateway al servicio. Mercado Pago reintenta los
webhooks que no responden 200, y "verificar" rescata los que se pierdan. Después, una prueba con un
monto mínimo (con aprobación) y la conciliación todos los días durante dos semanas:
```bash
node scripts/fase4-conciliar-pagos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
```

**Vuelta atrás de la Fase 4** (sin desvincular a nadie ni perder pagos):

1. En `/srv/tranqui/.env`: `PAGOS_EXTERNO=false` y `TRANQUI_SERVICES_PAGOS_URI=http://tranqui-backend:8080`.
2. `docker compose -p app stop pagos` y `docker compose -p app up -d tranqui-backend gateway`.
3. Devolver al monolito las cuentas de Mercado Pago y los pagos registrados por el servicio (idempotente;
   `--prueba` solo cuenta):
   ```bash
   node scripts/fase4-devolver-pagos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
   ```
4. Un reembolso que el servicio no llegó a hacer (cancelado con `EN_PROCESO` mientras estaba caído) se
   hace a mano desde Mercado Pago: la conciliación lo muestra como pago aprobado de un turno cancelado.

**Volver al servicio después:** borrar la base de pagos para que el primer arranque vuelva a copiar todo:
`docker compose -p app exec tranqui-db psql -U <DB_USER> -d tranqui_app -c "DROP DATABASE pagos"`, volver
las dos variables a su valor y `docker compose -p app up -d`.

## Fase 3: servicio de calendario

Google Calendar y Meet (`/api/medicos/google-calendar`, incluidos el callback del OAuth y el webhook de
Google, que no cambian de URL) viven en el contenedor `calendario`. Crea, mueve y borra los eventos de
los turnos a partir de los eventos de turnos, y el Meet vuelve al monolito por `calendario.meet.creado`
(el monolito lo consume con `EVENTOS_CONSUMO_SERVICIO=monolito`). La disponibilidad de turnos le pide
los horarios ocupados por `/internal/calendario/...`; si no responde, la agenda sale sin ese filtro.

**Variables en `/srv/tranqui/.env`:** `CALENDARIO_DB_PASSWORD` (`openssl rand -hex 24`). Las de Google
(`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_CALENDAR_ENABLED`, `GOOGLE_CALENDAR_WATCH_ENABLED`) y
`APP_PUBLIC_URL`/`FRONTEND_URL` son las mismas que ya usa el monolito. La consola de Google no cambia.

**Clave de cifrado:** el servicio lee los tokens de Google que guardó el monolito, así que recibe la
misma `ENCRYPTION_KEY` y cifra igual que él (formato `v2:`, del hotfix de la clave de cifrado). Lee
también los tokens del formato anterior y acepta los canales firmados con la clave vieja hasta que se
renuevan. El monolito tiene que tener ese hotfix antes de desplegar el servicio: con la v1.1.0 no leería
los tokens que el servicio renueva, ni en la vuelta atrás.

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
