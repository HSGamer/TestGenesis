package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.JobHubGrpc;
import me.hsgamer.testgenesis.uap.v1.JobInstruction;
import me.hsgamer.testgenesis.uap.v1.JobResponse;

public class JobService extends JobHubGrpc.JobHubImplBase {
    @Override
    public StreamObserver<JobResponse> execute(StreamObserver<JobInstruction> responseObserver) {
        String sessionId = IdInterceptor.SESSION_ID_CONTEXT_KEY.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing x-session-id header")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }

        return new StreamObserver<>() {
            @Override
            public void onNext(JobResponse value) {
                // TODO: Handle job response
            }

            @Override
            public void onError(Throwable t) {
                // TODO: Handle error
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
