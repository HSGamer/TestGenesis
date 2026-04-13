package me.hsgamer.testgenesis.cms.core;

import me.hsgamer.testgenesis.uap.v1.Capability;

import java.util.List;

public interface Agent {
    String id();

    String displayName();

    List<Capability> capabilities();

    boolean isReady();

    boolean supportsTestType(String testType);
}
