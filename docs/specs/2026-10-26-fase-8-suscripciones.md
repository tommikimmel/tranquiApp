# Spec: Fase 8 — Suscripciones

- **Fecha:** 2026-10-26
- **Tipo:** feature
- **Sprint:** sprint-02
- **Estado:** aprobada (2026-10-27)
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 7
- **Plan:** [microservicios.md, Fase 8](../arquitectura/microservicios.md#fase-8-suscripciones)
- **Forma de trabajo:** solo cambiar la arquitectura; sin buildear imágenes ni levantar el stack. Tests con Maven,
  de a un módulo. La verificación de punta a punta queda postergada, como en las Fases 5 a 7. Implementan workers
  con un plan cerrado por tarea (como en la Fase 7).

## Entendimiento del problema

Las suscripciones son lo único de negocio que queda en el monolito además de identidad. En el código es:

- **Suscripciones (`SubscriptionService`, ~900 líneas):** el checkout con un preapproval de Mercado Pago (con el
  token de la plataforma, `MP_ADMIN_ACCESS_TOKEN`, no el del profesional), la cancelación, el acceso (ACTIVE, o
  CANCELLED con el período pago todavía corriendo), los pagos manuales del admin (idempotentes por clave) y los
  webhooks de preapproval y de pago (`sub-{id}`), que hoy le reenvía el servicio de pagos al monolito.
- **Planes (`PlanService`):** planes y features, que se siembran al arrancar (`@PostConstruct`).
- **Facturación (`InvoiceService`, `arca/`):** factura C (y nota de crédito) con CAE de ARCA por cada pago, con la
  numeración en `invoice_sequences` y el PDF guardado en la base (`pdf_base64`).
- **Reconciliación (`SubscriptionReconciliationScheduler`):** todos los días a las 4 (preaviso de renovación,
  cancelaciones vencidas, pagos sin factura, facturas fallidas, suspensión pasada la gracia, vencimiento de los
  pagos manuales, control del tope del Monotributo) y una alerta cada hora.
- **Rutas:** `/api/subscriptions/**` (4) y `/api/admin/subscriptions/**` (12).
- **Datos:** `plans`, `features`, `plan_features`, `subscriptions`, `subscription_events`, `subscription_payments`,
  `invoices` e `invoice_sequences`. Los crea Hibernate (`ddl-auto: update`), no Flyway; los ids son IDENTITY.
- **Mails:** ya salen por evento (`suscripciones.aviso.programado`, que consume notificaciones desde la Fase 1).

Lo que encontré y el plan no resolvía:

1. **Quién lee el estado de la suscripción.**
   - El **portón del gateway** (Fase 3) pregunta `/internal/monolito/suscripciones/acceso` para las rutas que ya no
     van al monolito (cache de 60 s; si falla, deja pasar).
   - El **servicio de profesionales** copia `suscripcion_estado`, `suscripcion_periodo_hasta` y `puede_recetar`
     trayendo `/internal/monolito/profesionales/estado` cada 60 s, y con eso decide quién aparece en el buscador y
     quién puede recetar.
   - El **monolito** mismo: `SubscriptionAccessFilter` (403 SUBSCRIPTION_REQUIRED en sus rutas) y `/api/medicos`.
   - `suscripciones.suscripcion.cambio-estado.v1` existe pero nadie lo consume, y no lleva si hay acceso ni si puede
     recetar.
2. **Identidad sigue en el monolito hasta la Fase 9.**
   - La suscripción nace al registrarse el profesional (`AuthController`, PENDING_VERIFICATION) y pasa a VERIFIED o
     REJECTED cuando el admin verifica la matrícula (`AdminController`). Ningún evento dice "verificado" o
     "rechazado": el rechazo solo pone `verificado_admin=false`, igual que un profesional sin verificar.
   - La factura usa los datos fiscales de `usuario` (CUIT, razón social, condición de IVA, domicilio fiscal).
3. **La numeración de ARCA.** El número de cada factura sale solo de `invoice_sequences`; nunca se compara con el
   último autorizado en ARCA (`FECompUltimoAutorizado` está implementado pero no se usa). Un CAE fallido quema un
   número y el reintento arma una factura nueva con otro número. Si dos bases emitieran para el mismo punto de
   venta, se duplicarían números: **solo puede haber un dueño de la numeración** y la secuencia tiene que pasar
   exacta en el corte.
4. **Tareas que no pueden correr dos veces.** Los dos crons y los dos `@PostConstruct` (siembra de planes y la
   gracia de 14 días a los verificados) no tienen flag ni lock: con el servicio andando, el monolito también
   suspendería cuentas, mandaría preavisos y reintentaría facturas.
5. **`subscriptions.professional_id` no es único.** `findByProfessionalId` devuelve un `Optional` y explotaría con
   dos filas (puede pasar en la carrera entre el registro y el checkout).
6. **Memoria:** con la Fase 7 estamos en ~5,0 GB proyectados contra el techo de ~4,8. Un servicio más suma 288 MB.

## Solución propuesta (con las recomendaciones de las Preguntas)

Las suscripciones van al **servicio de pagos** con sus tablas aparte (pregunta 1): ya recibe los webhooks de Mercado
Pago y no suma un contenedor.

### S1 — Catálogo y monolito
- Flag `suscripciones.externo` (`SUSCRIPCIONES_EXTERNO`): con `true`, `/api/subscriptions/**` y
  `/api/admin/subscriptions/**` del monolito responden 409, y no corren los crons, los `@PostConstruct` de
  suscripciones ni el procesamiento de webhooks de suscripción. Con `false` (vuelta atrás), todo como hoy.
- Eventos nuevos (pregunta 2 y 3):
  - `suscripciones.suscripcion.cambio-estado.v2`: el v1 más `planCode`, `acceso` (la misma regla que
    `isAccessAllowed`), `puedeRecetar`, `periodoHasta` y `graciaHasta`. El monolito lo publica desde hoy en lugar
    del v1 (nadie consume el v1).
  - `identidad.profesional.verificacion-resuelta.v1` (`usuarioId`, `resultado`: VERIFICADO o RECHAZADO), por
    outbox, desde `AdminController`.
- **Copia del acceso en el monolito** (pregunta 2): tabla `suscripcion_acceso` (profesional, estado, acceso,
  puede recetar, período y gracia) que se actualiza con `cambio-estado.v2`, más una resincronización contra el
  servicio. Con el flag, `SubscriptionAccessFilter`, `/internal/monolito/suscripciones/acceso` y
  `/internal/monolito/profesionales/estado` leen de esa copia: **el gateway y el servicio de profesionales no
  cambian**.
- Rutas internas para la carga inicial (las ocho tablas, paginadas y con sus ids) y
  `/internal/monolito/suscriptores/{id}` con los datos fiscales, la profesión, el rol y la verificación.

### S2a — Datos y carga inicial en el servicio de pagos
- Flyway nuevo en la base `pagos`: las ocho tablas con los mismos ids y sus secuencias, `professional_id` único
  (pregunta 5), y la copia `suscriptor` (datos fiscales, profesión, verificación) que se refresca desde
  `/internal/monolito/suscriptores/{id}` con los eventos de identidad y una resincronización.
- Carga inicial con `PAGOS_SUSCRIPCIONES=true`: solo con las tablas vacías, todo o nada, secuencias arriba del
  máximo id; **aborta si hay dos suscripciones del mismo profesional** (para que el admin decida antes del corte).

### S2b — Suscripciones, Mercado Pago y reconciliación
- Mismas rutas y JSON: `/api/subscriptions/**` y `/api/admin/subscriptions/**`.
- Checkout, cancelación y estado del preapproval con el token de la plataforma (`MP_ADMIN_*`); el modo simulado
  queda igual.
- Los webhooks de suscripción (`subscription_preapproval`, `subscription_authorized_payment` y `payment` con
  `sub-`) se procesan en el mismo servicio en lugar de reenviarlos al monolito, cuando la suscripción ya es del
  servicio (si falla, 500 y Mercado Pago reintenta, como hoy).
- Alta de la suscripción con `identidad.usuario.registrado` (rol PSIQUIATRA) y VERIFIED o REJECTED con
  `identidad.profesional.verificacion-resuelta`.
- Los dos crons de reconciliación, con las mismas reglas. Publica `cambio-estado.v2` y
  `suscripciones.aviso.programado` por outbox (los mails siguen en notificaciones).
- `/internal/suscripciones/estado` paginado, para la resincronización del monolito.

### S2c — Facturación ARCA
- **Se mueve tal cual (decisión 4):** factura C y nota de crédito con CAE, el PDF y la misma configuración
  `ARCA_*` (certificado y clave por archivo, nunca en el repo), apagada (`ARCA_ENABLED=false`) como hoy. ARCA
  todavía no está definida: la numeración y los reintentos no cambian y quedan anotados en
  [cambios-a-futuro.md](../cambios-a-futuro.md).

### S3 — Gateway
- Regla `suscripciones` con su propia variable `TRANQUI_SERVICES_SUSCRIPCIONES_URI`, por defecto al monolito hasta
  el corte (como la clínica en la Fase 7), y su propio circuito.
- El reenvío de webhooks de suscripción del servicio de pagos al monolito queda para la vuelta atrás (con el flag
  de pagos en `false`).

### S4 — Infraestructura, corte, vuelta atrás y conciliación
- Compose: `SUSCRIPCIONES_EXTERNO`, `PAGOS_SUSCRIPCIONES`, `TRANQUI_SERVICES_SUSCRIPCIONES_URI`, y las variables
  `ARCA_*` y `MP_ADMIN_*` en pagos (los certificados como volumen de solo lectura). Sin contenedor nuevo.
- **Corte en mantenimiento**, como turnos y la clínica: hay plata y numeración fiscal.
- Script de vuelta atrás (las ocho tablas al monolito con sus ids, `invoice_sequences` incluida y nunca para
  abajo) y de conciliación (cantidades, estados, montos, números de factura y CAE).

### S5 — Verificación de punta a punta (postergada)
Checkout con Mercado Pago simulado y su webhook, cancelación, pago manual,
la reconciliación, el portón y el buscador con una suscripción vencida, la verificación y el rechazo de un
profesional, el servicio caído y la vuelta atrás.

## Criterios de aceptación

- [ ] Las rutas de suscripciones y de administración responden igual desde el servicio de pagos.
- [ ] Ningún pago, factura ni número de comprobante se pierde ni se duplica (la conciliación compara números y CAE).
- [ ] Los crons y las tareas de arranque corren en un solo lado según el flag.
- [ ] El portón del gateway, el buscador y "puede recetar" reflejan un cambio de estado sin tocar el gateway ni el
      servicio de profesionales.
- [ ] Con la vuelta atrás (variable del gateway + flags + script), todo funciona como hoy.
- [ ] `./mvnw test` de los módulos tocados sin errores nuevos.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **Plata y comprobantes fiscales:** la carga inicial y la vuelta atrás copian base a base (nunca por eventos) en
  mantenimiento; la conciliación compara números de factura y CAE.
- **Memoria del servicio de pagos:** la facturación (SOAP de ARCA y el PDF con OpenPDF) le suma trabajo en 288 MB.
  Se mide en S5; si no alcanza, se sube el límite (sin contenedor nuevo, igual queda por debajo de un servicio
  aparte).

## Preguntas

1. **¿Servicio nuevo o en pagos?** El plan pone un servicio `suscripciones` (+288 MB, ~5,3 GB proyectados).
   **Recomiendo ponerlas en el servicio de pagos**, con sus tablas aparte: ya recibe los webhooks de Mercado Pago,
   ya tiene el simulador y el outbox, y no suma memoria. La regla del gateway queda con su nombre propio para
   separarlo más adelante si hace falta.
2. **¿Cómo se enteran el gateway, profesionales y el monolito del estado?** **Recomiendo una copia del acceso en
   el monolito** alimentada por `cambio-estado.v2` (y una resincronización), y que sus dos rutas internas de hoy
   lean de ahí: el gateway y profesionales no cambian en esta fase. En la Fase 9, al retirar el monolito, pasan a
   leer el evento directo.
3. **Verificación del profesional:** ¿un evento nuevo `identidad.profesional.verificacion-resuelta` para que el
   servicio pase la suscripción a VERIFIED o REJECTED? Hoy el rechazo no se distingue de "sin verificar" en
   `usuario`. **Recomiendo que sí.**
4. **Numeración de ARCA:** ¿consultar el último autorizado antes de cada CAE y reintentar con el mismo número?
   Evita los números quemados y protege contra una secuencia mal copiada. **Recomiendo que sí**, en S2c.
5. **Suscripción única por profesional:** ¿índice único en el servicio, con la carga inicial abortando si hay
   duplicados? **Recomiendo que sí.**
6. **Fuera de alcance (como hoy):** los webhooks de preapproval sin deduplicar y el cobro doble posible si falla
   la cancelación del preapproval anterior, y el link al PDF de la factura (`/api/subscriptions/invoices/{id}/pdf`),
   que se guarda pero no tiene ruta. **Recomiendo dejarlos para después**, anotados.
7. Un PR a `develop` por tarea, Java 17, sin deploy ni pruebas con el stack, implementado por workers (como máximo
   cuatro a la vez).

## Decisiones (2026-10-27)

1. Las suscripciones van al servicio de pagos, con sus tablas aparte (sin servicio nuevo).
2. Copia del acceso en el monolito alimentada por `suscripciones.suscripcion.cambio-estado.v2`; el gateway y el
   servicio de profesionales no cambian en esta fase.
3. Evento nuevo `identidad.profesional.verificacion-resuelta.v1` para VERIFIED o REJECTED.
4. ARCA no está definida todavía: la facturación se mueve tal cual, apagada, sin cambiar la numeración. Lo que
   falta definir queda en [cambios-a-futuro.md](../cambios-a-futuro.md).
5. Una única suscripción por profesional (psicólogo o psiquiatra): índice único y la carga inicial aborta si hay
   duplicados.
6. Los webhooks sin deduplicar, el cobro doble posible y la ruta del PDF quedan anotados en
   [cambios-a-futuro.md](../cambios-a-futuro.md), junto con el resto de los cambios a futuro de la migración.
7. Igual que la Fase 7: un PR a `develop` por tarea, encadenados, Java 17, sin deploy ni pruebas con el stack,
   implementado por workers (como máximo cuatro a la vez). Las dudas se resuelven primero con lo que ya está
   implementado.
