# Spec: Fase 10 — Tests y validación de datos

- **Fecha:** 2026-10-28
- **Tipo:** calidad
- **Sprint:** sprint-02
- **Estado:** aprobada (2026-10-29)
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 9
- **Plan:** [microservicios.md, Fase 10](../arquitectura/microservicios.md#fase-10-tests-y-validación-de-datos)
- **Forma de trabajo:** implementan workers con un plan cerrado por tarea (como máximo cuatro a la vez). Esta fase sí
  necesita levantar el stack para los tests de punta a punta (pregunta 1).

## Entendimiento del problema

Pediste dos cosas: tests unitarios, de integración y de punta a punta de todo el sistema, y que **ningún servicio
confíe en lo que le manda otro**. Lo que hay hoy:

- **Tests:** cada módulo tiene los suyos (backend ~96 clases, gateway 21, libs 13, servicios de 1 a 7 clases de
  integración cada uno, más los de la Fase 9). Son sobre todo de integración con Testcontainers y el resto del mundo
  simulado. **No hay**:
  - medición de cobertura (ni JaCoCo ni umbral);
  - tests de contrato entre servicios: cada lado arma a mano el JSON que espera del otro, así que si uno cambia un
    campo, los dos siguen pasando y se rompe en producción;
  - tests de punta a punta del sistema migrado: el frontend tiene Playwright con **un solo** flujo
    (`frontend/e2e/patient-and-professional-flow.spec.ts`) contra el monolito, y las verificaciones de las Fases 5 a 9
    (R5, T5, H5, S5 e I6) están postergadas.
- **Validación entre servicios:**
  - **Eventos** (~90 manejadores): `ConsumidorEventos` valida el sobre (eventId, tipo, versión) y que el `data` se pueda
    leer como el record. Los 44 records de `libs/eventos/data` **no tienen ninguna validación**: un id nulo o negativo,
    un mail vacío o un estado desconocido llegan al manejador y se escriben en la copia.
  - **Rutas internas** (~47 llamadas de `ClienteInterno`): la respuesta se deserializa y se usa sin validar. Las
    rutas que reciben escrituras de otro servicio (por ejemplo `PUT /pacientes/{id}`) en algunos casos confían en lo
    que validó el que llama.
  - **Identidad del usuario:** los servicios toman mail y rol del JWT y buscan el id por mail en su copia. Desde la
    Fase 9 el token trae `uid`, pero nadie lo usa ni lo cruza con la copia.
  - **Encabezados:** el gateway no inyecta `X-User-Id`; ningún servicio debería aceptar uno que venga de afuera.

## Solución propuesta (con las recomendaciones de las Preguntas)

### V1 — Validación de eventos en `libs/eventos`
- Bean Validation (`jakarta.validation`) en los 44 records de `data/`: obligatorios, ids positivos, mails, montos no
  negativos, enums conocidos (estados de turno, pago, suscripción), fechas coherentes (fin después de inicio).
- `ConsumidorEventos` valida el `data` **antes** de llamar al manejador: si no cumple, va a la DLQ con el motivo (sin
  datos personales en el log). Un solo lugar, así ningún manejador puede saltearlo.
- El publicador valida lo mismo antes de escribir en la outbox: un evento inválido falla en origen, no en destino.
- Tests por record (válido, cada regla rota) y del consumidor (inválido a la DLQ, válido al manejador).

- **Hecho en V1:** Bean Validation en los 44 records de `libs/eventos/data` (ids que identifican el evento obligatorios y positivos, mails, montos no negativos, roles y estados contra sus valores en `Valores`), `ValidadorEventos` único usado por el consumidor (data inválido a la DLQ antes del manejador) y por la outbox de la librería y la del monolito (no se publica), con `eventos.validacion.modo` (`rechazar` por defecto; `loguear` en el compose de producción la primera semana). Los mensajes nombran campo y regla, nunca el valor. Test reflexivo que cubre todos los records (122 reglas).

### V2 — Validación de las rutas internas
- **Del lado del que llama:** cada record de respuesta de `ClienteInterno` con las mismas anotaciones, y un
  validador en el cliente: una respuesta inválida se trata como falla (reintento o fila salteada con log del id).
- **Del lado del que responde:** toda ruta interna que escribe valida su cuerpo con las mismas reglas que la ruta de
  usuario equivalente (no "lo validó el otro").
- **Identidad del pedido:** cada servicio cruza el `uid` del token con el id que encuentra por mail en su copia; si
  no coinciden, 401 y log. El gateway borra cualquier `X-User-Id` o `X-Service-Name` que venga de afuera.

- **Hecho en V2:** el gateway borra `X-User-Id` y `X-Service-Name` que vengan de afuera; las rutas internas de notificaciones validan el cuerpo; cada `ClienteInterno` valida lo que le responden con `ValidadorEventos.respuesta` (en una lista se saltea la fila inválida con un aviso, una respuesta suelta inválida es una falla), con reglas en los records de respuesta; y el `uid` del token se cruza con el id de la copia en los seis servicios (401 si no coincide, `tranqui.seguridad.cruzar-uid`) y con la cuenta en identidad y el monolito.

### T1 — Cobertura
- JaCoCo en todos los módulos, con el reporte en el CI y un umbral mínimo (pregunta 3) que el CI exige solo sobre el
  código nuevo o tocado (para no frenar por el monolito viejo).
- **Tests intermitentes:** los de ruteo de la clínica del gateway (`RuteoClinicaSoloProfesionalesTest`, `RuteoClinicaSinVariableTest`) a veces responden 503 en el CI (falló en #76, #81 y #82 y pasó al relanzar): buscar la causa (probablemente el circuito o el timeout del primer pedido) y dejarlos estables.
- Completar los unitarios donde la cobertura quede por debajo: reglas de negocio puras (cálculo de estadísticas,
  reglas de acceso de suscripción, liberación de turnos, honorarios, conciliación).

- **Hecho en T1:**
  - **Medición:** JaCoCo en todos los módulos. `scripts/ci/cobertura-cambios.mjs` mide las líneas de Java nuevas o tocadas del PR contra la rama base y el CI exige 90 %. Como los PR encadenados apuntan a `develop`, eso equivale a toda la migración: pasó de 75,8 % a 90,2 % (15696/17407).
  - **Tests nuevos:**
    - plantillas y transporte de mails;
    - clientes de QBI2, Resend, Google y Consul (contra servidores o clientes HTTP falsos);
    - suscripciones, webhooks y reconciliación de pagos;
    - reservas, cobros, gestión y horarios de turnos;
    - cargas iniciales con sus reintentos y su rechazo de datos inválidos;
    - inbox, outbox y tokens.
  - **Tests intermitentes del gateway:** la causa era el TimeLimiter de Resilience4j. Sin configurar, corta a 1 s y responde 503 aunque el servicio conteste. Ahora es 31 s, por encima del response-timeout más largo de las rutas, con un test de una respuesta de 1,5 s.
  - **Validación que faltaba (Fase 10, V2):** el monolito ahora valida lo que le devuelven turnos, profesionales, chat y calendario en las resincronizaciones, el resumen del chat y los horarios ocupados. Los intervalos ocupados exigen inicio y fin, también en turnos.

### T2 — Tests de contrato entre servicios
- Para cada ruta interna y cada evento: un **JSON de ejemplo canónico** en `libs/eventos` (test-jar), generado por el
  que produce y leído por el que consume. El productor tiene un test que verifica que su salida coincide con el
  ejemplo; el consumidor, uno que lo deserializa y lo valida. Si alguien cambia un campo, se rompe el test del lado
  que no se actualizó.

- **Hecho en T2:**
  - **Contratos:** `libs/eventos` publica un test-jar con `contratos/eventos` (los 44 records de `data`) y
    `contratos/internas` (cada ruta interna y los cuerpos de los pedidos), más el helper `Contratos`:
    - `mismaForma` compara campos y tipos de JSON del lado que produce; un mapa por id compara solo los valores.
    - `leer` exige del lado que consume que todo componente del record llegue con valor y pase sus validaciones.
    - `ServidorCaptura` toma el cuerpo que manda el cliente real.
    - Con `-Dcontratos.generar=true` los productores escriben los contratos que faltan.
  - **Lados:** el monolito y los siete servicios verifican lo que producen en sus tests de integración. Donde dos
    dueños sirven la misma ruta (monolito e identidad, o la vuelta atrás de notificaciones), los dos contra el mismo
    contrato. Cada consumidor tiene su `ContratosConsumidorTest`; el gateway sirve el contrato de acceso en su test del
    paywall.
  - **Lo que encontraron los contratos (arreglado):**
    - Profesionales mandaba la fecha de nacimiento del `PUT .../identidad` como `[1990,5,1]` (su `RestClient` no usaba
      la configuración de fechas) y el monolito la rechazaba. Ahora usa el mapper ISO de la librería.
    - Profesionales leía los turnos del servicio de turnos con un record con dos tokens que turnos nunca manda.
    - **Paginación cortada por una fila inválida:** las cargas iniciales cortaban cuando una página traía menos filas
      que las pedidas, contando las que quedaban después de saltear las inválidas (V2). Una sola fila inválida en una
      página llena dejaba sin copiar todo lo que seguía. `ValidadorEventos.recibidas` da las que llegaron y las 33
      cargas paginan con eso.
    - La resincronización de suscripciones del monolito no validaba la respuesta de pagos.

### T3 — Tests de punta a punta
- Playwright sobre el stack local completo (`docker-compose.local.yml`, proyecto `tranqui-local`, sin tocar tu stack
  `tranquiapp`), con datos semilla.
- Los recorridos: los de las verificaciones postergadas de las Fases 5 a 9 (perfil y buscador, reserva y pago
  simulado, cancelación y reembolso, historia clínica y receta, suscripción y paywall, registro, login, Mi Cuenta,
  tickets, verificación de matrícula) y el flujo de hoy de paciente y profesional.
- Pruebas de resiliencia: un servicio caído (circuito y mensaje claro), reinicio con eventos pendientes (outbox e
  inbox sin duplicados), la DLQ vacía al final.
- Un job del CI que levanta el stack y corre la suite (pregunta 2).
- **Esta suite también sirve para la Fase 11:** se corre contra `main` (el monolito) y contra la migración, y lo que
  pasa en uno y falla en el otro es funcionalidad perdida.

- **Hecho en T3:**
  - **Suite** en `frontend/e2e` (Playwright, de a un navegador), contra el stack local por el gateway:
    - `paciente.spec.ts` (interfaz): login por el formulario, buscador sin la profesional vencida, reserva con pago
      simulado, el turno en la agenda de la profesional, cancelación a más de 48 h con el mail del reembolso, Mi Cuenta.
    - `sistema.spec.ts` (API, por el gateway): registro con el código de Mailpit, verificación de matrícula por el admin,
      tickets, suscripción y paywall, historia clínica, perfil público. Historia clínica y recetas están "en
      construcción" en la interfaz, por eso van por la API.
    - `resiliencia.spec.ts` (con `E2E_RESILIENCIA=1` y `E2E_COMPOSE`): chat apagado da 503 claro y el resto anda;
      con notificaciones apagado los eventos esperan y salen una sola vez; al final todas las DLQ vacías.
  - **Variables:** `E2E_URL`, `E2E_API_URL`, `E2E_MAILPIT_URL` (para correrla contra `main` en la Fase 11).
  - **Límite de logins del gateway** (ráfaga de 10 y 5 por minuto por IP): la suite no lo afloja. Hace un login por
    cuenta, la interfaz reusa esa cookie y ante un 429 espera a que se reponga el cupo.
  - **CI:** job `punta-a-punta` en los PR, que levanta `docker-compose.local.yml` con claves RSA de usar y tirar y corre
    la suite completa. Si falla, sube los logs del stack y el reporte de Playwright.
  - **Lo que encontró la suite (arreglado):**
    - **El paywall no cortaba a nadie en la migración:** con `TRANQUI_PAYWALL_FUENTE: pagos` el gateway consultaba
      `lb://pagos` con el WebClient balanceado, que resuelve `http://servicio`. Fallaba con "Invalid scheme [lb]" y,
      como el paywall falla abierto, dejaba entrar a la profesional vencida. Test nuevo con pagos por discovery.
    - **El monolito no aplicaba ningún evento versionado:** faltaba la tabla `versiones_aplicadas` en sus migraciones
      (V3 creó solo el inbox). Las copias de turnos, profesionales y suscripciones fallaban y terminaban en
      `monolito.dlq`. V7 la crea, y un test exige que cada tabla de `libs/eventos` esté en una migración.
  - **Visto y no cambiado:** el calendario de la reserva pide los 31 días en paralelo; en desarrollo (StrictMode) son
    62 pedidos y la ráfaga del gateway es 60, así que alguno da 429 y ese día se ve sin horarios. En producción son 31,
    pero cambiar de modalidad dos veces seguidas puede pasar el límite. Conviene usar el endpoint de conteo.

### T4 — Frontend
- Vitest para los componentes y la capa `api.ts` que no tienen tests (hoy 18 archivos de test).
- **Hecho en T4:** el mismo criterio que en Java (decisión del 2026-10-30): 90 % de las líneas del frontend que cambia cada
  PR, no del frontend entero (32 % hoy).
  - `scripts/ci/cobertura-cambios-frontend.mjs` cruza el diff de `frontend/src` (sin los tests) con el `lcov.info` de
    Vitest. El job del frontend corre los tests con cobertura y, en los PR, el control.
  - Tests nuevos para lo que cambió la migración y no estaba cubierto: `api.ts` (checkout, salud y observabilidad,
    `VITE_API_URL`), la reserva vencida de `esperarCheckout`, el menú de Observabilidad de `AdminDashboard`, el panel de
    observabilidad (series por contenedor y servicio, refresco a pedido y automático) y la fábrica de SockJS de `useChat`.
  - Con los PR encadenados a develop: 159/176 líneas (90,3 %). Sin cubrir quedan los WebSocket de avisos en vivo de
    `App.tsx` y `LandingPage.tsx` (los cubre la suite de punta a punta) y el `catch` de observabilidad, al que no se
    llega porque cada pedido ya tiene el suyo.

## Criterios de aceptación

- [ ] Un evento o una respuesta interna con un dato inválido nunca llega a la base de otro servicio: va a la DLQ o se
      trata como falla, con un test que lo prueba por cada record.
- [ ] Cada ruta y evento entre servicios tiene su test de contrato de los dos lados.
- [ ] Cobertura medida en el CI con el umbral acordado.
- [ ] La suite de punta a punta pasa completa contra la migración, en local y en el CI.
- [ ] `./mvnw test` y los tests del frontend sin errores.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **Validar más estricto puede rechazar datos reales viejos** (por ejemplo un teléfono mal cargado hace años). Antes
  de activar el rechazo, un modo "solo loguear" por una propiedad, y una pasada contra una copia de los datos.
- **Recursos de la PC y del CI:** el stack completo pesa ~5 GB; la suite de punta a punta corre de a un navegador.

## Preguntas

1. **¿Puedo levantar el stack local para la suite de punta a punta?** Hasta ahora la regla es no buildear imágenes ni
   levantar nada. **Recomiendo** permitirlo solo en esta fase y solo con el proyecto aparte `tranqui-local`
   (`docker compose -p tranqui-local`, puertos distintos), sin tocar tu stack `tranquiapp`, y bajarlo al terminar.
2. **¿Los tests de punta a punta también en el CI?** **Recomiendo que sí**, en un job aparte (los PR no esperan por él,
   pero `develop` sí), porque es lo que va a cuidar la comparación con `main` en la Fase 11.
3. **Umbral de cobertura:** **recomiendo 70 % de líneas** sobre el código nuevo o tocado, y medir sin exigir en el
   resto.
4. **Validación estricta:** ¿rechazar desde el primer día, o una semana en modo "solo loguear"? **Recomiendo
   "solo loguear" en producción la primera semana** después del deploy (en local y en los tests, rechazar).
5. Igual que las Fases 8 y 9: un PR a `develop` por tarea, encadenados, Java 17, sin deploy, implementado por workers
   (como máximo cuatro a la vez).

## Decisiones (2026-10-29)

1. Se puede levantar el stack completo en Docker para la suite de punta a punta (proyecto aparte `tranqui-local`, sin
   tocar el stack `tranquiapp`).
2. La suite de punta a punta corre también en el CI.
3. **Cobertura mínima: 90 % de líneas** sobre el código nuevo o tocado.
4. La validación estricta arranca en producción la primera semana en modo "solo loguear"; en local y en los tests,
   rechaza.
5. Igual que las fases anteriores: un PR a `develop` por tarea, encadenados, Java 17, sin deploy.
