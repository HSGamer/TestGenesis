package me.hsgamer.testgenesis.cms.service;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.helidon.webserver.grpc.GrpcRouting;
import me.hsgamer.testgenesis.cms.core.*;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UAPService {
    public static final UAPService INSTANCE = new UAPService();

    static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");
    private static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Map<String, AgentImpl> agents = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JobAcceptance>> pendingJobAcceptances = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TranslationAcceptance>> pendingTranslationAcceptances = new ConcurrentHashMap<>();
    private final Map<String, JobSessionImpl> jobSessions = new ConcurrentHashMap<>();
    private final Map<String, TranslationSessionImpl> translationSessions = new ConcurrentHashMap<>();

    private UAPService() {
    }

    public Map<String, Agent> getAgents() {
        return Collections.unmodifiableMap(agents);
    }

    public CompletableFuture<JobTicketResult> registerJob(String agentId, JobTicket ticket) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = "JOB-" + UUID.randomUUID();
        CompletableFuture<JobTicketResult> future = new CompletableFuture<>();

        pendingJobAcceptances.put(sessionId, acceptance -> {
            pendingJobAcceptances.remove(sessionId);
            if (acceptance.getAccepted()) {
                JobSessionImpl session = new JobSessionImpl(ticket);
                jobSessions.put(sessionId, session);
                future.complete(new JobTicketResult(true, acceptance.getReason(), session));
            } else {
                future.complete(new JobTicketResult(false, acceptance.getReason(), null));
            }
        });

        agent.listener().onNext(ListenResponse.newBuilder()
                .setJobProposal(JobProposal.newBuilder()
                        .setSessionId(sessionId)
                        .setTestType(ticket.testType()))
                .build());

        return future;
    }

    public CompletableFuture<TranslationTicketResult> registerTranslation(String agentId, TranslationTicket ticket) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = "TRN-" + UUID.randomUUID();
        CompletableFuture<TranslationTicketResult> future = new CompletableFuture<>();

        pendingTranslationAcceptances.put(sessionId, acceptance -> {
            pendingTranslationAcceptances.remove(sessionId);
            if (acceptance.getAccepted()) {
                TranslationSessionImpl session = new TranslationSessionImpl(ticket);
                translationSessions.put(sessionId, session);
                future.complete(new TranslationTicketResult(true, acceptance.getReason(), session));
            } else {
                future.complete(new TranslationTicketResult(false, acceptance.getReason(), null));
            }
        });

        agent.listener().onNext(ListenResponse.newBuilder()
                .setTranslationProposal(TranslationProposal.newBuilder()
                        .setSessionId(sessionId)
                        .setTargetType(ticket.targetFormat()))
                .build());

        return future;
    }

    public GrpcRouting.Builder routing() {
        return GrpcRouting.builder()
                .intercept(new IdInterceptor())
                .unary(AgentHubOuterClass.getDescriptor(), "AgentHub", "Register", this::register)
                .bidi(AgentHubOuterClass.getDescriptor(), "AgentHub", "Listen", this::listen)
                .bidi(JobHubOuterClass.getDescriptor(), "JobHub", "Execute", this::execute)
                .bidi(TranslationHubOuterClass.getDescriptor(), "TranslationHub", "Translate", this::translate);
    }

    private void register(AgentRegistration request, StreamObserver<AgentRegistrationResponse> responseObserver) {
        String agentId = UUID.randomUUID().toString();
        AgentImpl agent = new AgentImpl(request.getDisplayName(), request.getCapabilitiesList(), new AtomicReference<>());
        agents.put(agentId, agent);
        logger.info("Registered agent: %s (%s)".formatted(agentId, request.getDisplayName()));
        responseObserver.onNext(AgentRegistrationResponse.newBuilder().setClientId(agentId).build());
        responseObserver.onCompleted();
    }

    private StreamObserver<ListenRequest> listen(StreamObserver<ListenResponse> responseObserver) {
        String agentId = CLIENT_ID_CTX.get();
        if (agentId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Missing required header: x-client-id")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Client ID is not registered")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        if (agent.listenerRef.get() != null) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription("A client with this ID is already listening")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }

        agent.listenerRef.set(responseObserver);
        logger.info("Agent %s (%s) connected to Listen stream.".formatted(agentId, agent.displayName));

        return new StreamObserver<>() {
            @Override
            public void onNext(ListenRequest value) {
                if (value.hasJobAcceptance()) {
                    JobAcceptance acceptance = value.getJobAcceptance();
                    Consumer<JobAcceptance> consumer = pendingJobAcceptances.get(acceptance.getSessionId());
                    if (consumer != null) {
                        consumer.accept(acceptance);
                    }
                } else if (value.hasTranslationAcceptance()) {
                    TranslationAcceptance acceptance = value.getTranslationAcceptance();
                    Consumer<TranslationAcceptance> consumer = pendingTranslationAcceptances.get(acceptance.getSessionId());
                    if (consumer != null) {
                        consumer.accept(acceptance);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "Listen stream error from Agent %s (%s)".formatted(agentId, agent.displayName), t);
                agents.remove(agentId);
            }

            @Override
            public void onCompleted() {
                logger.info("Agent %s (%s) disconnected.".formatted(agentId, agent.displayName));
                agents.remove(agentId);
                responseObserver.onCompleted();
            }
        };
    }

    private StreamObserver<JobResponse> execute(StreamObserver<JobInstruction> responseObserver) {
        String sessionId = SESSION_ID_CTX.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        JobSessionImpl session = jobSessions.get(sessionId);
        if (session == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }

        session.attachInstructionStream(responseObserver);

        // Send the initial job payload
        responseObserver.onNext(JobInstruction.newBuilder()
                .setJobInit(JobRequest.newBuilder()
                        .setTestType(session.ticket.testType())
                        .addAllPayloads(session.ticket.payloads()))
                .build());

        logger.info("Job execution stream opened for session: " + sessionId);

        return new StreamObserver<>() {
            @Override
            public void onNext(JobResponse value) {
                switch (value.getEventCase()) {
                    case STATUS -> session.updateStatus(value.getStatus());
                    case TELEMETRY -> session.dispatchTelemetry(value.getTelemetry());
                    case RESULT -> session.completeWithResult(value.getResult());
                    case EVENT_NOT_SET -> {
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "Job stream error for session: " + sessionId, t);
                session.updateStatus(JobStatus.newBuilder()
                        .setState(JobState.JOB_STATE_FAILED)
                        .setMessage("Stream error: " + t.getMessage())
                        .build());
            }

            @Override
            public void onCompleted() {
                logger.info("Job stream completed for session: " + sessionId);
                responseObserver.onCompleted();
            }
        };
    }

    private StreamObserver<TranslationResponse> translate(StreamObserver<TranslationInit> responseObserver) {
        String sessionId = SESSION_ID_CTX.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        TranslationSessionImpl session = translationSessions.get(sessionId);
        if (session == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }

        // Send the translation init payload
        responseObserver.onNext(TranslationInit.newBuilder()
                .setTargetFormat(session.ticket.targetFormat())
                .addAllPayloads(session.ticket.payloads())
                .build());

        logger.info("Translation stream opened for session: " + sessionId);

        return new StreamObserver<>() {
            @Override
            public void onNext(TranslationResponse value) {
                switch (value.getEventCase()) {
                    case STATUS -> session.updateStatus(value.getStatus());
                    case TELEMETRY -> session.dispatchTelemetry(value.getTelemetry());
                    case RESULT -> session.completeWithResult(value.getResult());
                    case EVENT_NOT_SET -> {
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "Translation stream error for session: " + sessionId, t);
                session.updateStatus(TranslationStatus.newBuilder()
                        .setState(TranslationState.TRANSLATION_STATE_FAILED)
                        .setMessage("Stream error: " + t.getMessage())
                        .build());
            }

            @Override
            public void onCompleted() {
                logger.info("Translation stream completed for session: " + sessionId);
                responseObserver.onCompleted();
            }
        };
    }

    private static class IdInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String clientId = headers.get(CLIENT_ID_KEY);
            String sessionId = headers.get(SESSION_ID_KEY);

            if (clientId == null && sessionId == null) {
                return next.startCall(call, headers);
            }

            Context ctx = Context.current();
            if (clientId != null) {
                ctx = ctx.withValue(CLIENT_ID_CTX, clientId);
            }
            if (sessionId != null) {
                ctx = ctx.withValue(SESSION_ID_CTX, sessionId);
            }
            return Contexts.interceptCall(ctx, call, headers, next);
        }
    }

    private static class NoOpStreamObserver<T> implements StreamObserver<T> {
        private static final NoOpStreamObserver<Object> INSTANCE = new NoOpStreamObserver<>();

        @SuppressWarnings("unchecked")
        private static <T> StreamObserver<T> getInstance() {
            return (StreamObserver<T>) INSTANCE;
        }

        @Override
        public void onNext(T value) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }
    }

    private record AgentImpl(String displayName, List<Capability> capabilities,
                             AtomicReference<StreamObserver<ListenResponse>> listenerRef) implements Agent {
        @Override
        public boolean isReady() {
            return listenerRef.get() != null;
        }

        private StreamObserver<ListenResponse> listener() {
            return listenerRef.get();
        }
    }

    private static class JobSessionImpl implements JobSession {
        private final JobTicket ticket;
        private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
        private final List<Consumer<JobStatus>> statusConsumers = new CopyOnWriteArrayList<>();
        private volatile StreamObserver<JobInstruction> instructionStream;
        private volatile JobStatus latestStatus;
        private volatile JobResult result;

        private JobSessionImpl(JobTicket ticket) {
            this.ticket = ticket;
        }

        void attachInstructionStream(StreamObserver<JobInstruction> stream) {
            this.instructionStream = stream;
        }

        void updateStatus(JobStatus status) {
            this.latestStatus = status;
            for (Consumer<JobStatus> consumer : statusConsumers) {
                consumer.accept(status);
            }
        }

        void dispatchTelemetry(Telemetry telemetry) {
            for (Consumer<Telemetry> consumer : telemetryConsumers) {
                consumer.accept(telemetry);
            }
        }

        void completeWithResult(JobResult result) {
            this.result = result;
            if (result.hasStatus()) {
                updateStatus(result.getStatus());
            }
        }

        @Override
        public void sendCommand(JobCommand command) {
            StreamObserver<JobInstruction> stream = instructionStream;
            if (stream != null) {
                stream.onNext(JobInstruction.newBuilder().setCommand(command).build());
            }
        }

        @Override
        public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
            telemetryConsumers.add(consumer);
        }

        @Override
        public void addStatusConsumer(Consumer<JobStatus> consumer) {
            statusConsumers.add(consumer);
        }

        @Override
        public JobStatus getStatus() {
            return latestStatus;
        }

        @Override
        public JobResult getResult() {
            return result;
        }
    }

    private static class TranslationSessionImpl implements TranslationSession {
        private final TranslationTicket ticket;
        private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
        private final List<Consumer<TranslationStatus>> statusConsumers = new CopyOnWriteArrayList<>();
        private volatile TranslationStatus latestStatus;
        private volatile TranslationResult result;

        private TranslationSessionImpl(TranslationTicket ticket) {
            this.ticket = ticket;
        }

        void updateStatus(TranslationStatus status) {
            this.latestStatus = status;
            for (Consumer<TranslationStatus> consumer : statusConsumers) {
                consumer.accept(status);
            }
        }

        void dispatchTelemetry(Telemetry telemetry) {
            for (Consumer<Telemetry> consumer : telemetryConsumers) {
                consumer.accept(telemetry);
            }
        }

        void completeWithResult(TranslationResult result) {
            this.result = result;
            if (result.hasStatus()) {
                updateStatus(result.getStatus());
            }
        }

        @Override
        public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
            telemetryConsumers.add(consumer);
        }

        @Override
        public void addStatusConsumer(Consumer<TranslationStatus> consumer) {
            statusConsumers.add(consumer);
        }

        @Override
        public TranslationStatus getStatus() {
            return latestStatus;
        }

        @Override
        public TranslationResult getResult() {
            return result;
        }
    }
}
