package me.hsgamer.testgenesis.client.processor;

import me.hsgamer.testgenesis.client.context.TestSessionContext;
import me.hsgamer.testgenesis.uap.v1.Capability;
import me.hsgamer.testgenesis.uap.v1.TestCapability;

/**
 * Interface for test execution logic.
 */
public interface TestSessionProcessor extends BaseProcessor {
    @Override
    default Capability getCapability() {
        return Capability.newBuilder()
                .setTest(getTestCapability())
                .build();
    }

    /**
     * Gets the specific test capability.
     *
     * @return The test capability.
     */
    TestCapability getTestCapability();

    /**
     * Processes a test session.
     *
     * @param sessionId The session ID.
     * @param context   The session context.
     * @throws Exception if an error occurs.
     */
    void process(String sessionId, TestSessionContext context) throws Exception;
}
