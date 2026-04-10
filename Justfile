# Unified UAP Project Management
set shell := ["bash", "-c"]

# Build and install everything in order
all: build-java build-node-api build-side-agent

# Build and install the Java API library
build-java:
    cd api/java && mvn clean install

# Build and install the Node.js API library
build-node-api:
    cd api/node && npm install && npm run build

# Build the Side Agent (depends on Node API)
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
    cd api/java && mvn clean
    rm -rf api/node/dist api/node/node_modules
    rm -rf implementation/client/side-agent/dist implementation/client/side-agent/node_modules
