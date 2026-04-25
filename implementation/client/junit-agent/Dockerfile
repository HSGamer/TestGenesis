# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1. Copy specification for proto generation
COPY specification /specification

# 2. Build testgenesis-client-java library
COPY implementation/client/testgenesis-client-java /workspace/testgenesis-client-java
WORKDIR /workspace/testgenesis-client-java
RUN mvn install -DskipTests

# 3. Build junit-agent
COPY implementation/client/junit-agent /workspace/junit-agent
WORKDIR /workspace/junit-agent
RUN mvn package -DskipTests

# Stage 2: Runtime
# Using JDK because the agent uses ToolProvider (System Java Compiler)
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Copy the main jar and the dependency libraries
COPY --from=build /workspace/junit-agent/target/junit-agent-1.0.0-SNAPSHOT.jar /app/agent.jar
COPY --from=build /workspace/junit-agent/target/lib /app/lib

# Default environment variables
ENV HUB_URL=http://localhost:9000
ENV CLIENT_NAME=JUnitAgent-Docker
ENV JUNIT_DEPENDENCIES=org.seleniumhq.selenium:selenium-java:4.43.0
ENV SELENIUM_REMOTE_URL=http://localhost:4444/wd/hub

# Create a local maven repository directory for ShrinkWrap
RUN mkdir -p /root/.m2/repository

# Run the agent
CMD ["java", "-jar", "agent.jar"]
