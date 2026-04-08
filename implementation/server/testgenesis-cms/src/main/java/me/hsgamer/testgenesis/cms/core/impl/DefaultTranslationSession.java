package me.hsgamer.testgenesis.cms.core.impl;

import lombok.Getter;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultTranslationSession implements TranslationSession {
    @Getter
    private final TranslationTicket ticket;
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TranslationStatus>> statusConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TranslationResult>> resultConsumers = new CopyOnWriteArrayList<>();
    @Getter
    private volatile TranslationStatus status;
    @Getter
    private volatile TranslationResult result;

    public DefaultTranslationSession(TranslationTicket ticket) {
        this.ticket = ticket;
    }

    public void updateStatus(TranslationStatus status) {
        this.status = status;
        for (Consumer<TranslationStatus> consumer : statusConsumers) {
            consumer.accept(status);
        }
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        for (Consumer<Telemetry> consumer : telemetryConsumers) {
            consumer.accept(telemetry);
        }
    }

    public void completeWithResult(TranslationResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        for (Consumer<TranslationResult> consumer : resultConsumers) {
            consumer.accept(result);
        }
    }

    @Override
    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.add(consumer);
    }

    @Override
    public void addStatusConsumer(Consumer<TranslationStatus> consumer) {
        statusConsumers.add(consumer);
    }

    @Override
    public void addResultConsumer(Consumer<TranslationResult> consumer) {
        resultConsumers.add(consumer);
    }
}
