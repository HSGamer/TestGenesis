# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1. Copy specification for proto generation (shared across Java components)
COPY specification /specification

# 2. Pre-cache testgenesis-client-java dependencies
COPY implementation/client/testgenesis-client-java/pom.xml /workspace/testgenesis-client-java/
WORKDIR /workspace/testgenesis-client-java
RUN mvn dependency:go-offline -B || true

# 3. Build testgenesis-client-java library
COPY implementation/client/testgenesis-client-java/src /workspace/testgenesis-client-java/src
RUN mvn install -DskipTests

# 4. Pre-cache selenium-junit-agent dependencies
WORKDIR /workspace/selenium-junit-agent
COPY implementation/client/selenium-junit-agent/pom.xml /workspace/selenium-junit-agent/
RUN mvn dependency:go-offline -B || true

# 5. Build selenium-junit-agent
COPY implementation/client/selenium-junit-agent/src /workspace/selenium-junit-agent/src
RUN mvn package -DskipTests

# Stage 2: Runtime
# Using JDK instead of JRE because the agent contains compiler code
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy the main jar and the dependency libraries
COPY --from=build /workspace/selenium-junit-agent/target/selenium-junit-agent-1.0.0-SNAPSHOT.jar /app/agent.jar
COPY --from=build /workspace/selenium-junit-agent/target/lib /app/lib

# Default environment variables
ENV HUB_URL=http://cms:9000
ENV CLIENT_NAME=SeleniumJUnitAgent-Docker
ENV SELENIUM_REMOTE_URL=http://selenium-hub:4444/wd/hub

# Run the agent using its pre-configured manifest classpath
CMD ["java", "-jar", "agent.jar"]
