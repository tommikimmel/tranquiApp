# Etapa de Compilación (contexto: raíz del repositorio, como backend y gateway)
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# Copiar POMs para aprovechar la caché de capas de Docker
COPY pom.xml .
COPY libs/comun/pom.xml libs/comun/
COPY libs/eventos/pom.xml libs/eventos/
COPY backend/pom.xml backend/
COPY gateway/pom.xml gateway/
COPY services/notificaciones/pom.xml services/notificaciones/
COPY services/chat/pom.xml services/chat/

# Copiar código fuente de las librerías y del servicio
COPY libs/comun/src libs/comun/src
COPY libs/eventos/src libs/eventos/src
COPY services/notificaciones/src services/notificaciones/src

RUN mvn clean package -DskipTests -pl services/notificaciones -am

# Etapa de Ejecución
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/services/notificaciones/target/*.jar app.jar
EXPOSE 8080
EXPOSE 8082
ENV TZ=America/Argentina/Buenos_Aires
# Sección 9.2: JVM afinada; TieredStopAtLevel=1 en los servicios chicos (arranque más rápido).
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=65", "-Xss256k", "-XX:ReservedCodeCacheSize=48m", "-XX:MaxMetaspaceSize=96m", "-XX:TieredStopAtLevel=1", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "app.jar"]
