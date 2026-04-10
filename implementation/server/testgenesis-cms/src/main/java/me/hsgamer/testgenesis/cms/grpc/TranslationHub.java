package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.impl.DefaultTranslationSession;
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

        DefaultTranslationSession session = uapService.getTranslationSessions().get(sessionId);
        if (session == null) {
            return Multi.createFrom().failure(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
        }

        log.info("Translation execution stream opened for session: {}", sessionId);

        Multi<TranslationInit> requestHandler = requests
                .onItem().transformToUniAndConcatenate(value -> {
                    switch (value.getEventCase()) {
                        case STATUS -> session.updateStatus(value.getStatus());
                        case TELEMETRY -> session.dispatchTelemetry(value.getTelemetry());
                        case RESULT -> session.completeWithResult(value.getResult());
                        case EVENT_NOT_SET -> {
                        }
                    }
                    return Uni.createFrom().<TranslationInit>nullItem();
                })
                .onTermination().invoke((failure, cancelled) -> {
                    if (cancelled || failure != null) {
                        session.updateStatus(TranslationStatus.newBuilder()
                                .setState(TranslationState.TRANSLATION_STATE_FAILED)
                                .setMessage("Stream terminated: failure=" + (failure != null ? failure.getMessage() : "none") + ", cancelled=" + cancelled)
                                .build());
                    }
                });

        Multi<TranslationInit> inboundInit = Multi.createFrom().item(TranslationInit.newBuilder()
                .setTargetFormat(session.getTicket().targetFormat())
                .addAllPayloads(session.getTicket().payloads())
                .build());

        return Multi.createBy().merging().streams(requestHandler, inboundInit)
                .onTermination().invoke((failure, cancelled) -> {
                    log.info("Translation stream termination for session {}: failure={}, cancelled={}",
                            sessionId, failure != null ? failure.getMessage() : "none", cancelled);
                });
    }
}
