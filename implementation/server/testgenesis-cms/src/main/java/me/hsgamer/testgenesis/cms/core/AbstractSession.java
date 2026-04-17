package me.hsgamer.testgenesis.cms.core;

import me.hsgamer.testgenesis.uap.v1.Telemetry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public abstract class AbstractSession<R> implements Session<R> {
    protected final String sessionId;
    protected final List<Telemetry> telemetryHistory = new CopyOnWriteArrayList<>();
    protected final Set<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArraySet<>();
    protected final Set<Runnable> completionCallbacks = new CopyOnWriteArraySet<>();

    protected AbstractSession(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void onCompletion(Runnable callback) {
        completionCallbacks.add(callback);
    }

    @Override
    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.add(consumer);
        telemetryHistory.forEach(consumer);
    }

    @Override
    public void removeTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.remove(consumer);
    }

    @Override
    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryHistory.add(telemetry);
        telemetryConsumers.forEach(consumer -> consumer.accept(telemetry));
    }

    protected void triggerCompletion() {
        completionCallbacks.forEach(Runnable::run);
        completionCallbacks.clear();
    }
}
