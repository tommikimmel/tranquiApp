# Etapa de Compilación
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
COPY services/calendario/pom.xml services/calendario/
COPY services/pagos/pom.xml services/pagos/


# Copiar código fuente de librerías y del gateway
COPY libs/comun/src libs/comun/src
COPY libs/eventos/src libs/eventos/src
COPY gateway/src gateway/src

# Compilar gateway y sus dependencias del multimodulo
RUN mvn clean package -DskipTests -pl gateway -am

# Etapa de Ejecución
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/gateway/target/*.jar app.jar
EXPOSE 8080
EXPOSE 8082
ENV TZ=America/Argentina/Buenos_Aires
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=65", "-Xss256k", "-XX:ReservedCodeCacheSize=48m", "-XX:MaxMetaspaceSize=96m", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "app.jar"]
