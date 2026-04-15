package me.hsgamer.testgenesis.agent.junit;

import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.uap.v1.Severity;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryExecutionListener implements TestExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryExecutionListener.class);
    private final TestSessionContext context;

    public TelemetryExecutionListener(TestSessionContext context) {
        this.context = context;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        context.sendTelemetry("JUnit test plan started. Total tests: " + testPlan.countTestIdentifiers(p -> p.isTest()), Severity.SEVERITY_INFO);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            context.sendTelemetry("Started test: " + testIdentifier.getDisplayName(), Severity.SEVERITY_INFO);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            String message = "Finished test: " + testIdentifier.getDisplayName() + " [" + testExecutionResult.getStatus() + "]";
            Severity severity = testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL 
                    ? Severity.SEVERITY_INFO 
                    : Severity.SEVERITY_ERROR;
            
            if (testExecutionResult.getThrowable().isPresent()) {
                message += " - Error: " + testExecutionResult.getThrowable().get().getMessage();
            }
            
            context.sendTelemetry(message, severity);
        }
    }
}
