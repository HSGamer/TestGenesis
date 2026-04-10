# Unified UAP Project Management
set shell := ["bash", "-c"]

# Build everything in order
all: build-cms build-side-agent

# Build the Quarkus CMS (includes internal gRPC generation)
build-cms:
    cd implementation/server/testgenesis-cms && mvn clean compile

# Build the Side Agent (includes internal gRPC generation)
build-side-agent:
    cd implementation/client/side-agent && npm install && npm run build

# Run the Side Agent in development mode
run-side-agent:
    cd implementation/client/side-agent && npm run dev

# Run the Quarkus CMS in development mode
run-cms:
    cd implementation/server/testgenesis-cms && mvn quarkus:dev

# Clean all generated build artifacts
clean:
    cd implementation/server/testgenesis-cms && mvn clean
    rm -rf implementation/client/side-agent/dist 
    rm -rf implementation/client/side-agent/node_modules
    rm -rf implementation/client/side-agent/src/generated
