# Unified UAP Project Management
set shell := ["bash", "-c"]

# Build everything in order
all: build-cms build-client-node build-side-agent

# Build the common NodeJS client module
build-client-node:
    cd implementation/client/testgenesis-client-node && npm install && npm run build

# Build the common Java client module
build-client-java:
    cd implementation/client/testgenesis-client-java && mvn clean install

# Build the Quarkus CMS (includes internal gRPC generation)
build-cms:
    cd implementation/server/testgenesis-cms && mvn clean compile

# Build the Side Agent (depends on build-client-node)
build-side-agent: build-client-node
    cd implementation/client/side-agent && npm install && npm run build

# Run the Side Agent in development mode
run-side-agent:
    cd implementation/client/side-agent && npm run start

# Run the Quarkus CMS in development mode
run-cms:
    cd implementation/server/testgenesis-cms && mvn quarkus:dev

# Clean all generated build artifacts
clean:
    cd implementation/server/testgenesis-cms && mvn clean
    rm -rf implementation/client/side-agent/dist
    rm -rf implementation/client/side-agent/node_modules
    rm -rf implementation/client/testgenesis-client-node/dist
    rm -rf implementation/client/testgenesis-client-node/node_modules
    rm -rf implementation/client/testgenesis-client-node/src/generated
