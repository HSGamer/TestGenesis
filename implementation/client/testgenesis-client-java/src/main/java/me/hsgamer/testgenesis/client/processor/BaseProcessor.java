package me.hsgamer.testgenesis.client.processor;

import me.hsgamer.testgenesis.uap.v1.Capability;

/**
 * Common interface for all specialized processors.
 */
public interface BaseProcessor {
    /**
     * Gets the capability of this processor.
     *
     * @return The capability.
     */
    Capability getCapability();
}

