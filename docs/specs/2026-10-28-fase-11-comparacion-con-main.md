# Spec: Fase 11 — Comparación con `main`

- **Fecha:** 2026-10-28
- **Tipo:** auditoría
- **Sprint:** sprint-02
- **Estado:** borrador, esperando aprobación
- **Rama / PR:** una rama y un PR a `develop` por tarea, encadenadas sobre la Fase 10
- **Plan:** [microservicios.md, Fase 11](../arquitectura/microservicios.md#fase-11-comparación-con-main)
- **Forma de trabajo:** la revisión la hacen workers con un plan cerrado por dominio (como máximo cuatro a la vez);
  cada diferencia que encuentren se arregla en su propio PR.

## Entendimiento del problema

Entre `main` (el monolito que está hoy en producción) y la punta de la cadena de PR (#12 a la Fase 10) cambió casi
todo el backend: nueve fases movieron el código a ocho servicios, el gateway y dos librerías, y el frontend también
cambió (por ejemplo el checkout de la Fase 4). Cada fase portó "con el mismo comportamiento", pero lo verificó con sus
propios tests, no contra `main`. Pediste que **ninguna funcionalidad quede afuera, sin código o deje de existir**,
comparando el frontend con su backend.

Lo que hace difícil comparar:
- Una misma funcionalidad hoy vive en un controller del monolito y después de la migración en un servicio, detrás de
  una regla del gateway y a veces con un evento en el medio (por ejemplo, un mail que antes mandaba el controller y
  ahora manda notificaciones al recibir un evento).
- Hay diferencias **a propósito** (arreglos de seguridad, decisiones de las specs): no tienen que aparecer como
  pérdidas, pero sí quedar listadas.
- Hay comportamiento que no pasa por ninguna ruta: crons, tareas de arranque, webhooks, mails, integraciones (Mercado
  Pago, Google, QBI2, ARCA, Resend).

## Solución propuesta (con las recomendaciones de las Preguntas)

### C1 — Inventario de `main`
Un script (`scripts/comparacion/inventario.mjs`) que recorre una revisión del repo y saca, en JSON:
- **Frontend:** cada llamada de `api.ts` y de los componentes (método, ruta, cuerpo que manda, campos que lee de la
  respuesta) y cada pantalla o ruta de React.
- **Backend:** cada endpoint (método, ruta, rol, request y response), cada `@Scheduled` y tarea de arranque, cada
  webhook, cada mail, cada evento publicado y cada llamada a una API externa.
Con eso se arma `docs/migracion/comparacion-main.md`: una fila por funcionalidad de `main`.

### C2 — Mapa de la migración
El mismo script sobre la punta de la cadena, más las reglas del gateway: para cada fila de C1, quién la atiende ahora
(regla del gateway → servicio → clase), y si un efecto pasó a un evento, qué manejador lo hace.

### C3 — Revisión por dominio
Workers, un dominio por tarea (identidad y cuenta; profesionales y buscador; turnos y agenda; pagos y reembolsos;
suscripciones y facturación; historia clínica y recetas; chat; notificaciones y mails; calendario; administración y
observabilidad). Cada uno, para cada fila de su dominio, compara lado a lado el código de `main` y el migrado:
request y response JSON campo por campo, roles, validaciones y mensajes de error, efectos (base, mails, eventos,
llamadas externas) y tareas programadas. Marca cada fila como:
- **igual**;
- **cambia a propósito** (con el link a la decisión de la spec que lo decidió);
- **distinto** o **falta** (con el detalle).
El frontend se revisa contra los dos: cada llamada de `main` y de la punta tiene que tener un dueño que responda lo
que el componente lee.

### C4 — Comparación ejecutando
- La suite de punta a punta de la Fase 10 corre contra `main` (monolito) y contra la migración; lo que pasa en uno y
  falla en el otro se agrega a la tabla.
- Un "golden master" de la API: un script manda la misma serie de pedidos (con los mismos datos semilla) a los dos
  stacks y compara las respuestas JSON, ignorando ids y fechas.

### C5 — Arreglos
Cada fila **distinto** o **falta** se arregla en un PR por dominio, con un test que la cubre (de contrato o de punta a
punta). La fase termina cuando la tabla no tiene filas en esos estados.

## Criterios de aceptación

- [ ] `docs/migracion/comparacion-main.md` cubre todas las llamadas del frontend y todos los endpoints, tareas,
      webhooks, mails e integraciones de `main`.
- [ ] Ninguna fila queda en **distinto** o **falta**; las **cambia a propósito** tienen su decisión enlazada.
- [ ] La suite de punta a punta y el golden master dan lo mismo en `main` y en la migración, salvo lo que cambia a
      propósito.

## Riesgos e impacto

- **Sin impacto en producción:** no se despliega nada.
- **Levantar los dos stacks a la vez no entra en la PC** (~5 GB la migración más el monolito). Se corren de a uno.
- **El inventario automático no ve todo** (lógica dentro de un método, efectos indirectos): por eso C3 es revisión de
  código, no solo el script.

## Preguntas

1. **¿La Fase 10 antes que la 11?** **Recomiendo que sí:** la suite de punta a punta de la Fase 10 es la herramienta
   principal para comparar en C4.
2. **¿Puedo levantar `main` en local** (monolito, con su base y datos semilla, en el proyecto aparte
   `tranqui-main`) para C4? **Recomiendo que sí**, de a un stack por vez.
3. **¿Los arreglos dentro de la fase?** **Recomiendo que sí**, un PR por dominio, para que la fase cierre con la tabla
   limpia.
4. **¿Las diferencias a propósito** (login falso de Google, rol del registro, DTO del admin, cookies, mails por evento,
   etc.) **se dejan como están**, solo listadas? **Recomiendo que sí.**
5. Igual que las fases anteriores: un PR a `develop` por tarea, encadenados, Java 17, sin deploy, implementado por
   workers (como máximo cuatro a la vez).
