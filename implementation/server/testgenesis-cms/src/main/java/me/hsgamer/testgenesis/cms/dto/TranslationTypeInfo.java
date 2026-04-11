package me.hsgamer.testgenesis.cms.dto;

import java.util.List;

public record TranslationTypeInfo(String type, List<String> sourcePayloadTypes, List<String> targetPayloadTypes) {
}
