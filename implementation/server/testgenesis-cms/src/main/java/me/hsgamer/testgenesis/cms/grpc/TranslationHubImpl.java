package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.impl.DefaultTranslationSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
public class TranslationHubImpl extends TranslationHubGrpc.TranslationHubImplBase {
    private final UAPService uapService;

    public TranslationHubImpl(UAPService uapService) {
        this.uapService = uapService;
    }

    @Override
    public StreamObserver<TranslationResponse> translate(StreamObserver<TranslationInit> responseObserver) {
        String sessionId = UAPService.SESSION_ID_CTX.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }
        DefaultTranslationSession session = uapService.getTranslationSessions().get(sessionId);
        if (session == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        // Send the translation init payload
        responseObserver.onNext(TranslationInit.newBuilder()
                .setTargetFormat(session.getTicket().targetFormat())
                .addAllPayloads(session.getTicket().payloads())
                .build());

        log.info("Translation stream opened for session: {}", sessionId);

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
                log.warn("Translation stream error for session: {}", sessionId, t);
                session.updateStatus(TranslationStatus.newBuilder()
                        .setState(TranslationState.TRANSLATION_STATE_FAILED)
                        .setMessage("Stream error: " + t.getMessage())
                        .build());
            }

            @Override
            public void onCompleted() {
                log.info("Translation stream completed for session: {}", sessionId);
                responseObserver.onCompleted();
            }
        };
    }

    private static class NoOpStreamObserver<T> implements StreamObserver<T> {
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
