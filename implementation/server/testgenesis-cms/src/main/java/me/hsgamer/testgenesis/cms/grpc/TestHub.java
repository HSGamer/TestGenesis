package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class TestHub extends MutinyTestHubGrpc.TestHubImplBase {
    private final UAPService uapService;

    @Override
    public Multi<TestInit> execute(Multi<TestResponse> requests) {
        String sessionId = UAPService.SESSION_ID_CTX.get();
        if (sessionId == null) {
            return Multi.createFrom().failure(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
        }

        TestSession session = uapService.getTestSessions().get(sessionId);

        if (session == null) {
            return Multi.createFrom().failure(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
        }

        log.info("Test execution stream opened for session: {}", sessionId);

        requests.subscribe().with(
                response -> {
                    switch (response.getEventCase()) {
                        case STATUS -> session.updateStatus(response.getStatus());
                        case TELEMETRY -> session.dispatchTelemetry(response.getTelemetry());
                        case RESULT -> session.completeWithResult(response.getResult());
                    }
                },
                failure -> {
                    log.error("Test stream failure for session {}", sessionId, failure);
                    session.updateStatus(TestStatus.newBuilder()
                            .setState(TestState.TEST_STATE_FAILED)
                            .setMessage("Stream failed: " + failure.getMessage())
                            .build());
                },
                () -> {
                    log.info("Test stream completed by client for session {}", sessionId);
                }
        );

        return Multi.createFrom().emitter(emitter -> {
            TestInit initMsg = TestInit.newBuilder()
                    .setTestType(session.getTicket().testType())
                    .addAllPayloads(session.getTicket().payloads())
                    .build();
            emitter.emit(initMsg);
        });
    }
}

