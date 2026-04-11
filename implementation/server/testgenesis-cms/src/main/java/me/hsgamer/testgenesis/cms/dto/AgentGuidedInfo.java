package me.hsgamer.testgenesis.cms.dto;

import java.util.List;

public record AgentGuidedInfo(String id, String displayName, List<TestTypeInfo> supportedTypes) {
}
