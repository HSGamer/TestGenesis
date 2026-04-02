import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import { JobHub, JobListenRequest, JobListenResponse, JobRegistration } from "uap-node";
import { Empty } from "@bufbuild/protobuf";
import { WebDriver } from "selenium-webdriver";
import { CONFIG } from "./config";
import { JobProcessor } from "./job-processor";

// --- Global State for Graceful Shutdown ---
const activeDrivers = new Set<WebDriver>();
let isShuttingDown = false;

// Initialize the ConnectRPC transport and client for the JobHub service.
const transport = createGrpcTransport({ baseUrl: CONFIG.HUB_URL, httpVersion: "2" });
const client = createClient(JobHub, transport);

/**
 * Main application loop. Establishes the control plane connection (Listen)
 * and maintains it with an exponential backoff strategy upon failure.
 */
async function main() {
  let delay = 1000;
  console.log(`Starting UAP Node Side Agent: ${CONFIG.CLIENT_NAME}`);

  while (!isShuttingDown) {
    try {
      console.log(`[Listen] Connecting to JobHub at ${CONFIG.HUB_URL}...`);

      async function* generateListenRequests(): AsyncGenerator<JobListenRequest> {
        // 1. Send initial registration
        yield new JobListenRequest({
          event: {
            case: "registration",
            value: new JobRegistration({
              displayName: CONFIG.CLIENT_NAME,
              capabilities: [
                { 
                  type: "selenium-side", 
                  payloads: [
                    { type: "selenium-side", isRequired: true, isRepeatable: false }
                  ] 
                }
              ],
            }),
          },
        });

        // 2. Periodic Ready/Heartbeat signal
        while (!isShuttingDown) {
          yield new JobListenRequest({ event: { case: "ready", value: new Empty() } });
          // Heartbeat every 30 seconds
          await new Promise((resolve) => setTimeout(resolve, 30000));
        }
      }

      for await (const directive of client.listen(generateListenRequests())) {
        if (isShuttingDown) break;
        delay = 1000; // Reset backoff on successful communication.

        if (directive.event.case === "runJob") {
          const sessionId = directive.event.value.sessionId;
          console.log(`[Listen] Received Job Assignment: ${sessionId}`);
          
          // Delegate job execution to the modular JobProcessor.
          const processor = new JobProcessor(
            sessionId,
            client,
            (driver) => activeDrivers.add(driver),
            (driver) => activeDrivers.delete(driver)
          );
          processor.process().catch(console.error);
        }
      }
    } catch (err) {
      if (isShuttingDown) break;
      console.error(`[Listen] Connection error: ${err}. Retrying in ${delay}ms...`);
      await new Promise((resolve) => setTimeout(resolve, delay));
      delay = Math.min(delay * 2, 60000);
    }
  }
}

/**
 * Graceful shutdown handler. Ensures all active WebDriver instances are quitted.
 */
async function shutdown() {
  if (isShuttingDown) return;
  isShuttingDown = true;
  console.log("\n[Shutdown] Graceful shutdown initiated...");

  const quitPromises = Array.from(activeDrivers).map(async (driver) => {
    try {
      await driver.quit();
    } catch (err) {}
  });

  await Promise.all(quitPromises);
  activeDrivers.clear();

  console.log("[Shutdown] Cleanup complete. Exiting.");
  process.exit(0);
}

// Register shutdown hooks for system signals.
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

// Entry point.
main().catch(console.error);
