import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient, Client, Transport } from "@connectrpc/connect";
import { create } from "@bufbuild/protobuf";
import { createWritableIterable } from "@connectrpc/connect/protocol";
import { AgentHub } from "./generated/AgentHub_pb.js";
import { AgentRegistrationSchema } from "./generated/AgentRegistration_pb.js";
import { ListenRequest, ListenRequestSchema } from "./generated/ListenRequest_pb.js";
import { 
  ProcessorType, 
  TestSessionProcessor, 
  TranslationSessionProcessor,
  AnyProcessor
} from "./processor.js";
import { TestSessionContext, TranslationSessionContext } from "./context.js";
import { TestResponse, TestResponseSchema } from "./generated/TestResponse_pb.js";
import { TranslationResponse, TranslationResponseSchema } from "./generated/TranslationResponse_pb.js";

export interface AgentConfig {
  hubUrl: string;
  displayName: string;
}

export class Agent {
  private readonly transport: Transport;
  private readonly agentClient: Client<typeof AgentHub>;
  private readonly processors = new Map<ProcessorType, AnyProcessor>();
  private isShuttingDown = false;

  constructor(private readonly config: AgentConfig) {
    this.transport = createGrpcTransport({ 
      baseUrl: config.hubUrl,
    });
    this.agentClient = createClient(AgentHub, this.transport);
  }

  public registerTestProcessor(processor: TestSessionProcessor) {
    this.processors.set("test", processor);
  }

  public registerTranslationProcessor(processor: TranslationSessionProcessor) {
    this.processors.set("translation", processor);
  }

  public async start() {
    console.log(`[Agent] Starting: ${this.config.displayName}`);
    console.log(`[Agent] Registered Capabilities: ${Array.from(this.processors.keys()).join(", ")}`);

    while (!this.isShuttingDown) {
      try {
        await this.runLifecycle();
      } catch (err) {
        if (this.isShuttingDown) break;
        console.error("[Agent][Fatal] Disconnected. Retrying in 5s...", err);
        await new Promise(r => setTimeout(r, 5000));
      }
    }
  }

  private async runLifecycle() {
    // 1. Identification & Capability Registration
    const registration = await this.agentClient.register(create(AgentRegistrationSchema, {
      displayName: this.config.displayName,
      capabilities: Array.from(this.processors.values()).map(p => p.getCapability()),
    }));

    const clientId = registration.clientId;
    console.log(`[Agent][Connected] Client ID: ${clientId}`);

    // 2. Control Stream Dispatcher
    const requestIterable = createWritableIterable<ListenRequest>();
    requestIterable.write(create(ListenRequestSchema, { event: { case: "ready", value: {} } }));
    
    const pendingSessions = new Map<string, ProcessorType>();
    const listenStream = this.agentClient.listen(requestIterable, { 
      headers: { "x-client-id": clientId } 
    });

    for await (const response of listenStream) {
      const event = response.event;
      
      if (event?.case === "sessionProposal") {
        const proposal = event.value;
        const type = proposal.details.case as ProcessorType;
        
        console.log(`[Agent][${type}] Received Proposal: ${proposal.sessionId}`);
        
        if (this.processors.has(type)) {
          pendingSessions.set(proposal.sessionId, type);
          requestIterable.write(create(ListenRequestSchema, { 
            event: { 
              case: "sessionAcceptance", 
              value: { sessionId: proposal.sessionId, accepted: true } 
            } 
          }));
        } else {
          console.warn(`[Agent][${type}] Rejecting: No processor registered for this type.`);
          requestIterable.write(create(ListenRequestSchema, { 
            event: { 
              case: "sessionAcceptance", 
              value: { sessionId: proposal.sessionId, accepted: false } 
            } 
          }));
        }
      } else if (event?.case === "sessionReady") {
        const sessionId = event.value.sessionId;
        const type = pendingSessions.get(sessionId);
        
        if (type) {
          pendingSessions.delete(sessionId);
          this.handleSessionReady(sessionId, type).catch(err => {
            console.error(`[Agent][${type}] Session Handling Error (${sessionId}):`, err);
          });
        }
      }
    }
  }

  private async handleSessionReady(sessionId: string, type: ProcessorType) {
    if (type === "test") {
      const processor = this.processors.get("test") as TestSessionProcessor;
      const responseIterable = createWritableIterable<TestResponse>();
      const stream = this.agentClient.execute(responseIterable, {
        headers: { "x-session-id": sessionId },
      });

      // Wait for Init
      let initMsg = null;
      for await (const msg of stream) {
        initMsg = msg;
        break; // First message is Init
      }

      if (!initMsg) throw new Error("Stream closed before TestInit was received.");

      const context = new TestSessionContext(initMsg, responseIterable);
      try {
        await processor.process(sessionId, context);
      } finally {
        responseIterable.close();
      }
    } else if (type === "translation") {
      const processor = this.processors.get("translation") as TranslationSessionProcessor;
      const responseIterable = createWritableIterable<TranslationResponse>();
      const stream = this.agentClient.translate(responseIterable, {
        headers: { "x-session-id": sessionId },
      });

      // Wait for Init
      let initMsg = null;
      for await (const msg of stream) {
        initMsg = msg;
        break; // First message is Init
      }

      if (!initMsg) throw new Error("Stream closed before TranslationInit was received.");

      const context = new TranslationSessionContext(initMsg, responseIterable);
      try {
        await processor.process(sessionId, context);
      } finally {
        responseIterable.close();
      }
    }
  }

  public shutdown() {
    this.isShuttingDown = true;
  }
}
