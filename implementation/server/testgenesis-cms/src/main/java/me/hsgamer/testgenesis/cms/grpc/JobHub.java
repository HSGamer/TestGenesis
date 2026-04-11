package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.JobSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class JobHub extends MutinyJobHubGrpc.JobHubImplBase {
    private final UAPService uapService;

    @Override
    public Multi<JobInstruction> execute(Multi<JobResponse> requests) {
        String sessionId = UAPService.SESSION_ID_CTX.get();
        if (sessionId == null) {
            return Multi.createFrom().failure(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
        }

        JobSession session = uapService.getJobSessions().get(sessionId);

        if (session == null) {
            return Multi.createFrom().failure(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
        }

        log.info("Job execution stream opened for session: {}", sessionId);

        requests.subscribe().with(
                response -> {
                    switch (response.getEventCase()) {
                        case STATUS -> session.updateStatus(response.getStatus());
                        case TELEMETRY -> session.dispatchTelemetry(response.getTelemetry());
                        case RESULT -> session.completeWithResult(response.getResult());
                    }
                },
                failure -> {
                    log.error("Job stream failure for session {}", sessionId, failure);
                    session.updateStatus(JobStatus.newBuilder()
                            .setState(JobState.JOB_STATE_FAILED)
                            .setMessage("Stream failed: " + failure.getMessage())
                            .build());
                },
                () -> {
                    log.info("Job stream completed by client for session {}", sessionId);
                }
        );

        return Multi.createFrom().emitter(emitter -> {
            JobInstruction initMsg = JobInstruction.newBuilder()
                    .setJobInit(JobRequest.newBuilder()
                            .setTestType(session.getTicket().testType())
                            .addAllPayloads(session.getTicket().payloads()))
                    .build();
            emitter.emit(initMsg);
            
            session.setCommandDispatcher(cmd -> {
                emitter.emit(JobInstruction.newBuilder().setCommand(cmd).build());
            });
        });
    }
}

