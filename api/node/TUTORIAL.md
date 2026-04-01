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
import { ConnectRouter, HandlerContext } from "@connectrpc/connect";
import { JobHub } from "uap-node";
import { JobListenRequest, JobListenResponse, JobAssignment, JobResponse, JobInstruction } from "uap-node";
import { randomUUID } from "crypto";

export default (router: ConnectRouter) =>
  router.service(JobHub, {
    async *listen(requests: AsyncIterable<JobListenRequest>): AsyncGenerator<JobListenResponse> {
      let agentName = "";
      for await (const req of requests) {
        if (req.content.case === "registration") {
          agentName = req.content.value.displayName;
          console.log(`Job Agent registered: ${agentName}`);
        } else if (req.content.case === "ready") {
          console.log(`Agent ${agentName} is ready for work (heartbeat received)`);
          // Check queue and dispatch job if available
          // yield new JobListenResponse({ content: { case: "runJob", value: ... } });
        }
      }
    },

    async *execute(requests: AsyncIterable<JobResponse>, context: HandlerContext): AsyncGenerator<JobInstruction> {
      const sessionId = context.requestHeader.get("uap-session-id");
      if (!sessionId) throw new Error("Missing 'uap-session-id' header");
      
      console.log(`Job session started: ${sessionId}`);
      
      for await (const message of requests) {
        if (message.response.case === "telemetry") {
          const t = message.response.value;
          console.log(`[${t.source}] [${t.level}] ${t.message}`);
        } else if (message.response.case === "result") {
          const r = message.response.value;
          const s = r.summary;
          console.log(`Job ${r.status?.state}. Started: ${s?.startTime?.toDate()}, Duration: ${s?.totalDuration?.seconds}s`);
        }
      }
    },
  });
```

### 1.2 — TranslationHub Implementation
The `TranslationHub` follows the same bi-directional pattern for `Listen` and `Translate`.

---

## Part 2: Implementing the Agent (Client)

### 2.1 — Agent Listening for Jobs

```typescript
import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import { JobHub, JobListenRequest, JobRegistration } from "uap-node";
import { Empty } from "@bufbuild/protobuf";

async function main() {
  const transport = createGrpcTransport({ baseUrl: "http://localhost:9090", httpVersion: "2" });
  const client = createClient(JobHub, transport);

  // 1. Create a generator for bi-directional Listen requests
  async function* generateRequests() {
    // Send registration first
    yield new JobListenRequest({
      content: {
        case: "registration",
        value: new JobRegistration({
          displayName: "Node.js Job Runner",
          capabilities: [{ testType: "selenium-side" }]
        })
      }
    });

    // Send 'ready' heartbeat every 30 seconds
    while (true) {
      await new Promise(r => setTimeout(r, 30000));
      yield new JobListenRequest({ content: { case: "ready", value: new Empty() } });
    }
  }

  // 2. Listen for assignments
  for await (const res of client.listen(generateRequests())) {
    if (res.content.case === "runJob") {
      const sessionId = res.content.value.sessionId;
      // startJobSession...
    }
  }
}
```
