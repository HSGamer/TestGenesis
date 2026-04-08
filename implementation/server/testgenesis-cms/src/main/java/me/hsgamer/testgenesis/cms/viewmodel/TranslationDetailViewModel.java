package me.hsgamer.testgenesis.cms.viewmodel;

public record TranslationDetailViewModel(
        Long id,
        String sourceTestName,
        String targetFormat,
        String agentName,
        String status,
        String requestedAt,
        String completedAt,
        String telemetryLog,
        Long resultPayloadId
) {
}
