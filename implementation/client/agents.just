# Generated Agents Justfile - DO NOT EDIT MANUALLY

build-manual-java-agent:
    just build-client manual-java-agent

run-manual-java-agent *args:
    just run-client manual-java-agent {{args}}

build-puppeteer-replay-agent:
    just build-client puppeteer-replay-agent

run-puppeteer-replay-agent *args:
    just run-client puppeteer-replay-agent {{args}}

build-selenium-junit-agent:
    just build-client selenium-junit-agent

run-selenium-junit-agent *args:
    just run-client selenium-junit-agent {{args}}

build-side-agent:
    just build-client side-agent

run-side-agent *args:
    just run-client side-agent {{args}}

