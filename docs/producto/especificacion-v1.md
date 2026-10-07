
---
# Especificación Funcional y Técnica: Tranqui App (MVP - V1)
**Documento Unificado para Stakeholders y Equipo de Desarrollo**
**Versión:** 1.2 (Diseño Arquitectónico Completo)
**Fecha:** Junio 2026

---

## 1. Visión General del Producto
Tranqui App es una plataforma SaaS (Software as a Service) de gestión clínica y pasarela transaccional diseñada para optimizar la operación de consultorios de salud mental. En esta primera versión (MVP), el sistema se validará con los activos existentes del consultorio del Dr. García Galván (Tranqui Neurociencias) en Córdoba, Argentina.

El MVP tiene como objetivo automatizar el flujo de reserva de turnos, habilitar múltiples conceptos de cobro digital directo (eliminando la verificación manual de comprobantes), integrar telemedicina nativa sin costos de infraestructura y ofrecer mensajería instantánea interna con priorización clínica.

---

## 2. Alcance del MVP vs. Futuras Versiones (Feats)

Para acelerar la salida a producción y mitigar fricciones regulatorias, el alcance se ha estructurado de la siguiente manera:

| Módulo / Funcionalidad | Incluido en MVP (V1) | Planificado para V2 |
| :--- | :---: | :---: |
| **Autenticación con Google (OAuth2)** | ✅ | |
| **Bloques de Turnos Automáticos (45 min)** | ✅ | |
| **Pasarela de Pagos Directa (Mercado Pago Marketplace)** | ✅ | |
| **Diferenciación de Conceptos (Consulta, Receta, Certificado)** | ✅ | |
| **Turnos por Obra Social (OSDE) con Validación Manual** | ✅ | |
| **Telemedicina Automática (Google Meet)** | ✅ | |
| **Mensajería Instantánea por WebSockets con Prioridad** | ✅ | |
| **Notificaciones de Agenda (WhatsApp Saliente + Internas)** | ✅ | |
| **Entorno Containerizado (Docker & Docker Compose)** | ✅ | |
| **Sistema de Backups Automatizado (pg_dump)** | ✅ | |
| **Panel Administrador "Fantasma"** | ✅ | |
| **Suscripción Mensual SaaS para Psiquiatras (Monetización)** | | ✅ |
| **Facturación Electrónica Automática (Integración AFIP)** | | ✅ |
| **Bot de WhatsApp Interactivo / IA (Confirmaciones)** | | ✅ |
| **Historia Clínica y Recetas Electrónicas Firmadas** | | ✅ |

---

## 3. Roles de Usuario y Reglas de Acceso

*   **Administrador (Centro Clínico):** Actúa como un "fantasma" dentro del sistema (no tiene interacción de chat con los pacientes). Se encarga de dar de alta manualmente a los psiquiatras cargando su e-mail y matrícula. Posee acceso exclusivo al ABM de profesionales y métricas globales de uso.
*   **Psiquiatra:** No se registra por sí mismo en la V1. Configura sus franjas horarias de trabajo, visualiza su agenda, gestiona el chat con sus pacientes y vincula las credenciales de su cuenta de Mercado Pago para recibir cobros directos sin intermediarios.
*   **Paciente:** Se registra/loguea de forma instantánea con su cuenta de Google. Solicita turnos (particulares o por OSDE), abona servicios digitales y accede a las videollamadas.

---

## 4. Stack Tecnológico Definitivo (V1)

### Componentes de Software
*   **Frontend Web:** React 19 + TypeScript + Vite + TailwindCSS.
*   **Backend (API REST & Sockets):** Java 17 + Spring Boot 3.x + Spring Security (JWT).
*   **Base de Datos:** PostgreSQL 15.
*   **Protocolo de Chat & Alertas:** WebSockets utilizando el protocolo STOMP nativo de Spring.
*   **Infraestructura y Contenedores:** Docker + Docker Compose.
*   **Integraciones Externas:** Mercado Pago SDK, Google Calendar API (Meet), y WhatsApp Business API (Twilio/Meta).

### Gestión de Dependencias Clínicas

#### A. Backend: Spring Boot (`pom.xml`)
```xml
<!-- Core & Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- Persistencia (PostgreSQL) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Seguridad & OAuth2 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>

<!-- Integraciones de Terceros -->
<dependency>
    <groupId>com.mercadopago</groupId>
    <artifactId>mercadopago-sdk</artifactId>
    <version>2.1.0</version>
</dependency>
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- Utilidades -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

```

#### B. Frontend: React (`package.json`)

```json
"dependencies": {
  "react": "^19.0.0",
  "react-dom": "^19.0.0",
  "react-router-dom": "^6.22.0",
  "axios": "^1.6.7",
  "@stomp/stompjs": "^7.0.0",
  "sockjs-client": "^1.6.1",
  "tailwindcss": "^3.4.1",
  "lucide-react": "^0.321.0"
}

```

---

## 5. Arquitectura del Modelo de Datos (PostgreSQL + JPA)

El diseño de la base de datos se desacopla de los turnos para permitir un ecosistema de cobro multiconcepto independiente. La tabla `Usuario` del Psiquiatra almacena su propio token de Mercado Pago para procesar los cobros sin comisiones de la plataforma.

```text
 [Usuario (Médico con Token MP)] ───1:N───► [Disponibilidad] (Rangos semanales)
     │
     ├───1:N───► [Turno] ◄───0/1─── [Pago] (external_reference en Mercado Pago)
     │                                │
     └───1:N───► [Mensaje] ◄──────────┘ (Vinculado al canal de chat)

```

### Entidades Núcleo

* **`TipoConcepto` (Enum):** `CONSULTA_PARTICULAR`, `CONSULTA_PRIMERA_VEZ`, `CONSULTA_OSDE`, `CERTIFICADO`, `RECETA_CONTROL`.
* **`EstadoPago` (Enum):** `PENDIENTE`, `APROBADO`, `RECHAZADO`, `REEMBOLSADO`.
* **`TipoTurno` (Enum):** `PARTICULAR`, `OSDE`.
* **`EstadoTurno` (Enum):** `PENDIENTE_PAGO`, `PENDIENTE_VALIDACION`, `CONFIRMADO`, `CANCELADO`.

---

## 6. Flujos Operativos Críticos

### A. Gestión de Turnos y Reglas de Negocio
* El backend calcula automáticamente bloques fijos de **45 minutos** basados en el rango horario del médico.

* **Flujo Particular / Primera Vez (Cobro Directo):** El turno se bloquea como `PENDIENTE_PAGO` por 10 minutos. El backend genera una Preferencia de Pago utilizando el *Access Token* específico del psiquiatra seleccionado. El dinero impacta de forma inmediata en la billetera virtual del médico al **0% de comisión de la app**. Si expira el tiempo sin confirmación del Webhook, un proceso `@Scheduled` libera el bloque.

* **Flujo OSDE (Copago Fijo & Validación en Diferido):** El paciente ingresa de forma obligatoria su **Número de Afiliado** y el sistema calcula la transacción en base al valor del **Copago Fijo institucional**. El turno se bloquea como `PENDIENTE_PAGO` por 10 minutos en Mercado Pago. Al registrarse la aprobación del copago, el estado muta a `CONFIRMADO` e impacta la agenda de Google Calendar bajo la nomenclatura visual `[OSDE] Consulta - Nombre Paciente`. La verificación de la vigencia del afiliado se procesa de forma manual/diferida por el personal del centro a través de su portal habitual de prestadores, utilizando el dato almacenado en la plataforma.

### B. Flujo Multiconcepto: Certificados y Recetas fuera de turno

* El paciente solicita una "Receta de control" o "Certificado" abonando el importe correspondiente, el cual va directo a la cuenta del médico.

* Al impactar el Webhook de aprobación, el backend **no genera ningún turno en la agenda**.

* Dispara una alerta por WebSocket al panel del psiquiatra en una sección llamada *"Documentos pendientes de emisión (Abonados)"*. El médico emite el documento firmado y se lo envía al paciente por el chat integrado de la aplicación.

### C. Política de Cancelación y Devoluciones Automáticas

* Si el paciente cancela la consulta con **más de 48 horas de anticipación**, el backend consume programáticamente la API de reembolsos de Mercado Pago utilizando las credenciales del médico asignado. El dinero se devuelve a la tarjeta del paciente, el turno pasa a `CANCELADO` y se libera el horario. Si faltan menos de 48 horas, el reembolso automático se bloquea.

---

## 7. Mensajería (WebSockets) y Notificaciones

### Chat Abierto con Priorización Clínica

El chat permanece abierto de forma permanente una vez establecido el primer contacto comercial. Para mitigar la saturación del psiquiatra, el dashboard ordena los canales de comunicación en tiempo real:

* 🔴 **Prioridad Alta (Tag "Con Turno Próximo"):** Pacientes con turnos en estado `CONFIRMADO` para el día en curso o dentro de las próximas 72 horas.
* ⚪ **Prioridad Baja:** Pacientes históricos sin consultas agendadas a corto plazo.

### Sistema de Notificaciones Dual

1. **WhatsApp (Pacientes):** Un servicio en segundo plano (`@Scheduled`) busca diariamente los turnos del día siguiente y envía un recordatorio automatizado de salida con los detalles de la cita y el enlace directo de Google Meet.
2. **WebSocket Interno (Psiquiatras):** Cuando entra una orden de OSDE o se acredita un pago particular, el backend envía un payload JSON al canal privado del médico (`/topic/notificaciones/{medicoId}`). La interfaz de React captura el evento y muestra una notificación en pantalla en tiempo real sin recargar el navegador.

---

## 8. Infraestructura, Respaldo y Despliegue (VPS)

### A. Entorno de Contenedores (Docker)

La aplicación se empaqueta por completo mediante archivos `Dockerfile` independientes y se orquesta a través de un archivo global `docker-compose.yml` en la raíz del proyecto, facilitando el aislamiento de la base de datos PostgreSQL, la API en Spring Boot y la compilación estática de React bajo Nginx.

### B. Estrategia de Backups en PostgreSQL

La persistencia de datos se protege mediante copias de seguridad automatizadas en caliente ejecutadas dentro del contenedor del motor relacional utilizando la herramienta nativa **`pg_dump`**. Un script en la VPS realiza un volcado diario de la base de datos estructurado bajo la siguiente rutina de terminal:

```bash
docker exec -t tranqui-db pg_dump -U tomaskimmel tranqui_app > /vps/backups/backup_$(date +%F).sql

```

### C. Flujo de Despliegue Continuo (Deploy)

La aplicación se aloja en una **VPS (Servidor Privado Virtual)** basada en Linux Ubuntu Server con IP pública dedicada y certificado SSL gestionado por Certbot/Nginx (requisito mandatorio para la escucha de Webhooks de Mercado Pago).

El despliegue se ejecuta en 4 pasos atómicos mediante terminal segura:

1. Conexión remota vía SSH: `ssh root@vps_ip`
2. Descarga del último código estable desde el repositorio privado: `git pull origin main`
3. Construcción y actualización de imágenes: `docker-compose build`
4. Lanzamiento de la infraestructura en segundo plano sin caída del servicio: `docker-compose up -d`

---

## 9. Políticas de Seguridad y Cumplimiento (MVP)

Debido a la alta sensibilidad de los datos clínicos y transaccionales manejados por Tranqui App, se aplican de forma mandatoria las siguientes contramedidas de seguridad en el diseño:

> [!IMPORTANT]
> **1. Cifrado AES-256**
> Los Access Tokens de Mercado Pago y Refresh Tokens de Google Calendar se almacenan cifrados en la base de datos PostgreSQL. Las llaves maestras de descifrado se inyectan mediante variables de entorno en Docker y nunca se exponen en el repositorio de código.

> [!CAUTION]
> **2. Aislamiento de Base de Datos**
> El contenedor de PostgreSQL (`tranqui-db`) opera en una red interna aislada dentro de Docker. No expone puertos al exterior de la VPS, previniendo intrusiones directas.

> [!IMPORTANT]
> **3. Canal Seguro (HTTPS/WSS)**
> Nginx bloquea el tráfico plano. Todas las peticiones API viajan bajo TLS/SSL (HTTPS) y las conexiones de mensajería instantánea se realizan mediante WebSockets Seguros (WSS).

> [!WARNING]
> **4. Protección contra XSS y CSRF**
> La persistencia de las sesiones extendidas se maneja en el Frontend mediante cookies seguras con directivas `HttpOnly`, `Secure` y `SameSite=Strict`, bloqueando la lectura de tokens por scripts maliciosos.

> [!NOTE]
> **5. Auditoría de Roles (RBAC)**
> Spring Security valida a nivel de método (anotaciones `@PreAuthorize`) que ningún usuario acceda a recursos ajenos a su rol, asegurando la privacidad del flujo clínico.