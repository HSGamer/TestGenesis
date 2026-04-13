package me.hsgamer.testgenesis.cms.core;

import me.hsgamer.testgenesis.uap.v1.Telemetry;

import java.util.function.Consumer;

public interface Session<R> {
    String getSessionId();

    void onCompletion(Runnable callback);

    void addTelemetryConsumer(Consumer<Telemetry> consumer);

    void removeTelemetryConsumer(Consumer<Telemetry> consumer);

    void dispatchTelemetry(Telemetry telemetry);

    void handleResponse(R response);

    void fail(String reason);
}
