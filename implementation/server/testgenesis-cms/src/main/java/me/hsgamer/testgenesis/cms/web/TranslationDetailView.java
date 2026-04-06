package me.hsgamer.testgenesis.cms.web;

import io.jstach.jstache.JStache;

/**
 * JStachio view for translation details.
 */
@JStache(path = "me/hsgamer/testgenesis/cms/web/translation_detail.mustache")
public record TranslationDetailView(
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
