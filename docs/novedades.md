# Mail de novedades

Al terminar un mantenimiento se puede mandar a todos los usuarios un mail breve con lo que cambió.
Se envía con los **Broadcasts de Resend** (el canal de marketing), que no consume el cupo diario de
los mails de la app (códigos, confirmaciones, recordatorios).

## Cómo se usa

1. Escribí un archivo con las novedades, en lenguaje de usuario:

   ```markdown
   # Mejoras en Tranqui App

   ## Para todos
   - Las páginas cargan más rápido.

   ## Pacientes
   - Ahora ves el link de la videollamada desde Mis Turnos.

   ## Profesionales
   - Nueva sección para ver tus ingresos del mes.
   ```

   - `# Título`: el título y el asunto del mail.
   - `## Para todos` (o `Todos`, `General`), `## Pacientes`, `## Profesionales`: secciones
     opcionales. Cada usuario recibe "Para todos" más la sección de su rol. Un rol sin viñetas no
     recibe mail.
   - Viñetas con `- `. Las viñetas antes de cualquier sección van a "Para todos".

2. Revisalo:

   | Comando | Qué hace |
   |---|---|
   | `npm run mantenimiento -- novedades-vista novedades.md` | Vista previa en la terminal, sin conectarse a nada |
   | `npm run mantenimiento -- novedades-prueba novedades.md` | Manda el mail real solo a los administradores, una versión por público |

3. Prendé el mantenimiento con las novedades y apagalo al terminar:

   ```
   npm run mantenimiento -- on "Volvemos a las 22 hs" --novedades novedades.md
   npm run mantenimiento -- off
   ```

   Al apagar, el script espera a que el sitio responda 200 y recién ahí dispara el envío. Si
   prendiste sin `--novedades`, apagar no manda nada.

4. Seguimiento:

   | Comando | Qué hace |
   |---|---|
   | `npm run mantenimiento -- novedades-estado [id]` | Estado del último envío (o de uno puntual) |
   | `npm run mantenimiento -- novedades-reintentar` | Reintenta un envío que quedó pendiente o falló |

## Cómo funciona

- **Contactos:** justo antes de cada envío, el backend sincroniza los contactos de Resend con los
  usuarios de la app (pacientes y profesionales con mail verificado y cuenta activa): crea los que
  faltan, los ubica en el segmento de su rol ("Tranqui App - Pacientes" o
  "Tranqui App - Profesionales") y trae las bajas hechas desde el link del mail.
- **Envío:** un broadcast por segmento. Corre en segundo plano en el backend.
- **Idempotencia:** cada envío tiene un id (`mant-AAAAMMDD-HHMM`). Un id ya enviado no se repite; si
  un envío falló a mitad, reintentarlo solo manda los segmentos que faltaron (tabla
  `novedades_envio`).
- **Baja (Ley 25.326):** cada mail tiene un link de baja que gestiona Resend. La sincronización
  nunca reactiva a quien se dio de baja. El usuario también puede darse de baja o volver a
  suscribirse desde Mi Cuenta (pacientes) o Configuración, Notificaciones (profesionales). Al
  eliminar una cuenta se borra su contacto en Resend.
- **Endpoint interno:** `POST /api/internal/novedades`. El script lo llama por SSH desde el propio VPS
  (`127.0.0.1:8081`). Exige el encabezado `X-Novedades-Token` (variable `NOVEDADES_TOKEN`, que vive
  solo en el `.env` del VPS) y rechaza todo pedido que haya pasado por Traefik. Ante cualquier
  rechazo responde 404.
- **Entorno local:** como los Broadcasts no pueden ir a Mailpit, el mismo envío manda un mail por
  usuario por SMTP (se ve en `localhost:8025`). El token local es `local-novedades-token`.

## Requisitos en producción

- `NOVEDADES_TOKEN` en el `.env` (un valor aleatorio largo, por ejemplo `openssl rand -hex 32`).
- La API key de Resend (`RESEND_API_KEY`) con permiso **Full access**: una key de solo envío no
  puede gestionar contactos ni broadcasts.
- El dominio del remitente (`RESEND_FROM_EMAIL`) verificado en Resend (ya lo está para los mails
  transaccionales).

## Límites y costos (Resend, octubre 2026)

- Broadcasts, plan Free: hasta 1.000 contactos, sin límite diario, USD 0. Por encima, Pro Marketing
  desde USD 40/mes (5.000 contactos).
- La sincronización hace ~2 llamadas a la API por usuario, espaciadas para respetar el límite de
  Resend (~2 por segundo): con 1.000 usuarios tarda unos 15 a 20 minutos, en segundo plano.
