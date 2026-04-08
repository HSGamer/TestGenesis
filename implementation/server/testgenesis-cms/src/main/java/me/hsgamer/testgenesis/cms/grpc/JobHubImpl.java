package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.impl.DefaultJobSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
public class JobHubImpl extends JobHubGrpc.JobHubImplBase {
    private final UAPService uapService;

    public JobHubImpl(UAPService uapService) {
        this.uapService = uapService;
    }

    @Override
    public StreamObserver<JobResponse> execute(StreamObserver<JobInstruction> responseObserver) {
        String sessionId = UAPService.SESSION_ID_CTX.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing required header: x-session-id")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }
        DefaultJobSession session = uapService.getJobSessions().get(sessionId);
        if (session == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Session not found: " + sessionId)
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        session.attachInstructionStream(responseObserver);

        // Send the initial job payload
        responseObserver.onNext(JobInstruction.newBuilder()
                .setJobInit(JobRequest.newBuilder()
                        .setTestType(session.getTicket().testType())
                        .addAllPayloads(session.getTicket().payloads()))
                .build());

        log.info("Job execution stream opened for session: {}", sessionId);

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
                log.warn("Job stream error for session: {}", sessionId, t);
                session.updateStatus(JobStatus.newBuilder()
                        .setState(JobState.JOB_STATE_FAILED)
                        .setMessage("Stream error: " + t.getMessage())
                        .build());
            }

            @Override
            public void onCompleted() {
                log.info("Job stream completed for session: {}", sessionId);
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
