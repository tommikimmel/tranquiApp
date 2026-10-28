# Spec: Fase 10 — Tests y validación de datos

- **Fecha:** 2026-10-28
- **Tipo:** calidad
- **Sprint:** sprint-02
- **Estado:** borrador, esperando aprobación
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

### V2 — Validación de las rutas internas
- **Del lado del que llama:** cada record de respuesta de `ClienteInterno` con las mismas anotaciones, y un
  validador en el cliente: una respuesta inválida se trata como falla (reintento o fila salteada con log del id).
- **Del lado del que responde:** toda ruta interna que escribe valida su cuerpo con las mismas reglas que la ruta de
  usuario equivalente (no "lo validó el otro").
- **Identidad del pedido:** cada servicio cruza el `uid` del token con el id que encuentra por mail en su copia; si
  no coinciden, 401 y log. El gateway borra cualquier `X-User-Id` o `X-Service-Name` que venga de afuera.

### T1 — Cobertura
- JaCoCo en todos los módulos, con el reporte en el CI y un umbral mínimo (pregunta 3) que el CI exige solo sobre el
  código nuevo o tocado (para no frenar por el monolito viejo).
- **Tests intermitentes:** los de ruteo de la clínica del gateway (`RuteoClinicaSoloProfesionalesTest`, `RuteoClinicaSinVariableTest`) a veces responden 503 en el CI (falló en #76, #81 y #82 y pasó al relanzar): buscar la causa (probablemente el circuito o el timeout del primer pedido) y dejarlos estables.
- Completar los unitarios donde la cobertura quede por debajo: reglas de negocio puras (cálculo de estadísticas,
  reglas de acceso de suscripción, liberación de turnos, honorarios, conciliación).

### T2 — Tests de contrato entre servicios
- Para cada ruta interna y cada evento: un **JSON de ejemplo canónico** en `libs/eventos` (test-jar), generado por el
  que produce y leído por el que consume. El productor tiene un test que verifica que su salida coincide con el
  ejemplo; el consumidor, uno que lo deserializa y lo valida. Si alguien cambia un campo, se rompe el test del lado
  que no se actualizó.

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

### T4 — Frontend
- Vitest para los componentes y la capa `api.ts` que no tienen tests (hoy 18 archivos de test).

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
