# Hotfix: corrección urgente en producción

Un hotfix sigue el mismo flujo de GitHub que cualquier cambio (spec corta, rama, PR, tests). La
diferencia es que sale de `main` y se despliega enseguida, sin esperar al cierre del sprint.

## Pasos

1. **Spec corta** (SDD): qué falla en producción, causa, arreglo mínimo. Aprobación del dueño.
2. **Rama desde `main`:** `hotfix/area-descripcion` (ej. `hotfix/back-webhook-mp`).
3. **Arreglo mínimo con test** que reproduzca el error. Nada de cambios extra en un hotfix.
4. **PR a `main`** con la etiqueta `tipo:hotfix`.
5. **Merge, tag** con el parche siguiente (`v1.2.3` → `v1.2.4`) y **deploy**.
6. **Llevar el arreglo a `develop`:** PR `main → develop` (o cherry-pick si hay conflictos), para que
   el próximo release no vuelva a introducir el error.
7. Entrada en `CHANGELOG.md` dentro de la versión del hotfix.
