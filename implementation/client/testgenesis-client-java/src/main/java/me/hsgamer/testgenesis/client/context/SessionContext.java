package me.hsgamer.testgenesis.client.context;

import io.grpc.stub.StreamObserver;

/**
 * Common high-level operations for any active test/translation session.
 *
 * @param <TInit>     The initialization message type.
 * @param <TResponse> The response message type.
 */
public abstract class SessionContext<TInit, TResponse> {
    protected final StreamObserver<TResponse> responseObserver;
    private final TInit init;

    protected SessionContext(TInit init, StreamObserver<TResponse> responseObserver) {
        this.init = init;
        this.responseObserver = responseObserver;
    }

    /**
     * Gets the initialization message.
     *
     * @return The init message.
     */
    public TInit getInit() {
        return init;
    }
}
