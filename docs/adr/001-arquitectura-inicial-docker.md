# ADR 001: Arquitectura Inicial, Dockerización y Skeletons de Tranqui App

## Estado
Aceptado

## Contexto
Necesitamos establecer las bases de la infraestructura de desarrollo local, los esqueletos iniciales de las aplicaciones Frontend y Backend, y asegurar un flujo de testing e integración continua estable y reproducible.

## Decisiones
1. **Orquestación con Docker Compose**:
   - Se configuró `docker-compose.yml` en la raíz del proyecto para orquestar:
     - `tranqui-db`: Base de datos PostgreSQL 15 con volumen local de datos persistente (`pgdata`) y healthcheck automático.
     - `tranqui-backend`: API en Spring Boot 3.5.15 (Java 17).
     - `tranqui-frontend`: SPA en React 19 servido mediante un servidor Nginx en producción.
   - Red aislada `tranqui-network` (tipo bridge) para la comunicación segura entre los servicios, aislando la base de datos de accesos externos.

2. **Esqueleto de Backend (Spring Boot)**:
   - Creado en `backend/` con soporte para Java 17 y Spring Boot 3.5.15 (para asegurar compatibilidad con compiladores Java 17, degradando de la versión 4.1.0 auto-generada).
   - Dependencias iniciales: Web, Security, Data JPA, Validation, WebSocket, PostgreSQL, Lombok y H2 (para testing).
   - Configuración de perfiles: `application.yml` para desarrollo (usando variables de entorno inyectadas) y `application.yml` para pruebas (usando H2 en memoria).
   - Implementación de un endpoint `/api/health` para comprobación de salud y su correspondiente prueba unitaria/de integración con `MockMvc`.

3. **Esqueleto de Frontend (React + Vite)**:
   - Creado en `frontend/` usando React 19 y TypeScript.
   - Configuración de servidor Nginx en producción (`nginx.conf` y `Dockerfile`) para servir estáticos y manejar rutas SPA mediante redirección a `/index.html`.

4. **Variables de Entorno**:
   - Creación de un archivo `.env.template` en la raíz para documentar de forma segura las variables requeridas para base de datos y llaves de cifrado AES.

## Consecuencias
- El entorno de desarrollo local está unificado mediante contenedores Docker, reduciendo discrepancias entre entornos.
- Se implementó un pipeline inicial de compilación y testing de integración exitoso (`BUILD SUCCESS`).
- El endpoint `/api/health` permite verificar la salud de los servicios al levantar el stack.
