# Etapa de Compilación
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# Copiar POMs para aprovechar la caché de Docker
COPY pom.xml .
COPY libs/comun/pom.xml libs/comun/
COPY libs/eventos/pom.xml libs/eventos/
COPY backend/pom.xml backend/
COPY gateway/pom.xml gateway/
COPY services/notificaciones/pom.xml services/notificaciones/
COPY services/chat/pom.xml services/chat/
COPY services/calendario/pom.xml services/calendario/
COPY services/pagos/pom.xml services/pagos/
COPY services/profesionales/pom.xml services/profesionales/

# Copiar código fuente de las librerías y del backend
COPY libs/comun/src libs/comun/src
COPY libs/eventos/src libs/eventos/src
COPY backend/src backend/src

# Compilar backend y sus dependencias del multimodulo
RUN mvn clean package -DskipTests -pl backend -am

# Etapa de Ejecución
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/target/*.jar app.jar
EXPOSE 8080
# Todos los turnos, horarios y comparaciones de fecha/hora del negocio son en hora de
# Argentina — sin esto, el contenedor (y por lo tanto todo LocalDate.now()/LocalTime.now()
# sin ZoneId explicito) corre en UTC, desalineando 3hs los turnos del dia contra la hora real.
ENV TZ=America/Argentina/Buenos_Aires
ENTRYPOINT ["java", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "app.jar"]
