package me.hsgamer.testgenesis.agent.junit;

import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.uap.v1.Severity;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

public class TelemetryExecutionListener implements TestExecutionListener {
    private final TestSessionContext context;

    public TelemetryExecutionListener(TestSessionContext context) {
        this.context = context;
    }

    @Override
    public void executionStarted(TestIdentifier tid) {
        if (tid.isTest()) context.sendTelemetry("Started: " + tid.getDisplayName(), Severity.SEVERITY_INFO);
    }

    @Override
    public void executionFinished(TestIdentifier tid, TestExecutionResult res) {
        if (tid.isTest()) {
            String msg = String.format("Finished: %s [%s]%s", tid.getDisplayName(), res.getStatus(),
                res.getThrowable().map(t -> " - " + t.getMessage()).orElse(""));
            context.sendTelemetry(msg, res.getStatus() == TestExecutionResult.Status.SUCCESSFUL ? Severity.SEVERITY_INFO : Severity.SEVERITY_ERROR);
        }
    }
}
