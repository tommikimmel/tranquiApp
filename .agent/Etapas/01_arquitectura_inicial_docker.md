# Etapa 1: Arquitectura Inicial, Dockerización y Skeletons

Esta etapa se enfoca en establecer las bases de la infraestructura local y los esqueletos iniciales de los proyectos Frontend y Backend, asegurando que puedan compilar y comunicarse bajo contenedores aislados.

---

## 1. Objetivos de la Etapa
1.  Crear los directorios y proyectos esqueleto (`frontend/` y `backend/`).
2.  Configurar la orquestación local con Docker Compose.
3.  Establecer la red interna privada de Docker para aislar la base de datos PostgreSQL.
4.  Definir variables de entorno de ejemplo (`.env.template`) para la inyección de secretos.

---

## 2. Definición Técnica y Código de Soporte

### A. Archivo `docker-compose.yml` (Raíz del proyecto)
Este archivo define los tres servicios base (Base de datos, Backend y Frontend) con sus respectivas redes y políticas de reinicio.

```yaml
version: '3.8'

services:
  tranqui-db:
    image: postgres:15-alpine
    container_name: tranqui-db
    restart: always
    environment:
      POSTGRES_DB: ${DB_NAME:-tranqui_app}
      POSTGRES_USER: ${DB_USER:-tomaskimmel}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-securepassword}
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - tranqui-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 10s
      timeout: 5s
      retries: 5

  tranqui-backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: tranqui-backend
    restart: always
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://tranqui-db:5432/${DB_NAME:-tranqui_app}
      SPRING_DATASOURCE_USERNAME: ${DB_USER:-tomaskimmel}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-securepassword}
      SPRING_PROFILES_ACTIVE: dev
      ENCRYPTION_KEY: ${ENCRYPTION_KEY:-masterdecryptionkey32charspart12}
    depends_on:
      tranqui-db:
        condition: service_healthy
    networks:
      - tranqui-network

  tranqui-frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: tranqui-frontend
    restart: always
    ports:
      - "80:80"
    networks:
      - tranqui-network
    depends_on:
      - tranqui-backend

volumes:
  pgdata:
    driver: local

networks:
  tranqui-network:
    driver: bridge
```

### B. Dockerfile del Backend (`backend/Dockerfile`)
```dockerfile
# Etapa de Compilación
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa de Ejecución
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### C. Dockerfile del Frontend (`frontend/Dockerfile`)
```dockerfile
# Etapa de Compilación
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Servidor Web Nginx para servir estáticos
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## 3. Estrategia de Testing y Verificación

### A. Pruebas de Conectividad y Healthcheck
Para comprobar que la base de datos está operando correctamente y responde a las solicitudes de Spring Boot, ejecuta:
```bash
# Iniciar servicios en segundo plano
docker-compose up -d --build

# Revisar el estado de salud de la BD
docker inspect --format='{{json .State.Health}}' tranqui-db
```

### B. Pruebas del Backend (Ping Endpoint)
Crear un endpoint simple `/api/health` en Spring Boot para verificar que el servidor está levantado y conectado a la BD.
*   **Test Unitario de Integración (`HealthControllerTest.java`):**
```java
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldReturnStatusUp() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Volumen de Datos de Postgres:** No olvides declarar el volumen `pgdata` bajo `/var/lib/postgresql/data` en la BD para evitar perder los turnos y configuraciones cada vez que el contenedor sea recreado.
*   **Template `.env.template`:** Asegúrate de proveer variables realistas pero vacías o genéricas para evitar leaks de credenciales.
*   **Orden de Inicialización:** El backend depende de que el healthcheck de `tranqui-db` pase a `service_healthy`. Esto previene fallos tempranos en la inyección del pool de conexiones JPA (`HikariCP`).
