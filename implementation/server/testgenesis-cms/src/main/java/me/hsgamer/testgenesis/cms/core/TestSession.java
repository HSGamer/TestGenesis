package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import me.hsgamer.testgenesis.cms.util.StatusUtil;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TestSession implements Session<TestResponse> {

    @Getter
    private final String sessionId;
    @Getter
    private final TestTicket ticket;
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Telemetry> telemetryHistory = new CopyOnWriteArrayList<>();
    private final List<Consumer<TestStatus>> statusConsumers = new CopyOnWriteArrayList<>();

    private final List<Consumer<TestResult>> resultConsumers = new CopyOnWriteArrayList<>();
    private final List<Runnable> completionListeners = new CopyOnWriteArrayList<>();


    @Getter
    private volatile TestStatus status;
    @Getter
    private volatile TestResult result;

    public TestSession(String sessionId, TestTicket ticket) {
        this.sessionId = sessionId;
        this.ticket = ticket;
    }

    public void updateStatus(TestStatus status) {
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


    public void completeWithResult(TestResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        resultConsumers.forEach(consumer -> consumer.accept(result));
    }

    @Override
    public void handleResponse(TestResponse response) {
        switch (response.getEventCase()) {
            case STATUS -> updateStatus(response.getStatus());
            case TELEMETRY -> dispatchTelemetry(response.getTelemetry());
            case RESULT -> completeWithResult(response.getResult());
        }
    }

    @Override
    public void fail(String reason) {
        updateStatus(TestStatus.newBuilder()
                .setState(TestState.TEST_STATE_FAILED)
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

    public void addStatusConsumer(Consumer<TestStatus> consumer) {
        statusConsumers.add(consumer);
        if (status != null) {
            consumer.accept(status);
        }
    }

    public void removeStatusConsumer(Consumer<TestStatus> consumer) {
        statusConsumers.remove(consumer);
    }

    public void addResultConsumer(Consumer<TestResult> consumer) {
        resultConsumers.add(consumer);
        if (result != null) {
            consumer.accept(result);
        }
    }

    public void removeResultConsumer(Consumer<TestResult> consumer) {
        resultConsumers.remove(consumer);
    }

    public void onCompletion(Runnable callback) {
        if (status != null && StatusUtil.isTerminal(status.getState())) {
            callback.run();
        } else {
            completionListeners.add(callback);
        }
    }
}

