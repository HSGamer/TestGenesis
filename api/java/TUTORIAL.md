# UAP Java Implementation Tutorial (Split-Service)

This tutorial shows how to implement the **JobHub** and **TranslationHub** in Java.

## Prerequisites

- Java 25
- Maven 3.9+
- The `uap-java` library built via `mvn compile`

---

## Part 1: Implementing the Hub (Server)

The services are now defined in `job.proto` and `translation.proto`.

### 1.1 — JobHub Implementation

```java
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.JobHubGrpc;
import me.hsgamer.testgenesis.uap.v1.JobListenRequest;
import me.hsgamer.testgenesis.uap.v1.JobListenResponse;
import me.hsgamer.testgenesis.uap.v1.RegistrationResponse;
import me.hsgamer.testgenesis.uap.v1.ExecuteJobMessage;
import me.hsgamer.testgenesis.uap.v1.JobInstruction;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobHubService extends JobHubGrpc.JobHubImplBase {
    private final ConcurrentHashMap<String, StreamObserver<JobListenResponse>> listeners = new ConcurrentHashMap<>();

    @Override
    public void listen(JobListenRequest request, StreamObserver<JobListenResponse> responseObserver) {
        String agentId = UUID.randomUUID().toString();
        System.out.printf("Job Agent '%s' registered: %s%n", request.getDisplayName(), agentId);

        // 1. Send RegistrationResponse as the first message
        responseObserver.onNext(JobListenResponse.newBuilder()
                .setRegistration(RegistrationResponse.newBuilder()
                        .setAgentId(agentId)
                        .setServerTime(com.google.protobuf.Timestamp.newBuilder()
                                .setSeconds(System.currentTimeMillis() / 1000)
                                .build())
                        .build())
                .build());

        listeners.put(agentId, responseObserver);
    }

    @Override
    public StreamObserver<ExecuteJobMessage> execute(StreamObserver<JobInstruction> responseObserver) {
        return new StreamObserver<>() {
            private String sessionId;

            @Override
            public void onNext(ExecuteJobMessage msg) {
                if (msg.hasSessionId()) {
                    sessionId = msg.getSessionId();
                    System.out.printf("Job session started: %s%n", sessionId);
                    // Hub sends JobRequest...
                } else if (msg.hasUpdate()) {
                    // Handle status/telemetry/result...
                }
            }

            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }
}
```

### 1.2 — TranslationHub Implementation
The `TranslationHub` follows the same pattern using `TranslationHubGrpc.TranslationHubImplBase`.

---

## Part 2: Implementing the Agent (Client)

### 2.1 — Agent Listening for Jobs

```java
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.JobHubGrpc;
import me.hsgamer.testgenesis.uap.v1.JobListenRequest;
import me.hsgamer.testgenesis.uap.v1.JobListenResponse;
import me.hsgamer.testgenesis.uap.v1.TestCapability;
import me.hsgamer.testgenesis.uap.v1.ExecuteJobMessage;
import me.hsgamer.testgenesis.uap.v1.JobInstruction;

public class JobAgent {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090).usePlaintext().build();
        JobHubGrpc.JobHubStub asyncStub = JobHubGrpc.newStub(channel);

        asyncStub.listen(JobListenRequest.newBuilder()
                .setDisplayName("Java Job Runner")
                .addCapabilities(TestCapability.newBuilder().setTestType("selenium-side").build())
                .build(), new StreamObserver<>() {
            @Override
            public void onNext(JobListenResponse res) {
                if (res.hasRegistration()) {
                    System.out.println("Registered with ID: " + res.getRegistration().getAgentId());
                } else if (res.hasRunJob()) {
                    String sessionId = res.getRunJob().getSessionId();
                    startJobSession(asyncStub, sessionId);
                }
            }

            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });
    }

    private static void startJobSession(JobHubGrpc.JobHubStub stub, String sessionId) {
        StreamObserver<ExecuteJobMessage> stream = stub.execute(new StreamObserver<>() {
            @Override public void onNext(JobInstruction ins) { /* Handle initialization/commands */ }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });

        // First message: session_id
        stream.onNext(ExecuteJobMessage.newBuilder().setSessionId(sessionId).build());
    }
}
```
