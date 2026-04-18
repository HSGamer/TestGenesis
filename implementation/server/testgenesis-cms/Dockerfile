# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1. Cache dependencies
# Copy only the pom.xml and the shared specification to download dependencies first.
# This ensures that we don't re-download everything if only source code changes.
COPY specification /specification
COPY implementation/server/testgenesis-cms/pom.xml /workspace/testgenesis-cms/
WORKDIR /workspace/testgenesis-cms/

# Download dependencies (go-offline)
# We ignore errors in go-offline as some Quarkus/gRPC plugins might require source code to resolve fully,
# but this will still cache the vast majority of artifacts.
RUN mvn dependency:go-offline -B || true

# 2. Build application
# Copy source code and build
COPY implementation/server/testgenesis-cms/src /workspace/testgenesis-cms/src
RUN mvn package -DskipTests

# Stage 2: Runtime
# Using a lightweight JRE image for the final application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /work/

# Copy the Quarkus fast-jar distribution from the build stage
COPY --from=build /workspace/testgenesis-cms/target/quarkus-app/ /work/

EXPOSE 8080 9000

# Run the application
CMD ["java", "-jar", "quarkus-run.jar", "-Dquarkus.http.host=0.0.0.0"]
