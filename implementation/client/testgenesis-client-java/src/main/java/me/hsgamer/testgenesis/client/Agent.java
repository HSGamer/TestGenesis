package me.hsgamer.testgenesis.client;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.client.context.TranslationSessionContext;
import me.hsgamer.testgenesis.client.processor.TestSessionProcessor;
import me.hsgamer.testgenesis.client.processor.TranslationSessionProcessor;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UAP Agent client for Java.
 * Multithreaded, pure Java implementation using gRPC-Java.
 */
public class Agent {
    private static final Logger logger = Logger.getLogger(Agent.class.getName());

    private final String hubUrl;
    private final String displayName;
    private final Map<String, Object> processors = new ConcurrentHashMap<>();
    private final Map<String, String> pendingSessions = new ConcurrentHashMap<>();
    private final ExecutorService sessionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean shuttingDown = false;
    private ManagedChannel channel;
    private StreamObserver<ListenRequest> controlRequestObserver;

    public Agent(String hubUrl, String displayName) {
        this.hubUrl = hubUrl;
        this.displayName = displayName;
    }

    public void registerTestProcessor(TestSessionProcessor processor) {
        processors.put("test", processor);
    }

    public void registerTranslationProcessor(TranslationSessionProcessor processor) {
        processors.put("translation", processor);
    }

    public void start() {
        logger.info("[Agent] Starting: " + displayName);

        while (!shuttingDown) {
            try {
                runLifecycle();
            } catch (Exception e) {
                if (shuttingDown) break;
                logger.log(Level.SEVERE, "[Agent][Fatal] Disconnected. Retrying in 5s...", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void shutdown() {
        shuttingDown = true;
        if (channel != null) {
            channel.shutdownNow();
        }
        sessionExecutor.shutdownNow();
    }

    private void runLifecycle() throws Exception {
        String host = hubUrl;
        int port = 80;
        if (hubUrl.startsWith("http://")) {
            host = hubUrl.substring(7);
        } else if (hubUrl.startsWith("https://")) {
            host = hubUrl.substring(8);
            port = 443;
        }

        String[] parts = host.split(":");
        if (parts.length > 1) {
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        }

        channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext() // For production, should be configurable
            .build();

        AgentHubGrpc.AgentHubBlockingStub blockingStub = AgentHubGrpc.newBlockingStub(channel);
        AgentHubGrpc.AgentHubStub asyncStub = AgentHubGrpc.newStub(channel);

        // 1. Identification
        AgentRegistration.Builder registrationBuilder = AgentRegistration.newBuilder()
            .setDisplayName(displayName);

        for (Object p : processors.values()) {
            if (p instanceof TestSessionProcessor tp) {
                registrationBuilder.addCapabilities(tp.getCapability());
            } else if (p instanceof TranslationSessionProcessor tp) {
                registrationBuilder.addCapabilities(tp.getCapability());
            }
        }

        AgentRegistrationResponse registration = blockingStub.register(registrationBuilder.build());
        String clientId = registration.getClientId();
        logger.info("[Agent] Connected as " + clientId);

        // 2. Control Stream
        Metadata metadata = new Metadata();
        Metadata.Key<String> clientIdKey = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(clientIdKey, clientId);

        AgentHubGrpc.AgentHubStub metaAsyncStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        final CountDownLatch streamLatch = new CountDownLatch(1);

        this.controlRequestObserver = metaAsyncStub.listen(new StreamObserver<ListenResponse>() {
            @Override
            public void onNext(ListenResponse response) {
                handleListenResponse(response, asyncStub);
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "[Agent] Listen stream error", t);
                streamLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("[Agent] Listen stream completed by server");
                streamLatch.countDown();
            }
        });

        controlRequestObserver.onNext(ListenRequest.newBuilder()
            .setReady(Empty.getDefaultInstance())
            .build());

        streamLatch.await();
    }

    private void handleListenResponse(ListenResponse response, AgentHubGrpc.AgentHubStub stub) {
        if (response.hasSessionProposal()) {
            SessionProposal proposal = response.getSessionProposal();
            String sessionId = proposal.getSessionId();
            String type = proposal.getDetailsCase().name().toLowerCase();

            logger.info("[Agent][" + type + "] Handling Proposal: " + sessionId);

            boolean accepted = processors.containsKey(type);

            if (accepted) {
                pendingSessions.put(sessionId, type);
            } else {
                logger.warning("[Agent][" + type + "] Rejecting: No processor registered.");
            }

            controlRequestObserver.onNext(ListenRequest.newBuilder()
                .setSessionAcceptance(SessionAcceptance.newBuilder()
                    .setSessionId(sessionId)
                    .setAccepted(accepted)
                    .build())
                .build());

        } else if (response.hasSessionReady()) {
            String sessionId = response.getSessionReady().getSessionId();
            String type = pendingSessions.remove(sessionId);
            if (type != null) {
                sessionExecutor.submit(() -> handleSessionReady(sessionId, type, stub));
            }
        }
    }

    private void handleSessionReady(String sessionId, String type, AgentHubGrpc.AgentHubStub asyncStub) {
        Metadata metadata = new Metadata();
        Metadata.Key<String> sessionIdKey = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(sessionIdKey, sessionId);
        AgentHubGrpc.AgentHubStub sessionStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        if ("test".equals(type)) {
            TestSessionProcessor processor = (TestSessionProcessor) processors.get("test");
            handleTestSession(sessionId, processor, sessionStub);
        } else if ("translation".equals(type)) {
            TranslationSessionProcessor processor = (TranslationSessionProcessor) processors.get("translation");
            handleTranslationSession(sessionId, processor, sessionStub);
        }
    }

    private void handleTestSession(String sessionId, TestSessionProcessor processor, AgentHubGrpc.AgentHubStub stub) {
        BlockingQueue<TestInit> initQueue = new LinkedBlockingQueue<>(1);

        StreamObserver<TestResponse> responseObserver = stub.execute(new StreamObserver<TestInit>() {
            @Override
            public void onNext(TestInit value) {
                initQueue.offer(value);
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.SEVERE, "[Agent][Test] Stream error (" + sessionId + ")", t);
            }

            @Override
            public void onCompleted() {
                logger.info("[Agent][Test] Stream completed (" + sessionId + ")");
            }
        });

        try {
            logger.info("[Agent][Test] Active: " + sessionId);
            TestInit init = initQueue.poll(30, TimeUnit.SECONDS);
            if (init == null) throw new TimeoutException("Timeout waiting for TestInit");

            TestSessionContext context = new TestSessionContext(init, responseObserver);
            try {
                processor.process(sessionId, context);
            } catch (Exception e) {
                context.sendResult(TestResult.newBuilder()
                    .setStatus(TestStatus.newBuilder()
                        .setState(TestState.TEST_STATE_FAILED)
                        .setMessage("Internal Agent Error: " + e.getMessage())
                        .build())
                    .setSummary(Summary.newBuilder()
                        .setMetadata(Struct.newBuilder()
                            .putFields("exception", Value.newBuilder().setStringValue(e.toString()).build())
                            .build())
                        .build())
                    .build());
                throw e;
            } finally {
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Agent][Test] Session processing error (" + sessionId + ")", e);
        }
    }

    private void handleTranslationSession(String sessionId, TranslationSessionProcessor processor, AgentHubGrpc.AgentHubStub stub) {
        BlockingQueue<TranslationInit> initQueue = new LinkedBlockingQueue<>(1);

        StreamObserver<TranslationResponse> responseObserver = stub.translate(new StreamObserver<TranslationInit>() {
            @Override
            public void onNext(TranslationInit value) {
                initQueue.offer(value);
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.SEVERE, "[Agent][Translation] Stream error (" + sessionId + ")", t);
            }

            @Override
            public void onCompleted() {
                logger.info("[Agent][Translation] Stream completed (" + sessionId + ")");
            }
        });

        try {
            logger.info("[Agent][Translation] Active: " + sessionId);
            TranslationInit init = initQueue.poll(30, TimeUnit.SECONDS);
            if (init == null) throw new TimeoutException("Timeout waiting for TranslationInit");

            TranslationSessionContext context = new TranslationSessionContext(init, responseObserver);
            try {
                processor.process(sessionId, context);
            } catch (Exception e) {
                context.sendResult(TranslationResult.newBuilder()
                    .setStatus(TranslationStatus.newBuilder()
                        .setState(TranslationState.TRANSLATION_STATE_FAILED)
                        .setMessage("Internal Agent Error: " + e.getMessage())
                        .build())
                    .build());
                throw e;
            } finally {
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Agent][Translation] Session processing error (" + sessionId + ")", e);
        }
    }
}
