package me.hsgamer.testgenesis.agent.junit;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.client.utils.UapUtils;
import me.hsgamer.testgenesis.uap.v1.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TelemetryExecutionListener implements TestExecutionListener {
    private final TestSessionContext context;
    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final List<StepReport> testReports = new ArrayList<>();

    public TelemetryExecutionListener(TestSessionContext context) {
        this.context = context;
    }

    @Override
    public void executionStarted(TestIdentifier identifier) {
        if (!identifier.isTest()) {
            return;
        }
        startTimes.put(identifier.getUniqueId(), System.currentTimeMillis());
        context.sendTelemetry("Started: " + identifier.getDisplayName(), Severity.SEVERITY_INFO);
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        if (!identifier.isTest()) {
            return;
        }

        long startMs = startTimes.getOrDefault(identifier.getUniqueId(), System.currentTimeMillis());
        long durationMs = System.currentTimeMillis() - startMs;

        StepStatus stepStatus = switch (result.getStatus()) {
            case SUCCESSFUL -> StepStatus.STEP_STATUS_PASSED;
            case FAILED, ABORTED -> StepStatus.STEP_STATUS_FAILED;
        };

        Summary.Builder summaryBuilder = Summary.newBuilder()
                .setStartTime(UapUtils.toTimestamp(Instant.ofEpochMilli(startMs)))
                .setTotalDuration(UapUtils.msToDuration(durationMs));

        result.getThrowable().ifPresent(t -> {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            summaryBuilder.setMetadata(Struct.newBuilder()
                    .putFields("stacktrace", Value.newBuilder()
                            .setStringValue(sw.toString())
                            .build())
                    .build());
        });

        testReports.add(StepReport.newBuilder()
                .setName(identifier.getDisplayName())
                .setStatus(stepStatus)
                .setSummary(summaryBuilder.build())
                .build());

        String message = String.format("Finished: %s [%s] (%dms)%s",
                identifier.getDisplayName(),
                result.getStatus(),
                durationMs,
                result.getThrowable().map(t -> " - " + t.getMessage()).orElse(""));
        Severity severity = result.getStatus() == TestExecutionResult.Status.SUCCESSFUL
                ? Severity.SEVERITY_INFO
                : Severity.SEVERITY_ERROR;
        context.sendTelemetry(message, severity);
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        if (!identifier.isTest()) {
            return;
        }

        testReports.add(StepReport.newBuilder()
                .setName(identifier.getDisplayName())
                .setStatus(StepStatus.STEP_STATUS_FAILED)
                .setSummary(Summary.newBuilder()
                        .setStartTime(UapUtils.toTimestamp(Instant.now()))
                        .setTotalDuration(UapUtils.msToDuration(0))
                        .build())
                .build());

        context.sendTelemetry("Skipped: " + identifier.getDisplayName() + " - " + reason, Severity.SEVERITY_WARN);
    }

    public List<StepReport> getTestReports() {
        return testReports;
    }
}
