# SDD — Desarrollo guiado por especificaciones

Regla de oro: **no se toca código sin una spec aprobada por el dueño del proyecto.** Aplica a
features, fixes, restylings, cambios de base de datos e infraestructura. Las únicas excepciones
son cambios triviales que el usuario pida textualmente (por ejemplo, corregir un texto puntual), y
aun así se describe el cambio antes de hacerlo.

## Pasos

1. **Entender.** Leer el pedido, el código involucrado y la documentación relevante. Reproducir el
   problema si es un bug.
2. **Escribir la spec** con la plantilla [`docs/specs/_plantilla.md`](../specs/_plantilla.md) y
   mostrársela al usuario. Debe incluir:
   - **Entendimiento del problema:** qué pasa hoy, por qué, y a quién afecta.
   - **Solución propuesta:** qué se va a cambiar, en qué archivos, y alternativas descartadas.
   - **Criterios de aceptación:** cómo sabemos que quedó bien, verificable.
   - **Riesgos:** qué puede romperse, impacto en producción, migraciones necesarias.
   - **Preguntas abiertas**, si las hay.
3. **Esperar aprobación.** Si el usuario pide cambios, se actualiza la spec y se vuelve a mostrar.
4. **Implementar** en una rama nueva (`docs/flujo/git.md`), con tests.
5. **Verificar** contra los criterios de aceptación: tests, y navegador si es visible.
6. **Cerrar la spec:** marcarla como implementada, anotar desvíos respecto del plan y el link al PR.

## Dónde viven las specs

`docs/specs/AAAA-MM-DD-descripcion-corta.md`, una por cambio. La spec se commitea en la misma rama
que el código, así el PR muestra el plan y su implementación juntos.

Para cambios chicos alcanza con mostrar la spec en el chat y guardarla resumida; para cambios
medianos o grandes, el archivo es obligatorio.
