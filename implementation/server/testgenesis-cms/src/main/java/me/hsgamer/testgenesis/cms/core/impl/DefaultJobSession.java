package me.hsgamer.testgenesis.cms.core.impl;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import lombok.Getter;
import me.hsgamer.testgenesis.cms.core.JobSession;
import me.hsgamer.testgenesis.cms.core.JobTicket;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.function.Consumer;

public class DefaultJobSession implements JobSession {
    @Getter
    private final JobTicket ticket;
    private final BroadcastProcessor<Telemetry> telemetryProcessor = BroadcastProcessor.create();
    private final BroadcastProcessor<JobStatus> statusProcessor = BroadcastProcessor.create();
    private final BroadcastProcessor<JobInstruction> instructionProcessor = BroadcastProcessor.create();

    @Getter
    private volatile JobStatus status;
    @Getter
    private volatile JobResult result;

    public DefaultJobSession(JobTicket ticket) {
        this.ticket = ticket;
    }

    public Multi<JobInstruction> instructionStream() {
        return instructionProcessor;
    }

    public Multi<JobStatus> statusStream() {
        return statusProcessor;
    }

    public Multi<Telemetry> telemetryStream() {
        return telemetryProcessor;
    }

    public void updateStatus(JobStatus status) {
        this.status = status;
        statusProcessor.onNext(status);
    }

    public void dispatchTelemetry(Telemetry telemetry) {
        telemetryProcessor.onNext(telemetry);
    }

    public void completeWithResult(JobResult result) {
        this.result = result;
        if (result.hasStatus()) {
            updateStatus(result.getStatus());
        }
    }

    @Override
    public void sendCommand(JobCommand command) {
        instructionProcessor.onNext(JobInstruction.newBuilder().setCommand(command).build());
    }

    @Override
    public void addTelemetryConsumer(Consumer<Telemetry> consumer) {
        telemetryStream().subscribe().with(consumer);
    }

    @Override
    public void addStatusConsumer(Consumer<JobStatus> consumer) {
        statusStream().subscribe().with(consumer);
    }
}
