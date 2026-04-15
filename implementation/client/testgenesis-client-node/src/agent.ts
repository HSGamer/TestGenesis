import {createGrpcTransport} from "@connectrpc/connect-node";
import {Client, createClient, Transport} from "@connectrpc/connect";
import {create} from "@bufbuild/protobuf";
import {createWritableIterable} from "@connectrpc/connect/protocol";
import {AgentHub, AgentRegistrationSchema, ListenRequest, ListenRequestSchema, SummarySchema, TestResponse, TestResultSchema, TestState, TestStatusSchema, TranslationResponse, TranslationResultSchema, TranslationState, TranslationStatusSchema} from "./generated/index.js";
import {AnyProcessor, ProcessorType, TestSessionProcessor, TranslationSessionProcessor, TestSessionContext, TranslationSessionContext} from "./context.js";

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
        console.log(`[Agent] Ready: ${this.config.displayName} (${Array.from(this.processors.keys()).join(", ")})`);

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

    public shutdown() {
        this.isShuttingDown = true;
    }

    private async runLifecycle() {
        // 1. Identification & Capability Registration
        const registration = await this.agentClient.register(create(AgentRegistrationSchema, {
            displayName: this.config.displayName,
            capabilities: Array.from(this.processors.values()).map(p => p.getCapability()),
        }));

        const clientId = registration.clientId;
        console.log(`[Agent] Connected as ${registration.clientId}`);

        // 2. Control Stream Dispatcher
        const requestIterable = createWritableIterable<ListenRequest>();
        const pendingSessions = new Map<string, ProcessorType>();
        const listenStream = this.agentClient.listen(requestIterable, {
            headers: {"x-client-id": clientId}
        });
        await requestIterable.write(create(ListenRequestSchema, {event: {case: "ready", value: {}}}));

        for await (const response of listenStream) {
            const event = response.event;

            if (event?.case === "sessionProposal") {
                const proposal = event.value;
                const type = proposal.details.case as ProcessorType;

                console.log(`[Agent][${type}] Handling Proposal: ${proposal.sessionId}`);

                if (this.processors.has(type)) {
                    pendingSessions.set(proposal.sessionId, type);
                    await requestIterable.write(create(ListenRequestSchema, {
                        event: {
                            case: "sessionAcceptance",
                            value: {sessionId: proposal.sessionId, accepted: true}
                        }
                    }));
                } else {
                    console.warn(`[Agent][${type}] Rejecting: No processor registered for this type.`);
                    await requestIterable.write(create(ListenRequestSchema, {
                        event: {
                            case: "sessionAcceptance",
                            value: {sessionId: proposal.sessionId, accepted: false}
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
                headers: {"x-session-id": sessionId},
            });

            console.log(`[Agent][Test] Active: ${sessionId}`);

            // Wait for Init without closing the stream (avoiding 'for await...break')
            const streamIterator = stream[Symbol.asyncIterator]();
            const { value: initMsg, done } = await streamIterator.next();

            if (done || !initMsg) throw new Error("Stream closed before TestInit was received.");

            const context = new TestSessionContext(initMsg, responseIterable);
            try {
                await processor.process(sessionId, context);
            } catch (err: any) {
                await context.sendResult(create(TestResultSchema, {
                    status: create(TestStatusSchema, {
                        state: TestState.FAILED,
                        message: `Internal Agent Error: ${err.message || String(err)}`
                    }),
                    summary: create(SummarySchema, {
                        metadata: {
                            exception: String(err),
                            stack: err.stack || "No stack trace available"
                        }
                    })
                }));
                throw err;
            } finally {
                responseIterable.close();
            }
        } else if (type === "translation") {
            const processor = this.processors.get("translation") as TranslationSessionProcessor;
            const responseIterable = createWritableIterable<TranslationResponse>();
            const stream = this.agentClient.translate(responseIterable, {
                headers: {"x-session-id": sessionId},
            });

            console.log(`[Agent][Translation] Active: ${sessionId}`);

            // Wait for Init without closing the stream (avoiding 'for await...break')
            const streamIterator = stream[Symbol.asyncIterator]();
            const { value: initMsg, done } = await streamIterator.next();

            if (done || !initMsg) throw new Error("Stream closed before TranslationInit was received.");

            const context = new TranslationSessionContext(initMsg, responseIterable);
            try {
                await processor.process(sessionId, context);
            } catch (err: any) {
                await context.sendResult(create(TranslationResultSchema, {
                    status: create(TranslationStatusSchema, {
                        state: TranslationState.FAILED,
                        message: `Internal Agent Error: ${err.message || String(err)}`
                    }),
                    summary: create(SummarySchema, {
                        metadata: {
                            exception: String(err),
                            stack: err.stack || "No stack trace available"
                        }
                    })
                }));
                throw err;
            } finally {
                responseIterable.close();
            }
        }
    }
}
