package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TestSession {
    @Getter
    private final TestTicket ticket;
    
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TestStatus>> statusConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<TestResult>> resultConsumers = new CopyOnWriteArrayList<>();
    @Getter
    private Consumer<TestCommand> commandDispatcher;

    @Getter
    private volatile TestStatus status;
    @Getter
    private volatile TestResult result;

    public TestSession(TestTicket ticket) {
        this.ticket = ticket;
    }

    public void setCommandDispatcher(Consumer<TestCommand> dispatcher) {
        this.commandDispatcher = dispatcher;
    }

    public void updateStatus(TestStatus status) {
        this.status = status;
        statusConsumers.forEach(consumer -> consumer.accept(status));
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryConsumers.forEach(consumer -> consumer.accept(telemetry));
    }

    public void completeWithResult(TestResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        resultConsumers.forEach(consumer -> consumer.accept(result));
    }

    public void sendCommand(TestCommand command) {
        if (commandDispatcher != null) {
            commandDispatcher.accept(command);
        }
    }

    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.add(consumer);
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
}
