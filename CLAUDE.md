# CLAUDE.md

Las reglas de trabajo para agentes de IA en este repositorio están en **[AGENTS.md](AGENTS.md)**.
Leelo completo antes de empezar cualquier tarea: incluye la regla de SDD (mostrar la spec y
esperar aprobación antes de tocar código), el flujo de ramas y la prohibición de tocar producción
sin aprobación explícita.

## Específico de Claude Code

- **Verificación en el navegador:** si existe la skill local `verify`
  (`.claude/skills/verify/`, no versionada), usala para levantar y recorrer la app. Si no,
  seguí [docs/entornos/local.md](docs/entornos/local.md).
- **Commits:** terminá el mensaje con la línea de coautoría que indique el entorno.
- **Preguntas al usuario:** hacelas en texto, numeradas, salvo que el usuario pida otra forma.
