# Spec: Fase 6 — Servicio de turnos

- **Fecha:** 2026-10-22
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** aprobada (2026-10-25); T1 a T4 implementadas (#55 a #58), T5 postergada (sin builds ni stack)
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 5
- **Plan:** [microservicios.md, Fase 6](../arquitectura/microservicios.md#fase-6-turnos)
- **Forma de trabajo (pedido del usuario, 2026-10-22):** solo cambiar la arquitectura. Sin buildear imágenes
  ni levantar el stack; los tests van con Maven, de a un módulo. La verificación de punta a punta queda
  postergada, como la de la Fase 5.

## Entendimiento del problema

El plan pone en esta fase "reservas, agenda, cancelación, reprogramación, asistencia, documentos", con los
datos de `turno` y copias de profesionales y pacientes, y avisa que es el corazón de la app: se migra con una
ventana de mantenimiento corta para mover los datos. En el código es:

- **`TurnoService` (1.291 líneas), `AgendaService`, `LiberarTurnosScheduler`** y los recordatorios de
  `NotificationScheduler` (confirmación de asistencia a las 9 hs).
- **13 rutas de `TurnoController`:** horarios libres de un profesional y el conteo de la home, reservar,
  "Mis turnos" del profesional (hoy y todos), cancelar, abandonar el pago, asistencia, documento enviado,
  reprogramar y los dos links públicos de los mails (confirmar asistencia y "no voy a ir"). Más
  `GET /api/pacientes/me/turnos` ("Mis turnos" del paciente), que está en `ClinicalController`.
- **Publica** reservado, confirmado, cancelado, reprogramado, expirado, recordatorio, documento solicitado y
  enviado, y asistencia confirmada. Los consumen notificaciones, chat, calendario y pagos (con descarte de
  versiones viejas por turno).

Lo que encontré y el plan no resolvía:

1. **Turnos lee y escribe datos de otros dominios.**
   - **Profesionales:** la disponibilidad, la duración e intervalo, las modalidades, las tarifas y el precio
     del perfil. Desde la Fase 5 salen por eventos completos (`profesional.actualizado` con el perfil,
     `disponibilidad.actualizada.v2`, `tarifa.actualizada.v2`), así que turnos puede tener su copia igual
     que el monolito.
   - **Identidad:** la reserva **crea al paciente** si el mail no existe (un médico que le reserva a alguien
     sin cuenta) y le guarda la obra social y el número de afiliado. Además, la agenda del profesional
     muestra datos personales del paciente (DNI, CUIL, domicilio, sexo, fecha de nacimiento, credencial de
     la obra social). Identidad sigue en el monolito hasta la Fase 9.
   - **Pagos:** "¿el profesional vinculó Mercado Pago?" para cortar la reserva, y confirmar el turno cuando
     llega `pagos.pago.aprobado` (hoy lo hace `ManejadoresPagos` en el monolito).
   - **Calendario:** el link de Meet (`calendario.meet.creado`) y los horarios ocupados de Google (ruta
     interna, ya existe).
2. **El monolito sigue leyendo turnos después de la fase.** Las estadísticas del panel (decisión 3 de la
   Fase 5: van al final), la historia clínica ("pacientes atendidos" y quién puede ver la historia de quién),
   las recetas (marcan como enviado el turno de receta) y el pago guardado en el monolito (tiene una clave al
   turno). Igual que en la Fase 5, necesita una copia.
3. **El documento adjunto no pasa por notificaciones.** Cuando el profesional manda un certificado o un
   informe, el monolito lo manda por mail directo con el archivo (`enviarDocumentoAdjunto`); el archivo no
   viaja en eventos (sección 5.3) y el servicio de notificaciones no lo manda. Si turnos se lleva la ruta,
   alguien tiene que mandar ese mail. Ver Preguntas.
4. **Las versiones de los eventos.** Notificaciones, chat, calendario y pagos descartan los eventos de un
   turno con versión vieja. El servicio tiene que seguir la numeración del monolito (como en la Fase 5).
5. **Los ids de los turnos** están en todos lados (pagos, chat, calendario, notificaciones, el pago y las
   recetas del monolito, los links de los mails ya enviados). El servicio tiene que conservarlos.
6. **Un bug conocido de la Fase 4:** si Mercado Pago avisa el pago de un turno que ya venció, se confirma
   igual aunque otro paciente haya tomado el horario.
7. **Memoria:** con turnos son 3.552 + 288 = **3.840 MB** de límites y **~5,0 GB proyectados en el VPS**,
   por encima del techo de ~4,8 GB del plan (sección 9.1). Ver Preguntas.

## Solución propuesta (con las recomendaciones de las Preguntas)

### T1 — Catálogo y monolito
- **Evento nuevo `turnos.turno.actualizado.v1`** con el turno completo (estado, fecha, horas, modalidad,
  tipo, servicio, precio, asistencia, documento enviado, Meet, link del checkout, afiliado), publicado en
  cada cambio. El monolito lo usa para su copia; los eventos de siempre (reservado, confirmado, etc.) no
  cambian.
- **Flag `turnos.externo` (`TURNOS_EXTERNO`):** con `true`, las rutas de turnos del monolito responden 409
  (no deberían llegarle: el gateway las manda al servicio), `LiberarTurnosScheduler` y los recordatorios no
  corren, y `ManejadoresPagos`/`ManejadoresCalendario` dejan de tocar turnos (lo hace el servicio). El
  monolito mantiene `turno` como copia con `turno.actualizado` y una resincronización periódica, como la
  Fase 5. Con `false` (vuelta atrás), todo como hoy.
- **Rutas internas para el servicio:** turnos completos para la carga inicial (paginados), pacientes
  (identidad para la copia) y **buscar o crear paciente** (`POST /internal/monolito/pacientes`), que hace lo
  mismo que la reserva hoy (mail normalizado, teléfono con +54, obra social y afiliado) y publica
  `identidad.usuario.registrado/actualizado`.
- **Recetas:** cuando el monolito emite recetas a un paciente, en vez de llamar a
  `marcarRecetasEnviadasParaPaciente` publica un evento (`profesionales.receta.emitida.v1`, sin datos de
  salud: profesional y paciente) y el servicio marca los turnos de receta como enviados.

- **Hecho en T1 (lo que apareció al implementar):**
  - `turno.actualizado` lleva también el estado del pago del turno (transacción, estado, monto, fecha): la
    copia del pago en el monolito (la usan las ganancias de las estadísticas) sale del mismo evento y no
    depende de que `pagos.pago.*` llegue después del turno. Con el flag, los handlers de turnos de
    `ManejadoresPagos` y el del Meet de `ManejadoresCalendario` no hacen nada.
  - Los tokens de los links de los mails no viajan en el evento: van en la carga inicial (ruta interna) y en
    el script de vuelta atrás (base a base).
  - Notificaciones pedía los enlaces del mail de asistencia a `/internal/monolito/turnos/{id}/enlaces-asistencia`.
    Ahora pide `/internal/turnos/turnos/{id}/enlaces-asistencia`, que atiende el servicio de turnos y, antes del
    corte o en la vuelta atrás, el monolito con la misma URL.
  - El documento adjunto (decisión 3): `POST /internal/notificaciones/documentos` en notificaciones (que suma el
    filtro de JWT de servicio para `/internal/**` y el envío con adjunto por Resend o SMTP). El monolito atiende la
    misma URL para la vuelta atrás de la Fase 1.
  - Con el flag, las rutas de turnos que lleguen igual al monolito responden 409 (`TurnosExternoFilter`) y no
    corren el vencimiento del bloqueo ni los recordatorios.

### T2 — Servicio `services/turnos`
- Base propia `turnos` (JDBC, como los anteriores): `turno` (mismos ids; la secuencia arranca después del
  máximo), copias de profesionales (perfil para la agenda, disponibilidad, tarifas, Mercado Pago vinculado) y
  de pacientes (los datos que muestra la agenda), inbox y outbox.
- **Mismas rutas y JSON** que el monolito, más `GET /api/pacientes/me/turnos`.
- **Lógica:** la de `TurnoService` tal cual (bloques libres con el bloqueo de 5 minutos y el cruce entre
  modalidades, precios por modalidad, sobreturno, documentos que no ocupan agenda, política de 48 hs con
  `ReembolsoService.correspondeReembolso`, reprogramación, asistencia, tokens de los mails), el vencimiento a
  los 5 minutos y los recordatorios de las 9 hs.
- **Consume:** profesionales (perfil, disponibilidad, tarifas), pagos (pago aprobado/rechazado, reembolso,
  checkout, cuentas vinculadas), calendario (Meet), identidad (pacientes) y la receta emitida.
- **Publica** los mismos tópicos de turnos que hoy, siguiendo la versión de cada turno del monolito, más
  `turno.actualizado`.
- **Pago de un turno vencido (bug 6):** si el horario sigue libre, se confirma (como hoy); si ya lo tomó otro,
  el turno queda cancelado y se publica `turnos.turno.cancelado` con reembolso, para que pagos le devuelva la
  plata y el paciente reciba los mails de siempre.

- **Hecho en T2:**
  - La agenda de cada profesional (perfil, disponibilidad, tarifas, Mercado Pago) se copia del servicio de
    profesionales por eventos y, además, cada 15 minutos de la copia del monolito: así sigue al día también si
    profesionales vuelve al monolito (Fase 5). La ruta de profesionales del monolito suma `foto=false`.
  - Las rutas internas del monolito mandan las fechas en ISO (el monolito las serializa `dd/MM/yyyy`); afectaba
    también a la ruta de la Fase 5 (corregido en T1). El servicio responde al frontend con el mismo formato del
    monolito.
  - `confirmarTurnoOsde` no tiene llamadores en el monolito: no se portó.
  - Cuidado al recorrer las franjas: si una termina cerca de la medianoche, el paso del monolito daba la vuelta
    al día y no terminaba nunca; el servicio corta ahí.
  - **Encontrado y sin cambiar (mismo comportamiento que hoy):** cancelar, marcar asistencia y reprogramar no
    verifican que el turno sea de quien lo pide (cualquier paciente o profesional logueado puede hacerlo con el
    id). **Corregido en un PR aparte** (decidido el 2026-10-25): en el monolito y en el servicio, cancelar exige ser
    el paciente o el profesional del turno, y asistencia y reprogramar, el profesional; si no, 403.

### T3 — Gateway
- Las rutas de `TurnoController` van a `TRANQUI_SERVICES_TURNOS_URI`: las reglas `turnos-conteo`,
  `turnos-disponibles`, `medicos-turnos` y las de `/api/turnos/**` ya existen desde la Fase 0 (hoy al
  monolito), solo cambia el destino. Se agregan `/api/pacientes/me/turnos` (antes de la regla general de
  pacientes, que sigue en el monolito con la historia clínica) y `/internal/turnos/**` para la
  resincronización del monolito.

### T4 — Infraestructura, corte, vuelta atrás y conciliación
- Compose, CI, deploy y métricas, como las fases anteriores.
- **Corte con ventana de mantenimiento** (lo pide el plan): `npm run mantenimiento -- on`, el servicio hace la
  carga inicial con la app quieta (sin reservas a medio pagar que se pierdan), flag + ruta, y
  `mantenimiento off`. Se estima en pocos minutos.
- Script de vuelta atrás (turnos creados o cambiados en el servicio, al monolito, con la versión por encima) y
  de conciliación (cantidades por estado y por profesional, y campo por campo de una muestra).

- **Hecho en T4:** el corte queda escrito en `produccion.md` (mantenimiento, esperar los 5 minutos de los
  bloqueos, flag y ruta, conciliar, sacar el mantenimiento). Los scripts devuelven también el pago del turno, los
  tokens de los mails, la versión y la secuencia de `turno` del monolito.

### T5 — Verificación de punta a punta (postergada)
Queda escrita para cuando se pueda levantar el stack: reservar y pagar, cancelar con y sin reembolso,
reprogramar, asistencia y los links de los mails, documentos y recetas, un turno vencido pagado con el horario
tomado, el servicio caído, la ventana de corte y la vuelta atrás.

## Criterios de aceptación

- [ ] Las rutas de turnos responden igual desde el servicio (mismo JSON y mismos errores).
- [ ] Los turnos conservan sus ids y los consumidores no descartan ningún evento por versión.
- [ ] La agenda usa la disponibilidad, la duración y las tarifas del servicio de profesionales.
- [ ] Un pago aprobado confirma el turno; uno de un turno vencido con el horario tomado se reembolsa.
- [ ] Las estadísticas, la historia clínica y las recetas del monolito siguen andando con su copia.
- [ ] Ningún evento lleva datos de salud ni el documento adjunto.
- [ ] Con la vuelta atrás (variable del gateway + flag + script), todo funciona como hoy.
- [ ] `./mvnw test` de los módulos tocados sin errores nuevos.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **Es el flujo de la plata y la agenda:** el corte va con la app en mantenimiento, con la conciliación y la
  vuelta atrás probadas antes en local (cuando se pueda levantar el stack).
- **Doble dueño durante la transición:** se evita con el corte en un solo paso (flag + ruta + mantenimiento).
- **Memoria del VPS** (pregunta 5).

## Decisiones (2026-10-25)

1. **Pacientes:** el servicio le pide al monolito "buscar o crear paciente" por una ruta interna (503 si no
   responde) y guarda una copia de los datos que muestra la agenda, actualizada con los eventos de identidad.
2. **Copia en el monolito:** `turno` queda como copia, con `turno.actualizado` y la resincronización periódica.
3. **Documento adjunto:** lo manda el servicio de notificaciones por una ruta interna nueva; turnos le pasa el
   archivo en el momento y no se guarda.
4. **Turno vencido pagado con el horario tomado:** se cancela y se reembolsa con la misma lógica de
   cancelación y reembolso de hoy (mismos eventos y mismos mails).
5. **Memoria:** se acepta pasar el techo por ahora y se mide el consumo real en el VPS antes del deploy.
6. **Numeración:** historia clínica y recetas son la Fase 7; suscripciones pasa a la 8 e identidad a la 9
   (plan, documentación y comentarios del código actualizados en el PR de esta spec).
7. Un PR a `develop` por tarea, Java 17, sin deploy ni pruebas con el stack.

## Preguntas

1. **Pacientes (identidad):** la reserva crea al paciente si no existe y la agenda muestra sus datos
   personales. ¿El servicio **le pide al monolito** "buscar o crear paciente" por una ruta interna (si el
   monolito no responde, la reserva falla con 503) y **guarda una copia** de los datos que muestra la agenda,
   actualizada con los eventos de identidad, como en la Fase 5? **Recomiendo que sí.**
2. **Copia en el monolito:** las estadísticas, la historia clínica y las recetas siguen leyendo `turno`.
   ¿El monolito mantiene `turno` como copia con el evento nuevo `turno.actualizado` y una resincronización
   periódica, igual que la caché de la Fase 5? **Recomiendo que sí.**
3. **El documento adjunto:** ¿quién manda el mail con el archivo?
   - a) El servicio de notificaciones, con una **ruta interna nueva** a la que turnos le pasa el archivo en
     el momento (no por eventos, no se guarda). Notificaciones ya tiene Resend y las plantillas.
   - b) El servicio de turnos, con su propio envío de mails.
   - c) Queda en el monolito hasta la fase de historia clínica y recetas.

   **Recomiendo la a)**: un solo lugar manda mails y el archivo no se guarda en ningún lado.
4. **Pago de un turno vencido con el horario tomado:** ¿se cancela y se reembolsa el pago completo (con los
   mails de cancelación y reembolso de siempre)? **Recomiendo que sí.** La alternativa es confirmarlo igual
   (hoy) y que el profesional lo resuelva a mano.
5. **Memoria:** con turnos se pasa el techo del plan (~5,0 GB proyectados contra ~4,8 GB, en un VPS de 8 GB).
   Opciones:
   - a) Aceptarlo por ahora y medir el consumo real en el VPS antes del deploy (los servicios usan bastante
     menos que su límite: pagos ~175 MB de 288).
   - b) Bajar el límite del monolito de 512 a 384 MB, ya que le quedan menos dominios.
   - c) Bajar los servicios chicos (notificaciones, chat, calendario) a 256 MB.

   **Recomiendo la a)**, y decidir b) o c) con la medición.
6. **La fase de historia clínica y recetas** (decisión 1 de la Fase 5) va después de turnos. ¿La llamo
   **Fase 7: historia clínica y recetas** y corro suscripciones a la 8 e identidad a la 9? **Recomiendo que
   sí** (actualizo el plan en el mismo PR de la spec cuando la apruebes).
7. Un PR a `develop` por tarea, Java 17, sin deploy ni pruebas con el stack (como pediste).
