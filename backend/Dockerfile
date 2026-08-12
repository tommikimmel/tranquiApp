# Etapa de Compilación
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa de Ejecución
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Todos los turnos, horarios y comparaciones de fecha/hora del negocio son en hora de
# Argentina — sin esto, el contenedor (y por lo tanto todo LocalDate.now()/LocalTime.now()
# sin ZoneId explicito) corre en UTC, desalineando 3hs los turnos del dia contra la hora real.
ENV TZ=America/Argentina/Buenos_Aires
ENTRYPOINT ["java", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "app.jar"]
