# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first (layer caching for dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build production JAR
COPY src/ src/
RUN ./mvnw clean package -Pproduction -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
