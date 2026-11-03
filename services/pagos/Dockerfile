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
COPY services/calendario/pom.xml services/calendario/
COPY services/pagos/pom.xml services/pagos/
COPY services/profesionales/pom.xml services/profesionales/
COPY services/turnos/pom.xml services/turnos/
COPY services/identidad/pom.xml services/identidad/

# Copiar código fuente de las librerías y del servicio
COPY libs/comun/src libs/comun/src
COPY libs/eventos/src libs/eventos/src
COPY services/pagos/src services/pagos/src

RUN mvn clean package -DskipTests -pl services/pagos -am

# Etapa de Ejecución
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/services/pagos/target/*.jar app.jar
EXPOSE 8080
EXPOSE 8082
ENV TZ=America/Argentina/Buenos_Aires
# Sección 9.2: JVM afinada; TieredStopAtLevel=1 en los servicios chicos (arranque más rápido).
# Heap al 30 % de 288 MB (~86 MB; el vivo usa ~55), metaspace hasta 128 MB y caché de código de 32 MB.
# Con poca RAM la JVM puede usar MinRAMPercentage (50 % por defecto) e ignorar MaxRAMPercentage: se
# fijan los dos. Medido con NMT en la Fase 2: lo que no es heap (clases, símbolos, código, CDS, hilos)
# suma ~165-175 MB fijos y con SerialGC el heap llega a su máximo: el proceso queda en ~255-260 MB.

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MinRAMPercentage=30", "-XX:MaxRAMPercentage=30", "-Xss256k", "-XX:ReservedCodeCacheSize=32m", "-XX:MaxMetaspaceSize=128m", "-XX:TieredStopAtLevel=1", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "app.jar"]
