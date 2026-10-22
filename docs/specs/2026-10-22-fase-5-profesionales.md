# Spec: Fase 5 — Servicio de profesionales

- **Fecha:** 2026-10-22
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** propuesta (esperando aprobación)
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 4
- **Plan:** [microservicios.md, Fase 5](../arquitectura/microservicios.md#fase-5-profesionales)

## Entendimiento del problema

El plan pone en esta fase "perfiles, buscador, tarifas, disponibilidad, historia clínica y recetas", con
los datos de "la parte profesional de `usuario`, `tarifa_medico`, `disponibilidad`, historia clínica y
recetas". En el código eso es:

- **Perfil y buscador:** `MedicoService` (771 líneas) y 7 rutas de `MedicoController` (`GET /api/medicos`,
  `/perfil`, `/stats`, `/disponibilidad`, `/disponibilidad-config`). El perfil son ~40 columnas de
  `usuario` mezcladas con las de identidad y las de pacientes: matrícula (9 columnas), título,
  especialidad, descripción, foto (base64 dentro de la fila, a veces de varios MB), tags, domicilio de
  atención con coordenadas, modalidades, duración del turno, experiencia, publicaciones, `verificadoAdmin`.
- **Tarifas y disponibilidad:** `tarifa_medico` y `disponibilidad`, con `DisponibilidadService`.
- **Historia clínica:** `ClinicalService` (536 líneas) y 14 rutas en `/api/pacientes/**` (seguimientos
  diarios, informes, pacientes atendidos, "mis turnos" del paciente). Datos de salud: la sección más
  sensible de la app.
- **Recetas:** `RecetaService` (472 líneas), integración con QBI2 (hoy deshabilitada en producción) y 6
  rutas en `/api/recetas`.

Lo que encontré y el plan no resolvía:

1. **Turnos (que sigue en el monolito hasta la Fase 6) lee disponibilidad y tarifas en cada consulta.**
   `TurnoService` arma los horarios libres con `disponibilidad`, la duración del turno y el intervalo del
   perfil, y el precio con `tarifa_medico`. El plan ya previó los eventos
   (`profesionales.disponibilidad.actualizada`, `tarifa.actualizada`, `profesional.actualizado`) que hoy
   publica el monolito y que consumiría turnos. Ver Preguntas.
2. **El buscador depende de tres dueños distintos.** `isMedicoVerificado` exige el perfil completo, la
   verificación del administrador (`verificadoAdmin`, que se cambia desde `AdminController`), Mercado
   Pago vinculado (ahora en el servicio de pagos) y la suscripción al día (`SubscriptionService`, monolito
   hasta la Fase 7). El servicio tiene que enterarse de las tres cosas por eventos.
3. **La historia clínica depende de turnos.** "Pacientes atendidos" y quién puede ver la historia de quién
   salen de los turnos confirmados del profesional. Si sale antes que turnos, necesita una copia de los
   turnos (como el chat) y la regla de acceso a datos de salud queda en dos lugares. Ver Preguntas.
4. **Las estadísticas del panel (`/api/medicos/stats`)** cuentan turnos y pagos: no son del perfil.
5. **Rutas:** la regla del gateway de profesionales cubre `/api/medicos/**`, `/api/conceptos/**`,
   `/api/recetas/**` y `/api/clinical/**`, pero la historia clínica vive en `/api/pacientes/**` y hoy cae
   en la regla general del monolito. Además `/api/medicos/**` incluye rutas de turnos
   (`/api/medicos/turnos`, `turnos-disponibles`), pagos y calendario, que ya tienen reglas propias antes.

## Solución propuesta (con las recomendaciones de las Preguntas)

### R1 — Catálogo y monolito
- Campos que faltan en `ProfesionalActualizadoData` para que el monolito (turnos, mails, chat,
  calendario) tenga lo que hoy lee de `usuario`: modalidades, duración del turno e intervalo, domicilio de
  atención, especialidad, título y si está visible en el buscador. `disponibilidad.actualizada` y
  `tarifa.actualizada` con lo que turnos necesita para tener una copia fiel: hoy la disponibilidad va
  franja por franja sin la modalidad, y la tarifa sin el servicio, los precios online y presencial, ni si
  requiere obra social, agenda o es receta. Lo más simple es que cada evento traiga la disponibilidad o
  las tarifas completas del profesional, en un tópico de versión nueva (`.v2`).
- Flag `profesionales.externo` (`PROFESIONALES_EXTERNO`): con `true` el monolito no escribe el perfil, la
  disponibilidad ni las tarifas; mantiene sus tablas como copia (caché) del servicio, y `TurnoService`
  sigue leyendo de ellas como hoy. Con `false` (vuelta atrás), todo como hoy.
- La copia se actualiza de dos maneras (decisión 2):
  - **Cuando hay una modificación:** cada cambio de perfil, disponibilidad o tarifa en el servicio publica
    su evento y el monolito lo aplica en segundos.
  - **Cada cierto tiempo:** una tarea del monolito (cada 15 minutos, configurable con
    `PROFESIONALES_RESINCRONIZACION_MINUTOS`) pide al servicio, por una ruta interna, los profesionales
    modificados desde la última pasada y corrige la copia. Cubre un evento perdido o aplicado fuera de
    orden. Si el servicio está caído, la tarea lo reintenta en la próxima pasada y la agenda sigue con lo
    que tiene.
- Eventos que el servicio necesita del monolito: la verificación del administrador
  (`profesionales.profesional.visibilidad-cambiada`, que ya existe) y el estado de la suscripción
  (`suscripciones.suscripcion.cambio-estado`, que ya existe). Mercado Pago llega de pagos.
- Rutas internas para la carga inicial: perfiles, disponibilidad y tarifas.

### R2 — Servicio `services/profesionales`
- Base propia `profesionales` (JDBC, como calendario y pagos): `profesional` (el perfil, con la foto),
  `disponibilidad`, `tarifa`, y los flags del buscador (verificado, Mercado Pago, suscripción).
- Mismas rutas y JSON: `GET /api/medicos` (buscador público), `GET/PUT /api/medicos/perfil`,
  `GET/PUT /api/medicos/disponibilidad`, `PUT /api/medicos/disponibilidad-config`, y el perfil público.
- Publica `profesionales.*` (los mismos tópicos que hoy publica el monolito). Consume identidad (nombre,
  mail, alta y baja), la verificación, la suscripción y las cuentas de Mercado Pago.

### R3 — Gateway
- Las rutas del perfil, la disponibilidad y el buscador van a `TRANQUI_SERVICES_PROFESIONALES_URI`; las de
  turnos, pagos y calendario siguen con sus reglas, y `/api/medicos/stats` sigue en el monolito.

### R4 — Infraestructura, vuelta atrás y conciliación
- Compose, CI, deploy, métricas y presupuesto (3.264 + 288 = 3.552 MB). Script de vuelta atrás (perfil,
  disponibilidad y tarifas al monolito) y de conciliación (cantidades y campos entre las dos bases).

### R5 — Verificación de punta a punta en local
Editar el perfil y verlo en el buscador, la disponibilidad y las tarifas en la agenda y la reserva, la
verificación del administrador, la suscripción vencida y Mercado Pago desvinculado sacando al profesional
del buscador, el servicio caído (la agenda sigue con la copia del monolito), la vuelta atrás.

## Criterios de aceptación

- [ ] Las rutas del perfil, la disponibilidad y el buscador responden igual desde el servicio.
- [ ] Cambiar la disponibilidad o una tarifa se refleja en los horarios y precios de la reserva.
- [ ] El buscador muestra exactamente a los mismos profesionales que hoy (perfil completo, verificado,
      Mercado Pago y suscripción).
- [ ] Con el servicio caído, la agenda y la reserva siguen andando.
- [ ] Un evento perdido se corrige solo en la siguiente resincronización periódica.
- [ ] Ningún evento lleva datos de salud.
- [ ] Con la vuelta atrás (variable del gateway + flag), todo funciona como hoy.
- [ ] `./mvnw test`, tests del frontend y lint sin errores nuevos.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **La foto en base64** viaja en el buscador y en la carga inicial; la copia se hace paginada.
- **Dos dueños del perfil durante la transición:** el corte va en un solo paso (flag + ruta), como en las
  fases anteriores.

## Decisiones

1. **Alcance:** la Fase 5 hace perfil, buscador, tarifas y disponibilidad. La historia clínica y las
   recetas pasan a una fase propia después de turnos (Fase 6).
2. **Disponibilidad y tarifas para turnos:** el monolito guarda una copia (caché) que se actualiza con cada
   modificación (evento) y con una resincronización periódica (ver R1).
3. **Estadísticas del panel:** quedan en el monolito y se mueven al final de la migración.
4. Pendiente (ver Preguntas).

## Preguntas

1. **Alcance:** ¿la Fase 5 hace solo perfil, buscador, tarifas y disponibilidad, y la historia clínica y
   las recetas pasan a una fase propia **después de turnos** (Fase 6)? Dependen de los turnos para saber
   quién puede ver la historia de quién; moverlas antes obliga a copiar los turnos y a repetir la regla de
   acceso a datos de salud en dos lugares. **Recomiendo separarlas y hacerlas después de la Fase 6.**
2. **Disponibilidad y tarifas para turnos:** ¿el monolito (turnos) las mantiene como copia a partir de
   eventos (como dice el plan: la agenda sigue andando aunque profesionales esté caído) o le pregunta al
   servicio en cada consulta de horarios (más simple, pero la agenda depende del servicio)?
   **Recomiendo la copia por eventos.**
3. **Estadísticas del panel (`/api/medicos/stats`):** cuentan turnos y pagos. ¿Se quedan en el monolito
   hasta la Fase 6 (la ruta no se mueve)? **Recomiendo que sí.**
4. **Verificación del administrador:** hoy la hace `AdminController` en el monolito. ¿Sigue ahí y avisa por
   evento, o pasa al servicio con su ruta? **Recomiendo que siga en el monolito por ahora** (el panel de
   administración se reparte por dominio en las fases siguientes) y avise por
   `profesionales.profesional.visibilidad-cambiada`.

   Qué es hoy: en el panel de administración, el admin revisa la matrícula que cargó el profesional y
   toca **Verificar** (`POST /api/admin/users/{id}/verify`) o **Rechazar** (`.../reject`). Verificar pone
   el rol de psiquiatra, marca `verificadoAdmin`, guarda quién y cuándo validó la matrícula (eso habilita
   las recetas) y pasa la suscripción de "pendiente de verificación" a "verificada". Rechazar desmarca
   `verificadoAdmin` y deja la suscripción rechazada. Sin la verificación, el profesional no aparece en el
   buscador aunque tenga el perfil completo. Como toca rol (identidad), suscripción y recetas (que siguen
   en el monolito), conviene que quede ahí y que el servicio solo reciba el resultado.
5. Un PR a `develop` por tarea, Java 17, sin deploy (como en las fases anteriores).
