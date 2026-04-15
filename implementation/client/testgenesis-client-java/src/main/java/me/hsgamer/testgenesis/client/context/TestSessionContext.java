package me.hsgamer.testgenesis.client.context;

import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.client.utils.UapUtils;
import me.hsgamer.testgenesis.uap.v1.*;

/**
 * Domain-specific context for Test sessions.
 */
public class TestSessionContext extends SessionContext<TestInit, TestResponse> {

    public TestSessionContext(TestInit init, StreamObserver<TestResponse> responseObserver) {
        super(init, responseObserver);
    }

    /**
     * Sends a status report.
     *
     * @param status The status.
     */
    public void sendStatus(TestStatus status) {
        responseObserver.onNext(TestResponse.newBuilder()
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
        responseObserver.onNext(TestResponse.newBuilder()
            .setTimestamp(UapUtils.now())
            .setTelemetry(Telemetry.newBuilder()
                .setMessage(message)
                .setTimestamp(UapUtils.now())
                .setSeverity(severity)
                .setSource("agent")
                .build())
            .build());
    }

    /**
     * Sends the final result.
     *
     * @param result The result.
     */
    public void sendResult(TestResult result) {
        responseObserver.onNext(TestResponse.newBuilder()
            .setTimestamp(UapUtils.now())
            .setResult(result)
            .build());
    }
}
