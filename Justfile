import? 'implementation/client/agents.just'

set shell := ["bash", "-c"]

# Project Roots
node_client  := "implementation/client/testgenesis-client-node"
java_client  := "implementation/client/testgenesis-client-java"
cms          := "implementation/server/testgenesis-cms"
gen_cli      := "implementation/client/testgenesis-cli"
client_root  := "implementation/client"

# Discovery: List all agent directories (excludes SDKs and CLI)
clients := `ls implementation/client | grep -vE "^testgenesis-client-|testgenesis-cli|^java-verification-agent|final-agent" | tr '\n' ' '`

# Default Build
all: refresh-agents build-cms build-sdk build-all-clients

# --- Build Recipes ---

build-cms:
    mvn -f {{cms}} clean compile

build-node-client:
    cd {{node_client}} && npm install && npm run build

build-java-client:
    mvn -f {{java_client}} clean install

build-sdk: build-node-client build-java-client

build-gen-cli:
    cd {{gen_cli}} && npm install && npm run build

# --- Generic Client Recipes ---

# List discovered agent clients
list-clients:
    @echo "Discovered Agents:"
    @printf "  - %s\n" {{clients}}

# Build a specific client by name
build-client name:
    @echo "[Just] Building client: {{name}}"
    @if [ -f "{{client_root}}/{{name}}/package.json" ]; then \
        if [ "{{name}}" != "testgenesis-client-node" ]; then \
            just build-node-client; \
        fi; \
        cd {{client_root}}/{{name}} && npm install && npm run build; \
    elif [ -f "{{client_root}}/{{name}}/pom.xml" ]; then \
        just build-java-client; \
        mvn -f {{client_root}}/{{name}}/pom.xml compile; \
    else \
        echo "Error: Unsupported project type in {{name}}"; exit 1; \
    fi

# Build all discovered clients
build-all-clients:
    @printf "%s\n" {{clients}} | xargs -I {} just build-client {}

# Run a specific client
# Usage: just run-client <name> [args...]
run-client name *args:
    @echo "[Just] Running client: {{name}}"
    @if [ -f "{{client_root}}/{{name}}/package.json" ]; then \
        cd {{client_root}}/{{name}} && npm start -- {{args}}; \
    elif [ -f "{{client_root}}/{{name}}/pom.xml" ]; then \
        mvn -f {{client_root}}/{{name}}/pom.xml exec:java -Dexec.args="{{args}}"; \
    else \
        echo "Error: Unsupported project type in {{name}}"; exit 1; \
    fi

# --- Generator & Maintenance ---

# Generate a new client project
# Usage: just gen-client <name> [options]
gen-client name *args: build-gen-cli
    cd {{client_root}} && node testgenesis-cli/dist/index.js {{name}} {{args}}
    just refresh-agents

# Refresh dynamic agent recipes for tab completion
refresh-agents: build-gen-cli
    cd {{client_root}} && node testgenesis-cli/dist/index.js --refresh

# --- Utility ---

clean:
    mvn -f {{cms}} clean
    mvn -f {{java_client}} clean
    @for client in {{clients}}; do \
        if [ -f "{{client_root}}/$$client/pom.xml" ]; then \
            mvn -f {{client_root}}/$$client clean; \
        elif [ -f "{{client_root}}/$$client/package.json" ]; then \
            rm -rf {{client_root}}/$$client/{dist,node_modules}; \
        fi; \
    done
    rm -rf {{node_client}}/{dist,node_modules,src/generated}
