# Build stage
FROM maven:3.9.6-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Esta línea compila tu proyecto Java
RUN mvn clean package -DskipTests -U

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app
# ¡¡¡LA CORRECCIÓN ESTÁ EN LA SIGUIENTE LÍNEA!!!
# Debe ser "ProyectoFutbol-0.0.1-SNAPSHOT.jar" porque ese es el nombre de tu artifactId en pom.xml
COPY --from=build /app/target/ProyectoFutbol-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]