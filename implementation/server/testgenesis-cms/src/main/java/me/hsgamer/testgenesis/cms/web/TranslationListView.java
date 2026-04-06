package me.hsgamer.testgenesis.cms.web;

import io.jstach.jstache.JStache;
import java.util.List;

/**
 * JStachio view for listing translations.
 */
@JStache(path = "me/hsgamer/testgenesis/cms/web/translations.mustache")
public record TranslationListView(
        List<TranslationView> translations
) {
    /**
     * View-specific representation of a translation result.
     */
    public record TranslationView(
            Long id,
            String sourceTestName,
            String targetFormat,
            String agentName,
            String status,
            String requestedAt
    ) {}
}
