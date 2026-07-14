#!/bin/bash
set -e

echo "Iniciando despliegue de Tranqui App..."

# Navegar a la carpeta del proyecto
cd /app

# Descargar cambios desde la rama principal estable
echo "Obteniendo último código estable..."
git pull origin main

# Reconstruir imágenes de Docker
echo "Recompilando imágenes Docker..."
docker-compose build

# Recrear contenedores en segundo plano sin detener la API
echo "Lanzando nuevos contenedores..."
docker-compose up -d --remove-orphans

# Eliminar imágenes huérfanas obsoletas para liberar espacio en disco
docker image prune -f

echo "Despliegue finalizado exitosamente."
