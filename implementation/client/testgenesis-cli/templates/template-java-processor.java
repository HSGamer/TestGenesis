package me.hsgamer.testgenesis.agent;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.client.processor.TestSessionProcessor;
import me.hsgamer.testgenesis.client.utils.UapUtils;
import me.hsgamer.testgenesis.uap.v1.PayloadRequirement;
import me.hsgamer.testgenesis.uap.v1.Severity;
import me.hsgamer.testgenesis.uap.v1.Summary;
import me.hsgamer.testgenesis.uap.v1.TestCapability;
import me.hsgamer.testgenesis.uap.v1.TestResult;
import me.hsgamer.testgenesis.uap.v1.TestState;
import me.hsgamer.testgenesis.uap.v1.TestStatus;

import java.util.logging.Logger;

/**
 * Example Java processor implementation.
 */
public class ExampleProcessor implements TestSessionProcessor {
    private static final Logger logger = Logger.getLogger(ExampleProcessor.class.getName());

    @Override
    public TestCapability getTestCapability() {
        return TestCapability.newBuilder()
            .setType("example-test-java")
            .addPayloads(PayloadRequirement.newBuilder()
                .setType("example-payload-java")
                .setIsRequired(true)
                .addAcceptedMimeTypes("application/json")
                .build())
            .build();
    }

    @Override
    public void process(String sessionId, TestSessionContext context) throws Exception {
        logger.info("[ExampleProcessor] Processing session: " + sessionId);

        // 1. Initial Status
        context.sendStatus(TestStatus.newBuilder()
            .setState(TestState.TEST_STATE_ACKNOWLEDGED)
            .setMessage("Initializing example Java execution...")
            .build());

        long startTime = System.currentTimeMillis();

        // 2. Simulated work
        context.sendTelemetry("Starting step 1 (Java)...", Severity.SEVERITY_INFO);
        Thread.sleep(1000);
        
        context.sendStatus(TestStatus.newBuilder()
            .setState(TestState.TEST_STATE_RUNNING)
            .setMessage("Step 1 complete.")
            .build());

        context.sendTelemetry("Starting step 2 (Java)...", Severity.SEVERITY_INFO);
        Thread.sleep(1000);

        long duration = System.currentTimeMillis() - startTime;

        // 3. Send Result
        context.sendResult(TestResult.newBuilder()
            .setStatus(TestStatus.newBuilder()
                .setState(TestState.TEST_STATE_COMPLETED)
                .setMessage("Example Java test finished successfully")
                .build())
            .setSummary(Summary.newBuilder()
                .setStartTime(UapUtils.now())
                .setTotalDuration(UapUtils.msToDuration(duration))
                .setMetadata(Struct.newBuilder()
                    .putFields("processor", Value.newBuilder().setStringValue("ExampleProcessorJava").build())
                    .putFields("duration_ms", Value.newBuilder().setNumberValue(duration).build())
                    .build())
                .build())
            .build());

        // 4. Final Status
        context.sendStatus(TestStatus.newBuilder()
            .setState(TestState.TEST_STATE_COMPLETED)
            .setMessage("Done.")
            .build());
    }
}
