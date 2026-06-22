# Workflow de Git, Commits y Normas de Desarrollo

Este documento define la metodología de trabajo para el control de versiones, la integración de código y las prácticas de pruebas en **Tranqui App**. Todos los miembros del equipo y agentes de IA deben seguir estas pautas rigurosamente.

---

## 1. Modelo de Ramas (Git Flow)

El desarrollo se organiza a través de un esquema basado en Git Flow con las siguientes ramas principales:

*   `main`: Contiene el código de producción listo para el usuario final. Solo recibe fusiones de ramas `release/*` y `hotfix/*`. Cada cambio en esta rama debe ser etiquetado con su correspondiente tag de versión (ej. `v1.0.0`).
*   `develop`: Es la rama central de integración. Todos los desarrollos de nuevas características se integran aquí.
*   `feature/*`: Ramas secundarias de desarrollo de funcionalidades. Se crean desde `develop` y se vuelven a mezclar en `develop` mediante Pull Requests.
    *   *Formato:* `feature/funcionalidad-deseada` (ej. `feature/oauth2-login`, `feature/calculo-slots`).
*   `bugfix/*`: Para corregir errores detectados en la rama `develop`.
    *   *Formato:* `bugfix/descripcion-error` (ej. `bugfix/reembolso-excepcion`).
*   `release/*`: Ramas de preparación de versión antes de salir a producción. Se desprenden de `develop` y se mezclan tanto en `main` como en `develop`.
    *   *Formato:* `release/vX.Y.Z` (ej. `release/v1.0.0`).
*   `hotfix/*`: Correcciones urgentes de errores críticos en producción. Se crean desde `main` y se mezclan en `main` y `develop` inmediatamente.
    *   *Formato:* `hotfix/descripcion-urgente` (ej. `hotfix/caida-webhook-mp`).

---

## 2. Formato de Commits (Conventional Commits)

Cada commit debe describir de forma precisa el cambio implementado utilizando la especificación de **Conventional Commits**. El formato requerido es:

`tipo(alcance): descripción breve en minúsculas`

### Tipos de Commit Permitidos:
*   `feat`: Una nueva característica o funcionalidad.
    *   *Ejemplo:* `feat(auth): integrar inicio de sesión con google oauth2`
*   `fix`: Solución a un error o bug.
    *   *Ejemplo:* `fix(chat): corregir pérdida de conexión websocket en reconexión`
*   `docs`: Cambios en la documentación.
    *   *Ejemplo:* `docs(readme): añadir instrucciones de despliegue local`
*   `style`: Cambios estéticos o de formato que no afectan el comportamiento del código (espacios, punto y coma, etc.).
    *   *Ejemplo:* `style(frontend): formatear vista del calendario según eslint`
*   `refactor`: Modificación de código que no corrige un bug ni añade una característica, pero mejora su estructura.
    *   *Ejemplo:* `refactor(db): simplificar consulta jpa en turnos`
*   `test`: Añadir o corregir pruebas existentes.
    *   *Ejemplo:* `test(auth): agregar prueba unitaria para descifrado de access token`
*   `chore`: Tareas de mantenimiento, actualización de dependencias, scripts de build, configuraciones de docker, etc.
    *   *Ejemplo:* `chore(deps): actualizar sdk de mercado pago a version 2.1.0`

---

## 3. Manejo de Secretos y Variables de Entorno

> [!CAUTION]
> **Queda estrictamente prohibido persistir credenciales o secretos en el repositorio.**
> Ninguna contraseña, llave API, credencial de base de datos o token de seguridad debe guardarse en archivos controlados por Git.

### Buenas Prácticas:
1.  **Archivo `.env`:** Todos los valores sensibles del frontend y backend se configuran mediante un archivo `.env` en la raíz del proyecto. Este archivo debe añadirse al `.gitignore`.
2.  **Archivo `.env.template`:** Se debe mantener actualizado este archivo plantilla en el repositorio con las claves vacías de las variables necesarias para que un desarrollador nuevo pueda clonar el proyecto y configurarlo rápidamente.
3.  **Inyección en Spring/React:** En el backend, las propiedades de Spring (`application.yml`) leerán las variables utilizando `${VARIABLE_NAME}`. En el frontend, Vite leerá las variables con el prefijo `VITE_`.

---

## 4. Convenciones de Testing y Calidad

Para asegurar la robustez de Tranqui App antes del despliegue:
*   **Pruebas Unitarias:** Cada servicio del backend debe contar con pruebas unitarias (`JUnit` + `Mockito`) que cubran las reglas de negocio críticas (ej. cálculo de bloques horarios, lógica de reembolso menor a 48 hs). Cobertura mínima obligatoria: 90%.
*   **Pruebas de Integración:** Utilizar `@SpringBootTest` con bases de datos en memoria (`H2` o contenedores de prueba `Testcontainers` de Postgres) para verificar que las transacciones y persistencias JPA se realicen correctamente.
*   **Frontend Testing:** Componentes complejos del flujo de reserva deben probarse usando `React Testing Library` o similar para verificar su comportamiento interactivo.
*   **Verificación Automática:** Todo Pull Request hacia `develop` debe compilar la aplicación, correr las pruebas unitarias y pasar linter sin errores.
