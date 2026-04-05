package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.stub.StreamObserver;

/**
 * A reusable, generic StreamObserver that ignores all incoming events.
 * Useful for handling error states where a return value is still required.
 */
public class NoOpStreamObserver<T> implements StreamObserver<T> {
    private static final NoOpStreamObserver<Object> INSTANCE = new NoOpStreamObserver<>();

    @SuppressWarnings("unchecked")
    public static <T> StreamObserver<T> getInstance() {
        return (StreamObserver<T>) INSTANCE;
    }

    @Override
    public void onNext(T value) {
        // Do nothing
    }

    @Override
    public void onError(Throwable t) {
        // Do nothing
    }

    @Override
    public void onCompleted() {
        // Do nothing
    }
}
