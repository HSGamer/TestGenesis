package me.hsgamer.testgenesis.cms.dto;

import java.util.List;

public record AgentInfo(
    String id,
    String displayName,
    List<TestTypeInfo> supportedTests,
    List<TranslationTypeInfo> supportedTranslations
) {
    public boolean supportsTestType(String testType) {
        return supportedTests.stream().anyMatch(st -> st.testType().equals(testType));
    }

    public boolean supportsTranslationType(String type) {
        return supportedTranslations.stream().anyMatch(st -> st.type().equals(type));
    }
}
