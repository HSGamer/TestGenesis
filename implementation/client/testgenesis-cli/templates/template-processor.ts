import {
    create,
    SummarySchema,
    testCapability,
    TestResultSchema,
    TestSessionContext,
    TestSessionProcessor,
    TestState,
    TestStatusSchema,
    timestampNow,
    msToDuration,
    cleanObject
} from "testgenesis-client-node";

/**
 * Example processor implementation.
 */
export class ExampleProcessor implements TestSessionProcessor {
    public getCapability() {
        return testCapability({
            type: "example-test",
            payloads: [
                {type: "example-payload", isRequired: true, acceptedMimeTypes: ["application/json"]}
            ]
        });
    }

    public async process(sessionId: string, context: TestSessionContext) {
        console.log(`[ExampleProcessor] Processing session: ${sessionId}`);

        // 1. Initial Status
        await context.sendStatus(create(TestStatusSchema, {
            state: TestState.ACKNOWLEDGED,
            message: "Initializing example execution..."
        }));

        const startTime = new Date();

        // 2. Simulated work
        await context.sendTelemetry("Starting step 1...");
        await new Promise(r => setTimeout(r, 1000));
        
        await context.sendStatus(create(TestStatusSchema, {
            state: TestState.RUNNING,
            message: "Step 1 complete."
        }));

        await context.sendTelemetry("Starting step 2...");
        await new Promise(r => setTimeout(r, 1000));

        const endTime = new Date();
        const duration = endTime.getTime() - startTime.getTime();

        // 3. Send Result
        await context.sendResult(create(TestResultSchema, {
            status: create(TestStatusSchema, {
                state: TestState.COMPLETED,
                message: "Example test finished successfully"
            }),
            reports: [], // Add step reports here
            summary: create(SummarySchema, {
                startTime: timestampNow(),
                totalDuration: msToDuration(duration),
                metadata: cleanObject({
                    processor: "ExampleProcessor",
                    duration_ms: duration
                })
            }),
            attachments: []
        }));

        // 4. Final Status
        await context.sendStatus(create(TestStatusSchema, {
            state: TestState.COMPLETED,
            message: "Done."
        }));
    }
}
