package me.hsgamer.testgenesis.cms.dto;

import java.util.List;

public record TestTypeInfo(String testType, List<String> requiredPayloadTypes, List<String> optionalPayloadTypes) {
}
