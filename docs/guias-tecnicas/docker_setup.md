---
name: docker-setup
description: "Pautas para la configuración y administración del entorno de contenedores Docker y Docker Compose."
---

# Skill: Docker Setup & Orchestration

Esta habilidad contiene directrices detalladas para la containerización y orquestación local y de producción de **Tranqui App**.

## 1. Responsabilidades del Agente
*   Crear y depurar archivos `Dockerfile` multiplataforma (multi-stage builds) para optimizar el tamaño de las imágenes.
*   Mantener el archivo `docker-compose.yml` asegurando que las variables de entorno se inyecten dinámicamente y no haya contraseñas en duro.
*   Garantizar la conexión en red privada (`tranqui-network`) de la base de datos para aislarla del acceso público.

## 2. Comandos Clave de Operación
*   **Iniciar servicios:** `docker-compose up -d --build`
*   **Detener servicios sin borrar volúmenes:** `docker-compose down`
*   **Ver logs de la API:** `docker-compose logs -f tranqui-backend`
*   **Verificar redes de Docker:** `docker network inspect tranqui-network`

## 3. Prácticas de Rendimiento
*   Utilizar imágenes base basadas en `alpine` o `slim` para reducir el consumo de disco y acelerar la descarga en el servidor VPS.
*   Montar volúmenes persistentes con nombres (ej. `pgdata`) para los datos de PostgreSQL.
