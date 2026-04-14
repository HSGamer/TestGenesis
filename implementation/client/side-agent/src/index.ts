import {Agent} from "testgenesis-client-node";
import {WebDriver} from "selenium-webdriver";
import {parseArgs} from "node:util";
import {TestProcessor} from "./test-processor.js";
import {TranslationProcessor} from "./translation-processor.js";

/**
 * Encapsulates the application configuration from CLI arguments
 * and environment variables.
 */
const {values} = parseArgs({
    args: process.argv.slice(2),
    options: {
        name: {type: "string", short: "n"},
        url: {type: "string", short: "u"},
        selenium: {type: "string", short: "s"},
    },
});

const CONFIG = {
    HUB_URL: values.url || process.env.HUB_URL || "http://localhost:9000",
    CLIENT_NAME: values.name || process.env.CLIENT_NAME || "SideAgent-" + Math.random().toString(36).substring(7),
    SELENIUM_REMOTE_URL: values.selenium || process.env.SELENIUM_REMOTE_URL,
};

console.log(`[Config] Hub: ${CONFIG.HUB_URL}`);
console.log(`[Config] Name: ${CONFIG.CLIENT_NAME}`);
if (CONFIG.SELENIUM_REMOTE_URL) {
    console.log(`[Config] Selenium: ${CONFIG.SELENIUM_REMOTE_URL}`);
}

const activeDrivers = new Set<WebDriver>();
let isShuttingDown = false;

async function main() {
    console.log(`Starting High-Level Side Agent: ${CONFIG.CLIENT_NAME}`);

    const agent = new Agent({
        hubUrl: CONFIG.HUB_URL,
        displayName: CONFIG.CLIENT_NAME,
    });

    // Register Processors (Framework handles Clients, Streams, and Init)
    agent.registerTestProcessor(new TestProcessor(
        d => activeDrivers.add(d),
        d => activeDrivers.delete(d),
        CONFIG.SELENIUM_REMOTE_URL
    ));

    agent.registerTranslationProcessor(new TranslationProcessor());

    // Handle Shutdown
    async function shutdown() {
        if (isShuttingDown) return;
        isShuttingDown = true;
        console.log("\n[Shutdown] Cleaning up...");
        agent.shutdown();
        for (const driver of activeDrivers) await driver.quit().catch(() => {
        });
        setTimeout(() => process.exit(0), 1000);
    }

    process.on("SIGINT", shutdown);
    process.on("SIGTERM", shutdown);

    // Start Agent
    await agent.start();
}

main().catch(console.error);
