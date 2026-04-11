package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class TranslationHub extends MutinyTranslationHubGrpc.TranslationHubImplBase {
    private final UAPService uapService;

    @Override
    public Multi<TranslationInit> translate(Multi<TranslationResponse> requests) {
        String sessionId = UAPService.SESSION_ID_CTX.get();
        if (sessionId == null) {
            return Multi.createFrom().failure(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
        }

        TranslationSession session = uapService.getTranslationSessions().get(sessionId);

        if (session == null) {
            return Multi.createFrom().failure(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
        }

        log.info("Translation execution stream opened for session: {}", sessionId);

        // 1. Handle incoming responses from the agent
        requests.subscribe().with(
                response -> {
                    switch (response.getEventCase()) {
                        case STATUS -> session.updateStatus(response.getStatus());
                        case TELEMETRY -> session.dispatchTelemetry(response.getTelemetry());
                        case RESULT -> session.completeWithResult(response.getResult());
                    }
                },
                failure -> {
                    log.error("Translation stream failure for session {}", sessionId, failure);
                    session.updateStatus(TranslationStatus.newBuilder()
                            .setState(TranslationState.TRANSLATION_STATE_FAILED)
                            .setMessage("Stream failed: " + failure.getMessage())
                            .build());
                },
                () -> {
                    log.info("Translation stream completed by client for session {}", sessionId);
                }
        );

        // 2. Return the outgoing initialization payload
        return Multi.createFrom().emitter(emitter -> {
            TranslationInit initMsg = TranslationInit.newBuilder()
                    .setTargetFormat(session.getTicket().targetFormat())
                    .addAllPayloads(session.getTicket().payloads())
                    .build();
            emitter.emit(initMsg);

            // Completes the stream on the server side because we only ever send 1 message. 
            // In a continuous outbound stream we'd keep it open, but complete() is fine here.
            // Wait, we probably need to keep the gRPC stream open if we expect the client to keep sending data.
            // Under mutiny gRPC bidirectional streams, if the server completes its returned stream,
            // the server closes its write direction, but the read direction can technically stay open.
            // However, usually it's best to not complete if the client expects a long-lived bidirectional channel.
            // We'll leave it open but not emit anything else, and just clean up on termination if needed.
        });
    }
}
