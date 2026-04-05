package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.TranslationHubGrpc;
import me.hsgamer.testgenesis.uap.v1.TranslationInit;
import me.hsgamer.testgenesis.uap.v1.TranslationResponse;

public class TranslationService extends TranslationHubGrpc.TranslationHubImplBase {
    @Override
    public StreamObserver<TranslationResponse> translate(StreamObserver<TranslationInit> responseObserver) {
        String sessionId = IdInterceptor.SESSION_ID_CONTEXT_KEY.get();
        if (sessionId == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Missing x-session-id header")
                    .asRuntimeException());
            return NoOpStreamObserver.getInstance();
        }
        
        return new StreamObserver<>() {
            @Override
            public void onNext(TranslationResponse value) {
                // TODO: Handle translation response
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
