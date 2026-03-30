# UAP Node.js Implementation Tutorial

This tutorial shows how to implement both the **Hub (Server)** and the **Agent (Client)** in Node.js/TypeScript using the `uap-node` library with ConnectRPC.

## Prerequisites

- Node.js 25+
- The `uap-node` library built via `npm run build`

Install the library as a local dependency in your project:

```bash
npm install ../api/node
```

You also need a ConnectRPC transport for Node.js:

```bash
npm install @connectrpc/connect @connectrpc/connect-node @bufbuild/protobuf
```

---

## Part 1: Implementing the Hub (Server)

The Hub is the **server**. It manages agent registration, dispatches work assignments, and handles execution streams.

### 1.1 — Service Implementation

Create a file `hub.ts` that implements the `UniversalHub` service:

```typescript
import { ConnectRouter } from "@connectrpc/connect";
import { UniversalHub } from "uap-node";
import {
  RegistrationRequest,
  RegistrationResponse,
  AgentIdentity,
  HubDirective,
  ExecuteJobMessage,
  JobInstruction,
  TranslateMessage,
  TranslationInstruction,
  JobAssignment,
  JobRequest,
  Payload,
  TranslationInit,
} from "uap-node";
import { Timestamp } from "@bufbuild/protobuf";
import { randomUUID } from "crypto";

// In-memory state
const agentStreams = new Map<string, (directive: HubDirective) => void>();
const pendingJobs = new Map<string, JobRequest>();

export default (router: ConnectRouter) =>
  router.service(UniversalHub, {

    // ─── Control Plane ──────────────────────────────────────────

    async register(request: RegistrationRequest): Promise<RegistrationResponse> {
      const agentId = randomUUID();

      console.log(`Agent registered: '${request.displayName}' (id: ${agentId})`);
      console.log(`  Capabilities: ${request.capabilities?.items.length ?? 0} items`);

      return new RegistrationResponse({
        agentId,
        serverTime: Timestamp.now(),
      });
    },

    async *listen(request: AgentIdentity): AsyncGenerator<HubDirective> {
      const agentId = request.agentId;
      console.log(`Agent listening: ${agentId}`);

      // Create a queue-based approach for pushing directives
      const queue: HubDirective[] = [];
      let resolve: (() => void) | null = null;

      // Store a push function so other parts of the code can dispatch work
      agentStreams.set(agentId, (directive: HubDirective) => {
        queue.push(directive);
        if (resolve) {
          resolve();
          resolve = null;
        }
      });

      try {
        // Continuously yield directives as they arrive
        while (true) {
          if (queue.length > 0) {
            yield queue.shift()!;
          } else {
            // Wait for a new directive to be pushed
            await new Promise<void>((r) => { resolve = r; });
          }
        }
      } finally {
        agentStreams.delete(agentId);
        console.log(`Agent disconnected: ${agentId}`);
      }
    },

    // ─── Execution Plane: Job ───────────────────────────────────

    async *executeJob(
      requests: AsyncIterable<ExecuteJobMessage>
    ): AsyncGenerator<JobInstruction> {
      let sessionId = "";

      for await (const message of requests) {
        switch (message.content.case) {
          case "sessionId": {
            // First message: correlate with the pending job
            sessionId = message.content.value;
            console.log(`Job session started: ${sessionId}`);

            const jobRequest = pendingJobs.get(sessionId);
            pendingJobs.delete(sessionId);

            if (jobRequest) {
              yield new JobInstruction({
                content: { case: "jobInit", value: jobRequest },
              });
            }
            break;
          }

          case "update": {
            // Subsequent messages: status updates, telemetry, results
            const update = message.content.value;
            switch (update.response.case) {
              case "status":
                console.log(
                  `[${sessionId}] Status: ${update.response.value.state} - ${update.response.value.message}`
                );
                break;
              case "telemetry":
                console.log(
                  `[${sessionId}] Telemetry: ${update.response.value.message}`
                );
                break;
              case "result":
                console.log(
                  `[${sessionId}] Result: ${update.response.value.status?.state} (${update.response.value.steps.length} steps)`
                );
                break;
            }
            break;
          }
        }
      }

      console.log(`[${sessionId}] Job session closed`);
    },

    // ─── Execution Plane: Translation ───────────────────────────

    async *translate(
      requests: AsyncIterable<TranslateMessage>
    ): AsyncGenerator<TranslationInstruction> {
      let sessionId = "";

      for await (const message of requests) {
        switch (message.content.case) {
          case "sessionId": {
            sessionId = message.content.value;
            console.log(`Translation session started: ${sessionId}`);

            yield new TranslationInstruction({
              content: {
                case: "translationInit",
                value: new TranslationInit({
                  targetFormat: "playwright-js",
                  payload: new Payload({
                    type: "selenium-side",
                    content: {
                      case: "rawData",
                      value: new TextEncoder().encode("..."),
                    },
                  }),
                }),
              },
            });
            break;
          }

          case "update": {
            const update = message.content.value;
            switch (update.response.case) {
              case "status":
                console.log(
                  `[${sessionId}] Translation Status: ${update.response.value.state}`
                );
                break;
              case "telemetry":
                console.log(
                  `[${sessionId}] Translation Log: ${update.response.value.message}`
                );
                break;
              case "result":
                console.log(
                  `[${sessionId}] Translation Complete: ${update.response.value.status?.state}`
                );
                break;
            }
            break;
          }
        }
      }

      console.log(`[${sessionId}] Translation session closed`);
    },
  });

// ─── Helper: Dispatch a job to an agent ─────────────────────────

export function dispatchJob(agentId: string, jobRequest: JobRequest): void {
  const push = agentStreams.get(agentId);
  if (!push) {
    console.error(`Agent ${agentId} is not connected`);
    return;
  }

  const sessionId = randomUUID();
  pendingJobs.set(sessionId, jobRequest);

  push(
    new HubDirective({
      instruction: {
        case: "runJob",
        value: new JobAssignment({ sessionId }),
      },
    })
  );

  console.log(`Dispatched job ${sessionId} to agent ${agentId}`);
}
```

### 1.2 — Starting the Server

```typescript
import { fastify } from "fastify";
import { fastifyConnectPlugin } from "@connectrpc/connect-fastify";
import routes from "./hub.js";

async function main() {
  const server = fastify({ http2: true });

  await server.register(fastifyConnectPlugin, { routes });

  await server.listen({ host: "0.0.0.0", port: 9090 });
  console.log("Hub server started on port 9090");
}

main();
```

> **Note**: You can also use the built-in `http2` module from Node.js with `@connectrpc/connect-node` instead of Fastify. See the [ConnectRPC Node.js docs](https://connectrpc.com/docs/node/getting-started) for alternatives.

#### Alternative: Using the built-in Node.js HTTP/2 server

```typescript
import http2 from "http2";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import routes from "./hub.js";

const handler = connectNodeAdapter({ routes });

const server = http2.createServer(handler);
server.listen(9090, () => {
  console.log("Hub server started on port 9090");
});
```

---

## Part 2: Implementing the Agent (Client)

The Agent is the **client**. It connects to the Hub, registers, listens for directives, and opens execution streams when assigned work.

### 2.1 — Agent Implementation

Create a file `agent.ts`:

```typescript
import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import { UniversalHub } from "uap-node";
import {
  RegistrationRequest,
  AgentIdentity,
  Capabilities,
  Capability,
  TestCapability,
  ExecuteJobMessage,
  JobResponse,
  JobStatus,
  JobStatus_State,
  JobResult,
  StepReport,
  Telemetry,
  TranslateMessage,
  TranslationResponse,
  TranslationStatus,
  TranslationStatus_State,
  TranslationResult,
  Payload,
} from "uap-node";
import { Timestamp } from "@bufbuild/protobuf";

async function main() {
  // Connect to the Hub
  const transport = createGrpcTransport({
    baseUrl: "http://localhost:9090",
    httpVersion: "2",
  });

  const client = createClient(UniversalHub, transport);

  // ─── Step 1: Register ─────────────────────────────────────────

  const registration = await client.register(
    new RegistrationRequest({
      displayName: "Selenium Agent - Dev Machine",
      capabilities: new Capabilities({
        items: [
          new Capability({
            id: "selenium-side-runner",
            type: {
              case: "test",
              value: new TestCapability({ testType: "selenium-side" }),
            },
          }),
        ],
      }),
    })
  );

  const agentId = registration.agentId;
  console.log(`Registered with agent_id: ${agentId}`);

  // ─── Step 2: Listen for directives ────────────────────────────

  console.log("Listening for directives...");

  for await (const directive of client.listen(
    new AgentIdentity({ agentId })
  )) {
    switch (directive.instruction.case) {
      case "runJob": {
        const sessionId = directive.instruction.value.sessionId;
        console.log(`Received job assignment: ${sessionId}`);
        // Run on a separate async context
        executeJob(client, sessionId);
        break;
      }

      case "startTranslation": {
        const sessionId = directive.instruction.value.sessionId;
        console.log(`Received translation assignment: ${sessionId}`);
        executeTranslation(client, sessionId);
        break;
      }

      case "heartbeatRequest": {
        console.log("Heartbeat received");
        break;
      }
    }
  }
}

// ─── Job Execution ──────────────────────────────────────────────

async function executeJob(
  client: ReturnType<typeof createClient<typeof UniversalHub>>,
  sessionId: string
): Promise<void> {
  // Create an async generator that yields ExecuteJobMessages
  async function* sendMessages(): AsyncGenerator<ExecuteJobMessage> {
    // Step 1: Send session_id
    yield new ExecuteJobMessage({
      content: { case: "sessionId", value: sessionId },
    });

    // Step 2: ACKNOWLEDGED
    yield new ExecuteJobMessage({
      content: {
        case: "update",
        value: new JobResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "status",
            value: new JobStatus({
              state: JobStatus_State.ACKNOWLEDGED,
              message: "Job accepted",
            }),
          },
        }),
      },
    });

    // Step 3: EXECUTING
    yield new ExecuteJobMessage({
      content: {
        case: "update",
        value: new JobResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "status",
            value: new JobStatus({
              state: JobStatus_State.EXECUTING,
              message: "Running tests...",
            }),
          },
        }),
      },
    });

    // Step 4: Telemetry
    yield new ExecuteJobMessage({
      content: {
        case: "update",
        value: new JobResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "telemetry",
            value: new Telemetry({
              message: "Opened browser: Chrome 130",
            }),
          },
        }),
      },
    });

    yield new ExecuteJobMessage({
      content: {
        case: "update",
        value: new JobResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "telemetry",
            value: new Telemetry({
              message: "Navigated to https://example.com/login",
            }),
          },
        }),
      },
    });

    // Step 5: COMPLETED
    yield new ExecuteJobMessage({
      content: {
        case: "update",
        value: new JobResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "status",
            value: new JobStatus({
              state: JobStatus_State.COMPLETED,
              message: "All tests passed",
            }),
          },
        }),
      },
    });

    // Step 6: Final result
    yield new ExecuteJobMessage({
      content: {
        case: "update",
        value: new JobResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "result",
            value: new JobResult({
              status: new JobStatus({
                state: JobStatus_State.COMPLETED,
                message: "All tests passed",
              }),
              steps: [
                new StepReport({
                  name: "open /login",
                  status: new JobStatus({ state: JobStatus_State.COMPLETED }),
                }),
                new StepReport({
                  name: "click #submit",
                  status: new JobStatus({ state: JobStatus_State.COMPLETED }),
                }),
              ],
              logs: ["Test suite completed in 2.3s"],
            }),
          },
        }),
      },
    });
  }

  // Open the bidi stream — send messages and process Hub instructions
  for await (const instruction of client.executeJob(sendMessages())) {
    switch (instruction.content.case) {
      case "jobInit":
        console.log(
          `[${sessionId}] Received job: payload type = ${instruction.content.value.payload?.type}`
        );
        break;
      case "command":
        console.log(
          `[${sessionId}] Received command: ${instruction.content.value.command.case}`
        );
        break;
    }
  }

  console.log(`[${sessionId}] Job session closed`);
}

// ─── Translation Execution ──────────────────────────────────────

async function executeTranslation(
  client: ReturnType<typeof createClient<typeof UniversalHub>>,
  sessionId: string
): Promise<void> {
  async function* sendMessages(): AsyncGenerator<TranslateMessage> {
    // Step 1: Send session_id
    yield new TranslateMessage({
      content: { case: "sessionId", value: sessionId },
    });

    // Step 2: ACKNOWLEDGED
    yield new TranslateMessage({
      content: {
        case: "update",
        value: new TranslationResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "status",
            value: new TranslationStatus({
              state: TranslationStatus_State.ACKNOWLEDGED,
              message: "Translation accepted",
            }),
          },
        }),
      },
    });

    // Step 3: PROCESSING
    yield new TranslateMessage({
      content: {
        case: "update",
        value: new TranslationResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "status",
            value: new TranslationStatus({
              state: TranslationStatus_State.PROCESSING,
              message: "Converting 5 test cases...",
            }),
          },
        }),
      },
    });

    // Step 4: COMPLETED + Result
    yield new TranslateMessage({
      content: {
        case: "update",
        value: new TranslationResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "status",
            value: new TranslationStatus({
              state: TranslationStatus_State.COMPLETED,
            }),
          },
        }),
      },
    });

    yield new TranslateMessage({
      content: {
        case: "update",
        value: new TranslationResponse({
          timestamp: Timestamp.now(),
          response: {
            case: "result",
            value: new TranslationResult({
              status: new TranslationStatus({
                state: TranslationStatus_State.COMPLETED,
              }),
              translatedPayload: new Payload({
                type: "playwright-js",
                content: {
                  case: "rawData",
                  value: new TextEncoder().encode(
                    "const { test } = require('@playwright/test');"
                  ),
                },
              }),
              translationLog: "Converted 5/5 test cases. 0 warnings.",
            }),
          },
        }),
      },
    });
  }

  for await (const instruction of client.translate(sendMessages())) {
    switch (instruction.content.case) {
      case "translationInit":
        console.log(
          `[${sessionId}] Translate: ${instruction.content.value.payload?.type} -> ${instruction.content.value.targetFormat}`
        );
        break;
      case "command":
        console.log(
          `[${sessionId}] Translation command: ${instruction.content.value.command.case}`
        );
        break;
    }
  }

  console.log(`[${sessionId}] Translation session closed`);
}

// ─── Entry point ────────────────────────────────────────────────

main().catch(console.error);
```

---

## Part 3: Handling the `stop` and `help` Commands

Every UAP Agent **MUST** implement the `help` and `stop` commands. Here is how to handle them inside the instruction loop:

```typescript
for await (const instruction of client.executeJob(sendMessages())) {
  switch (instruction.content.case) {
    case "jobInit":
      // ... handle initialization
      break;

    case "command": {
      const cmd = instruction.content.value;
      switch (cmd.command.case) {
        case "help":
          // Respond with a Telemetry message listing available commands.
          // In a real implementation, you would push this into the
          // sendMessages generator via a shared queue.
          console.log("Help requested — sending command list");
          break;

        case "stop":
          // Gracefully terminate: push FAILED status + partial result
          // into the sendMessages generator, then return.
          console.log("Stop requested — shutting down gracefully");
          return;

        case "custom":
          console.log(`Custom command: ${cmd.command.value}`);
          break;
      }
      break;
    }
  }
}
```

> **Note**: In a production implementation, you would use a shared queue (e.g., an `AsyncQueue`) between the `sendMessages` generator and the instruction handler. This allows the command handler to push response messages (like help output or a stop acknowledgment) into the outbound stream dynamically.

---

## Part 4: Reconnection Strategy

If the `Listen` stream disconnects, the Agent should reconnect with exponential backoff:

```typescript
async function listenWithBackoff(
  client: ReturnType<typeof createClient<typeof UniversalHub>>,
  agentId: string
): Promise<void> {
  let delay = 1000; // Initial delay: 1 second
  const maxDelay = 60000; // Maximum delay: 60 seconds

  while (true) {
    try {
      for await (const directive of client.listen(
        new AgentIdentity({ agentId })
      )) {
        delay = 1000; // Reset delay on successful message

        switch (directive.instruction.case) {
          case "runJob":
            executeJob(client, directive.instruction.value.sessionId);
            break;
          case "startTranslation":
            executeTranslation(client, directive.instruction.value.sessionId);
            break;
          case "heartbeatRequest":
            console.log("Heartbeat received");
            break;
        }
      }
    } catch (err) {
      console.error(
        `Listen stream disconnected: ${err}. Reconnecting in ${delay}ms...`
      );
      await new Promise((resolve) => setTimeout(resolve, delay));
      delay = Math.min(delay * 2, maxDelay); // Exponential backoff with cap
    }
  }
}
```
