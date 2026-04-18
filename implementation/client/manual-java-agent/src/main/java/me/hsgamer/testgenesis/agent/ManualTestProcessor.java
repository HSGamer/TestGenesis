package me.hsgamer.testgenesis.agent;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.client.processor.TestSessionProcessor;
import me.hsgamer.testgenesis.client.utils.UapUtils;
import me.hsgamer.testgenesis.uap.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ManualTestProcessor implements TestSessionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ManualTestProcessor.class);
    private final ManualAgentApp app;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public ManualTestProcessor(ManualAgentApp app) {
        this.app = app;
    }

    @Override
    public TestCapability getTestCapability() {
        return TestCapability.newBuilder()
                .setType("manual-test")
                .addPayloads(PayloadRequirement.newBuilder()
                        .setType("manual-script")
                        .setIsRequired(true)
                        .addAcceptedMimeTypes("text/plain")
                        .build())
                .build();
    }

    @Override
    public void process(String sessionId, TestSessionContext context) throws Exception {
        if (!busy.compareAndSet(false, true)) {
            logger.warn("Agent is busy, rejecting session {}", sessionId);
            throw new IllegalStateException("Agent is busy with another session");
        }

        List<StepReport> reports = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        boolean allPassed = true;

        try {
            logger.info("Starting manual test session: {}", sessionId);

            // Phase 1: Loading
            String payloadText = "";
            List<Payload> payloads = context.getInit().getPayloadsList();
            for (Payload p : payloads) {
                if (p.getType().equals("manual-script")) {
                    if (p.hasAttachment() && !p.getAttachment().getData().isEmpty()) {
                        payloadText = p.getAttachment().getData().toStringUtf8();
                        logger.info("Loaded steps from attachment: {}", p.getAttachment().getName());
                        break;
                    }
                }
            }
            
            if (payloadText.isEmpty()) {
                throw new IllegalArgumentException("No valid .txt attachment found in 'manual-script' payload.");
            }

            // Phase 2: Acknowledge
            CompletableFuture<Boolean> ackFuture = new CompletableFuture<>();
            SwingUtilities.invokeLater(() -> app.showAcknowledgePrompt(sessionId, ackFuture));
            
            if (!ackFuture.get()) {
                logger.info("User rejected session {}", sessionId);
                throw new IllegalStateException("User rejected the test session");
            }
            
            context.sendStatus(TestStatus.newBuilder()
                    .setState(TestState.TEST_STATE_ACKNOWLEDGED)
                    .setMessage("User acknowledged session.")
                    .build());
            
            // Phase 3: Execution
            context.sendStatus(TestStatus.newBuilder()
                    .setState(TestState.TEST_STATE_RUNNING)
                    .setMessage("Executing manual test steps...")
                    .build());

            String[] steps = payloadText.split("\\r?\\n");
            List<String> validSteps = new ArrayList<>();
            for (String s : steps) {
                if (!s.trim().isEmpty()) validSteps.add(s.trim());
            }

            for (int i = 0; i < validSteps.size(); i++) {
                String stepText = validSteps.get(i);
                CompletableFuture<StepResult> future = new CompletableFuture<>();
                int stepIndex = i + 1;
                int totalSteps = validSteps.size();

                long stepStartTimeMillis = System.currentTimeMillis();
                com.google.protobuf.Timestamp stepStartTimestamp = UapUtils.now();

                SwingUtilities.invokeLater(() -> app.showTestStep(stepIndex, totalSteps, stepText, future));

                StepResult result = future.get(); // Wait for user interaction
                
                long stepDurationMillis = System.currentTimeMillis() - stepStartTimeMillis;

                StepReport report = StepReport.newBuilder()
                        .setName("Step " + stepIndex + ": " + stepText)
                        .setStatus(result.passed ? StepStatus.STEP_STATUS_PASSED : StepStatus.STEP_STATUS_FAILED)
                        .setSummary(Summary.newBuilder()
                                .setStartTime(stepStartTimestamp)
                                .setTotalDuration(UapUtils.msToDuration(stepDurationMillis))
                                .setMetadata(Struct.newBuilder()
                                        .putFields("message", Value.newBuilder().setStringValue(result.message).build())
                                        .build())
                                .build())
                        .build();
                
                reports.add(report);
                context.sendTelemetry(String.format("Step %d %s (Duration: %d ms)", stepIndex, (result.passed ? "PASSED" : "FAILED"), stepDurationMillis), 
                        result.passed ? Severity.SEVERITY_INFO : Severity.SEVERITY_ERROR);
                
                if (!result.passed) {
                    allPassed = false;
                    break; 
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            
            context.sendResult(TestResult.newBuilder()
                    .setStatus(TestStatus.newBuilder()
                            .setState(allPassed ? TestState.TEST_STATE_COMPLETED : TestState.TEST_STATE_FAILED)
                            .setMessage(allPassed ? "All manual steps passed" : "Manual test failed at some steps")
                            .build())
                    .addAllReports(reports)
                    .setSummary(Summary.newBuilder()
                            .setStartTime(UapUtils.now())
                            .setTotalDuration(UapUtils.msToDuration(duration))
                            .build())
                    .build());

        } catch (Exception e) {
            logger.error("Error during manual test", e);
            context.sendStatus(TestStatus.newBuilder()
                    .setState(TestState.TEST_STATE_FAILED)
                    .setMessage("Manual test interrupted: " + e.getMessage())
                    .build());
        } finally {
            busy.set(false);
            SwingUtilities.invokeLater(app::showIdle);
        }
    }

    public static class StepResult {
        public final boolean passed;
        public final String message;

        public StepResult(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }
    }
}
