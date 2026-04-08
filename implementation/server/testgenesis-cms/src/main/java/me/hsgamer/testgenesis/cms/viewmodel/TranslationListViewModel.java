package me.hsgamer.testgenesis.cms.viewmodel;

import java.util.List;

public record TranslationListViewModel(
        List<TranslationView> translations
) {
    public record TranslationView(
            Long id,
            String sourceTestName,
            String targetFormat,
            String agentName,
            String status,
            String requestedAt
    ) {
    }
}
