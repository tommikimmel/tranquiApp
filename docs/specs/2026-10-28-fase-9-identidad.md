# Spec: Fase 9 — Identidad

- **Fecha:** 2026-10-28
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** aprobada (2026-10-28)
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 8
- **Plan:** [microservicios.md, Fase 9](../arquitectura/microservicios.md#fase-9-identidad-y-retiro-del-monolito)
- **Forma de trabajo:** igual que las Fases 7 y 8: solo cambiar la arquitectura, sin buildear imágenes ni levantar
  el stack; tests con Maven de a un módulo; implementan workers con un plan cerrado por tarea (como máximo cuatro a
  la vez). La verificación de punta a punta queda postergada.

## Entendimiento del problema

Después de la Fase 8, lo único que el monolito atiende solo es identidad, más algunas piezas sueltas:

- **Cuenta y sesión (`AuthController`, `AccountService`, `JwtService`, `GoogleAuthService`):** registro, login,
  login con Google, verificación de mail, reenvío del código, recuperación de contraseña, `set-new-password`,
  completar el perfil, términos, `me`, logout y Mi Cuenta (datos, notificaciones, novedades, contraseña y
  eliminación con anonimización).
- **Soporte (`TicketController`, `SoporteController`):** tickets y sus mensajes, y la solicitud de copia de datos.
- **Administración de usuarios (`AdminController`):** listado, cambio de rol, verificación y rechazo de matrícula,
  contraseña temporal y tickets del admin.
- **Piezas sueltas:** el portón del sitio (`/api/site-access`, cookie `SITE-ACCESS`), las novedades
  (`/api/internal/novedades`, Resend Broadcasts), `/api/health` (solo lo usa el healthcheck del contenedor),
  `/api/medicos/stats` (lee la copia de `turno`) y `DataInitializer` (crea el admin).
- **Datos:** la parte de identidad de `usuario` (cuenta, credenciales, verificación de mail, datos personales y de
  paciente, matrícula y verificación, datos fiscales, preferencias), `ticket`, `ticket_mensaje` y
  `novedades_envio`. Las crea Hibernate (`ddl-auto: update`), no Flyway; los ids son IDENTITY.

Lo que encontré y el plan no resolvía:

1. **El monolito es la fuente de datos de otros servicios, no solo de sus rutas.** Los eventos de identidad son
   pobres (id, mail, nombre, rol), así que los servicios piden el resto al monolito:
   - pagos: `/internal/monolito/suscriptores/{id}` (datos fiscales, profesión, verificación);
   - profesionales: `/internal/monolito/profesionales[/{id}]`, `/profesionales/estado` cada 60 s (verificación, Mercado
     Pago vinculado y suscripción), `/internal/monolito/turnos` cada 15 minutos y `PUT /pacientes/{id}` y
     `PUT /profesionales/{id}/identidad` (escrituras);
   - turnos: `/internal/monolito/pacientes[/{id}]`, `POST /pacientes` (crea el paciente al reservar) y
     `/internal/monolito/profesionales` cada 15 minutos;
   - el paywall del gateway: `/internal/monolito/suscripciones/acceso` (la copia de la Fase 8).
2. **El JWT.** Lo firma el monolito con HS256 y `JWT_SECRET`, y con la **misma clave** lo validan el gateway y los
   seis servicios. Cualquiera que tenga la clave puede fabricar un token de admin. Dura 7 días, sin refresh ni
   revocación, y **no lleva el id del usuario** (todo se resuelve por mail). El plan pide que el gateway valide con
   la clave pública de identidad.
3. **Dos problemas de seguridad que ya están en producción (`main`):**
   - **Login falso con Google:** `GoogleAuthService.verifyGoogleToken` acepta cualquier token `mock-<email>` en
     cualquier entorno. `POST /api/auth/google {"idToken":"mock-admin@tranqui.com"}` inicia sesión como el admin,
     sin contraseña.
   - `GET /api/admin/users` devuelve la entidad `Usuario` entera: hash de la contraseña, códigos de verificación y
     recuperación y los tokens cifrados de Mercado Pago y Google (solo lo ve un admin).
4. **Mails que todavía salen directo del monolito** (no por evento): la contraseña temporal del admin y la solicitud
   de copia de datos.
5. **Las novedades no llegan desde la Fase 0:** `scripts/mantenimiento.mjs` las dispara en `127.0.0.1:8081`, pero en
   el compose de microservicios el backend no publica ningún puerto.
6. **La `version` de `usuario`** la suben identidad y la copia del perfil profesional. Los consumidores descartan
   versiones viejas (`conVersion`), así que identidad tiene que seguir desde la misma versión.
7. **Retirar el monolito ahora borra todas las vueltas atrás.** Ninguna de las Fases 1 a 8 está en producción
   todavía, y cada una vuelve atrás con un flag del monolito. Si se apaga junto con identidad, no queda red.
8. **Memoria:** identidad suma un contenedor (~288 MB) mientras el monolito siga prendido.

## Solución propuesta (con las recomendaciones de las Preguntas)

La Fase 9 mueve identidad a su servicio y deja todo listo para que el monolito quede sin tráfico, **pero no lo apaga**
(decisión 2): después vienen la Fase 10 (tests y validación de datos) y la Fase 11 (comparación con `main`), y el
apagado y el archivo de su base quedan para una fase posterior. Requiere la Fase 8 cortada (el paywall pasa a leer de
pagos).

### I1 — Catálogo, JWT y monolito
- **JWT nuevo (pregunta 3):** RS256 con el par de claves de identidad (`JWT_PRIVATE_KEY` solo en identidad,
  `JWT_PUBLIC_KEY` en el gateway y los servicios; ambas en el `.env` del VPS, nunca en el repo). Mismos claims y
  cookie, más `uid` (id del usuario).
  - Un verificador único en `libs/eventos` (junto a `ServiceJwt`, donde ya está jjwt) que reemplaza los seis `UsuarioJwt` y el del gateway: acepta RS256 y,
    mientras `JWT_HS256_ACEPTADO=true`, también los HS256 de hoy, así nadie pierde la sesión en el corte. Siete días
    después del corte se apaga.
- **Eventos nuevos (pregunta 6):** `identidad.password-temporal.generada.v1` y
  `identidad.soporte.solicitud-datos.v1`; notificaciones manda los dos mails, como ya hace con el código de
  verificación.
- Flag `identidad.externo` (`IDENTIDAD_EXTERNO`) en el monolito: con `true`, sus rutas de identidad responden 409 y
  no corre `DataInitializer`. Con `false` (vuelta atrás), todo como hoy.
- **Sin el login falso también en el monolito** (decisión 1): `mock-` solo con `google.simulado=true` (por defecto
  `false`), y `/api/admin/users` con un DTO sin hashes, códigos ni tokens. Los dos mails directos pasan a los
  eventos nuevos cuando `envio-directo=false`, como el código de verificación.
- Rutas internas para la carga inicial: `usuario` (solo las columnas de identidad, con `version`), `ticket`,
  `ticket_mensaje` y `novedades_envio`, paginadas y con sus ids.
- **Hecho en I1:** Catálogo ampliado con eventos outbox `identidad.password-temporal.generada.v1` y `identidad.soporte.solicitud-datos.v1` y sus DTOs de contrato. Paquete `jwt` en `libs/eventos` con `UsuarioToken`, `JwtUsuario`, `ClavesRsa`, `EmisorJwtUsuario` y `VerificadorJwtUsuario` (validación de tokens RS256 de identidad y transición HS256 del monolito con mitigación contra confusión de algoritmo y rechazo de claves débiles). En el monolito: `JwtService` genera tokens con claim `uid` y valida con `VerificadorJwtUsuario`; flag `identidad.externo` con filtro 409 (`IdentidadExternoFilter`) e inhibición de `DataInitializer`/`LocalDemoSeeder`; deshabilitado login falso de Google salvo `google.simulado=true`; `/api/admin/users` serializado vía `UsuarioAdminDto` sin datos sensibles; mails de reseteo y soporte emitidos por outbox cuando `envio-directo=false`; rutas internas paginadas de carga inicial en `/internal/identidad/carga/*`; y validación estricta de roles en `AuthController.register` permitiendo únicamente `PACIENTE` o `PSIQUIATRA`.

### I2a — Servicio `identidad`: datos y carga inicial
- Módulo `services/identidad` y base `identidad`. Flyway desde cero: `usuario` (solo identidad), `ticket`,
  `ticket_mensaje`, `novedades_envio`, outbox e inbox, con los mismos ids y secuencias.
- Carga inicial con `IDENTIDAD_CARGA=true`: solo con las tablas vacías, todo o nada, secuencias arriba del máximo
  id, conservando `version` (hallazgo 6).
- **Hecho en I2a:** servicio `identidad` (módulo Maven, Flyway `V1__esquema_identidad.sql` con `usuario` [57 columnas], `ticket`, `ticket_mensaje`, `novedades_envio`, outbox e inbox, secuencias numéricas y clases JDBC en paquete `datos` con control de versión optimista); carga inicial `CargaInicialIdentidad` transaccional con validación exhaustiva de contrato y datos antes de insertar, gateada por `IDENTIDAD_CARGA`; tests de integración con Testcontainers en verde.

### I2b — Servicio `identidad`: rutas
- Las mismas rutas y JSON: `/api/auth/**` (incluida Mi Cuenta), `/api/tickets/**`, `/api/soporte/**`,
  `/api/admin/users/**`, `/api/admin/tickets/**` y `/api/site-access/**`. Emite el JWT RS256 en la misma cookie.
- **Sin el login falso:** el `mock-` solo con `identidad.google.simulado=true`, que no existe fuera de local.
- `/api/admin/users` responde con un DTO, sin hashes, códigos ni tokens.
- Publica los mismos eventos que hoy (`usuario.registrado`, `actualizado`, `eliminado`, `codigo-verificacion`,
  tickets, `verificacion-resuelta` y `visibilidad-cambiada`) por outbox, y los dos nuevos de I1.
- `DataInitializer`: el admin con `ADMIN_INITIAL_PASSWORD` si no existe (y las cuentas de prueba con
  `SEED_TEST_ACCOUNTS`), publicando sus eventos.
- Novedades: las mismas, en `/internal/identidad/novedades`.
- **Rutas internas con el mismo JSON que el monolito (pregunta 4):** `/internal/identidad/{pacientes, pacientes/{id},
  profesionales, profesionales/{id}, profesionales/estado, suscriptores, suscriptores/{id}}`, `POST /pacientes`,
  `PUT /pacientes/{id}` y `PUT /profesionales/{id}/identidad`. `profesionales/estado` lleva solo la verificación;
  Mercado Pago y la suscripción salen de pagos (I3).
- **Hecho en I2b:** rutas de cuenta y sesión en `/api/auth/**` (registro restringido a PACIENTE/PSIQUIATRA con `perfil_registro` [Flyway `V2__perfil_registro.sql`], verificación de mail, reenvío y recuperación de contraseña con `SecureRandom`, login, Google con simulación restringida a `identidad.google.simulado=true`, completar perfil, términos, me, mi cuenta y eliminación con anonimización y punto de extensión para Resend); sesión y JWT RS256 (`EmisorJwtUsuario`, cookie `SESSION-TOKEN` con atributos dev/prod idénticos al monolito, filtro de autenticación con `VerificadorJwtUsuario` y transición HS256); eventos outbox en la misma transacción (`usuario.registrado`, `actualizado`, `eliminado`, `codigo-verificacion`); semilla `DataInitializer` sincronizada con la carga inicial publicando `usuario.registrado`; suite de integración con 26 tests en verde.
- **Hecho en I2c:** tickets (`/api/tickets/**` y `/api/admin/tickets/**`) con sus eventos, soporte (`solicitud-datos` por evento), administración de usuarios (DTO sin datos sensibles, con el perfil del registro; verificar y rechazar con sus tres eventos; contraseña temporal por evento), portón del sitio (`/api/site-access`), novedades (`/internal/identidad/novedades` y `mi-cuenta/novedades`, Resend y SMTP local) y las rutas internas `/internal/identidad/...` con el mismo JSON que el monolito y validación de todo lo que reciben. Además: la semilla ya no bloquea el arranque, y los eventos de usuario y de ticket llevan la versión guardada + 1, como el monolito (antes salían con una menos y las copias habrían descartado el primer evento después del corte).

### I3 — Los servicios dejan de leer del monolito
- **Identidad:** pagos, profesionales y turnos leen de `/internal/identidad/...` en lugar de `/internal/monolito/...`,
  con una propiedad por servicio (`TRANQUI_FUENTE_IDENTIDAD`, por defecto el monolito hasta el corte). Volver atrás
  es sacar la variable.
- **Del dueño de cada dato** (ya está en producción desde su fase, así que cambia sin corte):
  - turnos trae los profesionales de `/internal/profesionales/...`;
  - profesionales trae los turnos de `/internal/turnos/turnos`, y el estado de Mercado Pago y de la suscripción por
    eventos de pagos (`cambio-estado.v2` y la vinculación), con resincronización contra pagos;
  - el paywall del gateway pregunta a pagos (`/internal/suscripciones/acceso`, misma caché y deja pasar si falla;
    pregunta 5). Cierra el punto 8 de [cambios-a-futuro.md](../cambios-a-futuro.md).
- `/api/medicos/stats` pasa a turnos, que tiene los datos (pregunta 6).
- **Hecho en I3a:** Los seis microservicios (calendario, chat, notificaciones, pagos, profesionales y turnos) migran su clase `seguridad/UsuarioJwt` para delegar en `VerificadorJwtUsuario` de `libs/eventos`, incorporando `jwt.public-key` (`JWT_PUBLIC_KEY`) y `jwt.hs256-aceptado` (`JWT_HS256_ACEPTADO`, default `true`) con soporte de arranque condicional sin secreto cuando HS256 está deshabilitado. El record `UsuarioJwt.Usuario` incorpora el campo `Long uid` (usar `uid` en lugar de resolver por mail queda para la Fase 10). Las suites de test de los seis servicios emiten tokens RS256 con `EmisorJwtUsuario` y claves generadas en el test, manteniendo la verificación de la transición HS256, el rechazo con 401 con `jwt.hs256-aceptado=false` y el rechazo con 401 ante tokens RS256 firmados con otra clave.
- **Hecho en I3b:** `TRANQUI_FUENTE_IDENTIDAD` (`monolito` por defecto o `identidad`) en pagos, profesionales y turnos: con `identidad`, las llamadas de régimen van a `/internal/identidad/...`, profesionales trae los turnos de `/internal/turnos/turnos` y turnos la agenda de `/internal/profesionales/profesionales`; Mercado Pago y la suscripción llegan a profesionales de pagos (eventos `pagos.cuenta-mp.*` y `cambio-estado.v2`, más resincronización contra `/internal/pagos/cuentas-mp` y `/internal/suscripciones/estado`), con su propia versión (`V3__fuentes_mp_y_suscripcion.sql`), y el refresco desde identidad ya no los pisa. Lo que llega de otro servicio se valida (fila salteada o evento a la DLQ). Con `monolito`, todo como hoy.
- **Hecho en I3c:** Mails de contraseña temporal y solicitud de copia de datos en notificaciones vía manejadores de eventos outbox (`identidad.password-temporal.generada.v1` e `identidad.soporte.solicitud-datos.v1`) con validación estricta y derivación a DLQ; endpoint `GET /api/medicos/stats` en turnos portado con ventanas temporales y métricas de negocio del dashboard para psiquiatras; rutas internas `GET /internal/suscripciones/acceso` (evaluación de paywall) y `GET /internal/pagos/cuentas-mp` (resincronización de cuentas con versión sincronizada y migración Flyway V3) en pagos protegidas por JWT de servicio.

### I4 — Gateway
- Regla `identidad` con su propia variable `TRANQUI_SERVICES_IDENTIDAD_URI`, por defecto al monolito hasta el corte,
  para `/api/auth/**`, `/api/tickets/**`, `/api/soporte/**`, `/api/admin/users/**`, `/api/admin/tickets/**`,
  `/api/site-access/**` y `/internal/identidad/**`. Los límites de login y registro, igual que hoy.
- `/api/medicos/stats` a turnos.
- Validación con el verificador de I1.
- `/api/health` lo responde el propio gateway (pregunta 6).
- **Hecho en I4:** `UserJwtVerifier` en el gateway delega en `VerificadorJwtUsuario` con `jwt.public-key`, `jwt.secret` y `jwt.hs256-aceptado`, retornando `UsuarioToken` adaptado a `PortonesGlobalFilter`, `AdminAuthValidator` y `UserOrIpKeyResolver`; regla `identidad` con variable `TRANQUI_SERVICES_IDENTIDAD_URI` (por defecto al monolito) para `/api/auth/**`, `/api/tickets/**`, `/api/soporte/**`, `/api/admin/users/**`, `/api/admin/tickets/**` y `/api/site-access/**`, manteniendo `/api/admin/subscriptions/**` en suscripciones; rutas internas `/internal/identidad/**` (con `TRANQUI_SERVICES_IDENTIDAD_INTERNO_URI`) e `/internal/pagos/**` protegidas con JWT de servicio; regla `turnos-stats` para `/api/medicos/stats` hacia turnos; `HealthController` del gateway atendiendo directamente `/api/health`; paywall en `AccesoSuscripcionCliente` con soporte para fuente `pagos` (`TRANQUI_PAYWALL_FUENTE`) consultando `/internal/suscripciones/acceso` con `@LoadBalanced` y validación estricta del campo booleano `permitido`; filtros de portones alineados con las excepciones del monolito; y suite de tests completa migrada a RS256 y ampliada con pruebas de ruteo, paywall, transicion HS256 y salud.

### I5 — Infraestructura, corte, vuelta atrás y conciliación
- Compose: contenedor `identidad` (base nueva en `db-servicios`), `IDENTIDAD_EXTERNO`, `IDENTIDAD_CARGA`,
  `TRANQUI_SERVICES_IDENTIDAD_URI`, `TRANQUI_FUENTE_IDENTIDAD`, las claves y `JWT_HS256_ACEPTADO`. Identidad publica
  `127.0.0.1` en el VPS para el script de novedades, que se corrige (hallazgo 5).
- **Corte en mantenimiento:** hay contraseñas y sesiones. Las sesiones de hoy siguen valiendo por la transición
  HS256.
- Script de vuelta atrás (las cuatro tablas al monolito con sus ids) y de conciliación (cantidades, mails, roles y
  hashes comparados sin imprimirlos).
- Memoria medida en la sección de producción.

### I6 — Verificación de punta a punta (postergada)
Registro, verificación de mail, login con contraseña y con Google, recuperación, Mi Cuenta y eliminación, tickets,
verificación y rechazo de matrícula, el paywall, un token HS256 viejo durante la transición, el servicio caído y la
vuelta atrás.

## Criterios de aceptación

- [ ] Las rutas de identidad, soporte y administración de usuarios responden igual desde el servicio.
- [ ] Ningún servicio ni el gateway llama a `/internal/monolito/**` con las variables del corte puestas.
- [ ] Solo identidad puede firmar un JWT de usuario; un token de hoy sigue valiendo hasta apagar la transición.
- [ ] Ni el login falso ni la fuga de `/api/admin/users` existen en el servicio.
- [ ] Ningún mail sale directo: todos por evento a notificaciones.
- [ ] Con la vuelta atrás (variables, flag y script), todo funciona como hoy.
- [ ] `./mvnw test` de los módulos tocados sin errores nuevos.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada (salvo el arreglo de la pregunta 1, si se aprueba).
- **Contraseñas y datos personales:** la carga y la vuelta atrás copian base a base en mantenimiento; la
  conciliación nunca imprime hashes ni datos personales.
- **El cambio de JWT toca el gateway y los seis servicios:** por eso la doble validación y un verificador único
  probado una vez.

## Preguntas

1. **Arreglo urgente en `main`:** el login falso con Google deja entrar como cualquier usuario en producción.
   **Recomiendo un hotfix ya, en un PR a `main`**: aceptar `mock-` solo con una propiedad que vale `false` por
   defecto, y un DTO en `/api/admin/users`. El merge y el deploy los hacés vos. ¿Lo preparo?
2. **¿El retiro del monolito en una Fase 10 aparte?** Ninguna fase está en producción y cada una vuelve atrás con
   el monolito. **Recomiendo que la Fase 9 deje al monolito sin tráfico** y que el apagado, el borrado del código
   de vuelta atrás y el archivo de la base vayan en una Fase 10, con su spec, cuando todo lleve un tiempo estable en
   producción.
3. **JWT:** ¿RS256 con un verificador único, `uid` en el token y siete días de doble validación? **Recomiendo que
   sí**; es lo que pide el plan y corta la posibilidad de fabricar tokens desde cualquier servicio.
4. **Datos que hoy los servicios piden al monolito:** ¿rutas `/internal/identidad/...` con el mismo JSON, o eventos
   con todos los datos? **Recomiendo las rutas:** cada servicio cambia solo de dónde lee y la vuelta atrás es una
   variable.
5. **Paywall del gateway:** ¿preguntar a pagos, o que el gateway consuma eventos? **Recomiendo preguntar a pagos**,
   con la misma caché: el gateway no tiene base ni RabbitMQ.
6. **Piezas sin dueño en el plan.** Recomiendo:
   - el portón del sitio y las novedades, en identidad;
   - `/api/medicos/stats`, en turnos;
   - `/api/health`, en el gateway;
   - los mails directos, por eventos nuevos a notificaciones.
7. **Fuera de alcance (anotado en [cambios-a-futuro.md](../cambios-a-futuro.md)):**
   - la revocación de tokens: un cambio de rol o una cuenta eliminada tardan hasta 7 días en notarse en los
     servicios;
   - que turnos cancele los turnos futuros al eliminar una cuenta;
   - un evento de cambio de mail.

   **Recomiendo dejarlos para después.**
8. Igual que la Fase 8: un PR a `develop` por tarea, encadenados, Java 17, sin deploy ni pruebas con el stack,
   implementado por workers (como máximo cuatro a la vez).

## Decisiones (2026-10-28)

1. No se hace el arreglo en `main`: la app no está en producción al 100 %. El login falso y la fuga de
   `/api/admin/users` se corrigen en el monolito (I1) y en identidad (I2b), dentro de la cadena a `develop`.
2. La Fase 9 deja todo listo para la migración, **sin apagar el monolito**: hay que validar el funcionamiento y
   compararlo con lo que hace hoy. El apagado, el borrado del código de vuelta atrás y el archivo de la base van en
   una fase posterior, con su spec.
3. JWT RS256 con verificador único, `uid` y siete días de doble validación.
4. Rutas `/internal/identidad/...` con el mismo JSON que las del monolito.
5. El paywall del gateway pregunta a pagos.
6. El portón y las novedades, a identidad; `/api/medicos/stats`, a turnos; `/api/health`, al gateway; los mails
   directos, por eventos a notificaciones.
7. La revocación de tokens, la cancelación de turnos al eliminar una cuenta y el evento de cambio de mail quedan en
   [cambios-a-futuro.md](../cambios-a-futuro.md).
8. Igual que la Fase 8: un PR a `develop` por tarea, encadenados, Java 17, sin deploy ni pruebas con el stack,
   implementado por workers (como máximo cuatro a la vez).
9. Se agregan dos fases antes del apagado (cada una con su spec):
   - **Fase 10 — Tests y validación de datos:** tests unitarios, de integración y de punta a punta, y que cada
     servicio valide todo lo que recibe de otro (eventos, respuestas internas y encabezados): nunca se confía en lo
     que manda otro servicio.
   - **Fase 11 — Comparación con `main`:** recorrer el frontend y el backend de `main` contra todo lo que está en
     los PR, para que ninguna funcionalidad quede afuera, sin código o deje de existir.
