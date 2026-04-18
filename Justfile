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

run-cms:
    mvn -f {{cms}} quarkus:dev

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
    rm -rf .docker/

# --- Deployment Configuration ---

HOST_FILE := "host_file.txt"
PASS_FILE := "pass_file.txt"
SSH_HOST := `if [ -f {{HOST_FILE}} ]; then cat {{HOST_FILE}}; else echo "host@ip"; fi`
SSH := `if [ -f {{PASS_FILE}} ]; then echo "sshpass -f {{PASS_FILE}} ssh"; else echo "ssh"; fi`
SCP := `if [ -f {{PASS_FILE}} ]; then echo "sshpass -f {{PASS_FILE}} scp"; else echo "scp"; fi`
RSYNC := `if [ -f {{PASS_FILE}} ]; then echo "rsync -avz --delete -e 'sshpass -f {{PASS_FILE}} ssh'"; else echo "rsync -avz --delete"; fi`

# --- Deployment Recipes ---

# Deploy to a VPS via SSH
# Usage: just deploy [user@host] [remote_path]
deploy host=SSH_HOST path="~/TestGenesis":
    @echo "[Deploy] Syncing files to {{host}}:{{path}}..."
    {{RSYNC}} --exclude-from='.dockerignore' ./ {{host}}:{{path}}
    @echo "[Deploy] Building and starting services on remote..."
    {{SSH}} {{host}} "cd {{path}} && docker compose up --build -d"

# Configure a remote Docker context (Alternative method)
# Usage: just setup-remote-context <name> <ssh-url>
setup-remote-context name url:
    docker context create {{name}} --docker "host={{url}}"
    @echo "Success! Use 'docker --context {{name}} compose up --build -d' to deploy."

# Create a versioned deployment bundle (ZIP)
bundle:
    bash bundle.sh

# Deploy via ZIP bundle (Upload -> Unzip -> Build)
# Usage: just deploy-zip [user@host] [remote_path]
deploy-zip host=SSH_HOST path="~/TestGenesis": bundle
    @echo "[Deploy] Uploading bundle to {{host}}..."
    @BUNDLE=$$(ls testgenesis-*.zip | sort -V | tail -n 1); \
    {{SCP}} $$BUNDLE {{host}}:{{path}}.zip; \
    @echo "[Deploy] Extracting and starting on remote..."
    {{SSH}} {{host}} "mkdir -p {{path}} && unzip -o {{path}}.zip -d {{path}} && cd {{path}} && docker compose up --build -d"
