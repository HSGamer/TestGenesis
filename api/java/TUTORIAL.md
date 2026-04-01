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
import me.hsgamer.testgenesis.uap.v1.JobResponse;
import me.hsgamer.testgenesis.uap.v1.JobInstruction;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobHubService extends JobHubGrpc.JobHubImplBase {
    @Override
    public StreamObserver<JobListenRequest> listen(StreamObserver<JobListenResponse> responseObserver) {
        return new StreamObserver<>() {
            private String agentName;

            @Override
            public void onNext(JobListenRequest req) {
                if (req.hasRegistration()) {
                    agentName = req.getRegistration().getDisplayName();
                    System.out.printf("Job Agent '%s' registered.%n", agentName);
                } else if (req.hasReady()) {
                    System.out.printf("Agent '%s' is ready for work.%n", agentName);
                    // Check queue and dispatch:
                    // responseObserver.onNext(JobListenResponse.newBuilder().setRunJob(...).build());
                }
            }

            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }

    @Override
    public StreamObserver<JobResponse> execute(StreamObserver<JobInstruction> responseObserver) {
        // Implementation using 'uap-session-id' header via ServerCallInterceptor (not shown)
        return new StreamObserver<>() {
            @Override
            public void onNext(JobResponse msg) {
                // Handle status/telemetry/result...
            }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }
}
```

### 1.2 — TranslationHub Implementation
The `TranslationHub` follows the same bi-directional pattern for `Listen` and `Translate`.

---

## Part 2: Implementing the Agent (Client)

### 2.1 — Agent Listening for Jobs

```java
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.*;
import com.google.protobuf.Empty;

public class JobAgent {
    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090).usePlaintext().build();
        JobHubGrpc.JobHubStub asyncStub = JobHubGrpc.newStub(channel);

        StreamObserver<JobListenRequest> listenStream = asyncStub.listen(new StreamObserver<>() {
            @Override
            public void onNext(JobListenResponse res) {
                if (res.hasRunJob()) {
                    String sessionId = res.getRunJob().getSessionId();
                    startJobSession(asyncStub, sessionId);
                }
            }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });

        // 1. Send Registration
        listenStream.onNext(JobListenRequest.newBuilder()
                .setRegistration(JobRegistration.newBuilder()
                        .setDisplayName("Java Job Runner")
                        .addCapabilities(TestCapability.newBuilder().setTestType("selenium-side").build())
                        .build())
                .build());

        // 2. Send periodic Ready signals (heartbeats)
        while (true) {
            Thread.sleep(30000);
            listenStream.onNext(JobListenRequest.newBuilder()
                    .setReady(Empty.getDefaultInstance())
                    .build());
        }
    }

    private static void startJobSession(JobHubGrpc.JobHubStub stub, String sessionId) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("uap-session-id", Metadata.ASCII_STRING_MARSHALLER), sessionId);

        JobHubGrpc.JobHubStub sessionStub = MetadataUtils.attachHeaders(stub, metadata);
        sessionStub.execute(new StreamObserver<>() {
            @Override public void onNext(JobInstruction ins) { /* Handle initialization/commands */ }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });
    }
}
```
