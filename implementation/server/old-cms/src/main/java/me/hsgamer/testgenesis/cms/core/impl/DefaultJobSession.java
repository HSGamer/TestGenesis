package me.hsgamer.testgenesis.cms.core.impl;

import io.grpc.stub.StreamObserver;
import lombok.Getter;
import me.hsgamer.testgenesis.cms.core.JobSession;
import me.hsgamer.testgenesis.cms.core.JobTicket;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultJobSession implements JobSession {
    @Getter
    private final JobTicket ticket;
    private final List<Consumer<Telemetry>> telemetryConsumers = new CopyOnWriteArrayList<>();
    private final List<Consumer<JobStatus>> statusConsumers = new CopyOnWriteArrayList<>();
    private volatile StreamObserver<JobInstruction> instructionStream;
    @Getter
    private volatile JobStatus status;
    @Getter
    private volatile JobResult result;

    public DefaultJobSession(JobTicket ticket) {
        this.ticket = ticket;
    }

    public void attachInstructionStream(StreamObserver<JobInstruction> stream) {
        this.instructionStream = stream;
    }

    public void updateStatus(JobStatus status) {
        this.status = status;
        for (Consumer<JobStatus> consumer : statusConsumers) {
            consumer.accept(status);
        }
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        for (Consumer<Telemetry> consumer : telemetryConsumers) {
            consumer.accept(telemetry);
        }
    }

    public void completeWithResult(JobResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
    }

    @Override
    public void sendCommand(JobCommand command) {
        StreamObserver<JobInstruction> stream = instructionStream;
        if (stream != null) {
            stream.onNext(JobInstruction.newBuilder().setCommand(command).build());
        }
    }

    @Override
    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryConsumers.add(consumer);
    }

    @Override
    public void addStatusConsumer(Consumer<JobStatus> consumer) {
        statusConsumers.add(consumer);
    }
}
