package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import me.hsgamer.testgenesis.cms.util.StatusUtil;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TranslationSession implements Session<TranslationResponse> {
    @Getter
    private final String sessionId;
    @Getter
    private final TranslationTicket ticket;
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Telemetry> telemetryHistory = new CopyOnWriteArrayList<>();
    private final List<Consumer<TranslationStatus>> statusConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TranslationResult>> resultConsumers = new CopyOnWriteArrayList<>();
    @Getter
    private final List<Payload> rawPayloads = new CopyOnWriteArrayList<>();
    private final List<Runnable> completionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<List<Payload>>> rawPayloadConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<List<GeneratedPayload>>> resultPayloadConsumers = new CopyOnWriteArrayList<>();
    @Getter
    private volatile TranslationStatus status;
    @Getter
    private volatile TranslationResult result;

    public TranslationSession(String sessionId, TranslationTicket ticket) {
        this.sessionId = sessionId;
        this.ticket = ticket;
    }

    public void updateStatus(TranslationStatus status) {
        this.status = status;
        statusConsumers.forEach(consumer -> consumer.accept(status));

        if (StatusUtil.isTerminal(status.getState())) {
            completionListeners.forEach(Runnable::run);
            completionListeners.clear();
        }
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryHistory.add(telemetry);
        telemetryConsumers.forEach(consumer -> consumer.accept(telemetry));
    }

    public void completeWithResult(TranslationResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        dispatchRawPayloads(result.getPayloadsList());

        List<GeneratedPayload> records = new ArrayList<>();
        for (int i = 0; i < result.getPayloadsCount(); i++) {
            Payload p = result.getPayloads(i);
            records.add(new GeneratedPayload(i, p.hasAttachment() ? p.getAttachment().getName() : "unnamed", p.getType()));
        }
        resultPayloadConsumers.forEach(c -> c.accept(records));

        resultConsumers.forEach(consumer -> consumer.accept(result));
    }

    @Override
    public void handleResponse(TranslationResponse response) {
        switch (response.getEventCase()) {
            case STATUS -> updateStatus(response.getStatus());
            case TELEMETRY -> dispatchTelemetry(response.getTelemetry());
            case RESULT -> completeWithResult(response.getResult());
        }
    }

    @Override
    public void fail(String reason) {
        updateStatus(TranslationStatus.newBuilder()
            .setState(TranslationState.TRANSLATION_STATE_FAILED)
            .setMessage(reason)
            .build());
    }

    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.add(consumer);
        telemetryHistory.forEach(consumer);
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

    public void addRawPayloadConsumer(Consumer<List<Payload>> consumer) {
        rawPayloadConsumers.add(consumer);
        if (!rawPayloads.isEmpty()) {
            consumer.accept(rawPayloads);
        }
    }

    public void removeRawPayloadConsumer(Consumer<List<Payload>> consumer) {
        rawPayloadConsumers.remove(consumer);
    }

    public void addResultPayloadConsumer(Consumer<List<GeneratedPayload>> consumer) {
        resultPayloadConsumers.add(consumer);
        if (result != null) {
            List<GeneratedPayload> records = new ArrayList<>();
            for (int i = 0; i < result.getPayloadsCount(); i++) {
                Payload p = result.getPayloads(i);
                records.add(new GeneratedPayload(i, p.hasAttachment() ? p.getAttachment().getName() : "unnamed", p.getType()));
            }
            consumer.accept(records);
        }
    }

    public void removeResultPayloadConsumer(Consumer<List<GeneratedPayload>> consumer) {
        resultPayloadConsumers.remove(consumer);
    }

    public void dispatchRawPayloads(List<Payload> payloads) {
        this.rawPayloads.addAll(payloads);
        rawPayloadConsumers.forEach(consumer -> consumer.accept(payloads));
    }

    public void onCompletion(Runnable callback) {
        if (status != null && StatusUtil.isTerminal(status.getState())) {
            callback.run();
        } else {
            completionListeners.add(callback);
        }
    }

    public record GeneratedPayload(int index, String name, String type) {
    }
}
