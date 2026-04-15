package me.hsgamer.testgenesis.agent.selenium.junit;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.client.processor.TestSessionProcessor;
import me.hsgamer.testgenesis.client.utils.UapUtils;
import me.hsgamer.testgenesis.uap.v1.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class SeleniumJUnitProcessor implements TestSessionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SeleniumJUnitProcessor.class);

    @Override
    public TestCapability getTestCapability() {
        return TestCapability.newBuilder()
            .setType("selenium-junit")
            .addPayloads(PayloadRequirement.newBuilder()
                .setType("selenium-junit")
                .setIsRequired(true)
                .addAcceptedMimeTypes("text/x-java-source")
            )
            .build();
    }

    @Override
    public void process(String sessionId, TestSessionContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        List<StepReport> reports = new ArrayList<>();
        Path workDir = null;

        try {
            // 1. Initialize
            long stepStart = System.currentTimeMillis();
            Payload payload = context.getInit().getPayloadsList().stream()
                .filter(p -> "selenium-junit".equals(p.getType()))
                .findFirst().orElse(null);
            
            if (payload == null || !payload.hasAttachment()) {
                fail(sessionId, context, reports, "Initialize", "Missing payload", stepStart, null);
                return;
            }
            
            String sourceCode = payload.getAttachment().getData().toStringUtf8();
            String className = payload.getAttachment().getName().replace(".java", "");
            workDir = Files.createTempDirectory("uap-junit-" + sessionId);
            reports.add(createReport("Initialize", StepStatus.STEP_STATUS_PASSED, stepStart));

            // 2. Compile
            stepStart = System.currentTimeMillis();
            Path javaFile = workDir.resolve(className + ".java");
            Files.writeString(javaFile, sourceCode, StandardCharsets.UTF_8);

            ByteArrayOutputStream errOutput = new ByteArrayOutputStream();
            int res = ToolProvider.getSystemJavaCompiler().run(null, null, errOutput, 
                javaFile.toString(), "-classpath", buildClasspath(), "-d", workDir.toString());
            
            if (res != 0) {
                fail(sessionId, context, reports, "Compile", errOutput.toString(StandardCharsets.UTF_8), stepStart, null);
                return;
            }
            reports.add(createReport("Compile", StepStatus.STEP_STATUS_PASSED, stepStart));

            // 3. Run
            stepStart = System.currentTimeMillis();
            try (URLClassLoader cl = new URLClassLoader(new URL[]{workDir.toUri().toURL()}, this.getClass().getClassLoader())) {
                SummaryGeneratingListener listener = new SummaryGeneratingListener();
                LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(cl.loadClass(className))).build(),
                    listener, new TelemetryExecutionListener(context));

                var summary = listener.getSummary();
                boolean success = summary.getTestsFailedCount() == 0 && summary.getTestsAbortedCount() == 0;
                reports.add(createReport("Run", success ? StepStatus.STEP_STATUS_PASSED : StepStatus.STEP_STATUS_FAILED, stepStart));

                context.sendResult(TestResult.newBuilder()
                    .setStatus(TestStatus.newBuilder().setState(success ? TestState.TEST_STATE_COMPLETED : TestState.TEST_STATE_FAILED).build())
                    .addAllReports(reports).setSummary(Summary.newBuilder()
                        .setStartTime(UapUtils.toTimestamp(java.time.Instant.ofEpochMilli(startTime)))
                        .setTotalDuration(UapUtils.msToDuration(System.currentTimeMillis() - startTime)).build()).build());
            }
        } catch (Exception e) {
            fail(sessionId, context, reports, "Execution", e.getMessage(), startTime, e);
        } finally {
            if (workDir != null) deleteDir(workDir.toFile());
        }
    }

    private String buildClasspath() {
        StringBuilder cpBuilder = new StringBuilder();
        cpBuilder.append(System.getProperty("java.class.path"));

        ClassLoader cl = this.getClass().getClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL url : ucl.getURLs()) {
                    String path = url.getPath();
                    if (path != null && !path.isEmpty()) {
                        cpBuilder.append(File.pathSeparator).append(path);
                    }
                }
            }
            cl = cl.getParent();
        }

        Class<?>[] keyClasses = {
            me.hsgamer.testgenesis.client.Agent.class,
            org.openqa.selenium.WebDriver.class,
            org.openqa.selenium.chrome.ChromeDriver.class,
            org.openqa.selenium.firefox.FirefoxDriver.class,
            org.openqa.selenium.remote.RemoteWebDriver.class,
            org.openqa.selenium.support.ui.WebDriverWait.class,
            org.junit.Test.class,
            org.hamcrest.Matcher.class,
            org.junit.jupiter.api.Test.class,
            org.slf4j.Logger.class
        };

        for (Class<?> clazz : keyClasses) {
            try {
                java.security.CodeSource cs = clazz.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null) {
                    String path = cs.getLocation().getPath();
                    if (path != null && !path.isEmpty()) {
                        cpBuilder.append(File.pathSeparator).append(path);
                    }
                }
            } catch (Exception ignored) {}
        }
        return cpBuilder.toString();
    }

    private StepReport createReport(String name, StepStatus status, long start) {
        return StepReport.newBuilder().setName(name).setStatus(status).setSummary(Summary.newBuilder()
            .setStartTime(UapUtils.toTimestamp(java.time.Instant.ofEpochMilli(start)))
            .setTotalDuration(UapUtils.msToDuration(System.currentTimeMillis() - start)).build()).build();
    }

    private void fail(String sid, TestSessionContext ctx, List<StepReport> reports, String step, String err, long start, Exception e) {
        logger.error("[{}] {} failed: {}", sid, step, err, e);
        reports.add(createReport(step, StepStatus.STEP_STATUS_FAILED, start));
        ctx.sendResult(TestResult.newBuilder().setStatus(TestStatus.newBuilder().setState(TestState.TEST_STATE_FAILED).build()).addAllReports(reports)
            .setSummary(Summary.newBuilder().setMetadata(Struct.newBuilder().putFields("error", Value.newBuilder().setStringValue(err).build()).build()).build()).build());
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDir(f);
        dir.delete();
    }
}
