set shell := ["bash", "-c"]

# Project Roots
node_client  := "implementation/client/testgenesis-client-node"
java_client  := "implementation/client/testgenesis-client-java"
cms          := "implementation/server/testgenesis-cms"
side_agent   := "implementation/client/side-agent"
junit_agent  := "implementation/client/selenium-junit-agent"
gen_cli      := "implementation/client/testgenesis-cli"

# Default: Build everything
all: build-cms build-side build-junit

# --- Build Recipes ---

build-cms:
    mvn -f {{cms}} clean compile

build-node-client:
    cd {{node_client}} && npm install && npm run build

build-java-client:
    mvn -f {{java_client}} clean install

build-side: build-node-client
    cd {{side_agent}} && npm install && npm run build

build-junit: build-java-client
    mvn -f {{junit_agent}} clean compile

# --- Run Recipes ---

run-cms:
    mvn -f {{cms}} quarkus:dev

run-side:
    cd {{side_agent}} && npm start

run-junit:
    mvn -f {{junit_agent}} exec:java -Dexec.mainClass="me.hsgamer.testgenesis.agent.selenium.junit.Main"

# --- Generator ---

# Generate a new client project
# Usage: just gen-client <name> [options]
gen-client name *args: build-gen-cli
    cd implementation/client && node testgenesis-cli/dist/index.js {{name}} {{args}}

build-gen-cli:
    cd {{gen_cli}} && npm install && npm run build

# --- Utility ---

clean:
    mvn -f {{cms}} clean
    mvn -f {{java_client}} clean
    mvn -f {{junit_agent}} clean
    rm -rf {{side_agent}}/{dist,node_modules}
    rm -rf {{node_client}}/{dist,node_modules,src/generated}
