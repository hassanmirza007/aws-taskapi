# ---- Build stage: compile the jar inside a Maven image ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Copy the pom first so dependency downloads are cached between builds
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Run stage: small JRE image, no Maven or source code ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Don't run as root inside the container
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/target/taskapi.jar app.jar
USER app

EXPOSE 8080

# Size the heap from the container's memory limit (important on ECS/Fargate)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
