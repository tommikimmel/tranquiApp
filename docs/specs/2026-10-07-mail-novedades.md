# Spec: mail de novedades al terminar el mantenimiento

- **Fecha:** 2026-10-07
- **Tipo:** feature
- **Sprint:** sprint-01
- **Estado:** implementada (pendiente de deploy)
- **Rama / PR:** `feature/full-mail-novedades` (apilada sobre los PRs #2 y #3)

## Entendimiento del problema

Al terminar un mantenimiento, el dueño quiere mandar a todos los usuarios un mail breve con lo que
cambió. La app solo mandaba mails puntuales (Resend transaccional, plan Free: 100 por día
compartidos con códigos y confirmaciones), así que un envío masivo por ese canal podía dejar a
usuarios nuevos sin su código de verificación.

## Decisiones (aprobadas)

- **Canal:** Broadcasts de Resend (Free: 1.000 contactos, sin límite diario, no consume el cupo
  transaccional).
- **Baja:** link de baja en cada mail, como pide la Ley 25.326, gestionado por Resend; también
  desde Mi Cuenta y Configuración.
- **Contenido:** un solo archivo dividido en "Para todos", "Pacientes" y "Profesionales"; cada
  usuario recibe lo general más su sección.
- **Disparo:** el texto se carga al prender el mantenimiento (`--novedades`) y se envía al apagarlo,
  después de verificar que el sitio responda 200.

## Criterios de aceptación

- [x] `on --novedades` guarda el texto y muestra la vista previa; `off` lo envía con el sitio arriba.
- [x] Sin `--novedades`, `off` no envía nada.
- [x] Nadie recibe el mail dos veces (mismo id); un envío fallido solo reintenta lo que faltó.
- [x] El endpoint de envío no es accesible desde Internet (token + rechazo de lo que pasa por Traefik).
- [x] Prueba solo para administradores.
- [x] Baja desde el mail y desde Mi Cuenta / Configuración; nunca se reactiva a quien se dio de baja.
- [x] Al eliminar una cuenta se borra su contacto en Resend.
- [ ] Envío real por Broadcast en producción (requiere deploy, `NOVEDADES_TOKEN` y API key con Full access).

## Resultado

- Backend: `NovedadesService`, `ResendAudienciaClient`, `NovedadesInternoController`, tabla
  `novedades_envio`, columna `usuario.recibir_novedades`, endpoints `GET/PUT
  /api/auth/mi-cuenta/novedades`. 14 tests nuevos; suite completa (584) en verde.
- Frontend: interruptor "Novedades de Tranqui" en Mi Cuenta y en Configuración del profesional;
  Política de Privacidad actualizada. 344 tests en verde, sin avisos de lint nuevos.
- Script: `on --novedades`, envío al apagar, `novedades-vista`, `novedades-prueba`,
  `novedades-estado` y `novedades-reintentar`.
- Probado de punta a punta en el entorno local (Mailpit): 14 mails con la sección correcta por rol,
  la paciente dada de baja quedó afuera, el reenvío del mismo id no mandó nada, y el endpoint
  respondió 404 sin token, con token incorrecto y con encabezados de Traefik.
- No se pudo probar el Broadcast real contra Resend (requiere la API key de producción con Full
  access); la lógica está cubierta con tests del cliente simulado.
- Hallazgo aparte: los interruptores de Configuración, Notificaciones del profesional ("Nueva
  reserva", etc.) son decorativos; quedó en `docs/bugs-conocidos.md`.
