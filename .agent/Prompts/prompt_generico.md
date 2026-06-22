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
*   **Formato de las preguntas**: **NO** las realices en formato de lista. Redáctalas de forma tal que el usuario pueda ingresar y completar con la respuesta correspondiente para cada caso de manera simple.