import {Agent} from "testgenesis-client-node";
import {parseArgs} from "node:util";
import {ExampleProcessor} from "./processor.js";

/**
 * Encapsulates the application configuration from CLI arguments
 * and environment variables.
 */
const {values} = parseArgs({
    args: process.argv.slice(2),
    options: {
        name: {type: "string", short: "n"},
        url: {type: "string", short: "u"},
    },
});

const CONFIG = {
    HUB_URL: values.url || process.env.HUB_URL || "http://localhost:9000",
    CLIENT_NAME: values.name || process.env.CLIENT_NAME || "__name__-" + Math.random().toString(36).substring(7),
};

console.log(`[__name__] Hub: ${CONFIG.HUB_URL}`);
console.log(`[__name__] Name: ${CONFIG.CLIENT_NAME}`);

async function main() {
    const agent = new Agent({
        hubUrl: CONFIG.HUB_URL,
        displayName: CONFIG.CLIENT_NAME,
    });

    // Register Processors
    agent.registerTestProcessor(new ExampleProcessor());

    // Handle Shutdown
    async function shutdown() {
        console.log("\n[Shutdown] Cleaning up...");
        agent.shutdown();
        setTimeout(() => process.exit(0), 1000);
    }

    process.on("SIGINT", shutdown);
    process.on("SIGTERM", shutdown);

    // Start Agent
    await agent.start();
}

main().catch(console.error);
