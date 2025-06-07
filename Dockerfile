# Build stage
# CORRECCIÓN FINAL AQUÍ: Usamos una imagen Maven más genérica y estable
FROM maven:3-jdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -U

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app
# Asegurarse que el nombre del JAR sea el correcto (ProyectoFutbol)
COPY --from=build /app/target/ProyectoFutbol-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]