# Etapa 8: DevOps (Backups de Base de Datos y Pipeline de Despliegue en VPS)

Esta etapa abarca la puesta en producción de Tranqui App, detallando el flujo de despliegue continuo en la VPS, la configuración del Proxy Inverso Nginx con cifrado SSL/TLS y el sistema de copias de seguridad calientes (`pg_dump`) programado en el sistema operativo.

---

## 1. Objetivos de la Etapa
1.  Implementar un script en bash para realizar respaldos automatizados de la BD PostgreSQL mediante `pg_dump`.
2.  Configurar Nginx en la VPS como Proxy Inverso para redirigir tráfico HTTPS y habilitar WebSockets Seguros (WSS).
3.  Obtener e instalar el certificado de seguridad SSL gratuito mediante Let's Encrypt (Certbot).
4.  Crear un script de despliegue automatizado (`deploy.sh`) ejecutable en la VPS.

---

## 2. Definición Técnica y Código de Soporte

### A. Script de Backup de Base de Datos (`backup.sh`)
Este script se ejecuta dentro de la VPS y realiza el volcado diario de datos almacenando el archivo resultante en un directorio local seguro.

```bash
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
```

#### Tarea Cron en Linux (VPS):
Abrir `crontab -e` y añadir la siguiente línea para ejecutar el backup de manera silenciosa todos los días a las 03:00 AM:
```text
0 3 * * * /vps/scripts/backup.sh > /dev/null 2>&1
```

### B. Configuración de Nginx (`nginx.conf` en VPS)
El proxy inverso recibe las peticiones en el puerto `80` (redireccionado al `443` HTTPS) y delega la lógica al backend de Spring y el frontend en Nginx de Docker.

```nginx
server {
    listen 80;
    server_name tranquiapp.com www.tranquiapp.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name tranquiapp.com www.tranquiapp.com;

    # Certificados SSL generados por Certbot
    ssl_certificate /etc/letsencrypt/live/tranquiapp.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/tranquiapp.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Frontend estático React
    location / {
        proxy_pass http://localhost:80; # Puerto expuesto por el contenedor frontend
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # API REST del Backend
    location /api {
        proxy_pass http://localhost:8080; # Puerto del backend en Spring
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSockets (Chat STOMP)
    location /ws-tranqui {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
    }
}
```

### C. Script de Despliegue Continuo (`deploy.sh`)
```bash
#!/bin/bash
set -e

echo "Iniciando despliegue de Tranqui App..."

# Navegar a la carpeta del proyecto
cd /vps/projects/tranqui

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
```

---

## 3. Estrategia de Testing y Verificación

### A. Prueba de Restauración del Backup (Crítico)
*   **Prueba de Recuperación ante Desastres:**
    Crear una base de datos temporal vacía localmente y ejecutar la restauración del último backup comprimido para verificar que los datos no se corrompan durante el proceso de exportación:
```bash
# Descomprimir y restaurar
gunzip -c backup_tranqui_app_XXXX.sql.gz | docker exec -i tranqui-db-test psql -U tomaskimmel -d tranqui_test_restore
```

### B. Validación de Renovación del Certificado SSL
Verificar que la renovación automática del certificado provisto por Let's Encrypt funciona correctamente ejecutando:
```bash
certbot renew --dry-run
```

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Monitoreo del Espacio en Disco:** El proceso de `pg_dump` diario genera archivos acumulativos. Aunque el script tiene una directiva para borrar backups antiguos de más de 30 días, se aconseja exportar una copia semanal fuera de la VPS (ej. a un bucket de almacenamiento S3 externo) para mitigar fallos físicos del servidor de hosting.
*   **Zero Downtime:** Docker Compose recrea los contenedores de forma secuencial. Sin embargo, al actualizar la BD con nuevas migraciones de datos, es aconsejable desplegar en horarios de bajo tráfico (madrugada) para mitigar bloqueos momentáneos en las conexiones de la API.
