import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import { AgentHub, ListenRequest, AgentRegistration, Capability, JobHub } from "uap-node";
import { Empty } from "@bufbuild/protobuf";
import { WebDriver } from "selenium-webdriver";
import { CONFIG } from "./config";
import { JobProcessor } from "./job-processor";

// --- Global State for Graceful Shutdown ---
const activeDrivers = new Set<WebDriver>();
let isShuttingDown = false;

// Initialize clients for both the Control Plane (AgentHub) and Execution Plane (JobHub).
const transport = createGrpcTransport({ baseUrl: CONFIG.HUB_URL, httpVersion: "2" });
const agentClient = createClient(AgentHub, transport);
const jobClient = createClient(JobHub, transport);

/**
 * Main application loop. Establishes the unified control plane connection (Listen)
 * and maintains it with an exponential backoff strategy upon failure.
 */
async function main() {
  let delay = 1000;
  console.log(`Starting UAP Node Side Agent: ${CONFIG.CLIENT_NAME}`);

  while (!isShuttingDown) {
    try {
      // 1. Unary registration phase to obtain a session identity.
      console.log(`[Register] Connecting to AgentHub at ${CONFIG.HUB_URL}...`);
      const registration = await agentClient.register(new AgentRegistration({
        displayName: CONFIG.CLIENT_NAME,
        capabilities: [
          new Capability({
            format: {
              case: "test",
              value: {
                type: "selenium-side",
                payloads: [
                  { type: "selenium-side", isRequired: true, isRepeatable: false }
                ]
              }
            }
          })
        ],
      }));

      const clientId = registration.clientId;
      console.log(`[Register] Success! Assigned Client ID: ${clientId}`);

      // 2. Control plane connection (Listen) using the session Identity in headers.
      console.log(`[Listen] Establishing control stream...`);
      const requestQueue: ListenRequest[] = [];

      async function* generateListenRequests(): AsyncGenerator<ListenRequest> {
        while (!isShuttingDown) {
          if (requestQueue.length > 0) {
            yield requestQueue.shift()!;
          } else {
            // No more pulsing; we just wait for queue items.
            // A short delay to prevent spinning, but ideally we'd use a Trigger/Deferred.
            await new Promise((resolve) => setTimeout(resolve, 1000));
          }
        }
      }

      // We pass the clientId in the headers for session identification.
      const listenStream = agentClient.listen(generateListenRequests(), { 
        headers: { "x-client-id": clientId } 
      });

      for await (const response of listenStream) {
        if (isShuttingDown || !listenStream) break;
        delay = 1000;

        if (response.event.case === "jobProposal") {
          const proposal = response.event.value;
          console.log(`[Listen] Received Job Proposal: ${proposal.sessionId} (${proposal.testType})`);
          
          requestQueue.push(new ListenRequest({
            event: {
              case: "jobAcceptance",
              value: { sessionId: proposal.sessionId, accepted: true }
            }
          }));

          const processor = new JobProcessor(
            proposal.sessionId,
            jobClient,
            (driver) => activeDrivers.add(driver),
            (driver) => activeDrivers.delete(driver)
          );
          processor.process().catch(console.error);
        }
      }

      // If the Hub closes the stream, we assume a shutdown is requested.
      if (!isShuttingDown) {
        console.log("[Listen] Stream closed by Hub. Signaling shutdown.");
        await shutdown();
      }
    } catch (err) {
      if (isShuttingDown) break;
      console.error(`[Handshake] Error: ${err}. Retrying in ${delay}ms...`);
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
