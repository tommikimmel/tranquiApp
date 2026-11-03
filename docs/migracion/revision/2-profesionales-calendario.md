# C3 — Revisión: profesionales/buscador y calendario (main vs migración)

- **main:** `f11-main/backend/src/main/java/com/tranqui/app` (abreviado `M/`).
- **Migración:** rama `test/full-fase-11-c4-ejecucion` (4dd7c06). El worktree `f11-c4` desapareció a mitad de la
  revisión; lo que faltaba se leyó con `git show` de esa rama (solo lectura). Abreviaturas: `PR/` =
  `services/profesionales/src/main/java/com/tranqui/profesionales`, `CA/` = `services/calendario/src/main/java/com/tranqui/calendario`,
  `TU/` = `services/turnos/src/main/java/com/tranqui/turnos`, `ID/` = `services/identidad/src/main/java/com/tranqui/identidad`.
- **Ruteo verificado** (`gateway/.../application.yml`, en orden): `medicos-mercadopago` → pagos, `medicos-google-calendar` → calendario,
  `profesionales` (`/api/medicos`, `/perfil`, `/disponibilidad`, `/disponibilidad-config`) → profesionales, `turnos-stats` → turnos,
  `profesionales-monolito` (resto de `/api/medicos/**`) → monolito. En `docker-compose.local.yml` todos apuntan a su servicio
  (`TRANQUI_SERVICES_*_URI`) y `TRANQUI_FUENTE_IDENTIDAD=identidad`. Ninguna ruta de mis dominios queda cayendo al monolito.
- Portones del gateway (`PortonesGlobalFilter.java:47-77`): mismas excepciones que `SiteAccessFilter`/`SubscriptionAccessFilter` de
  main para `/api/medicos`, webhook y callbacks de Google/MP.

## 1. Profesionales y buscador

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `GET /api/medicos` (buscador público) | `MedicoController.java:64-67`, `MedicoService.java:79-108` | `PR/web/MedicoController.java:41-44`, `PR/perfil/Perfiles.java:66-73`, `Profesionales.java:83-85` | **igual** | Rol PSIQUIATRA, verificado + acceso por suscripción, orden por id, mismo JSON (campos de `MedicoDto` 1 a 1, incluso los datos personales que main ya exponía). Público sin token (`FiltroAutenticacionUsuario.java:64-68`). |
| Regla "perfil verificado" (`isMedicoVerificado`) | `MedicoService.java:695-729` | `Perfiles.java:246-261` | **igual** (la regla) | Mismas condiciones y el `[]` de experiencia. Depende de `mp_vinculada` copiada: ver hallazgo 1. |
| Visibilidad por suscripción (`isAccessAllowed`) | `SubscriptionService.java:200-220` (en vivo) | `Perfiles.java:267-275` con datos copiados | **cambia a propósito** | Spec fase 5, "decisión 2" (copia cada 60 s) y fase 9 I3b (MP/suscripción llegan de pagos). Regla idéntica. |
| Visibilidad por verificación del admin | `AdminController` en el monolito | Identidad publica `profesionales.profesional.visibilidad-cambiada` (`ID/web/AdminController.java:148,190`) → `PR/interno/Manejadores.java:74-79` refresca | **cambia a propósito** | Spec fase 5, pregunta 4; fase 9. |
| `GET /api/medicos/perfil` | `MedicoController.java:69-73`, `MedicoService.java:110-122` | `MedicoController.java:46-50`, `Perfiles.java:75-77` | **igual** | 403 sin rol, 404 "Médico no encontrado con email: …". El auto-arreglo de rol de main (l.116-119) es inalcanzable por el `@PreAuthorize`. Caso borde a verificar: hallazgo 6. |
| `PUT /perfil` — validaciones del perfil | `MedicoService.java:136-262` | `Perfiles.java:79-120` | **igual** | Mismos mensajes y orden: email de contacto, domicilio 8-140, torre/piso/depto/barrio (50/20/20/100), foto 3 MB (mismo cálculo que `ImageUtils.decodedByteSize`), REFEPS 12 dígitos, sello 40/40/25. Campos solo-presencial en null si no ofrece presencial. Redes/tags/pacientesAtiende null → conserva. |
| `PUT /perfil` — datos de identidad (nombre, apellido, teléfono, sexo, fecha, CUIL, documento, foto) | Se guardan sin validar en `Usuario` (`MedicoService.java:140-152, 208`) | Van a identidad `PUT /internal/identidad/profesionales/{id}/identidad` (`PR/interno/ClienteInterno.java:290-299`), que valida (`ID/interno/InternoIdentidadController.java:288-318`) | **distinto** | Reglas nuevas que main no tenía y error mal informado: hallazgo 2. |
| `PUT /perfil` — copia local de MP / suscripción / puede recetar | n/a (todo en `Usuario`) | `Perfiles.java:181-184` llama `guardarDatosMonolito` con la respuesta de identidad | **distinto** | Pisa con `false`/`null` lo que es de pagos: hallazgo 1. |
| `PUT /perfil` — tarifas (honorarios y servicios) | `MedicoService.java:264-327` | `Perfiles.java:187-212`, `Tarifas.java:33-60` | **igual** | Alta/actualización por servicio, obra social forzada, `esReceta` solo si no requiere agenda y el plan permite recetar, borrado de las no enviadas. Además publica `profesionales.tarifa.actualizada.v2` (spec fase 5, eventos). |
| Tarifas por defecto (primer acceso sin tarifas) | `MedicoService.java:62-72, 337-358` | `Perfiles.java:39-46, 323-337` | **igual** | Mismos 6 servicios y precios; receta habilitada solo si puede recetar; filtra `primera`/`certificado-laboral`; "Desde $X" de la tarifa `particular`. |
| Foto de perfil | `Usuario.fotoUrl` | Guardada en identidad; copia local con hash (`Profesionales.java:27-39,122-140`) | **cambia a propósito** | Spec fase 5, decisión 1 (identidad dueña de la foto). Límite y mensaje iguales. |
| `PUT /api/medicos/disponibilidad-config` | `MedicoController.java:97-104`, `MedicoService.java:492-512` | `MedicoController.java:59-64`, `Perfiles.java:214-238` | **igual** | Mismas validaciones (>0, ≥0) y mensajes; null conserva; devuelve el perfil; publica `profesional.actualizado`. |
| `GET /api/medicos/disponibilidad?modalidad=` | `MedicoController.java:106-112`, `DisponibilidadService.java:32-56` | `MedicoController.java:66-74`, `PR/perfil/Disponibilidades.java:32-54`, `Agendas.java:110-113` | **igual** | Franjas legacy (modalidad null) incluidas; grilla por defecto L-V 9-12/14-17 solo la primera vez y solo en la modalidad prioritaria. Modalidad inválida: 400 en ambos (main por conversión de enum). |
| `PUT /api/medicos/disponibilidad?modalidad=` | `DisponibilidadService.java:58-81` | `Disponibilidades.java:56-70`, `Agendas.java:116-121` | **igual** | Reemplaza la modalidad + legacy, devuelve lo enviado. Publica `profesionales.disponibilidad.actualizada.v2` (spec fase 5). |
| `GET /api/medicos/stats?periodo=` | `MedicoController.java:83-89`, `MedicoService.java:514-693` | `TU/web/TurnoController.java:54-63`, `TU/turnos/Estadisticas.java:39-200` | **distinto** (menor) | Cálculos, sufijos y JSON idénticos. Diferencia: período inválido → 400 en la migración, MENSUAL en main. Hallazgo 3. |
| Mercado Pago (lo que toca al perfil): status/connect/connect-simulado/disconnect/callback | `MedicoController.java:128-203` | `services/pagos/.../web/MercadoPagoController.java:62-113` | **igual** (JSON, roles, 403 y redirects) | El efecto en "verificado"/buscador llega por `pagos.cuenta-mp.vinculada/desvinculada` (`PR/interno/Manejadores.java:93-112`) y resincronización: **cambia a propósito** (spec fase 9, I3b). |
| Permisos y rutas públicas | `SecurityConfig.java:44` (`/api/medicos` permitAll) | Filtro de profesionales + portones del gateway | **igual** | Sin token en rutas privadas: 401 en ambos casos. |

## 2. Calendario (Google Calendar)

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `GET /google-calendar/status` | `MedicoController.java:205-213` | `CA/web/GoogleCalendarController.java:85-88` | **igual** | `{connected, simulated}`; "conectado" pasa de flag en `Usuario` a "existe `cuenta_google`" (spec fase 3, datos). |
| `POST /google-calendar/connect-simulado` | `MedicoController.java:220-234` | `GoogleCalendarController.java:91-101` | **igual** | 403 con el mismo texto si no es local; crea la cuenta sin tokens. |
| `GET /google-calendar/connect` | `MedicoController.java:236-242`, `GoogleCalendarOAuthService.java:62-72` | `GoogleCalendarController.java:103-106`, `CA/google/TokensGoogle.java:58-67` | **igual** | Mismo scope, `offline`, `consent`, redirect `<public-url>/api/medicos/google-calendar/callback`, state HMAC con la misma clave. |
| `GET /google-calendar/callback` | `MedicoController.java:304-338` | `GoogleCalendarController.java:160-188` | **igual** | Mismos redirects (`error&reason=…`, `invalid_state`, `exchange_failed`, `success`), registra canal (best-effort) y sincronización completa inmediata. Solo cambia el log (sin stack ni cuerpo de Google). |
| `POST /google-calendar/disconnect` | `MedicoController.java:244-255`, `GoogleCalendarSyncService.java:288-302`, `GoogleCalendarOAuthService.java:204-215` | `GoogleCalendarController.java:108-118`, `CA/agenda/AgendaTurnos.java:168-175` | **distinto** (menor) | Mismo orden (canal → eventos de la app → tokens → caché). Pero solo borra de Google los eventos de turnos que siguen en `turno_agendado` (hoy y futuros): hallazgo 4. |
| `GET /google-calendar/eventos` | `MedicoController.java:296-302`, `GoogleCalendarSyncService.java:304-316` | `GoogleCalendarController.java:120-127` | **igual** | Mismo DTO `{id,title,fecha,hour,endHour,allDay}`. Contenido afectado por hallazgo 5. |
| `POST /google-calendar/webhook` | `MedicoController.java:261-294` | `GoogleCalendarController.java:133-158`, `CA/agenda/Canales.java:103-105` | **igual** | `sync` → 200; token HMAC; canal y recurso tienen que coincidir; siempre 200; sin sesión (`CA/seguridad/FiltroAutenticacionUsuario.java:22-24`) y exento de portones. |
| Sincronización periódica (cada 5 min) | `GoogleCalendarPollingScheduler.java:30-39` | `CA/agenda/Programador.java:38-50` | **igual** | Arranque 30 s en vez de 10 s (inventario). Recorre todas las cuentas en vez de "PSIQUIATRA con flag": equivalente porque la cuenta existe solo si está conectado. |
| Sincronización incremental / completa | `GoogleCalendarSyncService.java:59-173` | `CA/agenda/Sincronizacion.java:59-130`, `GoogleCalendarApiHttp.java:84-108` | **igual** | syncToken, `showDeleted`, `singleEvents`, 410 → completa, ventana −7/+90 días, exporta pendientes al final. Guarda además `transparente` (spec fase 3, G3). |
| Exclusión de eventos creados por la app (no son "externos") | `GoogleCalendarSyncService.java:225-231` (`turnoRepository.existsByMedicoIdAndGoogleEventId`, toda la historia) | `Sincronizacion.java:141-145` con `TurnosAgendados.java:89-93` (solo turnos no borrados) | **distinto** | `turno_agendado` borra los pasados cada noche (`Programador.java:67-73`, `TurnosAgendados.java:107-109`): hallazgo 5. |
| Canales watch: registro, detención, renovación diaria (4 AM, vencen en 48 h, vida 6 días) | `GoogleCalendarWatchService.java:58-135`, `GoogleCalendarWatchScheduler.java:29-43` | `Canales.java:54-100`, `Programador.java:53-65` | **igual** | Misma dirección y token firmado (los canales ya registrados siguen sirviendo, spec fase 3 punto 4). |
| Evento de Google y Meet para turnos confirmados | Sincrónico dentro de la confirmación (`TurnoService.java:476`, `PagoWebhookHandler.java:96`, `GoogleCalendarService.java:61-160`) | Por `turnos.turno.confirmado` → `AgendaTurnos.java:60-112`, publica `calendario.meet.creado.v1` | **cambia a propósito** | Spec fase 3, decisión/pregunta 1: el link llega después y en un segundo mail. Título, descripción, `hangoutsMeet`, extracción del link y calendario `primary` iguales. Documentos no van a Google (igual). |
| Reintento de turnos sin evento + mail "ya tenés el link" | `GoogleCalendarSyncService.java:175-223` | `AgendaTurnos.java:153-161`, notificaciones `ManejadoresTurnos.java:266-278` | **igual** | Solo `linkNuevo`, respeta la preferencia de mails del paciente. |
| Reprogramar / cancelar → mover / borrar el evento | `GoogleCalendarService.java:203-264` (desde `TurnoService.java:725, 928`) | `AgendaTurnos.java:115-150` por eventos | **igual** (por eventos, spec fase 3) | |
| Meet simulado en local | `GoogleCalendarService.java:144-159` | `AgendaTurnos.java:73-80, 204-215` | **igual** | Mismo formato `abc-defg-hij`. |
| Bloqueo de horarios por eventos de Google en la agenda pública | En vivo, por día, caché 90 s (`TurnoService.java:96-150`, `GoogleCalendarService.java:162-201`) | Copia sincronizada: `CA/interno/InternoCalendarioController.java:32-43` ← `TU/agenda/Horarios.java:~165-172` | **cambia a propósito** | Spec fase 3, punto 2 y "Cambio de comportamiento"; transparentes y todo el día igual; recorte de eventos de varios días (G3). Horizonte: hallazgo 7 (a verificar). |
| Cuenta eliminada → corta canal y borra datos de Google | No verificado en main | `CA/agenda/Manejadores.java:~88-99` | **a verificar** | Fuera de lo pedido; lo dejo anotado para quien revise identidad (Ley 25.326). |

**Filas por estado (34 en total):** igual 22 · cambia a propósito 5 · distinto 6 · falta 0 · a verificar 1 (más 2 casos borde a verificar en los hallazgos 6 y 7).

## Hallazgos (distinto / falta / a verificar)

1. **[distinto, ALTO] Guardar "Mi perfil" borra en profesionales la vinculación de MP, la suscripción y "puede recetar".**
   `Perfiles.java:181-184` guarda la respuesta de identidad con `guardarDatosMonolito`, que pisa `mp_vinculada`, `suscripcion_estado`,
   `suscripcion_periodo_hasta` y `puede_recetar` (`Profesionales.java:122-140`). Identidad los devuelve en `null`
   (`ID/interno/InternoIdentidadController.java:330-335`), y al mapearlos a `boolean` quedan en `false`. Con `TRANQUI_FUENTE_IDENTIDAD=identidad`
   (lo que usa el compose), esto pasa cada vez que se guarda el perfil. La resincronización con pagos lo arregla recién en la siguiente pasada
   (`SincronizacionMonolito.java:59-77`, cada 60 s, y solo si pagos responde). La spec de fase 9 (I3b) dice que "el refresco desde identidad
   ya no los pisa", pero este camino quedó afuera.
   *Impacto:* durante ≥1 minuto después de guardar, el profesional desaparece del buscador (`tieneAcceso` da false). La respuesta del PUT vuelve
   con `verificado:false` si MP está habilitado, así que aparece el aviso de "completá tu perfil", y no puede emitir recetas (`puede_recetar=false`).
   Si pagos está caído, el problema dura hasta que vuelva.
   *Arreglo:* en `Perfiles.java:183`, si `interno.fuenteIdentidad()` usar `profesionales.guardarDatosIdentidad(...)` (como
   `SincronizacionMonolito.java:182-186`). Agregar un test: guardar el perfil no cambia `mp_vinculada` ni la suscripción.

2. **[distinto, MEDIO] Validaciones nuevas de identidad en `PUT /perfil`, y su error se muestra como 503 genérico.**
   Identidad rechaza ahora cosas que main aceptaba (`InternoIdentidadController.java:288-318`): nombre vacío; `tipoDocumento` fuera de
   `DNI, LC, LE, Pasaporte` (l.51); teléfono que no tenga 6-15 dígitos; CUIL que no tenga 11 dígitos; documento fuera de 6-9 dígitos; fecha de nacimiento futura o anterior a 1900.
   El frontend de main guarda `"PASAPORTE"` en mayúsculas (`LoginPage.tsx:936`, `CompleteProfileModal.tsx:145`, `MiCuentaView.tsx:439`): un
   profesional registrado con pasaporte **no puede guardar su perfil**. Además, el 400 de identidad es una `RestClientException` y
   `Perfiles.java:172-179` lo convierte en 503 "No se pudo guardar el perfil en este momento. Probá de nuevo en unos minutos.", así que el
   profesional reintenta sin saber qué campo corregir.
   *Arreglo:* aceptar `PASAPORTE` sin distinguir mayúsculas (o normalizar). En `Perfiles`, distinguir `HttpClientErrorException` 4xx y
   devolver 400 con el mensaje de identidad, y dejar el 503 solo para caídas. Decidir si las demás reglas nuevas son a propósito; si lo son,
   documentarlas en la spec de fase 9/10.

3. **[distinto, BAJO] `GET /api/medicos/stats` con período desconocido.** main lo toma como MENSUAL (`MedicoService.java:531, 550-551`).
   La migración responde 400 "Período inválido" (`Estadisticas.java:43-46`). El frontend solo manda DIARIO/SEMANAL/MENSUAL (`api.ts:216`),
   así que hoy no afecta al usuario.
   *Arreglo:* caer en MENSUAL como main, o documentarlo como cambio a propósito.

4. **[distinto, BAJO] Al desvincular Google quedan en el calendario del profesional los eventos de turnos pasados.**
   main borra de Google todos los turnos con `googleEventId` (`GoogleCalendarSyncService.java:288-302`). La migración solo borra los de
   `turno_agendado` (`AgendaTurnos.java:168-175`, `TurnosAgendados.java:101-103`), y esa tabla pierde los turnos pasados cada noche.
   *Impacto:* después de desvincular, quedan en su Google las consultas "[Tranqui App] …" ya ocurridas. En main desaparecían.
   *Arreglo:* no borrar de `turno_agendado` los que tienen `google_event_id` (o guardar los ids de evento aparte) y borrarlos al desvincular.
   Si se decide que quedarse con el historial es mejor, documentarlo en la spec de fase 3.

5. **[distinto, MEDIO] Las consultas de la app de los últimos 7 días aparecen como "eventos externos" duplicados.**
   La sincronización completa lee desde −7 días (`Sincronizacion.java:111-112`) y descarta los eventos de la app con `esEventoDeLaApp`
   (`TurnosAgendados.java:89-93`), que solo mira `turno_agendado`. Esa tabla borra los turnos de días anteriores a las 3:30
   (`Programador.java:67-73`). main miraba toda la tabla de turnos (`GoogleCalendarSyncService.java:228`). Una sincronización completa pasa
   al conectar, cuando Google da 410 y en la primera pasada después del arranque. En esos casos, los eventos de turnos de la semana pasada
   se guardan en `evento_externo`. Lo mismo pasa con cambios incrementales sobre esos eventos.
   *Impacto:* en "Próximos eventos" y en el calendario del profesional, cada consulta pasada aparece dos veces: como turno y como
   "[Tranqui App] Consulta …". Además se publica `calendario.horario.bloqueado` de más. En días pasados no bloquea horarios.
   *Arreglo:* el mismo que el 4 (conservar los ids de evento de turnos pasados), o filtrar en `aplicarCambio` los eventos con el prefijo o la
   propiedad extendida de la app. Test: con un turno de ayer borrado de la copia, la sincronización completa no lo guarda como externo.

6. **[a verificar, BAJO] Profesional recién registrado antes de que llegue a la copia.** `Perfiles.buscar` (`Perfiles.java:240-243`) da
   404 si identidad todavía no avisó (`usuario.registrado` → `Manejadores.java:46-53`). main siempre encontraba al usuario. Normalmente son
   segundos. Hay que confirmar con la suite C4 que el primer `GET /perfil` después del registro no falle. Si falla, refrescar a pedido
   (`sincronizacion.refrescar`) cuando falta el profesional.

7. **[a verificar, BAJO] Horizonte del bloqueo por Google.** main consultaba Google en vivo para cualquier fecha
   (`GoogleCalendarService.java:166-201`). La copia solo trae −7/+90 días en la sincronización completa (`Sincronizacion.java:111-112`).
   Hay que confirmar si las reservas pueden pasar los 90 días. No encontré un límite en el frontend ni en `TurnoService`. Si pueden, un
   horario ocupado en Google más allá de los 90 días se ofrecería igual. Arreglo: alinear la ventana con el horizonte de reserva, o limitar
   la reserva a 90 días.

---
No revisé (fuera de dominio o sin tiempo): cálculo de `puedeRecetar`/estado de suscripción en pagos (lo que alimenta la visibilidad),
el paywall del gateway en detalle, la carga inicial de calendario/profesionales y la vuelta atrás.
