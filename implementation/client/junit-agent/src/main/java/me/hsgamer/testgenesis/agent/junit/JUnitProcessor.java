package me.hsgamer.testgenesis.agent.junit;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class JUnitProcessor implements TestSessionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(JUnitProcessor.class);

    private final List<File> defaultJars;
    private final DependencyResolver resolver;

    public JUnitProcessor(List<File> defaultJars, DependencyResolver resolver) {
        this.defaultJars = defaultJars;
        this.resolver = resolver;
    }

    @Override
    public TestCapability getTestCapability() {
        return TestCapability.newBuilder()
                .setType("java-junit")
                .addPayloads(PayloadRequirement.newBuilder()
                        .setType("java-junit")
                        .setIsRequired(true)
                        .addAcceptedMimeTypes("text/x-java")
                        .addAcceptedMimeTypes("text/x-java-source")
                        .addAcceptedMimeTypes("application/x-java-source"))
                .addPayloads(PayloadRequirement.newBuilder()
                        .setType("junit-config")
                        .setIsRequired(false)
                        .addAcceptedMimeTypes("text/plain"))
                .build();
    }

    @Override
    public void process(String sessionId, TestSessionContext context) throws Exception {
        long sessionStartMs = System.currentTimeMillis();
        List<StepReport> pipelineReports = new ArrayList<>();
        Path workDir = null;

        try {
            long stepStart = System.currentTimeMillis();

            Payload sourcePayload = findPayload(context, "java-junit");
            if (sourcePayload == null || !sourcePayload.hasAttachment()) {
                failAndReturn(sessionId, context, pipelineReports, "Extract Payloads",
                        "Missing required 'java-junit' source payload", stepStart);
                return;
            }

            String sourceCode = sourcePayload.getAttachment().getData().toStringUtf8();
            String className = sourcePayload.getAttachment().getName().replace(".java", "");
            workDir = Files.createTempDirectory("uap-junit-" + sessionId);
            pipelineReports.add(buildReport("Extract Payloads", StepStatus.STEP_STATUS_PASSED, stepStart));

            stepStart = System.currentTimeMillis();
            List<File> sessionJars = resolveSessionDependencies(sessionId, context);
            List<File> allJars = mergeJars(defaultJars, sessionJars);
            logger.info("[{}] Using {} JARs ({} default + {} session)",
                    sessionId, allJars.size(), defaultJars.size(), sessionJars.size());
            pipelineReports.add(buildReport("Resolve Dependencies", StepStatus.STEP_STATUS_PASSED, stepStart));

            stepStart = System.currentTimeMillis();
            String compileError = compileSource(sourceCode, className, workDir, allJars);
            if (compileError != null) {
                failAndReturn(sessionId, context, pipelineReports, "Compile", compileError, stepStart);
                return;
            }
            pipelineReports.add(buildReport("Compile", StepStatus.STEP_STATUS_PASSED, stepStart));

            stepStart = System.currentTimeMillis();
            List<StepReport> testReports = executeTests(className, workDir, allJars, context);
            boolean allPassed = testReports.stream()
                    .allMatch(r -> r.getStatus() == StepStatus.STEP_STATUS_PASSED);
            pipelineReports.add(buildReport("Execute Tests",
                    allPassed ? StepStatus.STEP_STATUS_PASSED : StepStatus.STEP_STATUS_FAILED, stepStart));

            List<StepReport> allReports = new ArrayList<>(pipelineReports);
            allReports.addAll(testReports);

            TestState finalState = allPassed ? TestState.TEST_STATE_COMPLETED : TestState.TEST_STATE_FAILED;
            context.sendResult(TestResult.newBuilder()
                    .setStatus(TestStatus.newBuilder().setState(finalState).build())
                    .addAllReports(allReports)
                    .setSummary(Summary.newBuilder()
                            .setStartTime(UapUtils.toTimestamp(Instant.ofEpochMilli(sessionStartMs)))
                            .setTotalDuration(UapUtils.msToDuration(System.currentTimeMillis() - sessionStartMs))
                            .build())
                    .build());

        } catch (Exception e) {
            failAndReturn(sessionId, context, pipelineReports, "Unexpected Error",
                    e.getMessage(), sessionStartMs);
        } finally {
            if (workDir != null) {
                deleteDirectory(workDir.toFile());
            }
        }
    }

    private List<File> resolveSessionDependencies(String sessionId, TestSessionContext context) {
        Payload configPayload = findPayload(context, "junit-config");
        if (configPayload == null || !configPayload.hasAttachment()) {
            return List.of();
        }

        List<String> coordinates = new ArrayList<>();
        String text = configPayload.getAttachment().getData().toStringUtf8();
        for (String line : text.split("\\r?\\n|\\\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                coordinates.add(trimmed);
            }
        }

        if (coordinates.isEmpty()) {
            return List.of();
        }

        logger.info("[{}] Resolving session dependencies: {}", sessionId, coordinates);
        return resolver.resolve(coordinates);
    }

    private List<File> mergeJars(List<File> defaults, List<File> session) {
        Set<String> seen = new LinkedHashSet<>();
        List<File> result = new ArrayList<>();
        for (File f : defaults) {
            if (seen.add(f.getAbsolutePath())) {
                result.add(f);
            }
        }
        for (File f : session) {
            if (seen.add(f.getAbsolutePath())) {
                result.add(f);
            }
        }
        return result;
    }

    private String compileSource(String sourceCode, String className, Path workDir, List<File> jars) throws Exception {
        Path javaFile = workDir.resolve(className + ".java");
        Files.writeString(javaFile, sourceCode, StandardCharsets.UTF_8);

        String classpath = buildClasspath(jars);
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        int exitCode = ToolProvider.getSystemJavaCompiler().run(
                null, null, errorStream,
                javaFile.toString(),
                "-classpath", classpath,
                "-d", workDir.toString());

        return exitCode == 0 ? null : errorStream.toString(StandardCharsets.UTF_8);
    }

    private List<StepReport> executeTests(String className, Path workDir, List<File> jars, TestSessionContext context) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(workDir.toUri().toURL());
        for (File jar : jars) {
            urls.add(jar.toURI().toURL());
        }

        try (URLClassLoader classLoader = new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader())) {
            Class<?> testClass = classLoader.loadClass(className);

            SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
            TelemetryExecutionListener telemetryListener = new TelemetryExecutionListener(context);

            LauncherFactory.create().execute(
                    LauncherDiscoveryRequestBuilder.request()
                            .selectors(selectClass(testClass))
                            .build(),
                    summaryListener, telemetryListener);

            return telemetryListener.getTestReports();
        }
    }

    private String buildClasspath(List<File> jars) {
        Set<String> entries = new LinkedHashSet<>();

        String systemCp = System.getProperty("java.class.path");
        if (systemCp != null) {
            entries.addAll(Arrays.asList(systemCp.split(File.pathSeparator)));
        }

        ClassLoader cl = getClass().getClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL url : ucl.getURLs()) {
                    String path = url.getPath();
                    if (path != null && !path.isEmpty()) {
                        entries.add(path);
                    }
                }
            }
            cl = cl.getParent();
        }

        for (File jar : jars) {
            entries.add(jar.getAbsolutePath());
        }

        Class<?>[] keyClasses = {
                me.hsgamer.testgenesis.client.Agent.class,
                org.junit.Test.class,
                org.hamcrest.Matcher.class,
                org.junit.jupiter.api.Test.class,
                org.slf4j.Logger.class
        };
        for (Class<?> clazz : keyClasses) {
            try {
                java.security.CodeSource cs = clazz.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null) {
                    entries.add(cs.getLocation().getPath());
                }
            } catch (Exception ignored) {
            }
        }

        return String.join(File.pathSeparator, entries);
    }

    private Payload findPayload(TestSessionContext context, String type) {
        return context.getInit().getPayloadsList().stream()
                .filter(p -> type.equals(p.getType()))
                .findFirst()
                .orElse(null);
    }

    private StepReport buildReport(String name, StepStatus status, long startMs) {
        return StepReport.newBuilder()
                .setName(name)
                .setStatus(status)
                .setSummary(Summary.newBuilder()
                        .setStartTime(UapUtils.toTimestamp(Instant.ofEpochMilli(startMs)))
                        .setTotalDuration(UapUtils.msToDuration(System.currentTimeMillis() - startMs))
                        .build())
                .build();
    }

    private void failAndReturn(String sessionId, TestSessionContext context,
                               List<StepReport> reports, String step, String error, long startMs) {
        logger.error("[{}] {} failed: {}", sessionId, step, error);
        reports.add(buildReport(step, StepStatus.STEP_STATUS_FAILED, startMs));
        context.sendResult(TestResult.newBuilder()
                .setStatus(TestStatus.newBuilder().setState(TestState.TEST_STATE_FAILED).build())
                .addAllReports(reports)
                .setSummary(Summary.newBuilder()
                        .setMetadata(Struct.newBuilder()
                                .putFields("error", Value.newBuilder()
                                        .setStringValue(error != null ? error : "Unknown error")
                                        .build())
                                .build())
                        .build())
                .build());
    }

    private void deleteDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteDirectory(child);
            }
        }
        dir.delete();
    }
}
