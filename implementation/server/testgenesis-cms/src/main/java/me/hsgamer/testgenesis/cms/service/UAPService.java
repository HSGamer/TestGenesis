package me.hsgamer.testgenesis.cms.service;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.helidon.webserver.grpc.GrpcRouting;
import me.hsgamer.testgenesis.uap.v1.*;

public class UAPService {
    private static final Metadata.Key<String> CLIENT_ID_KEY =
            Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SESSION_ID_KEY =
            Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");

    public static final UAPService INSTANCE = new UAPService();

    private UAPService() {
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
        // TODO: generate client_id, store agent capabilities, respond
    }

    private StreamObserver<ListenRequest> listen(StreamObserver<ListenResponse> responseObserver) {
        String clientId = CLIENT_ID_CTX.get();
        if (clientId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Missing required header: x-client-id")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        // TODO: register the control stream, dispatch proposals
        return NoOpStreamObserver.getInstance();
    }

    private StreamObserver<JobResponse> execute(StreamObserver<JobInstruction> responseObserver) {
        String sessionId = SESSION_ID_CTX.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        // TODO: correlate session, flush pending JobRequest, stream telemetry
        return NoOpStreamObserver.getInstance();
    }

    private StreamObserver<TranslationResponse> translate(StreamObserver<TranslationInit> responseObserver) {
        String sessionId = SESSION_ID_CTX.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        // TODO: correlate session, flush pending TranslationInit, stream telemetry
        return NoOpStreamObserver.getInstance();
    }

    private static class IdInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

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
}
