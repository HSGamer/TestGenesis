package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TranslationSession {
    @Getter
    private final TranslationTicket ticket;
    
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TranslationStatus>> statusConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TranslationResult>> resultConsumers = new CopyOnWriteArrayList<>();

    @Getter
    private volatile TranslationStatus status;
    @Getter
    private volatile TranslationResult result;

    public TranslationSession(TranslationTicket ticket) {
        this.ticket = ticket;
    }

    public void updateStatus(TranslationStatus status) {
        this.status = status;
        statusConsumers.forEach(consumer -> consumer.accept(status));
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryConsumers.forEach(consumer -> consumer.accept(telemetry));
    }

    public void completeWithResult(TranslationResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        resultConsumers.forEach(consumer -> consumer.accept(result));
    }

    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.add(consumer);
    }

    public void removeTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.remove(consumer);
    }

    public void addStatusConsumer(Consumer<TranslationStatus> consumer) {
        statusConsumers.add(consumer);
        if (status != null) {
            consumer.accept(status);
        }
    }

    public void removeStatusConsumer(Consumer<TranslationStatus> consumer) {
        statusConsumers.remove(consumer);
    }

    public void addResultConsumer(Consumer<TranslationResult> consumer) {
        resultConsumers.add(consumer);
        if (result != null) {
            consumer.accept(result);
        }
    }
}
