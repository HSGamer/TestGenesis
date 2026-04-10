package me.hsgamer.testgenesis.cms.core.impl;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import lombok.Getter;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.function.Consumer;

public class DefaultTranslationSession implements TranslationSession {
    @Getter
    private final TranslationTicket ticket;
    private final BroadcastProcessor<Telemetry> telemetryProcessor = BroadcastProcessor.create();
    private final BroadcastProcessor<TranslationStatus> statusProcessor = BroadcastProcessor.create();
    private final BroadcastProcessor<TranslationResult> resultProcessor = BroadcastProcessor.create();

    @Getter
    private volatile TranslationStatus status;
    @Getter
    private volatile TranslationResult result;

    public DefaultTranslationSession(TranslationTicket ticket) {
        this.ticket = ticket;
    }

    public Multi<TranslationStatus> statusStream() {
        return statusProcessor;
    }

    public Multi<Telemetry> telemetryStream() {
        return telemetryProcessor;
    }

    public Multi<TranslationResult> resultStream() {
        return resultProcessor;
    }

    public void updateStatus(TranslationStatus status) {
        this.status = status;
        statusProcessor.onNext(status);
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryProcessor.onNext(telemetry);
    }

    public void completeWithResult(TranslationResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        resultProcessor.onNext(result);
    }

    @Override
    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryStream().subscribe().with(consumer);
    }

    @Override
    public void addStatusConsumer(Consumer<TranslationStatus> consumer) {
        statusStream().subscribe().with(consumer);
    }

    @Override
    public void addResultConsumer(Consumer<TranslationResult> consumer) {
        resultStream().subscribe().with(consumer);
    }
}
