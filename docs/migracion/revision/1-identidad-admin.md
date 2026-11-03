# C3 — Identidad y cuenta, administración y observabilidad

Comparación de lógica `main` (`f11-main`, `7d06fb0`) contra la migración (`f11-c4`). **Ojo:** a mitad de la revisión
el worktree `f11-c4` desapareció del disco; el resto se leyó con `git show cf418b5:<ruta>` (punta de
`test/full-fase-11-c3-revision` / `fix/full-fase-11-alerta-admins`, el mismo código).

Rutas abreviadas:
- **M** = `f11-main/backend/src/main/java/com/tranqui/app/`
- **I** = `services/identidad/src/main/java/com/tranqui/identidad/`
- **N** = `services/notificaciones/src/main/java/com/tranqui/notificaciones/`
- **Spec F9** = `docs/specs/2026-10-28-fase-9-identidad.md`

## 1. Identidad y cuenta

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `POST /api/auth/register`: validaciones y mensajes (mail, contraseña, mail repetido, términos, foto de 3 MB, domicilio de 8 a 140, Torre/Piso/Depto/Barrio, `+54`, carrera por mail único) | M `controller/AuthController.java:75-126,205-211` | I `web/AuthController.java:128-243` | igual | Los mismos textos y el mismo orden. La carrera por mail repetido devuelve el mismo 400 (`DuplicateKeyException` en vez de `DataIntegrityViolationException`). La respuesta es la misma (`message`, `email`, `requiresVerification`). |
| Registro: rol permitido | Cualquier valor del enum `Rol`, ADMIN y VISITADOR incluidos, o `null` (M `AuthController.java:139`, `model/Rol.java`) | Solo PACIENTE o PSIQUIATRA, si no 400 "El rol debe ser PACIENTE o PSIQUIATRA." (I `AuthController.java:137-140`) | cambia a propósito | Spec F9, líneas 86 y 109. En `main` cualquiera podía registrarse como ADMIN. El frontend solo manda esos dos roles. |
| Registro: **CUIL** del profesional | Se guarda `usuario.cuil` (M `AuthController.java:153`). El frontend lo manda (`LoginPage.tsx:424`) | `RegisterRequestDto.cuil` existe (I `web/dto/RegisterRequestDto.java:28`), pero el `Usuario.builder()` no lo usa (I `AuthController.java:202-236`) y `PerfilProfesionalData` no tiene CUIL | **distinto** | Ver hallazgo 1. |
| Registro: datos profesionales (matrícula, título, especialidad, CUIT, domicilio, modalidades) | Columnas de `usuario` | `perfil_registro` (I `AuthController.java:245-289`), con lo que los demás servicios leen | igual | Sí cambia lo que ve el panel admin: hallazgo 2. |
| Registro: suscripción inicial `PENDING_VERIFICATION` para profesionales | En la misma transacción (M `AuthController.java:187-204`) | Por evento `usuario.registrado`: pagos la crea con el mismo criterio de plan (`services/pagos/.../ManejadoresSuscripciones.java:64-105`) | igual | Es asíncrona. |
| Registro: mail con el código de verificación | Directo (M `AuthController.java:212`) | Evento `codigo-verificacion` tipo `REGISTRO`, que notificaciones manda con `enviarCodigoVerificacion` (N `avisos/ManejadoresIdentidad.java:11-27`) | cambia a propósito | Spec F9, criterio de la línea 159. La plantilla es idéntica: `diff` del método sin diferencias. Ahora el código sale de `SecureRandom` (spec F9, línea 109). |
| `POST /api/auth/verify-email` | M `AuthController.java:221-249` | I `AuthController.java:329-358` | igual | 404 "Usuario no encontrado.", "ya está verificado", código incorrecto y código vencido. |
| `POST /api/auth/resend-code` | M `:251-272` | I `:360-396` | igual | El mail sale por evento (tipo `EMAIL_VERIFICATION`, va a la misma plantilla). |
| `POST /api/auth/forgot-password` | M `:274-291` | I `:398-430` | igual | Responde lo mismo si el mail no existe, para no revelarlo. Evento `PASSWORD_RESET` que va a `enviarCodigoRecuperacion` (plantilla idéntica). |
| `POST /api/auth/reset-password` | M `:293-321` | I `:432-462` | igual | Los mismos cinco mensajes. |
| `POST /api/auth/login` | M `:341-379` | I `:464-485` | igual | 401 "Credenciales incorrectas" y "Esta cuenta fue eliminada.", 403 si falta verificar el mail (salvo ADMIN). Cookie `SESSION-TOKEN` con 7 días, `HttpOnly` y la misma regla de Secure y SameSite. Mismo `UserResponseDto` (el `rol` pasa de enum a String, el mismo JSON). |
| JWT de sesión | HS256 con `JWT_SECRET`, sin id | RS256 con `uid`; los HS256 se aceptan durante la transición | cambia a propósito | Spec F9, líneas 70-72 y decisión 3 (línea 209). |
| `POST /api/auth/google` con token real | M `:381-414`, `service/GoogleAuthService.java:67-92` | I `:487-501`, `seguridad/GoogleAuthService.java:81-129` | igual | Cuenta nueva: PACIENTE, mail verificado, `perfilCompleto=false`, sin términos aceptados. Ahora además publica `usuario.registrado`. |
| Login falso `mock-<mail>` | Lo acepta en cualquier entorno (M `GoogleAuthService.java:38`) | Solo con `identidad.google.simulado=true` (I `GoogleAuthService.java:52`) | cambia a propósito | Spec F9, líneas 81 y 98 y decisión 1 (línea 204). |
| `POST /api/auth/complete-profile` | M `:420-459` | I `:503-557` | igual | Los mismos campos obligatorios y el mismo mensaje. Ahora además publica `usuario.actualizado`. |
| `GET /api/auth/me` | M `:461-469` | I `:559-566` | igual | El 401 en vez de 403 sin sesión ya es conocido. |
| `POST /api/auth/aceptar-terminos` | M `:475-485` | I `:568-579` | igual | |
| `GET /api/auth/mi-cuenta` | M `service/AccountService.java:30-35,185-213` | I `cuenta/AccountService.java:37-41,205-233` | igual | `MiCuentaDto` con los mismos campos (comparados con `diff`). |
| `PUT /api/auth/mi-cuenta` | M `AccountService.java:37-76` | I `AccountService.java:43-83` | igual | Los mismos campos, `+54` y el 400 por foto de más de 3 MB. Ahora publica `usuario.actualizado` y turnos y profesionales refrescan su copia. |
| `PUT /api/auth/mi-cuenta/notificaciones` | M `:78-85` | I `:85-97` | igual | Ahora además publica `usuario.actualizado` con la preferencia. |
| `GET` y `PUT /api/auth/mi-cuenta/novedades` | M `AuthController.java:519-537`, `service/novedades/NovedadesService.java:199-225` | I `AuthController.java:612-632`, `novedades/NovedadesService.java:198-224` | igual | Lee de Resend (con las bajas hechas desde el mail), crea el contacto si no existe y devuelve `{recibir}`. Con cuerpo `null` ahora da `false` en vez de un NPE. |
| `POST /api/auth/mi-cuenta/password` | M `AccountService.java:101-121` | I `AccountService.java:117-137` | igual | Sin la actual si la cuenta es solo de Google. Mismos mensajes. |
| `POST /api/auth/set-new-password` | M `AuthController.java:559-578` | I `:648-666` | igual | |
| `POST /api/auth/mi-cuenta/eliminar` en identidad | M `AccountService.java:126-183`, `AuthController.java:580-613` | I `AccountService.java:139-203`, `AuthController.java:668-685` | igual | La misma anonimización, `deleted-{id}@tranquiapp.local`, el contacto de Resend borrado después del commit y la cookie borrada. Ahora además publica `usuario.eliminado`. |
| Efecto de la eliminación en otros servicios | Solo anonimiza la fila, que sigue existiendo (M `AccountService.java:123-125`: "Anonimiza en vez de borrar") | `usuario.eliminado` hace un `DELETE` de la copia `persona` y `profesional` en profesionales (`services/profesionales/.../interno/Manejadores.java:70-71,160-162`, `datos/Personas.java:156-158`, `datos/Profesionales.java:202-204`) | **a verificar** | Ver hallazgo 4. |
| `POST /api/auth/logout` | M `:615-635` | I `:687-691` | igual | Sin sesión: 403 en `main` y 401 en la migración, el mismo caso que `/me`. |
| Sesión de una cuenta eliminada o con el token de otra | `findByEmail` falla y queda anónimo (M `config/JwtAuthenticationFilter.java:34-45`) | 401 si no existe, si `cuentaEliminada` o si el `uid` no coincide (I `seguridad/FiltroAutenticacionUsuario.java:76-84`) | igual | Más estricto en la migración. |
| `GET /api/site-access/estado` y `POST /api/site-access/verificar` | M `controller/SiteAccessController.java` | I `web/SiteAccessController.java` | igual | Mismo JSON y mensaje "Contraseña incorrecta.", cookie `SITE-ACCESS` (SHA-256, 30 días). Ahora compara la contraseña en tiempo constante y acepta cuerpo `null`. |
| El portón aplicado al resto de la API | `config/SiteAccessFilter.java` (403 con el mismo JSON) | `gateway/.../portones/PortonesGlobalFilter.java:47-56,105-107` | igual | Las mismas excepciones, más `/internal/` y `/actuator/`, que no son públicas. `SITE_ACCESS_PASSWORD` llega a identidad y al gateway en `docker-compose.yml:209,343`. |
| `POST /api/tickets` | M `controller/TicketController.java:15-23`, `service/TicketService.java:28-61` | I `web/TicketController.java:30-44`, `tickets/TicketService.java:34-77` | igual | Roles PACIENTE y PSIQUIATRA, los mismos mensajes. La notificación a cada admin ("Nuevo ticket de soporte", `TICKET_NUEVO`, mismo texto) sale por `ticket.creado` (N `ManejadoresIdentidad.java:50-58`). |
| `GET /api/tickets/mios` | M `:25-29` | I `:46-56` | igual | Mismo orden (`fecha_actualizacion DESC`) y el mismo `TicketResumenDto`. |
| `GET /api/tickets/{id}` | M `:31-39`, `TicketService.java:81-117` | I `:58-72`, `TicketService.java:95-138` | igual | El dueño o un admin; si no, 403 "No tenés permiso para ver este ticket.". `deAdmin` y mensajes por `fecha_envio ASC`. |
| `POST /api/tickets/{id}/mensajes` | M `TicketService.java:119-177` | I `TicketService.java:140-226` | igual | Si responde el admin: ACTIVO, notificación "Respondieron tu ticket" y mail `enviarRespuestaTicket` (plantilla idéntica) por `ticket.respondido`. Si responde el creador: de RESUELTO pasa a PENDIENTE y se avisa a los admins por `ticket.mensaje-recibido` (N `ManejadoresIdentidad.java:29-69`). |
| `POST /api/soporte/solicitud-datos` | Mail directo (M `controller/SoporteController.java:17-30`) | Evento `soporte.solicitud-datos`, que notificaciones manda (I `web/SoporteController.java:30-59`, N `ManejadoresIdentidad.java:97-117`) | cambia a propósito | Spec F9, líneas 76-78 y 125. Los mismos roles y la misma plantilla (el destino `soporte@tranquisalud.com` ahora es configurable, con el mismo valor por defecto). |
| Límite de pedidos en login y registro | Ninguno en la aplicación | `RequestRateLimiter` por IP (`gateway/src/main/resources/application.yml:295-307`) | cambia a propósito | Spec Fase 0 (`2026-10-14-fase-0-plataforma.md:41`) y spec F9, línea 130. Puede dar 429. |
| Ediciones concurrentes de `usuario` y `ticket` | JPA sin `@Version`: gana la última | Versión optimista: `OptimisticLockingFailureException` (I `datos/Usuarios.java:372,433-436`, `datos/Tickets.java:103`), que `web/Errores.java` no mapea | **a verificar** | Ver hallazgo 5. |

## 2. Administración y observabilidad

| Funcionalidad | main | Migración | Estado | Detalle |
|---|---|---|---|---|
| `GET /api/admin/users`: forma de la respuesta | La entidad `Usuario` entera (M `controller/AdminController.java:21-24`) | `UsuarioAdminDto` (I `web/AdminController.java:45-63`) | cambia a propósito | Ya conocido (Fase 9, decisión 3). |
| `GET /api/admin/users`: **valores del perfil profesional** | Las columnas actuales de `usuario`, que también se editan desde el perfil | Salen de `perfil_registro`, la foto del registro (I `web/dto/UsuarioAdminDto.java:14-15`, `AdminController.java:57`) | **distinto** | Ver hallazgo 2. |
| `PUT /api/admin/users/{id}/rol` | M `:26-43` | I `:65-100` | igual | "Rol inválido", PSIQUIATRA implica `verificadoAdmin=true`. Con `rol` ausente ahora da 400 en vez de 500. Publica `usuario.actualizado`. |
| `POST /api/admin/users/{id}/verify` | Rol PSIQUIATRA, verificado, `licenseVerifiedAt` y `By="admin"`; suscripción a VERIFIED y su evento, **solo si ya tiene suscripción** (M `:56-80`) | Lo mismo, más los eventos `visibilidad-cambiada` y `verificacion-resuelta`; pagos pasa a VERIFIED con `logEvent`. **Si no hay suscripción, la crea** (`ManejadoresSuscripciones.java:126-148`) | **distinto** (caso borde) | Ver hallazgo 3. |
| `POST /api/admin/users/{id}/reject` | `verificadoAdmin=false`; suscripción a REJECTED si existe (M `:87-105`) | Lo mismo por evento; si no hay suscripción, la crea y la deja en REJECTED | **distinto** (caso borde) | Hallazgo 3. |
| `POST /api/admin/users/{id}/reset-password` | M `:112-152` | I `:188-227` | igual | El mismo generador (10 caracteres sin ambiguos), `mustChangePassword`, la misma respuesta `{password, email}` y el mismo 400. El mail sale por el evento `password-temporal.generada`, con la plantilla idéntica (spec F9, líneas 76-78). |
| Quién puede usar `/api/admin/users/**` y `/api/admin/tickets/**` | `@PreAuthorize("hasRole('ADMIN')")` con el rol de la base | `requireAdmin` con el rol de la base (I `AdminController.java:33-39`) | igual | 401 sin sesión y 403 sin cuerpo (ya conocido). |
| `GET /api/admin/tickets` | M `:158-161` | I `:229-240` | igual | Todos los tickets por `fecha_actualizacion DESC`. |
| `PUT /api/admin/tickets/{id}/estado` | M `:163-171`, `TicketService.java:179-192` | I `:242-258`, `TicketService.java:228-246` | igual | "Estado inválido." con 400 y 404 si el ticket no existe. Ahora también acepta espacios alrededor del estado. |
| `GET /api/admin/salud` | No existe | En el gateway (`controller/AdminSaludController.java`), rol ADMIN según el token | nuevo | No falta nada. Como valida el token, un admin degradado sigue entrando hasta 7 días (revocación postergada, decisión 7 de la spec F9, línea 214). |
| `GET /api/admin/observabilidad/{categoria}` | No existe | En el gateway (`controller/AdminObservabilidadController.java`) | nuevo | Lo mismo que la fila anterior. |
| `POST /api/internal/novedades` (y `?prueba=true`) | M `controller/NovedadesInternoController.java:25-39` | `POST /internal/identidad/novedades` (I `novedades/NovedadesController.java:26-40`) | cambia a propósito | Cambia la ruta (spec F9, línea 104; el script se corrigió en I5, línea 146). Iguales: token `X-Novedades-Token`, 404 si viene con `X-Forwarded-*`, 202 o 200, 400 con `{error}`, validaciones, broadcast por segmento, envío individual en local, idempotente por id, sincronización de bajas y prueba solo a admins. |
| `GET /api/internal/novedades/{id}` | M `:41-46` | I `NovedadesController.java:42-47` | cambia a propósito | Solo cambia la ruta. `Resultado` tiene los mismos campos (el estado pasa de enum a String, el mismo JSON). |
| Plantilla del mail de novedades | `ResendEmailService.htmlNovedades` | I `novedades/MailNovedades.java` | igual | El mismo texto, link de baja, CTA y remitente "Tranqui App <from>". No la comparé byte a byte. |

## Hallazgos (distinto / falta / a verificar)

1. **distinto: el CUIL que se carga al registrarse como profesional se pierde.**
   - En `main` se guarda en `usuario.cuil` (M `controller/AuthController.java:153`).
   - En la migración `RegisterRequestDto.cuil` se recibe (I `web/dto/RegisterRequestDto.java:28`), pero no se pasa al
     `Usuario.builder()` (I `web/AuthController.java:202-236`).
   - **Impacto:** `estaVerificado` de profesionales exige `cuil != null`
     (`services/profesionales/.../perfil/Perfiles.java:249`). El profesional recién registrado queda con el perfil
     incompleto y no aparece en el buscador hasta que vuelva a cargar el CUIL en su perfil. Además el panel admin
     muestra "CUIL" entre lo que le falta.
   - **Arreglo:** agregar `.cuil(dto.getCuil())` al builder del registro, más un test de integración que registre un
     PSIQUIATRA con CUIL y lo lea.

2. **distinto: el panel admin ve el perfil del registro, no el actual.**
   - `UsuarioAdminDto.desde` toma título, especialidad, matrícula (tipo, provincia y número), CUIT, domicilio,
     modalidades, descripción, tags, pacientes que atiende, institución, años de experiencia y experiencia de
     `perfil_registro` (I `web/dto/UsuarioAdminDto.java:14-15`, `web/AdminController.java:57`).
   - Esa tabla solo la escribe el registro, y los campos que se editan después quedan en `null`
     (I `web/AuthController.java:245-288`).
   - La carga inicial no la llena: `perfil_registro` solo se inserta desde `PerfilesRegistro.guardar`, que solo llama
     el registro.
   - **Impacto:** `AdminDashboard.getMissingProfileFields` (frontend, `AdminDashboard.tsx:118-146,265-272`) marca a
     **todo** profesional aprobado como "Aprobado, perfil incompleto (N)" aunque tenga el perfil completo. Para los
     profesionales que vienen de `main`, el admin no ve ni la matrícula ni el título al revisar una verificación
     pendiente. En `main` veía los valores actuales. El golden master de C4 no lo detectó porque compara la forma, no
     los valores.
   - **Arreglo:** que `getUsers` complete el perfil desde profesionales, con una ruta interna por lote o con una copia
     en identidad que se actualice con el evento `profesional.actualizado`. Y que la carga inicial llene
     `perfil_registro`, o el dato equivalente, desde las columnas del monolito.

3. **distinto (caso borde): verificar o rechazar a alguien sin suscripción le crea una.**
   - En `main` la transición se hace solo si ya existe la suscripción (M `controller/AdminController.java:67-77,94-102`).
   - En la migración, pagos crea la fila `PENDING_VERIFICATION` y le aplica VERIFIED o REJECTED
     (`services/pagos/.../ManejadoresSuscripciones.java:126-148`).
   - **Impacto:** si el admin verifica a alguien que llegó a PSIQUIATRA por "cambiar rol" (sin suscripción), le aparece
     una suscripción "consultorio" VERIFIED, con efecto en el paywall y la facturación. Si rechaza a un paciente,
     queda una suscripción REJECTED que no debería existir.
   - **Arreglo:** crear la fila anticipada solo si el usuario es profesional (rol PSIQUIATRA o con profesión según el
     suscriptor refrescado) y no crearla nunca para RECHAZADO. Si no, documentarlo como decisión.

4. **a verificar: borrar una cuenta borra las copias en profesionales en vez de anonimizarlas.**
   - `main` conserva la fila anonimizada a propósito: historia clínica con guarda legal de 10 años
     (M `service/AccountService.java:123-125`).
   - En la migración, `usuario.eliminado` hace `DELETE FROM persona` y `DELETE FROM profesional`
     (`services/profesionales/.../datos/Personas.java:156-158`, `datos/Profesionales.java:202-204`).
   - La historia clínica arma la lista de pacientes con `personas.porIds(...)`, y guardar un informe o un seguimiento
     da 404 "Paciente no encontrado" (`clinica/HistoriaClinicaService.java:162-163,228-229,263-264`).
   - **Impacto probable:** el paciente que elimina su cuenta desaparece de la lista de pacientes del profesional, y su
     historia ya no se puede consultar desde la app. En `main` seguía como "Usuario eliminado".
   - Lo dejo "a verificar" porque no leí la consulta de la lista de pacientes en `main` y es del dominio de
     profesionales.
   - **Arreglo:** que el manejador anonimice la copia (como identidad) en vez de borrarla, o que la refresque desde
     `/internal/identidad`.

5. **a verificar: las ediciones concurrentes pueden dar 500.**
   - `Usuarios.actualizar` y `Tickets.actualizar` usan versión optimista y tiran `OptimisticLockingFailureException`
     (I `datos/Usuarios.java:433-436`, `datos/Tickets.java:103`). `web/Errores.java` no la mapea, así que responde 500
     sin mensaje.
   - Además se pierde lo que se iba a guardar: por ejemplo, el mensaje del ticket se inserta en la misma transacción y
     vuelve atrás.
   - `main` no tenía `@Version`, así que ganaba la última escritura.
   - **Casos posibles:**
     - El usuario que llega en el filtro se carga al principio del pedido y `aceptar-terminos`, `set-new-password`,
       `mi-cuenta/novedades` y `complete-profile` lo actualizan con esa versión. Choca con una escritura interna desde
       profesionales (`PUT /internal/identidad/profesionales/{id}/identidad`) o con dos pestañas a la vez.
     - El admin y el creador responden el mismo ticket a la vez.
   - **Arreglo:** mapear `OptimisticLockingFailureException` a 409 con un mensaje ("Los datos cambiaron, volvé a
     intentar") o reintentar una vez releyendo la fila.

6. **a verificar (relacionado con el 2): el CUIT de los profesionales migrados no llega a pagos.**
   - `/internal/identidad/suscriptores` saca el CUIT de `perfil_registro`
     (I `interno/InternoIdentidadController.java:338-343`).
   - Los profesionales que vienen de `main` no tienen fila en esa tabla, así que pagos recibe `cuit=null`. `taxId` sí
     viene de `usuario`.
   - **Arreglo:** el mismo del hallazgo 2 (llenar `perfil_registro` en la carga inicial). Además, que el revisor de
     pagos confirme si la facturación usa `cuit` o `taxId`.

**Lo que no revisé:**
- `DataInitializer` (el alta del admin).
- Los claims del JWT, más allá de lo que dice la spec.
- La plantilla de novedades byte a byte.
- La consulta de la lista de pacientes de `main` (hallazgo 4).
