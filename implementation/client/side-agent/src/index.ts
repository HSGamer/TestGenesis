import { Agent } from "testgenesis-client-node";
import { WebDriver } from "selenium-webdriver";
import { CONFIG } from "./config.js";
import { TestProcessor } from "./test-processor.js";
import { TranslationProcessor } from "./translation-processor.js";

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
    d => activeDrivers.delete(d)
  ));

  agent.registerTranslationProcessor(new TranslationProcessor());

  // Handle Shutdown
  async function shutdown() {
    if (isShuttingDown) return;
    isShuttingDown = true;
    console.log("\n[Shutdown] Cleaning up...");
    agent.shutdown();
    for (const driver of activeDrivers) await driver.quit().catch(() => {});
    setTimeout(() => process.exit(0), 1000);
  }

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);

  // Start Agent
  await agent.start();
}

main().catch(console.error);
