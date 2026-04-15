package me.hsgamer.testgenesis.client.context;

import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.client.utils.UapUtils;
import me.hsgamer.testgenesis.uap.v1.*;

/**
 * Domain-specific context for Translation sessions.
 */
public class TranslationSessionContext extends SessionContext<TranslationInit, TranslationResponse> {

    public TranslationSessionContext(TranslationInit init, StreamObserver<TranslationResponse> responseObserver) {
        super(init, responseObserver);
    }

    /**
     * Sends a status report.
     *
     * @param status The status.
     */
    public void sendStatus(TranslationStatus status) {
        responseObserver.onNext(TranslationResponse.newBuilder()
            .setTimestamp(UapUtils.now())
            .setStatus(status)
            .build());
    }

    /**
     * Sends telemetry.
     *
     * @param message  The message.
     * @param severity The severity.
     */
    public void sendTelemetry(String message, Severity severity) {
        responseObserver.onNext(TranslationResponse.newBuilder()
            .setTimestamp(UapUtils.now())
            .setTelemetry(Telemetry.newBuilder()
                .setMessage(message)
                .setTimestamp(UapUtils.now())
                .setSeverity(severity)
                .setSource("translator")
                .build())
            .build());
    }

    /**
     * Sends the final result.
     *
     * @param result The result.
     */
    public void sendResult(TranslationResult result) {
        responseObserver.onNext(TranslationResponse.newBuilder()
            .setTimestamp(UapUtils.now())
            .setResult(result)
            .build());
    }
}
