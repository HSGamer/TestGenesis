package me.hsgamer.testgenesis.agent.junit;

import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.client.processor.TestSessionProcessor;
import me.hsgamer.testgenesis.uap.v1.*;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
                        .build())
                .build();
    }

    @Override
    public void process(String sessionId, TestSessionContext context) throws Exception {
        TestInit init = context.getInit();
        Payload payload = init.getPayloadsList().stream()
                .filter(p -> "selenium-junit".equals(p.getType()))
                .findFirst()
                .orElse(null);

        if (payload == null || !payload.hasAttachment()) {
            context.sendResult(TestResult.newBuilder()
                    .setStatus(TestStatus.newBuilder()
                            .setState(TestState.TEST_STATE_FAILED)
                            .setMessage("Missing required selenium-junit payload")
                            .build())
                    .build());
            return;
        }

        String sourceCode = payload.getAttachment().getData().toStringUtf8();
        String className = payload.getAttachment().getName().replace(".java", "");

        // 1. Setup Temporary Work Directory
        Path workDir = Files.createTempDirectory("uap-junit-" + sessionId);
        try {
            logger.info("[{}] Compiling test: {} in {}", sessionId, className, workDir);
            Path javaFile = workDir.resolve(className + ".java");
            Files.writeString(javaFile, sourceCode, StandardCharsets.UTF_8);

            // 2. Programmatic Compilation
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Java Compiler not found. Ensure you are running on a JDK.");
            }

            String classpath = System.getProperty("java.class.path");
            List<String> options = Arrays.asList("-classpath", classpath, "-d", workDir.toString());
            
            int compilationResult = compiler.run(null, null, null, 
                    javaFile.toString(), 
                    "-classpath", classpath, 
                    "-d", workDir.toString()
            );

            if (compilationResult != 0) {
                context.sendResult(TestResult.newBuilder()
                        .setStatus(TestStatus.newBuilder()
                                .setState(TestState.TEST_STATE_FAILED)
                                .setMessage("Compilation failed for " + className)
                                .build())
                        .build());
                return;
            }

            // 3. Load and Execute with JUnit Launcher
            try (URLClassLoader classLoader = new URLClassLoader(new URL[]{workDir.toUri().toURL()}, 
                    this.getClass().getClassLoader())) {
                
                Class<?> testClass = classLoader.loadClass(className);
                
                LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                        .selectors(selectClass(testClass))
                        .build();

                Launcher launcher = LauncherFactory.create();
                SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
                
                // Register a custom listener for telemetry
                launcher.registerTestExecutionListeners(summaryListener, new TelemetryExecutionListener(context));

                logger.info("[{}] Starting JUnit 5 execution", sessionId);
                launcher.execute(request);

                TestExecutionSummary summary = summaryListener.getSummary();
                boolean success = summary.getTestsFailedCount() == 0 && summary.getTestsAbortedCount() == 0;

                context.sendResult(TestResult.newBuilder()
                        .setStatus(TestStatus.newBuilder()
                                .setState(success ? TestState.TEST_STATE_COMPLETED : TestState.TEST_STATE_FAILED)
                                .setMessage(String.format("Executed %d tests. Failed: %d", 
                                        summary.getTestsFoundCount(), summary.getTestsFailedCount()))
                                .build())
                        .setSummary(Summary.newBuilder()
                                .setMetadata(com.google.protobuf.Struct.newBuilder()
                                        .putFields("testsFound", com.google.protobuf.Value.newBuilder().setNumberValue(summary.getTestsFoundCount()).build())
                                        .putFields("testsFailed", com.google.protobuf.Value.newBuilder().setNumberValue(summary.getTestsFailedCount()).build())
                                        .putFields("testsSucceeded", com.google.protobuf.Value.newBuilder().setNumberValue(summary.getTestsSucceededCount()).build())
                                        .build())
                                .build())
                        .build());
            }

        } finally {
            // Cleanup work directory
            deleteDirectory(workDir.toFile());
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }
}
