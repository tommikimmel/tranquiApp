# Contexto de Arquitectura y Configuración del Proyecto

Este documento establece las directrices arquitectónicas, la estructura de carpetas, las convenciones de nombrado y los requisitos de seguridad que deben seguir todos los desarrolladores y agentes de IA en **Tranqui App**.

---

## 1. Stack Tecnológico General
*   **Frontend:** React 19 + TypeScript + Vite + TailwindCSS.
*   **Backend:** Java 17 + Spring Boot 3.x + Spring Data JPA + Spring Security (JWT) + WebSockets (STOMP/SockJS).
*   **Base de Datos:** PostgreSQL 15.
*   **Contenedores:** Docker & Docker Compose para orquestar y desplegar localmente y en producción.
*   **Servicios Externos:** Mercado Pago SDK (Marketplace/Pagos Directos), Google Calendar API (Meet) y WhatsApp Business API (Twilio/Meta).

---

## 2. Estructura de Carpetas Sugerida

El proyecto se divide en dos directorios principales en la raíz (`frontend/` y `backend/`) junto con la configuración de Docker.

```text
tranqui/
├── .agent/                  # Directorio de agentes de IA y metadatos
│   ├── Etapas/              # Plan de etapas detalladas
│   ├── Skills/              # Habilidades del agente para desarrollo
│   ├── Prompts/             # Prompts estructurados
│   ├── ADRs/                # Architecture Decision Records
│   └── Base de Datos/       # DDL y diseño de base de datos
├── docker-compose.yml       # Orquestador local y producción
├── .env.template            # Plantilla de variables de entorno globales
├── README.md
├── frontend/                # Aplicación React 19
│   ├── Dockerfile
│   ├── package.json
│   ├── tailwind.config.js
│   ├── src/
│   │   ├── assets/          # Imágenes y estilos globales (index.css)
│   │   ├── components/      # Componentes reutilizables
│   │   ├── context/         # React Context (Auth, Chat)
│   │   ├── hooks/           # Custom hooks
│   │   ├── pages/           # Vistas principales (Login, Dashboard, Agenda)
│   │   ├── services/        # Clientes de API (Axios, WebSockets)
│   │   ├── utils/           # Helper functions
│   │   ├── App.tsx
│   │   └── main.tsx
└── backend/                 # API Spring Boot
    ├── Dockerfile
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/tranqui/app/
        │   │   ├── config/      # Configuraciones (Security, WebSocket, MP)
        │   │   ├── controller/  # Controladores REST y Socket
        │   │   ├── model/       # Entidades JPA y DTOs
        │   │   ├── repository/  # Repositorios JPA
        │   │   ├── service/     # Lógica de negocio e integraciones
        │   │   ├── util/        # Utilidades y cifrado
        │   │   └── TranquiAppApplication.java
        │   └── resources/
        │       ├── db/migration/ # Migraciones Flyway/Liquibase
        │       └── application.yml
        └── test/                # Pruebas Unitarias y de Integración
```

---

## 3. Convenciones de Nombrado y Estilos de Código

### A. Base de Datos (PostgreSQL)
*   **Tablas y Columnas:** Usar `snake_case` exclusivamente.
    *   *Ejemplo:* `usuario_id`, `tipo_concepto`, `estado_pago`.
*   **Nombres de Tablas:** En singular.
    *   *Ejemplo:* `usuario`, `turno`, `pago`, `mensaje`.
*   **Llaves Primarias y Foráneas:** Las llaves primarias deben llamarse `id` o `{tabla}_id`. Las llaves foráneas deben llamarse `{tabla_padre}_id` para máxima claridad.
*   **Enums:** Los enums se mapean como cadenas de texto (`VARCHAR`) con restricciones `CHECK` para evitar datos corruptos.

### B. Backend (Spring Boot / Java)
*   **Clases e Interfaces:** `PascalCase`.
    *   *Ejemplo:* `UsuarioController`, `TurnoService`, `EstadoPago`.
*   **Métodos y Variables:** `camelCase`.
    *   *Ejemplo:* `obtenerTurnosDisponibles()`, `accessTokenEncrypted`.
*   **Paquetes:** `lowercase` sin guiones ni caracteres especiales.
*   **DTOs:** Los Data Transfer Objects deben terminar con el sufijo `Dto`.
    *   *Ejemplo:* `CrearTurnoDto`.

### C. Frontend (React / TypeScript)
*   **Componentes y Vistas:** `PascalCase`.
    *   *Ejemplo:* `Sidebar.tsx`, `CalendarioTurnos.tsx`.
*   **Hooks, Funciones y Variables:** `camelCase`.
    *   *Ejemplo:* `useAuth()`, `formatearFecha()`, `cargando`.
*   **Archivos CSS y Configs:** `kebab-case`.
    *   *Ejemplo:* `index.css`, `tailwind.config.js`.

---

## 4. Políticas de Seguridad Obligatorias

> [!IMPORTANT]
> **1. Cifrado de Datos Sensibles (AES-256)**
> Los *Access Tokens* y *Refresh Tokens* de Mercado Pago y Google Calendar deben almacenarse **cifrados** en la base de datos PostgreSQL utilizando el algoritmo AES-256. La llave de descifrado maestro se inyectará como variable de entorno y **NUNCA** debe ser escrita directamente en el código fuente.

> [!CAUTION]
> **2. Aislamiento y Red Interna**
> El contenedor de la base de datos PostgreSQL (`tranqui-db`) debe operar en una red interna privada configurada en Docker Compose (`tranqui-network`). Bajo ninguna circunstancia debe exponer el puerto `5432` hacia el exterior en la VPS o entorno de producción. Solo la API del backend tiene acceso a la red de la base de datos.

> [!IMPORTANT]
> **3. Cookies Seguras para Sesiones**
> Los tokens JWT que mantienen la sesión del usuario no deben exponerse en `localStorage`. Deben ser devueltos por el backend y almacenados en el cliente como cookies con las banderas `HttpOnly`, `Secure` y `SameSite=Strict`.

> [!WARNING]
> **4. Canal Seguro (SSL/TLS)**
> En entornos de producción y VPS, todo el tráfico web HTTP debe redirigirse automáticamente a HTTPS. Las conexiones WebSocket del chat deben usar de forma exclusiva el esquema seguro `WSS`.

> [!NOTE]
> **5. Control de Acceso Basado en Roles (RBAC)**
> Las APIs deben protegerse mediante anotaciones en Spring Security, como `@PreAuthorize("hasRole('ADMIN')")` o `@PreAuthorize("hasRole('PSIQUIATRA')")`, validando la pertenencia de los datos al usuario que realiza la consulta para evitar vulnerabilidades de IDOR (Insecure Direct Object Reference).

---

## 5. Orquestación con Docker (Desarrollo y Producción)

El entorno local y el despliegue en VPS se unifican mediante **Docker Compose**, lo que garantiza que el comportamiento de la base de datos y los servicios sea idéntico en desarrollo y en producción.

### Contenedores Definidos:
1.  `tranqui-db`: PostgreSQL 15 con persistencia de volumen local.
2.  `tranqui-backend`: Spring Boot compilado con JDK 17, expuesto en el puerto `8080` (en desarrollo).
3.  `tranqui-frontend`: React 19 empaquetado bajo un servidor Nginx para producción, expuesto en el puerto `80` (redireccionado por SSL).

Cada contenedor se comunicará a través de la red virtual de Docker y se configurará usando un archivo `.env` local que no se sube al repositorio de Git.
