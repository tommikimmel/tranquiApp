# Spec: entorno local de desarrollo con datos de demo y servicios simulados

- **Fecha:** 2026-10-07
- **Tipo:** feature
- **Sprint:** sprint-01 (tarea 3)
- **Estado:** implementada
- **Rama / PR:** `feature/full-entorno-local`

## Entendimiento del problema

Para levantar la app en local hoy se usa el mismo `docker-compose.yml` de producción con un `.env`
editado a mano. Es riesgoso (el `.env` local tiene credenciales reales de Mercado Pago y Resend:
un flag mal puesto genera cobros o mails reales), los datos de prueba son pobres (`DataInitializer`
crea 8 cuentas casi vacías: sin turnos, chats ni pagos) y los mails solo quedan en el log.

Ya existen y se reutilizan: el simulador de pagos (`payment.simulation.enabled`), la conexión
simulada de Mercado Pago (`simularConexionMercadoPago`), el modo sin clave de Resend y el flag de
Google Calendar.

## Solución propuesta

1. **Un solo comando** (`npm run local`) que levanta `docker-compose.local.yml`: Postgres propio
   (volumen `tranqui-local-db`, puerto 5433), backend con perfil `local` (8081), frontend con Vite
   en modo desarrollo dentro del compose (5173, recarga en caliente) y Mailpit (8025). El compose
   local no lee el `.env`: trae valores ficticios propios.
2. **Verificación de puertos antes de cada rebuild:** el script revisa 5433, 8081, 5173, 8025 y
   1025; si alguno está ocupado por algo ajeno al entorno local, muestra qué es (contenedor o
   proceso) y pregunta si puede frenarlo. Si la respuesta es no, aborta sin tocar nada.
3. **Perfil `local`** (`application-local.yml`): Mercado Pago apagado y pagos simulados, Google
   Calendar, QBI2 y ARCA apagados, mails por SMTP a Mailpit.
4. **Mails a Mailpit:** dependencia `spring-boot-starter-mail`; los dos métodos de envío de
   `ResendEmailService` mandan por SMTP cuando `mail.transport=smtp`. Las plantillas no cambian. Por
   defecto (producción) se sigue usando Resend.
5. **Google simulado:** el botón de login con Google se oculta con `VITE_GOOGLE_LOGIN=false` (solo
   en el compose local); "Vincular Google Calendar" se simula en local igual que Mercado Pago.
6. **Datos de demo** (`LocalDemoSeeder`), que corre solo con perfil `local` **y**
   `app.demo-data=true`: 5 profesionales (psicóloga online y presencial con integraciones y
   suscripción mensual; psicólogo online con suscripción anual; psiquiatra con obra social y
   documentos; profesional con suscripción vencida; profesional recién registrado sin verificar),
   8 pacientes, ~40 turnos en las últimas 3 semanas y las próximas 2 (pagados, pendientes,
   cancelados con y sin reembolso, asistencia marcada, de hoy y dentro de la hora actual), pagos,
   chats leídos y no leídos, certificados pendientes y enviados, notas de agenda y tickets de
   soporte. Fechas relativas al día en que se levanta. Contraseña `admin123`.
7. **Reset:** `npm run local:reset` borra la base local y vuelve a sembrar.
8. **Docs:** `docs/entornos/local.md` reescrito.

## Criterios de aceptación

- [ ] `npm run local` levanta todo; `localhost:5173` muestra la landing con los profesionales de demo.
- [ ] Se puede entrar como paciente y como profesional con `admin123` y ver turnos, chats, pagos y documentos.
- [ ] Reservar y pagar funciona con el simulador y el mail aparece en `localhost:8025`.
- [ ] El botón de Google no aparece en local.
- [ ] Si un puerto está ocupado, el script pregunta antes de frenar lo que lo ocupa.
- [ ] Producción no cambia: sin perfil `local` no hay seeder, sin `mail.transport` se usa Resend,
      sin la variable el botón de Google se ve. Verificado con tests.
- [ ] Suite completa en verde.

## Riesgos e impacto

- El stack viejo de la máquina (`tranqui-backend`) ocupa el 8081, que el frontend tiene fijo: el
  script de puertos pide frenarlo (sin borrar sus datos).
- Se tocan `ResendEmailService.java` y `LoginPage.tsx`, siempre detrás de flags con el
  comportamiento actual por defecto.

## Preguntas abiertas

Ninguna (frontend dentro del compose y set de datos aprobados el 2026-10-07).

## Resultado

Implementado según lo aprobado, con estos desvíos y agregados:

- **Notas y pendientes de la agenda:** no se siembran porque se guardan en el navegador
  (`localStorage`), no en la base.
- **Paciente libre para reservar:** la app no deja reservar a un paciente con un turno activo, así
  que `martina.lopez@demo.tranqui` queda sin turnos para probar una reserva.
- **Vinculación simulada de Google Calendar:** exige `google.calendar.enabled=false` y además
  `app.local-simulations=true` (solo en el perfil `local`), así nunca está disponible en producción.
- El frontend corre con Node 22 (Vite 8 pide 20.19 o superior).

Verificación:

- Backend: 6 tests nuevos de `LocalDemoSeeder` (no existe sin el perfil `local`, no corre sin el
  flag, siembra lo esperado, deja publicados a los profesionales activos, es idempotente, deja a
  Martina sin turnos), 4 de SMTP y 2 de la simulación de Google Calendar.
- Frontend: typecheck y 344 tests en verde.
- Punta a punta con el entorno real: login sin Google, reserva y pago con Martina, 2 mails en
  Mailpit (paciente y profesional), panel de Lucía con próximo turno y Meet, documentos
  solicitados, Google Calendar vinculado y 5 pacientes.
- Script de puertos: aborta sin terminal y pregunta antes de frenar (probado respondiendo "no").
