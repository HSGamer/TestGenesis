package me.hsgamer.testgenesis.cms.dto;

import java.util.List;

public record AgentTranslationInfo(String id, String displayName, List<TranslationTypeInfo> supportedTranslations) {
    public boolean supportsTranslationType(String testType) {
        return supportedTranslations.stream().anyMatch(st -> st.type().equals(testType));
    }
}
