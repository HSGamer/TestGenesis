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
        mvn -f {{client_root}}/{{name}}/pom.xml clean compile; \
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

VPS_FILE := ".vps"

# --- Deployment Recipes ---

# Deploy to a VPS via SSH (Sync & Build)
# Usage: just deploy [remote_path] [user@host]
# Create a versioned deployment bundle (ZIP)
bundle:
    #!/usr/bin/env bash
    VERSION=$(grep -m1 '<version>' implementation/server/testgenesis-cms/pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
    OUTPUT_FILE="testgenesis-v${VERSION}.zip"
    echo "[Bundle] Creating v${VERSION} into ${OUTPUT_FILE}..."
    rm -f testgenesis-v*.zip
    git ls-files --cached --others --exclude-standard | zip "$OUTPUT_FILE" -@
    echo "[Bundle] Done! Created ${OUTPUT_FILE}"

# Deploy to a VPS via SSH (Sync & Build)
# Usage: just deploy [remote_path] [user@host]
deploy path="~/TestGenesis" host="":
    #!/usr/bin/env bash
    VPS_FILE=".vps"
    REMOTE_PATH="{{path}}"
    HOST="{{host}}"
    if [ -z "$HOST" ] && [ -f "$VPS_FILE" ]; then HOST=$(head -n 1 "$VPS_FILE"); fi
    if [ -z "$HOST" ]; then HOST="host@ip"; fi
    
    SSH_CMD="ssh"
    RSYNC_OPTS="-avz --delete"
    if [ -f "$VPS_FILE" ]; then
        export SSHPASS=$(tail -n 1 "$VPS_FILE")
        SSH_CMD="sshpass -e ssh"
        RSYNC_OPTS="$RSYNC_OPTS -e 'sshpass -e ssh'"
    fi

    echo "[Deploy] Syncing files to $HOST:$REMOTE_PATH..."
    eval "rsync $RSYNC_OPTS --exclude-from='.dockerignore' ./ \"$HOST:$REMOTE_PATH\""
    echo "[Deploy] Building and starting services on remote..."
    $SSH_CMD "$HOST" "cd $REMOTE_PATH && docker compose up --build -d --remove-orphans"

# Deploy via ZIP bundle (Upload -> Unzip -> Build)
# Usage: just deploy-zip [remote_path] [user@host]
deploy-zip path="~/TestGenesis" host="": bundle
    #!/usr/bin/env bash
    VPS_FILE=".vps"
    REMOTE_PATH="{{path}}"
    HOST="{{host}}"
    if [ -z "$HOST" ] && [ -f "$VPS_FILE" ]; then HOST=$(head -n 1 "$VPS_FILE"); fi
    if [ -z "$HOST" ]; then HOST="host@ip"; fi

    SSH_CMD="ssh"
    SCP_CMD="scp"
    if [ -f "$VPS_FILE" ]; then
        export SSHPASS=$(tail -n 1 "$VPS_FILE")
        SSH_CMD="sshpass -e ssh"
        SCP_CMD="sshpass -e scp"
    fi

    BUNDLE=$(ls testgenesis-*.zip 2>/dev/null | sort -V | tail -n 1)
    if [ -z "$BUNDLE" ]; then echo "Error: No bundle found."; exit 1; fi

    echo "[Deploy] Uploading bundle $BUNDLE to $HOST..."
    $SCP_CMD "$BUNDLE" "$HOST:$REMOTE_PATH.zip"
    echo "[Deploy] Extracting and starting on remote..."
    $SSH_CMD "$HOST" "mkdir -p $REMOTE_PATH && unzip -o $REMOTE_PATH.zip -d $REMOTE_PATH && cd $REMOTE_PATH && docker compose up --build -d --remove-orphans"

# Configure a remote Docker context (Alternative method)
# Usage: just setup-remote-context <name> <ssh-url>
setup-remote-context name url:
    docker context create {{name}} --docker "host={{url}}"
    @echo "Success! Use 'docker --context {{name}} compose up --build -d' to deploy."
