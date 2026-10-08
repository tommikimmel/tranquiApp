# ADR 008: DevOps (Automatización de Despliegue en VPS y Respaldos Hot de Base de Datos)

## Estado
Aceptado

## Contexto
Para la puesta en producción de Tranqui App se necesita automatizar el flujo de despliegue continuo en la VPS para minimizar la intervención manual y asegurar que la base de datos PostgreSQL sea respaldada periódicamente en caliente sin interrumpir el servicio. Además, se requiere exponer el sistema a través de HTTPS de forma segura soportando WebSockets encriptados (WSS).

## Decisiones
1. **Respaldos de Base de Datos (`backup.sh`):**
   Se implementó un script en bash (`devops/backup.sh`) ejecutado mediante el crontab del sistema de la VPS (`0 3 * * *` a las 03:00 AM).
   - El script realiza volcados lógicos calientes (`pg_dump`) de la base de datos ejecutando el comando directamente dentro del contenedor Docker `tranqui-db`.
   - Aplica compresión `gzip` y mantiene una política de rotación de backups eliminando archivos que tengan más de 30 días de antigüedad para proteger el espacio en disco.

2. **Proxy Inverso Nginx (`nginx.conf`):**
   Se redactó la configuración del servidor web Nginx de la VPS para funcionar como proxy inverso y puerta de enlace TLS:
   - Redirección automática de HTTP (puerto 80) a HTTPS (puerto 443).
   - Configuración de certificados Let's Encrypt / Certbot y protocolos seguros TLS 1.2 y TLS 1.3.
   - Enrutamiento hacia los puertos internos locales expuestos por Docker Compose: `/` para el frontend React, `/api` para el backend de Spring Boot, y `/ws-tranqui` con cabeceras `Upgrade` y `Connection` especiales para soportar el WebSocket del chat clínico prioritario de forma segura.

3. **Script de Despliegue Automatizado (`deploy.sh`):**
   Se generó `devops/deploy.sh` para agilizar los despliegues directos en el servidor VPS.
   - Navega al directorio, sincroniza los cambios de la rama principal estable (`main`), compila las imágenes actualizadas con `docker-compose build`, recrea los contenedores en segundo plano y limpia las imágenes huérfanas (`docker image prune -f`) para optimizar el almacenamiento.

## Consecuencias
* El despliegue de nuevos cambios se puede efectuar con un comando de forma segura reduciendo el tiempo de inactividad.
* Los datos de turnos, conceptos y chat se respaldan de manera automatizada reduciendo los riesgos de pérdida de información.
* El canal de comunicación de extremo a extremo (tanto REST como WebSockets STOMP) queda protegido mediante cifrado SSL/TLS.
