# Entorno local

Levanta la app completa en tu máquina, con datos de demo realistas y todas las integraciones
externas simuladas. Nada de lo que se haga acá genera cobros, mails reales ni llamadas a Google.

## Requisitos

- Docker (con Docker Compose v2).
- Node.js (solo para correr el script; el frontend corre dentro de Docker).

## Comandos (desde la raíz del repo)

| Comando | Qué hace |
|---|---|
| `npm run local` | Verifica los puertos, reconstruye y levanta todo |
| `npm run local:logs` | Logs del backend |
| `npm run local:down` | Frena el entorno (conserva la base local) |
| `npm run local:reset` | Frena el entorno, **borra la base local** y vuelve a levantar con datos de demo nuevos |

La primera vez tarda unos minutos (compila el backend e instala las dependencias del frontend).

### Verificación de puertos

Antes de cada rebuild, `npm run local` revisa los puertos 5433, 8080, 8081, 5173, 8025, 1025, 5672,
15672, 8500 y 6379. Si alguno está ocupado por algo que no es este entorno (un contenedor de otro
proyecto o un proceso), te muestra qué es y **te pregunta si puede frenarlo** (`docker stop` para
contenedores, que no borra sus datos). Si respondés que no, aborta sin tocar nada. Sin una
terminal interactiva, aborta y lista los puertos ocupados.

## Servicios

| Servicio | URL | Qué es |
|---|---|---|
| App | http://localhost:5173 | Frontend con Vite en modo desarrollo (recarga en caliente al editar `frontend/src`) |
| Gateway | http://localhost:8080 | Spring Cloud Gateway: punto de entrada unificado para frontend y clientes |
| Backend | http://localhost:8081 | Spring Boot con el perfil `local` (accesible directamente o vía Gateway) |
| Mails | http://localhost:8025 | Mailpit: acá llegan todos los mails que manda la app |
| Base | `localhost:5433` | PostgreSQL (`tranqui` / `tranqui-local`, base `tranqui_local`) |
| RabbitMQ (panel) | http://localhost:15672 | Panel web de RabbitMQ (`tranqui` / `tranqui-local`) |
| RabbitMQ (AMQP) | `localhost:5672` | Broker de mensajería (intercambios `tranqui.eventos`, `tranqui.reintentos`, `tranqui.dlx`) |
| Consul | http://localhost:8500 | Service discovery: catálogo, registro de servicios y healthchecks |
| Redis | `localhost:6379` | Almacenamiento en memoria para contadores de rate limiting del gateway |
| VictoriaMetrics | http://127.0.0.1:8428 | Base de métricas y series temporales (PromQL, retención 15d) |

### Infraestructura de microservicios y observabilidad

A partir de la Fase 0 ([Plan de migración a microservicios](../arquitectura/microservicios.md)), el
entorno local incluye la infraestructura de soporte para la arquitectura distribuida:

- **Servicio de profesionales (`profesionales`, Fase 5):** el buscador, "Mi perfil" y la agenda. Su base
  `profesionales` la crea `db-servicios` y en el primer arranque copia los profesionales del monolito. Para
  volver al monolito: sacar `TRANQUI_SERVICES_PROFESIONALES_URI` del gateway, poner
  `PROFESIONALES_EXTERNO: "false"` en el backend, frenar `profesionales` y correr
  `node scripts/fase5-devolver-profesionales.mjs`. `node scripts/fase5-conciliar-profesionales.mjs` compara
  las dos bases.
- **Servicio de pagos (`pagos`, Fase 4):** Mercado Pago simulado como en el monolito: la reserva pide el
  link aparte y abre el "Simulador de Pago"; "Vincular Mercado Pago" vincula sin Mercado Pago real. Su base
  `pagos` la crea `db-servicios`. Para volver a los pagos del monolito: sacar
  `TRANQUI_SERVICES_PAGOS_URI` del gateway, poner `PAGOS_EXTERNO: "false"` en el backend, frenar `pagos`
  y correr `node scripts/fase4-devolver-pagos.mjs`. `node scripts/fase4-conciliar-pagos.mjs` compara las
  dos bases.
- **Servicio de calendario (`calendario`, Fase 3):** Google Calendar y Meet, simulados como en el monolito
  ("Conectar" vincula sin Google y los turnos online reciben un Meet simulado, que llega en un segundo
  mail). Su base `calendario` la crea `db-servicios`. Para volver al calendario del monolito: sacar
  `TRANQUI_SERVICES_CALENDARIO_URI` del gateway, poner `CALENDARIO_EXTERNO: "false"` en el backend,
  frenar `calendario` y correr `node scripts/fase3-devolver-calendario.mjs`.
- **Servicio de chat (`chat`, Fase 2):** el chat en vivo (`/ws-tranqui`) y su historial, por el gateway.
  Su base `chat` la crea `db-servicios`; en el primer arranque copia los mensajes del monolito. Para
  volver el chat al monolito: sacar `TRANQUI_SERVICES_CHAT_URI` del gateway, frenar `chat` y correr
  `node scripts/fase2-devolver-mensajes.mjs` (devuelve los mensajes escritos en el servicio).
- **Servicio de notificaciones (`notificaciones`, Fase 1):** manda los mails (a Mailpit) y crea las
  notificaciones de la campanita a partir de eventos. Sin puerto publicado: se llega por el gateway
  (`/api/notificaciones`, `/ws-notificaciones`). Su base `notificaciones` la crea el contenedor
  `db-servicios` al levantar el entorno. Para comparar con el monolito:
  `node scripts/fase1-comparar-notificaciones.mjs`. Para volver al envío desde el monolito, poner
  `NOTIFICACIONES_ENVIO_DIRECTO: "true"` en el backend, sacar `TRANQUI_SERVICES_NOTIFICACIONES_URI` del gateway y
  frenar `notificaciones` (si sigue prendido, los mails salen dos veces).
- **RabbitMQ (`rabbitmq:3.13-management-alpine`):** broker de eventos asincrónicos por *push*.
  Configurado en `infra/rabbitmq/rabbitmq.conf` con `vm_memory_high_watermark.relative = 0.6`
  (límite de 256 MB de RAM), plugins `rabbitmq_management` y `rabbitmq_prometheus` habilitados,
  política de colas *lazy* por defecto (`lazy-queues`), y los tres exchanges durables definidos
  en la topología: `tranqui.eventos` (topic), `tranqui.reintentos` (direct) y `tranqui.dlx` (direct).
- **Consul (`hashicorp/consul:1.19`):** nodo servidor de desarrollo para registro y descubrimiento
  de servicios (`infra/consul/consul.json`), con UI habilitada en el puerto 8500 y sin ACLs en local.
- **Redis (`redis:7-alpine`):** base en memoria sin persistencia en disco (`--save ""` y
  `--appendonly no`) con tope de memoria chico (`maxmemory 64mb`, política `allkeys-lru`), usada
  para los contadores de token bucket del rate limiter del gateway.
- **VictoriaMetrics (`victoriametrics/victoria-metrics:v1.101.0`):** almacenamiento y motor PromQL de
  métricas locales con retención de 15 días (`-retentionPeriod=15d`) y límite de memoria de 192 MB con `-memory.allowedPercent=30` (con 96 MB lo mataba el OOM).
  Scrapea cada 15 segundos los endpoints Prometheus de Gateway, Backend, RabbitMQ y exportadores del sistema.
- **node-exporter y cAdvisor (perfiles `monitoring` / `full`):**
  Métricas de hardware del host (CPU, memoria, disco) y métricas por contenedor Docker. En entornos locales
  de desarrollo (particularmente en Windows con Docker Desktop / WSL2), node-exporter y cAdvisor pueden tener
  limitaciones para montar el sistema de archivos raíz (`/proc`, `/sys`) o el socket de Docker. Por ello,
  ambos servicios se encuentran agrupados bajo los perfiles `monitoring` y `full` de Docker Compose. Para
  iniciarlos manualmente se puede usar:
  ```bash
  docker compose -f docker-compose.local.yml --profile monitoring up -d
  ```
  Si no están activos, el panel de administración en el frontend muestra normalmente el estado de salud,
  latencia, tráfico, outbox y RabbitMQ, manejando el estado vacío de los gráficos de contenedores sin errores.

El entorno está definido en `docker-compose.local.yml` y **no lee el `.env`**: usa valores
ficticios propios. La configuración del backend está en
`backend/src/main/resources/application-local.yml`.

## Qué está simulado

| Integración | En local |
|---|---|
| Mercado Pago (cobros) | Simulador de pagos: en el checkout aparece "Simular Pago Exitoso" |
| Mercado Pago (vincular cuenta) | Vinculación simulada desde Configuración, Integraciones |
| Google Calendar | Vinculación simulada; los turnos online reciben un link de Meet ficticio |
| Login con Google | Oculto (`VITE_GOOGLE_LOGIN=false`); se ingresa con email y contraseña |
| Mails (Resend) | Se mandan por SMTP a Mailpit |
| Recetas (QBI2) y facturación (ARCA) | Apagadas |

## Datos de demo

Los crea `LocalDemoSeeder` la primera vez que se levanta (y después de cada `local:reset`). Las
fechas son relativas al día en que se siembran. **Contraseña de todas las cuentas: `admin123`.**

### Profesionales

| Cuenta | Perfil |
|---|---|
| `lucia.fernandez@demo.tranqui` | Psicóloga online y presencial, Mercado Pago y Google Calendar vinculados, suscripción mensual, la agenda más cargada |
| `mateo.suarez@demo.tranqui` | Psicólogo solo online, suscripción anual |
| `martin.rios@demo.tranqui` | Psiquiatra con obra social, recetas y documentos |
| `sofia.medina@demo.tranqui` | Suscripción vencida: al entrar ve la pantalla para reactivar y no aparece en el buscador |
| `tomas.ledesma@demo.tranqui` | Recién registrado: sin verificar y con el perfil incompleto |

### Pacientes

| Cuenta | Para qué sirve |
|---|---|
| `martina.lopez@demo.tranqui` | **Sin turnos: usala para probar una reserva** (la app no deja reservar con un turno activo) |
| `valentina.gomez@demo.tranqui` | Con obra social (OSDE) e historial |
| `nicolas.paz@demo.tranqui` | Con obra social (Swiss Medical), paciente del psiquiatra |
| `juan.martinez@demo.tranqui`, `camila.sosa@demo.tranqui`, `florencia.ruiz@demo.tranqui`, `agustin.vera@demo.tranqui` | Con turnos pasados y futuros |
| `pablo.sindatos@demo.tranqui` | Sin datos personales (flujo de completar perfil) |

### Otras cuentas

| Cuenta | Rol |
|---|---|
| `admin@tranqui.com` | Administrador |
| `profesional.test@tranqui.com`, `medico.verificado@gmail.com`, `paciente.test@tranqui.com`, etc. | Cuentas de prueba de siempre (`DataInitializer`) |

### Qué incluye

- ~45 turnos en las últimas 3 semanas y las próximas 2: atendidos, ausentes, cancelados con y sin
  reembolso, un turno de hoy ya atendido, uno dentro de la próxima hora (con botón de Meet) y uno
  pendiente de pago (se expira a los 5 minutos, como en producción).
- Pagos de esos turnos, certificados pedidos (pendientes y enviados), chats con mensajes leídos y
  sin leer, notificaciones del profesional y tickets de soporte (uno respondido).
- Las "Notas y pendientes" de la agenda no se siembran: se guardan en el navegador, no en la base.

## Problemas frecuentes

- **El navegador no muestra los cambios del frontend:** Vite recarga solo; si no, recargá la página.
  Si cambiaste dependencias (`package.json`), corré `npm run local:reset`.
- **Cambios en el backend:** `npm run local` reconstruye la imagen del backend.
- **Querés empezar de cero:** `npm run local:reset`.
