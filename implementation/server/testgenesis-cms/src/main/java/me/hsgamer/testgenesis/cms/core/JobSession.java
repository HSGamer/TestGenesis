package me.hsgamer.testgenesis.cms.core;

import me.hsgamer.testgenesis.uap.v1.JobCommand;
import me.hsgamer.testgenesis.uap.v1.JobResult;
import me.hsgamer.testgenesis.uap.v1.JobStatus;
import me.hsgamer.testgenesis.uap.v1.Telemetry;

import java.util.function.Consumer;

public interface JobSession {
    void sendCommand(JobCommand jobCommand);

    void addTelemetryConsumer(Consumer<Telemetry> telemetry);

    void addStatusConsumer(Consumer<JobStatus> status);

    JobStatus getStatus();

    JobResult getResult();
}
