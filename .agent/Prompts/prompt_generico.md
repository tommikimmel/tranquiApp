# Guía de Ejecución y Prompt Genérico para Agentes (Tranqui App)

Eres un agente de desarrollo de Inteligencia Artificial asignado al proyecto **Tranqui App**. Debes leer y seguir estrictamente las siguientes instrucciones de estructura, flujo de trabajo y documentación para la ejecución de tus tareas.

---

## 1. Directorios de Referencia Principales

Dentro del directorio `.agent/` (o `.agents/`), concéntrate en las siguientes carpetas:

*   **`Etapas/`**: Contiene la planificación paso a paso para el desarrollo tanto del **Backend** como del **Frontend**.
    *   [00_contexto_arquitectura.md](file:///home/tomaskimmel/Documentos/tranqui/.agent/Etapas/00_contexto_arquitectura.md): Contexto general técnico y arquitectura de la aplicación.
    *   [00_workflow_desarrollo.md](file:///home/tomaskimmel/Documentos/tranqui/.agent/Etapas/00_workflow_desarrollo.md): Normas sobre Git, formato de ramas y Conventional Commits.
*   **`skills/`**: Directorio que contiene las habilidades específicas (Docker, Base de Datos, Spring Security, etc.) que deberás leer y utilizar para llevar a cabo cada una de las etapas.

---

## 2. Instrucciones de la Tarea

1.  **Ejecutar la Etapa Solicitada**: Realiza de manera completa y testeada la etapa número:
    👉 **Etapa Número: `***`** (Reemplazar por el número de etapa correspondiente).
2.  **Uso Obligatorio de Skills**: Revisa la carpeta `skills/` y lee las directrices asociadas antes de programar la solución. Utilízalas como guía técnica y de buenas prácticas.

---

## 3. Registro de Decisiones de Arquitectura (ADRs)

Una vez finalizada la etapa correspondiente:
*   Crea un nuevo archivo Markdown dentro de la carpeta `ADRs/`.
*   **Nomenclatura del archivo**: `.agent/ADRs/{numeroADR}-"Nombre-del-cambio".md` (ej. `001-configuracion-docker.md`).
*   **Contenido del archivo**: Detalla de manera concisa pero completa todos los cambios realizados, lógica aplicada y pruebas de validación.

---

## 4. Dudas y Aclaraciones

En caso de encontrar inconsistencias en las especificaciones o tener dudas sobre la implementación:
*   Realiza las preguntas aclaratorias que consideres necesarias al usuario.
*   **Formato de las preguntas**: Realízalas en formato de lista con opciones claras para que el usuario pueda seleccionar las opciones que considere adecuadas de manera simple.

---

## 5. Workflow de Git y Gestión de Ramas (GitKraken y Origin)

Para mantener una visualización limpia e ilustrativa del grafo de commits en herramientas como GitKraken, y conservar el servidor remoto limpio de ramas temporales, sigue estrictamente este workflow:

1. **Creación de Ramas**:
   - Trabaja siempre en ramas de funcionalidad específicas creadas a partir de `develop`.
   - **Formato de rama**: `feat/be-fe/nombre-del-cambio` (ej. `feat/be-fe/arquitectura-inicial-docker`).
   - Evita el uso de caracteres especiales prohibidos en Git (como `:`).

2. **Integración con develop (Local)**:
   - Una vez finalizados y validados los cambios en la rama local, realiza la integración a `develop` usando un merge sin avance rápido (**no-fast-forward**). Esto genera de forma obligatoria un commit de fusión para que la rama paralela y la integración se dibujen en el gráfico de GitKraken:
     ```bash
     git checkout develop
     git merge --no-ff "feat/be-fe/nombre-del-cambio" -m "merge branch 'feat/be-fe/nombre-del-cambio' into develop"
     ```

3. **Subida de Cambios y Limpieza en Remoto (Origin)**:
   - **Subir develop**: Sube la rama `develop` integrada a origin:
     ```bash
     git push origin develop
     ```
   - **Limpieza de ramas de feature**: Las ramas `feat/be-fe/...` no deben permanecer en el repositorio remoto (`origin`). Si fueron subidas, elimínalas del remoto para conservar el servidor limpio:
     ```bash
     git push origin --delete feat/be-fe/nombre-del-cambio
     ```
   - Esto mantendrá las ramas locales y el grafo visual intacto, mientras que en origin solo existirán las ramas de integración principal (`develop`) y producción (`main`).