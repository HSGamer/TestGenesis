import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import { AgentHub } from "./generated/AgentHub_connect.js";
import { JobHub } from "./generated/JobHub_connect.js";
import { TranslationHub } from "./generated/TranslationHub_connect.js";
import { ListenRequest } from "./generated/ListenRequest_pb.js";
import { AgentRegistration } from "./generated/AgentRegistration_pb.js";
import { Capability } from "./generated/Capability_pb.js";
import { TranslationCapability } from "./generated/TranslationCapability_pb.js";
import { WebDriver } from "selenium-webdriver";
import { CONFIG } from "./config.js";
import { JobProcessor } from "./job-processor.js";
import { TranslationProcessor } from "./translation-processor.js";
import { createWritableIterable } from "@connectrpc/connect/protocol";

const activeDrivers = new Set<WebDriver>();
let isShuttingDown = false;


// Setup gRPC Clients
const transport = createGrpcTransport({ baseUrl: CONFIG.HUB_URL, httpVersion: "2" });
const agentClient = createClient(AgentHub, transport);
const jobClient = createClient(JobHub, transport);
const translationClient = createClient(TranslationHub, transport);

async function main() {
  console.log(`Starting Simplified Side Agent: ${CONFIG.CLIENT_NAME}`);

  while (!isShuttingDown) {
    try {
      // 1. Identification & Capability Registration
      const registration = await agentClient.register(new AgentRegistration({
        displayName: CONFIG.CLIENT_NAME,
        capabilities: [
          // Test Execution Capability
          new Capability({
            format: {
              case: "test",
              value: {
                type: "selenium-side",
                payloads: [
                  { type: "selenium-side", isRequired: true, acceptedMimeTypes: ["application/json"] },
                  { type: "runtime-env", acceptedMimeTypes: ["application/json"] }
                ]
              }
            }
          }),
          // Translation (SIDE Project Splitter) Capability
          new Capability({
            format: {
              case: "translation",
              value: new TranslationCapability({
                type: "selenium-ide-project-to-test",
                sourcePayloads: [{ type: "selenium-side-project", isRequired: true, acceptedMimeTypes: ["application/octet-stream"] }],
                targetPayloads: [{ type: "selenium-side", isRequired: true, isRepeatable: true, acceptedMimeTypes: ["application/json"] }]
              })
            }
          })
        ],
      }));

      const clientId = registration.clientId;
      console.log(`[Connected] Client ID: ${clientId}`);

      // 2. Control Stream Dispatcher
      const requestIterable = createWritableIterable<ListenRequest>();
      requestIterable.write(new ListenRequest({ event: { case: "ready", value: {} } }));
      const pendingSessions = new Map<string, "job" | "translation">();


      const listenStream = agentClient.listen(requestIterable, { headers: { "x-client-id": clientId } });

      for await (const response of listenStream) {
        const event = response.event;
        
        if (event.case === "jobProposal") {
          console.log(`[Job] Received Proposal: ${event.value.sessionId}`);
          pendingSessions.set(event.value.sessionId, "job");
          requestIterable.write(new ListenRequest({ event: { case: "jobAcceptance", value: { sessionId: event.value.sessionId, accepted: true } } }));

        } else if (event.case === "translationProposal") {
          console.log(`[Translate] Received Proposal: ${event.value.sessionId}`);
          pendingSessions.set(event.value.sessionId, "translation");
          requestIterable.write(new ListenRequest({ event: { case: "translationAcceptance", value: { sessionId: event.value.sessionId, accepted: true } } }));

        } else if (event.case === "sessionReady") {
          const type = pendingSessions.get(event.value.sessionId);
          if (type === "job") {
            pendingSessions.delete(event.value.sessionId);
            console.log(`[Job] Session Sync Verified: ${event.value.sessionId}`);
            new JobProcessor(
              event.value.sessionId, 
              jobClient, 
              d => activeDrivers.add(d), 
              d => activeDrivers.delete(d)
            ).process().catch(console.error);

          } else if (type === "translation") {
            pendingSessions.delete(event.value.sessionId);
            console.log(`[Translate] Session Sync Verified: ${event.value.sessionId}`);
            new TranslationProcessor(event.value.sessionId, translationClient).process().catch(console.error);
          }
        }

      }

    } catch (err) {
      if (isShuttingDown) break;
      console.error("[Fatal] Disconnected. Retrying in 5s...", err);
      await new Promise(r => setTimeout(r, 5000));
    }
  }
}

async function shutdown() {
  if (isShuttingDown) return;
  isShuttingDown = true;
  console.log("\n[Shutdown] Cleaning up...");
  for (const driver of activeDrivers) await driver.quit().catch(() => {});
  setTimeout(() => process.exit(0), 1000);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
main().catch(console.error);
