# UAP Java Implementation Tutorial

This tutorial shows how to implement both the **Hub (Server)** and the **Agent (Client)** in Java using the `uap-java` library.

## Prerequisites

- Java 25
- Maven 3.9+
- The `uap-java` library built via `mvn compile`

Add `uap-java` as a dependency in your project:

```xml
<dependency>
    <groupId>me.hsgamer</groupId>
    <artifactId>uap-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

You also need a gRPC transport. Choose one:

```xml
<!-- Option A: Netty (standard) -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty</artifactId>
</dependency>

<!-- Option B: Netty Shaded (self-contained, no Netty conflicts) -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
</dependency>
```

---

## Part 1: Implementing the Hub (Server)

The Hub is the gRPC **server**. It manages agent registration, dispatches work assignments, and handles execution streams.

### 1.1 — Service Implementation

Create a class that extends `UniversalHubGrpc.UniversalHubImplBase`:

```java
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HubService extends UniversalHubGrpc.UniversalHubImplBase {

    // Stores registered agents: agent_id -> Listen stream observer
    private final Map<String, StreamObserver<HubDirective>> agentListeners = new ConcurrentHashMap<>();

    // Stores pending job sessions: session_id -> job details (simplified)
    private final Map<String, JobRequest> pendingJobs = new ConcurrentHashMap<>();

    // ─── Control Plane ──────────────────────────────────────────────

    @Override
    public void register(RegistrationRequest request, StreamObserver<RegistrationResponse> responseObserver) {
        String agentId = UUID.randomUUID().toString();

        System.out.printf("Agent registered: '%s' (id: %s)%n", request.getDisplayName(), agentId);
        System.out.printf("  Capabilities: %d items%n", request.getCapabilities().getItemsCount());

        RegistrationResponse response = RegistrationResponse.newBuilder()
                .setAgentId(agentId)
                .setServerTime(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listen(AgentIdentity request, StreamObserver<HubDirective> responseObserver) {
        String agentId = request.getAgentId();
        System.out.printf("Agent listening: %s%n", agentId);

        // Store the stream so we can push directives to this agent later
        agentListeners.put(agentId, responseObserver);

        // Note: Do NOT call onCompleted() here.
        // This stream stays open for the agent's entire lifecycle.
    }

    // ─── Execution Plane: Job ───────────────────────────────────────

    @Override
    public StreamObserver<ExecuteJobMessage> executeJob(StreamObserver<JobInstruction> responseObserver) {
        return new StreamObserver<>() {
            private String sessionId;

            @Override
            public void onNext(ExecuteJobMessage message) {
                switch (message.getContentCase()) {
                    case SESSION_ID -> {
                        // First message: correlate with the pending job
                        sessionId = message.getSessionId();
                        System.out.printf("Job session started: %s%n", sessionId);

                        JobRequest jobRequest = pendingJobs.remove(sessionId);
                        if (jobRequest != null) {
                            // Send the job initialization to the agent
                            responseObserver.onNext(JobInstruction.newBuilder()
                                    .setJobInit(jobRequest)
                                    .build());
                        }
                    }
                    case UPDATE -> {
                        // Subsequent messages: status updates, telemetry, results
                        JobResponse update = message.getUpdate();
                        switch (update.getResponseCase()) {
                            case STATUS -> System.out.printf("[%s] Status: %s - %s%n",
                                    sessionId, update.getStatus().getState(), update.getStatus().getMessage());
                            case TELEMETRY -> System.out.printf("[%s] Telemetry: %s%n",
                                    sessionId, update.getTelemetry().getMessage());
                            case RESULT -> {
                                JobResult result = update.getResult();
                                System.out.printf("[%s] Result: %s (%d steps)%n",
                                        sessionId, result.getStatus().getState(), result.getStepsCount());
                            }
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.printf("[%s] Job stream error: %s%n", sessionId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.printf("[%s] Job session closed%n", sessionId);
                responseObserver.onCompleted();
            }
        };
    }

    // ─── Execution Plane: Translation ───────────────────────────────

    @Override
    public StreamObserver<TranslateMessage> translate(StreamObserver<TranslationInstruction> responseObserver) {
        // Same pattern as executeJob — handle session_id first, then updates.
        return new StreamObserver<>() {
            private String sessionId;

            @Override
            public void onNext(TranslateMessage message) {
                switch (message.getContentCase()) {
                    case SESSION_ID -> {
                        sessionId = message.getSessionId();
                        System.out.printf("Translation session started: %s%n", sessionId);

                        // Send the translation init to the agent
                        responseObserver.onNext(TranslationInstruction.newBuilder()
                                .setTranslationInit(TranslationInit.newBuilder()
                                        .setTargetFormat("playwright-js")
                                        .setPayload(Payload.newBuilder()
                                                .setType("selenium-side")
                                                .setRawData(com.google.protobuf.ByteString.copyFromUtf8("..."))
                                                .build())
                                        .build())
                                .build());
                    }
                    case UPDATE -> {
                        TranslationResponse update = message.getUpdate();
                        switch (update.getResponseCase()) {
                            case STATUS -> System.out.printf("[%s] Translation Status: %s%n",
                                    sessionId, update.getStatus().getState());
                            case TELEMETRY -> System.out.printf("[%s] Translation Log: %s%n",
                                    sessionId, update.getTelemetry().getMessage());
                            case RESULT -> System.out.printf("[%s] Translation Complete: %s%n",
                                    sessionId, update.getResult().getStatus().getState());
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.printf("[%s] Translation stream error: %s%n", sessionId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.printf("[%s] Translation session closed%n", sessionId);
                responseObserver.onCompleted();
            }
        };
    }

    // ─── Helper: Dispatch a job to an agent ─────────────────────────

    /**
     * Dispatches a job to a connected agent.
     * Call this from your CMS logic when a user triggers a test run.
     */
    public void dispatchJob(String agentId, JobRequest jobRequest) {
        StreamObserver<HubDirective> listener = agentListeners.get(agentId);
        if (listener == null) {
            System.err.printf("Agent %s is not connected%n", agentId);
            return;
        }

        String sessionId = UUID.randomUUID().toString();
        pendingJobs.put(sessionId, jobRequest);

        listener.onNext(HubDirective.newBuilder()
                .setRunJob(JobAssignment.newBuilder()
                        .setSessionId(sessionId)
                        .build())
                .build());

        System.out.printf("Dispatched job %s to agent %s%n", sessionId, agentId);
    }
}
```

### 1.2 — Starting the Server

```java
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class HubMain {
    public static void main(String[] args) throws Exception {
        HubService hubService = new HubService();

        Server server = ServerBuilder.forPort(9090)
                .addService(hubService)
                .build()
                .start();

        System.out.println("Hub server started on port 9090");
        server.awaitTermination();
    }
}
```

---

## Part 2: Implementing the Agent (Client)

The Agent is the gRPC **client**. It connects to the Hub, registers itself, listens for directives, and opens execution streams when assigned work.

### 2.1 — Agent Implementation

```java
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.Iterator;

public class AgentMain {

    public static void main(String[] args) throws InterruptedException {
        // Connect to the Hub
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        // We need both blocking (for Register) and async (for streams) stubs
        UniversalHubGrpc.UniversalHubBlockingStub blockingStub = UniversalHubGrpc.newBlockingStub(channel);
        UniversalHubGrpc.UniversalHubStub asyncStub = UniversalHubGrpc.newStub(channel);

        // ─── Step 1: Register ───────────────────────────────────────

        RegistrationResponse registration = blockingStub.register(RegistrationRequest.newBuilder()
                .setDisplayName("Selenium Agent - Dev Machine")
                .setCapabilities(Capabilities.newBuilder()
                        .addItems(Capability.newBuilder()
                                .setId("selenium-side-runner")
                                .setTest(TestCapability.newBuilder()
                                        .setTestType("selenium-side")
                                        .build())
                                .build())
                        .build())
                .build());

        String agentId = registration.getAgentId();
        System.out.printf("Registered with agent_id: %s%n", agentId);

        // ─── Step 2: Listen for directives (blocking iterator) ──────

        Iterator<HubDirective> directives = blockingStub.listen(
                AgentIdentity.newBuilder().setAgentId(agentId).build());

        System.out.println("Listening for directives...");

        while (directives.hasNext()) {
            HubDirective directive = directives.next();

            switch (directive.getInstructionCase()) {
                case RUN_JOB -> {
                    String sessionId = directive.getRunJob().getSessionId();
                    System.out.printf("Received job assignment: %s%n", sessionId);

                    // Open a new ExecuteJob stream on a separate thread
                    new Thread(() -> executeJob(asyncStub, sessionId)).start();
                }
                case START_TRANSLATION -> {
                    String sessionId = directive.getStartTranslation().getSessionId();
                    System.out.printf("Received translation assignment: %s%n", sessionId);

                    new Thread(() -> executeTranslation(asyncStub, sessionId)).start();
                }
                case HEARTBEAT_REQUEST -> {
                    System.out.println("Heartbeat received");
                }
            }
        }
    }

    // ─── Job Execution ──────────────────────────────────────────────

    private static void executeJob(UniversalHubGrpc.UniversalHubStub stub, String sessionId) {
        // The Hub's response stream (JobInstructions) will be handled by this observer
        StreamObserver<ExecuteJobMessage> requestObserver = stub.executeJob(new StreamObserver<>() {
            @Override
            public void onNext(JobInstruction instruction) {
                switch (instruction.getContentCase()) {
                    case JOB_INIT -> {
                        JobRequest req = instruction.getJobInit();
                        System.out.printf("[%s] Received job: payload type = %s%n",
                                sessionId, req.getPayload().getType());
                    }
                    case COMMAND -> {
                        JobCommand cmd = instruction.getCommand();
                        System.out.printf("[%s] Received command: %s%n", sessionId, cmd.getCommandCase());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.printf("[%s] Job stream error: %s%n", sessionId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.printf("[%s] Job stream closed by Hub%n", sessionId);
            }
        });

        // Step 1: Send session_id as the first message
        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setSessionId(sessionId)
                .build());

        // Step 2: Send ACKNOWLEDGED status
        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setUpdate(JobResponse.newBuilder()
                        .setTimestamp(now())
                        .setStatus(JobStatus.newBuilder()
                                .setState(JobStatus.State.ACKNOWLEDGED)
                                .setMessage("Job accepted")
                                .build())
                        .build())
                .build());

        // Step 3: Send EXECUTING status
        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setUpdate(JobResponse.newBuilder()
                        .setTimestamp(now())
                        .setStatus(JobStatus.newBuilder()
                                .setState(JobStatus.State.EXECUTING)
                                .setMessage("Running tests...")
                                .build())
                        .build())
                .build());

        // Step 4: Stream telemetry
        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setUpdate(JobResponse.newBuilder()
                        .setTimestamp(now())
                        .setTelemetry(Telemetry.newBuilder()
                                .setMessage("Opened browser: Chrome 130")
                                .build())
                        .build())
                .build());

        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setUpdate(JobResponse.newBuilder()
                        .setTimestamp(now())
                        .setTelemetry(Telemetry.newBuilder()
                                .setMessage("Navigated to https://example.com/login")
                                .build())
                        .build())
                .build());

        // Step 5: Send COMPLETED status
        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setUpdate(JobResponse.newBuilder()
                        .setTimestamp(now())
                        .setStatus(JobStatus.newBuilder()
                                .setState(JobStatus.State.COMPLETED)
                                .setMessage("All tests passed")
                                .build())
                        .build())
                .build());

        // Step 6: Send final JobResult
        requestObserver.onNext(ExecuteJobMessage.newBuilder()
                .setUpdate(JobResponse.newBuilder()
                        .setTimestamp(now())
                        .setResult(JobResult.newBuilder()
                                .setStatus(JobStatus.newBuilder()
                                        .setState(JobStatus.State.COMPLETED)
                                        .setMessage("All tests passed")
                                        .build())
                                .addSteps(StepReport.newBuilder()
                                        .setName("open /login")
                                        .setStatus(JobStatus.newBuilder()
                                                .setState(JobStatus.State.COMPLETED)
                                                .build())
                                        .build())
                                .addSteps(StepReport.newBuilder()
                                        .setName("click #submit")
                                        .setStatus(JobStatus.newBuilder()
                                                .setState(JobStatus.State.COMPLETED)
                                                .build())
                                        .build())
                                .addLogs("Test suite completed in 2.3s")
                                .build())
                        .build())
                .build());

        // Step 7: Close the stream
        requestObserver.onCompleted();
    }

    // ─── Translation Execution ──────────────────────────────────────

    private static void executeTranslation(UniversalHubGrpc.UniversalHubStub stub, String sessionId) {
        StreamObserver<TranslateMessage> requestObserver = stub.translate(new StreamObserver<>() {
            @Override
            public void onNext(TranslationInstruction instruction) {
                switch (instruction.getContentCase()) {
                    case TRANSLATION_INIT -> {
                        TranslationInit init = instruction.getTranslationInit();
                        System.out.printf("[%s] Translate: %s -> %s%n",
                                sessionId, init.getPayload().getType(), init.getTargetFormat());
                    }
                    case COMMAND -> System.out.printf("[%s] Translation command: %s%n",
                            sessionId, instruction.getCommand().getCommandCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.printf("[%s] Translation error: %s%n", sessionId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.printf("[%s] Translation stream closed by Hub%n", sessionId);
            }
        });

        // Step 1: Send session_id
        requestObserver.onNext(TranslateMessage.newBuilder()
                .setSessionId(sessionId)
                .build());

        // Step 2: ACKNOWLEDGED
        requestObserver.onNext(TranslateMessage.newBuilder()
                .setUpdate(TranslationResponse.newBuilder()
                        .setTimestamp(now())
                        .setStatus(TranslationStatus.newBuilder()
                                .setState(TranslationStatus.State.ACKNOWLEDGED)
                                .setMessage("Translation accepted")
                                .build())
                        .build())
                .build());

        // Step 3: PROCESSING
        requestObserver.onNext(TranslateMessage.newBuilder()
                .setUpdate(TranslationResponse.newBuilder()
                        .setTimestamp(now())
                        .setStatus(TranslationStatus.newBuilder()
                                .setState(TranslationStatus.State.PROCESSING)
                                .setMessage("Converting 5 test cases...")
                                .build())
                        .build())
                .build());

        // Step 4: COMPLETED + Result
        requestObserver.onNext(TranslateMessage.newBuilder()
                .setUpdate(TranslationResponse.newBuilder()
                        .setTimestamp(now())
                        .setStatus(TranslationStatus.newBuilder()
                                .setState(TranslationStatus.State.COMPLETED)
                                .build())
                        .build())
                .build());

        requestObserver.onNext(TranslateMessage.newBuilder()
                .setUpdate(TranslationResponse.newBuilder()
                        .setTimestamp(now())
                        .setResult(TranslationResult.newBuilder()
                                .setStatus(TranslationStatus.newBuilder()
                                        .setState(TranslationStatus.State.COMPLETED)
                                        .build())
                                .setTranslatedPayload(Payload.newBuilder()
                                        .setType("playwright-js")
                                        .setRawData(com.google.protobuf.ByteString.copyFromUtf8(
                                                "const { test } = require('@playwright/test');"))
                                        .build())
                                .setTranslationLog("Converted 5/5 test cases. 0 warnings.")
                                .build())
                        .build())
                .build());

        // Step 5: Close stream
        requestObserver.onCompleted();
    }

    // ─── Utility ────────────────────────────────────────────────────

    private static Timestamp now() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1_000_000))
                .build();
    }
}
```

---

## Part 3: Handling the `stop` and `help` Commands

Every UAP Agent **MUST** implement the `help` and `stop` commands. Here is how to handle them inside the `executeJob` method:

```java
// Inside the JobInstruction handler
case COMMAND -> {
    JobCommand cmd = instruction.getCommand();
    switch (cmd.getCommandCase()) {
        case HELP -> {
            // Respond with a Telemetry message listing available commands
            requestObserver.onNext(ExecuteJobMessage.newBuilder()
                    .setUpdate(JobResponse.newBuilder()
                            .setTimestamp(now())
                            .setTelemetry(Telemetry.newBuilder()
                                    .setMessage("Available commands: help, stop, pause, resume")
                                    .setExtraType("help")
                                    .setMetadata(com.google.protobuf.Struct.newBuilder()
                                            .putFields("help", toValue("Show this help message"))
                                            .putFields("stop", toValue("Stop the current test"))
                                            .putFields("pause", toValue("Pause the test execution"))
                                            .putFields("resume", toValue("Resume a paused test"))
                                            .build())
                                    .build())
                            .build())
                    .build());
        }
        case STOP -> {
            // Gracefully terminate: send FAILED status + partial result
            requestObserver.onNext(ExecuteJobMessage.newBuilder()
                    .setUpdate(JobResponse.newBuilder()
                            .setTimestamp(now())
                            .setStatus(JobStatus.newBuilder()
                                    .setState(JobStatus.State.FAILED)
                                    .setMessage("Stopped by Hub command")
                                    .build())
                            .build())
                    .build());

            requestObserver.onNext(ExecuteJobMessage.newBuilder()
                    .setUpdate(JobResponse.newBuilder()
                            .setTimestamp(now())
                            .setResult(JobResult.newBuilder()
                                    .setStatus(JobStatus.newBuilder()
                                            .setState(JobStatus.State.FAILED)
                                            .setMessage("Stopped by Hub command")
                                            .build())
                                    .addLogs("Execution stopped at step 3 of 5")
                                    .build())
                            .build())
                    .build());

            requestObserver.onCompleted();
        }
        case CUSTOM -> {
            String customCmd = cmd.getCustom();
            System.out.printf("Custom command: %s%n", customCmd);
        }
    }
}
```

---

## Part 4: Reconnection Strategy

If the `Listen` stream disconnects, the Agent should reconnect with exponential backoff:

```java
private static void listenWithBackoff(
        UniversalHubGrpc.UniversalHubBlockingStub stub,
        UniversalHubGrpc.UniversalHubStub asyncStub,
        String agentId) {

    long delay = 1000; // Initial delay: 1 second
    final long maxDelay = 60000; // Maximum delay: 60 seconds

    while (true) {
        try {
            Iterator<HubDirective> directives = stub.listen(
                    AgentIdentity.newBuilder().setAgentId(agentId).build());

            delay = 1000; // Reset delay on successful connection

            while (directives.hasNext()) {
                HubDirective directive = directives.next();
                // ... handle directive (see Part 2)
            }
        } catch (Exception e) {
            System.err.printf("Listen stream disconnected: %s. Reconnecting in %dms...%n",
                    e.getMessage(), delay);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            delay = Math.min(delay * 2, maxDelay); // Exponential backoff with cap
        }
    }
}
```
