package me.hsgamer.testgenesis.cms.core;

import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.function.Consumer;

public interface TranslationSession {
    void addTelemetryConsumer(Consumer<Telemetry> telemetry);

    void addStatusConsumer(Consumer<TranslationStatus> status);

    void addResultConsumer(Consumer<TranslationResult> result);

    TranslationStatus getStatus();

    TranslationResult getResult();
}
