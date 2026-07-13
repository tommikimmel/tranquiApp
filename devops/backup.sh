#!/bin/bash

# Configuración
BACKUP_DIR="/vps/backups"
DATE=$(date +%F_%H-%M-%S)
DB_CONTAINER_NAME="tranqui-db"
DB_USER="tomaskimmel"
DB_NAME="tranqui_app"
FILENAME="${BACKUP_DIR}/backup_${DB_NAME}_${DATE}.sql"

# Crear directorio si no existe
mkdir -p "$BACKUP_DIR"

# Ejecutar pg_dump dentro del contenedor Docker sin detener el servicio
echo "Iniciando respaldo de base de datos..."
docker exec -t "$DB_CONTAINER_NAME" pg_dump -U "$DB_USER" "$DB_NAME" > "$FILENAME"

# Comprimir backup
gzip "$FILENAME"

# Eliminar backups con más de 30 días de antigüedad
find "$BACKUP_DIR" -type f -name "*.sql.gz" -mtime +30 -delete

echo "Respaldo completado y guardado en ${FILENAME}.gz"
