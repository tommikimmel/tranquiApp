# Spec: Fase 7 — Historia clínica y recetas

- **Fecha:** 2026-10-25
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** propuesta (esperando aprobación)
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 6
- **Plan:** [microservicios.md, Fase 7](../arquitectura/microservicios.md#fase-7-historia-clínica-y-recetas)
- **Forma de trabajo:** solo cambiar la arquitectura; sin buildear imágenes ni levantar el stack. Tests con Maven,
  de a un módulo. La verificación de punta a punta queda postergada, como en las Fases 5 y 6.

## Entendimiento del problema

La Fase 5 separó la historia clínica y las recetas de profesionales porque quién puede ver la historia de quién
sale de los turnos. Con la Fase 6 los turnos ya publican `turno.actualizado` con el turno completo, así que otro
servicio puede tener esa relación sin preguntarle a nadie. En el código es:

- **Historia clínica (`ClinicalService`, 536 líneas, 13 rutas en `/api/pacientes/**`):** seguimientos diarios e
  informes que carga el profesional, seguimientos que carga el paciente, "pacientes atendidos" (la lista de la
  sección Pacientes), la ficha del paciente (sus datos personales) y la anulación y corrección por anexo de los
  asientos, con un hash de integridad por asiento (Leyes 26.529 y 27.706: nunca se borra un asiento).
- **Recetas (`RecetaService`, 485 líneas, 6 rutas en `/api/recetas`):** emitir una receta electrónica con QBI2
  (Innovamed), los catálogos de diagnósticos, medicamentos y financiadores, y "mis recetas".
- **Documentos de la API de conceptos (`ConceptoService`, 3 rutas en `/api/conceptos`):** el paciente pide un
  documento pago, el profesional lo emite (el cobro ya es del servicio de pagos desde la Fase 4).
- **Datos:** `seguimiento_diario`, `informe_clinico`, `receta` y `solicitud_documento`. **Son los datos más
  sensibles de la app.**

Lo que encontré y el plan no resolvía:

1. **Dependencias de otros dominios.**
   - **Turnos:** "pacientes atendidos" y la prioridad (turnos confirmados de los próximos 3 días, última visita).
   - **Chat:** los pacientes con los que el profesional chateó también son "atendidos", y el conteo de mensajes
     sin leer (ruta interna del chat, ya existe).
   - **Identidad:** la ficha del paciente muestra y **edita** sus datos personales (DNI, CUIL, domicilio,
     credencial de la obra social) con `PUT /api/pacientes/{id}`. Identidad sigue en el monolito hasta la Fase 9.
   - **Profesionales y suscripciones:** la receta exige la matrícula, el REFEPS, el domicilio y el sello del
     profesional, y que su plan permita recetar (el servicio de profesionales ya copia `puedeRecetar`).
   - **Mails:** el mail de "tu receta está lista" con el link lo manda el monolito directo (como pasaba con el
     documento adjunto); el link a la receta es un dato de salud y no puede ir en un evento.
2. **Agujeros de acceso (datos de salud).** Igual que en turnos, varias rutas no verifican la relación:
   - cualquier profesional puede **cargar** seguimientos e informes en la historia de cualquier paciente por id
     (`POST /api/pacientes/{id}/seguimientos|informes`) y **editar los datos personales** de cualquier paciente
     (`PUT /api/pacientes/{id}`);
   - un seguimiento cargado por el paciente sin profesional queda asignado a un profesional fijo
     (`paula@tranqui.com`), un resto de las pruebas.
   Lo que sí está bien: cada profesional solo **lee** lo que él cargó, y anular o corregir exige ser el autor.
3. **Memoria:** con la Fase 6 ya estamos en ~5,0 GB proyectados contra el techo de ~4,8. Un servicio más suma
   288 MB (~5,3 GB).

## Solución propuesta (con las recomendaciones de las Preguntas)

### H1 — Catálogo y monolito
- Flag `clinica.externo` (`CLINICA_EXTERNO`): con `true`, las rutas de historia clínica, recetas y conceptos del
  monolito responden 409 (el gateway las manda al servicio). Con `false` (vuelta atrás), todo como hoy.
- Rutas internas del monolito para la carga inicial: seguimientos, informes, recetas y solicitudes (paginadas),
  y `PUT /internal/monolito/pacientes/{id}` para la ficha del paciente (los datos personales siguen siendo de
  identidad; publica `identidad.usuario.actualizado`).
- `POST /internal/notificaciones/recetas` en notificaciones (como el documento adjunto): el mail de la receta con
  su link, sin guardarlo. El monolito atiende la misma URL para la vuelta atrás de la Fase 1.
- Sin copia en el monolito: después del corte nadie más lee la historia clínica (las estadísticas cuentan turnos y
  pagos). Menos copias de datos de salud. La vuelta atrás los devuelve con un script.

### H2 — Historia clínica y recetas en el servicio de profesionales (pregunta 1)
- Tablas nuevas en la base `profesionales`: `seguimiento_diario`, `informe_clinico`, `receta`, `solicitud_documento`
  (mismos ids y hashes), más las copias que necesita: la relación profesional-paciente (de `turno.actualizado`) y
  los datos del paciente (de identidad, como turnos).
- Mismas rutas y JSON: `/api/pacientes/**` (salvo `/me/turnos`, que es de turnos), `/api/recetas/**` y
  `/api/conceptos/**`.
- QBI2 se mueve con sus variables (`QBI2_RECIPE_*`); el modo simulado queda igual.
- **Reglas de acceso (pregunta 2):** cargar seguimientos e informes, ver y editar la ficha, y emitir recetas
  exigen que el paciente sea "atendido" por ese profesional (un turno confirmado o pendiente de validación con
  él, o un chat), las mismas reglas con las que se arma la lista de Pacientes. El seguimiento del paciente sin
  profesional se rechaza en vez de asignarlo a uno fijo.
- Publica `profesionales.historia-clinica.actualizada` (igual que hoy, sin contenido) y
  `profesionales.receta.emitida` (para turnos y para el mail).

### H3 — Gateway
- `/api/pacientes/**` (menos `/me/turnos`), `/api/recetas/**` y `/api/conceptos/**` al servicio de profesionales
  (la regla `profesionales-monolito` de la Fase 5 se reduce a las estadísticas).

### H4 — Infraestructura, corte, vuelta atrás y conciliación
- Variables de QBI2 en el servicio. Sin contenedor nuevo (pregunta 1): no suma memoria.
- **Corte en mantenimiento**, como turnos: la historia clínica no puede perder un asiento.
- Script de vuelta atrás (asientos, recetas y solicitudes nuevos o anulados en el servicio, al monolito, con sus
  ids y hashes) y de conciliación (cantidades y hashes de integridad asiento por asiento).

### H5 — Verificación de punta a punta (postergada)
Cargar y leer seguimientos e informes, anular y corregir, la ficha del paciente, una receta con QBI2 simulado y su
mail, un documento de conceptos pago, los accesos rechazados, el servicio caído, la vuelta atrás.

## Criterios de aceptación

- [ ] Las rutas de historia clínica, recetas y conceptos responden igual desde el servicio (salvo los accesos que
      ahora se rechazan).
- [ ] Ningún asiento se pierde ni cambia: mismos ids y hashes de integridad (la conciliación los compara).
- [ ] Ningún evento lleva datos de salud (ni contenido de asientos, ni medicamentos, ni el link de la receta).
- [ ] Un profesional no puede cargar ni ver la historia, ni editar la ficha, de un paciente que no atendió.
- [ ] Con la vuelta atrás (variable del gateway + flag + script), todo funciona como hoy.
- [ ] `./mvnw test` de los módulos tocados sin errores nuevos.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **Datos de salud:** se mueven de base; la carga inicial y la vuelta atrás copian base a base (nunca por eventos)
  y la conciliación compara los hashes.
- **Cambio de comportamiento en los accesos (pregunta 2):** un profesional que hoy carga cosas a un paciente sin
  turno ni chat con él dejaría de poder hacerlo.

## Preguntas

1. **¿Servicio nuevo o en profesionales?** El plan original ponía la historia clínica y las recetas en el
   servicio de profesionales. Separarlas en un servicio `clinica` aísla mejor los datos de salud, pero suma
   288 MB (~5,3 GB proyectados, más arriba del techo). **Recomiendo ponerlas en el servicio de profesionales**
   (ya tiene la matrícula, el REFEPS, el sello y `puedeRecetar`), con sus tablas aparte.
2. **Accesos:** ¿cargar seguimientos e informes, editar la ficha y emitir recetas exigen que el paciente sea
   "atendido" por ese profesional (turno confirmado o chat, como la lista de Pacientes)? ¿Y el seguimiento del
   paciente sin profesional se rechaza en vez de ir a `paula@tranqui.com`? **Recomiendo que sí a las dos**, en un
   PR propio dentro de la fase (como el de turnos).
3. **Ficha del paciente:** los datos personales siguen siendo de identidad. ¿El servicio los guarda en el monolito
   por una ruta interna (si el monolito no responde, la edición falla), como el perfil en la Fase 5?
   **Recomiendo que sí.**
4. **Mail de la receta:** ¿lo manda notificaciones por una ruta interna nueva (como el documento adjunto)?
   **Recomiendo que sí.**
5. **Copia en el monolito:** ¿sin copia de la historia clínica en el monolito (nadie más la lee), con la vuelta
   atrás por script? **Recomiendo que sí**: menos copias de datos de salud.
6. Un PR a `develop` por tarea, Java 17, sin deploy ni pruebas con el stack.
