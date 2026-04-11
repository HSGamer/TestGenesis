package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class JobSession {
    @Getter
    private final JobTicket ticket;
    
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<JobStatus>> statusConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<JobResult>> resultConsumers = new CopyOnWriteArrayList<>();
    @Getter
    private Consumer<JobCommand> commandDispatcher;

    @Getter
    private volatile JobStatus status;
    @Getter
    private volatile JobResult result;

    public JobSession(JobTicket ticket) {
        this.ticket = ticket;
    }

    public void setCommandDispatcher(Consumer<JobCommand> dispatcher) {
        this.commandDispatcher = dispatcher;
    }

    public void updateStatus(JobStatus status) {
        this.status = status;
        statusConsumers.forEach(consumer -> consumer.accept(status));
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryConsumers.forEach(consumer -> consumer.accept(telemetry));
    }

    public void completeWithResult(JobResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
        resultConsumers.forEach(consumer -> consumer.accept(result));
    }

    public void sendCommand(JobCommand command) {
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

    public void addStatusConsumer(Consumer<JobStatus> consumer) {
        statusConsumers.add(consumer);
        if (status != null) {
            consumer.accept(status);
        }
    }
    
    public void removeStatusConsumer(Consumer<JobStatus> consumer) {
        statusConsumers.remove(consumer);
    }
    
    public void addResultConsumer(Consumer<JobResult> consumer) {
        resultConsumers.add(consumer);
        if (result != null) {
            consumer.accept(result);
        }
    }
}
