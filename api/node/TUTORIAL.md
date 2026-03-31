# UAP Node.js Implementation Tutorial (Split-Service)

This tutorial shows how to implement the **JobHub** and **TranslationHub** in Node.js/TypeScript using ConnectRPC.

## Prerequisites

- Node.js 25+
- The `uap-node` library built via `npm run build`

---

## Part 1: Implementing the Hub (Server)

The Hub services are split into `JobHub` and `TranslationHub`.

### 1.1 — JobHub Implementation

```typescript
import { ConnectRouter } from "@connectrpc/connect";
import { JobHub } from "uap-node";
import { JobListenRequest, JobListenResponse, RegistrationResponse, JobAssignment } from "uap-node";
import { Timestamp } from "@bufbuild/protobuf";
import { randomUUID } from "crypto";

export default (router: ConnectRouter) =>
  router.service(JobHub, {
    async *listen(request: JobListenRequest): AsyncGenerator<JobListenResponse> {
      const agentId = randomUUID();
      console.log(`Job Agent registered: ${request.displayName} (id: ${agentId})`);

      // 1. Send RegistrationResponse as the first message
      yield new JobListenResponse({
        content: {
          case: "registration",
          value: new RegistrationResponse({
            agentId,
            serverTime: Timestamp.now(),
          }),
        },
      });

      // Hub waits for work to dispatch...
    },

    async *execute(requests: AsyncIterable<ExecuteJobMessage>): AsyncGenerator<JobInstruction> {
      let sessionId = "";
      for await (const message of requests) {
        if (message.content.case === "sessionId") {
          sessionId = message.content.value;
          console.log(`Job session started: ${sessionId}`);
          // Send JobRequest...
        }
      }
    },
  });
```

### 1.2 — TranslationHub Implementation
The `TranslationHub` follows the same async generator pattern using `TranslationHub`, `TranslationListenRequest`, and `TranslateMessage`.

---

## Part 2: Implementing the Agent (Client)

The Agent connects to the Hub services as needed.

### 2.1 — Agent Listening for Jobs

```typescript
import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import { JobHub } from "uap-node";
import { JobListenRequest, ExecuteJobMessage, JobResponse, JobStatus, JobStatus_State, JobResult } from "uap-node";

async function main() {
  const transport = createGrpcTransport({ baseUrl: "http://localhost:9090", httpVersion: "2" });
  const client = createClient(JobHub, transport);

  // 1. Listen (triggers registration and starts dispatcher)
  for await (const res of client.listen(new JobListenRequest({
    displayName: "Node.js Job Runner",
    capabilities: [{ testType: "selenium-side" }]
  }))) {
    if (res.content.case === "registration") {
      console.log("Registered with ID: " + res.content.value.agentId);
    } else if (res.content.case === "runJob") {
      const sessionId = res.content.value.sessionId;
      startJobSession(client, sessionId);
    }
  }
}

async function startJobSession(client: ReturnType<typeof createClient<typeof JobHub>>, sessionId: string) {
  async function* sendMessages(): AsyncGenerator<ExecuteJobMessage> {
    // 1. First message: session_id
    yield new ExecuteJobMessage({ content: { case: "sessionId", value: sessionId } });
    // 2. Subsequent status/telemetry updates...
  }

  for await (const instruction of client.execute(sendMessages())) {
    // Handle JobRequest and Commands...
  }
}

main().catch(console.error);
```
